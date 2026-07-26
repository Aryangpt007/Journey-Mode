package com.aryangpt007.journeymode.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import net.minecraft.item.Item;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Configuration handler for Journey Mode.
 *
 * blacklist.json and custom_thresholds.json each accept three key shapes, checked in this
 * priority order: exact item id > item tag > regex/wildcard pattern.
 *
 * 1.12.2 GAP: there is no vanilla Tags system pre-1.13 - so "tag" rules here (a key starting
 * with '#', e.g. "#c:ingots") are mapped onto OreDictionary entries instead, via a small
 * built-in translation table for common categories (ingots/gems/dusts/nuggets/plates as a
 * starting set - see TAG_TO_OREDICT). A tag key that doesn't map to a known category logs a
 * startup warning and is ignored entirely (not silently no-op'd without explanation).
 *
 * 1.12.2 GAP: the datapack-threshold-loader concept (data/&lt;namespace&gt;/journeymode/thresholds/
 * *.json, reloadable via IReloadableResourceManager) has no real equivalent on 1.12.2 - there is
 * no vanilla "data pack" layer distinct from asset/resource packs that a server owner can drop
 * JSON into at runtime the way 1.13+ data packs work; Forge 1.12.2's CraftingManager/recipe
 * loading is not a generic reloadable JSON layer third parties can target for arbitrary threshold
 * data. That layer is intentionally NOT implemented here. The config-file rule layer below (exact
 * id / OreDictionary tag / regex-wildcard, plus default_override) fully replaces its value for
 * this version, and the dev-API ThresholdProvider hook covers the "another mod wants to suggest a
 * threshold" use case that datapacks would otherwise serve.
 *
 * Thresholds additionally check a "default_override" config key (used by /journeymode all)
 * before falling through to the dev-API ThresholdProvider layer, then the recipe calculator.
 */
public class ConfigHandler {

    private static final Logger LOGGER = LogManager.getLogger("journeymode");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File CONFIG_DIR = new File("config/Journey Mode");
    private static final File BLACKLIST_FILE = new File(CONFIG_DIR, "blacklist.json");
    private static final File THRESHOLDS_FILE = new File(CONFIG_DIR, "custom_thresholds.json");

    public static final int DEFAULT_MAX_THRESHOLD_CAP = 64;

    // ---------------------------------------------------------------- 1.12.2 tag -> OreDictionary translation
    // Small built-in table covering common categories. Unmapped tag rules are logged and ignored -
    // see class javadoc. Extend this table if a widely-used category is missing.
    private static final Map<String, List<String>> TAG_TO_OREDICT = buildTagToOreDictTable();

    private static Map<String, List<String>> buildTagToOreDictTable() {
        Map<String, List<String>> table = new HashMap<String, List<String>>();
        table.put("ingots", Arrays.asList("ingotIron", "ingotGold", "ingotCopper", "ingotTin",
            "ingotLead", "ingotSilver", "ingotNickel", "ingotBronze", "ingotBrass", "ingotSteel",
            "ingotInvar", "ingotElectrum", "ingotConstantan", "ingotAluminum", "ingotZinc"));
        table.put("gems", Arrays.asList("gemDiamond", "gemEmerald", "gemQuartz", "gemAmethyst",
            "gemPeridot", "gemRuby", "gemSapphire", "gemApatite", "gemTopaz", "gemMalachite",
            "gemLapis", "gemCertusQuartz"));
        table.put("dusts", Arrays.asList("dustIron", "dustGold", "dustCopper", "dustTin",
            "dustLead", "dustSilver", "dustCoal", "dustRedstone", "dustGlowstone", "dustSulfur",
            "dustObsidian"));
        table.put("nuggets", Arrays.asList("nuggetIron", "nuggetGold", "nuggetCopper", "nuggetTin",
            "nuggetLead", "nuggetSilver"));
        table.put("plates", Arrays.asList("plateIron", "plateGold", "plateCopper", "plateTin",
            "plateLead", "plateSilver", "plateSteel", "plateBronze", "plateAluminum"));
        return Collections.unmodifiableMap(table);
    }

    /** Resolved OreDictionary names standing in for one "#tag" rule, kept for logging/sync. */
    private static final class OreDictRule {
        final String tagKey;
        final List<String> oreDictNames;
        OreDictRule(String tagKey, List<String> oreDictNames) {
            this.tagKey = tagKey;
            this.oreDictNames = oreDictNames;
        }
    }

    // ---- Resolved rule storage (rebuilt on every load/reload) ----
    private static final class Rules {
        Set<String> exactBlacklist = new HashSet<String>();
        List<OreDictRule> tagBlacklist = new ArrayList<OreDictRule>();
        List<Pattern> patternBlacklist = new ArrayList<Pattern>();

        Map<String, Integer> exactThresholds = new LinkedHashMap<String, Integer>();
        List<Map.Entry<OreDictRule, Integer>> tagThresholds = new ArrayList<Map.Entry<OreDictRule, Integer>>();
        List<Map.Entry<Pattern, Integer>> patternThresholds = new ArrayList<Map.Entry<Pattern, Integer>>();

        Integer defaultOverride = null;
        int maxThresholdCap = DEFAULT_MAX_THRESHOLD_CAP;
    }

    private static volatile Rules rules = new Rules();

    // Bumped whenever `rules` is replaced (reload or synced-from-server). Common-safe way for
    // client-only caches (e.g. CatalogStatsCache, since blacklist affects "total researchable")
    // to know when to recompute, without ConfigHandler ever referencing client-package classes
    // directly - that reference would be live on a dedicated server too and risk a
    // NoClassDefFoundError there.
    private static volatile int rulesGeneration = 0;

    public static int getRulesGeneration() {
        return rulesGeneration;
    }

    // Per-item resolution cache; cleared whenever config reloads.
    private static final Map<String, Boolean> resolvedBlacklistCache = new HashMap<String, Boolean>();
    private static final Map<String, Optional<Integer>> resolvedThresholdCache = new HashMap<String, Optional<Integer>>();

    // Lazily-built reverse index: Item -> every OreDictionary name it is registered under.
    // Rebuilt on reload since OreDictionary registrations don't change mid-session anyway.
    private static Map<Item, Set<String>> itemToOreDictNames = null;

    /**
     * Register the config and ensure directories and files exist
     */
    public static void register() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        initializeBlacklistFile();
        initializeThresholdsFile();
        reloadAll();
    }

    /** Called by /journeymode reloadconfig and by config-file writers after a mutation. */
    public static synchronized void reloadAll() {
        Rules loaded = new Rules();
        loadBlacklistInto(loaded);
        loadThresholdsInto(loaded);
        rules = loaded;
        rulesGeneration++;
        resolvedThresholdCache.clear();
        resolvedBlacklistCache.clear();
        itemToOreDictNames = null;
        LOGGER.info("Journey Mode config (re)loaded: {} exact/{} tag/{} pattern blacklist rules, " +
            "{} exact/{} tag/{} pattern threshold rules, default_override={}, max_threshold_cap={}",
            loaded.exactBlacklist.size(), loaded.tagBlacklist.size(), loaded.patternBlacklist.size(),
            loaded.exactThresholds.size(), loaded.tagThresholds.size(), loaded.patternThresholds.size(),
            loaded.defaultOverride, loaded.maxThresholdCap);
    }

    // ---------------------------------------------------------------- init/default files

    private static void initializeBlacklistFile() {
        if (!BLACKLIST_FILE.exists()) {
            JsonObject defaultBlacklist = new JsonObject();
            defaultBlacklist.addProperty("_comment",
                "Blacklist entries accept three forms, checked in this order: exact item id " +
                "('minecraft:bedrock'), an OreDictionary-backed tag ('#c:ingots' - see the " +
                "ConfigHandler class comment for supported categories on 1.12.2), or a " +
                "regex/wildcard pattern ('somemod:.*_dust'). All three live in the same " +
                "'blacklisted_items' list.");

            JsonArray items = new JsonArray();
            items.add("minecraft:bedrock");
            items.add("minecraft:barrier");
            items.add("minecraft:command_block");
            defaultBlacklist.add("blacklisted_items", items);

            writeJsonAtomically(BLACKLIST_FILE, defaultBlacklist);
            LOGGER.info("Created default blacklist.json");
        }
    }

    private static void initializeThresholdsFile() {
        if (!THRESHOLDS_FILE.exists()) {
            JsonObject defaultThresholds = new JsonObject();
            defaultThresholds.addProperty("_comment",
                "Keys accept three forms, checked in this order: exact item id, OreDictionary " +
                "tag ('#c:ingots'), or a regex/wildcard pattern ('somemod:.*_circuit'). " +
                "'default_override' (if set) applies to every item with no matching rule, " +
                "before falling back to the recipe-depth calculator. 'max_threshold_cap' bounds " +
                "what /journeymode threshold and /journeymode all will accept.");

            JsonObject thresholds = new JsonObject();
            thresholds.addProperty("minecraft:diamond", 10);
            thresholds.addProperty("minecraft:nether_star", 1);
            defaultThresholds.add("thresholds", thresholds);
            defaultThresholds.add("default_override", JsonNull.INSTANCE);
            defaultThresholds.addProperty("max_threshold_cap", DEFAULT_MAX_THRESHOLD_CAP);

            writeJsonAtomically(THRESHOLDS_FILE, defaultThresholds);
            LOGGER.info("Created default custom_thresholds.json");
        }
    }

    // ---------------------------------------------------------------- loading

    private static void loadBlacklistInto(Rules target) {
        JsonObject json = readJson(BLACKLIST_FILE);
        if (json == null || !json.has("blacklisted_items")) return;

        for (JsonElement element : json.getAsJsonArray("blacklisted_items")) {
            String key = element.getAsString();
            classifyRuleKeyForBlacklist(key, target);
        }
    }

    private static void loadThresholdsInto(Rules target) {
        JsonObject json = readJson(THRESHOLDS_FILE);
        if (json == null) return;

        if (json.has("thresholds")) {
            JsonObject thresholds = json.getAsJsonObject("thresholds");
            for (Map.Entry<String, JsonElement> entry : thresholds.entrySet()) {
                int value = entry.getValue().getAsInt();
                classifyRuleKeyForThreshold(entry.getKey(), value, target);
            }
        }

        if (json.has("default_override") && json.get("default_override").isJsonPrimitive()) {
            target.defaultOverride = Math.max(1, json.get("default_override").getAsInt());
        }

        if (json.has("max_threshold_cap") && json.get("max_threshold_cap").isJsonPrimitive()) {
            target.maxThresholdCap = Math.max(1, json.get("max_threshold_cap").getAsInt());
        }
        // NOTE: "show_tooltips" is intentionally no longer read here - it moved from a global
        // config value to a per-player field (IJourneyData#isShowTooltips/setShowTooltips). A
        // leftover "show_tooltips" key in an existing custom_thresholds.json is simply ignored.
    }

    private static void classifyRuleKeyForBlacklist(String key, Rules target) {
        if (key.startsWith("#")) {
            OreDictRule rule = resolveTagRule(key);
            if (rule != null) target.tagBlacklist.add(rule);
            return;
        }
        if (isPlainExactId(key)) {
            target.exactBlacklist.add(key);
            return;
        }
        try {
            target.patternBlacklist.add(compilePatternRule(key));
        } catch (PatternSyntaxException e) {
            LOGGER.warn("Ignoring invalid pattern rule '{}': {}", key, e.getMessage());
        }
    }

    private static void classifyRuleKeyForThreshold(String key, int value, Rules target) {
        if (key.startsWith("#")) {
            OreDictRule rule = resolveTagRule(key);
            if (rule != null) target.tagThresholds.add(new AbstractMap.SimpleEntry<OreDictRule, Integer>(rule, value));
            return;
        }
        if (isPlainExactId(key)) {
            target.exactThresholds.put(key, value);
            return;
        }
        try {
            target.patternThresholds.add(new AbstractMap.SimpleEntry<Pattern, Integer>(compilePatternRule(key), value));
        } catch (PatternSyntaxException e) {
            LOGGER.warn("Ignoring invalid pattern rule '{}': {}", key, e.getMessage());
        }
    }

    /**
     * Resolve a "#tagId" rule key onto a known OreDictionary category. Returns null (and logs a
     * warning) if the tag path isn't in TAG_TO_OREDICT - see class javadoc for the documented gap.
     */
    @Nullable
    private static OreDictRule resolveTagRule(String key) {
        String tagId = key.substring(1);
        String path = tagId;
        int colon = tagId.indexOf(':');
        if (colon >= 0) {
            path = tagId.substring(colon + 1);
        }
        List<String> oreDictNames = TAG_TO_OREDICT.get(path.toLowerCase(Locale.ROOT));
        if (oreDictNames == null) {
            LOGGER.warn("Ignoring tag rule '{}' - no built-in OreDictionary mapping for '{}' on 1.12.2 " +
                "(supported categories: {}). See ConfigHandler class comment.", key, path, TAG_TO_OREDICT.keySet());
            return null;
        }
        return new OreDictRule(tagId, oreDictNames);
    }

    private static boolean isPlainExactId(String key) {
        return key.matches("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");
    }

    /**
     * Compile a rule key that isn't an exact id or a tag into a Pattern. If it only contains
     * glob-safe characters (letters/digits/id punctuation plus '*'), '*' is treated as a glob
     * wildcard and everything else is escaped literally. Otherwise the key is compiled as a
     * raw regex as-is.
     */
    private static Pattern compilePatternRule(String key) {
        boolean globSafe = key.matches("[a-zA-Z0-9_./:\\-*]+");
        if (!globSafe) {
            return Pattern.compile(key);
        }
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    // ---------------------------------------------------------------- OreDictionary reverse index

    private static Map<Item, Set<String>> getItemOreDictIndex() {
        Map<Item, Set<String>> index = itemToOreDictNames;
        if (index == null) {
            index = new HashMap<Item, Set<String>>();
            for (String oreName : OreDictionary.getOreNames()) {
                for (net.minecraft.item.ItemStack stack : OreDictionary.getOres(oreName)) {
                    if (stack.isEmpty()) continue;
                    Set<String> names = index.get(stack.getItem());
                    if (names == null) {
                        names = new HashSet<String>();
                        index.put(stack.getItem(), names);
                    }
                    names.add(oreName);
                }
            }
            itemToOreDictNames = index;
        }
        return index;
    }

    private static boolean itemMatchesOreDictRule(Item item, OreDictRule rule) {
        Set<String> namesForItem = getItemOreDictIndex().get(item);
        if (namesForItem == null || namesForItem.isEmpty()) return false;
        for (String name : rule.oreDictNames) {
            if (namesForItem.contains(name)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- queries

    public static boolean isBlacklisted(Item item) {
        String itemId = itemId(item);
        if (itemId == null) return false;

        Boolean cached = resolvedBlacklistCache.get(itemId);
        if (cached != null) return cached;

        Rules r = rules;
        boolean result = r.exactBlacklist.contains(itemId);
        if (!result) {
            for (OreDictRule rule : r.tagBlacklist) {
                if (itemMatchesOreDictRule(item, rule)) { result = true; break; }
            }
        }
        if (!result) {
            for (Pattern p : r.patternBlacklist) {
                if (p.matcher(itemId).matches()) { result = true; break; }
            }
        }

        resolvedBlacklistCache.put(itemId, result);
        return result;
    }

    /** Legacy string-only check, kept for callers that only have an item id string on hand. */
    public static boolean isBlacklisted(String itemId) {
        Rules r = rules;
        if (r.exactBlacklist.contains(itemId)) return true;
        for (Pattern p : r.patternBlacklist) {
            if (p.matcher(itemId).matches()) return true;
        }
        return false;
    }

    /**
     * Full precedence chain: exact > tag(OreDictionary) > regex/wildcard > default_override >
     * dev-API ThresholdProvider > (null, meaning "let the calculator decide").
     */
    @Nullable
    public static Integer getThresholdOverride(Item item) {
        String itemId = itemId(item);
        if (itemId == null) return null;

        Optional<Integer> cached = resolvedThresholdCache.get(itemId);
        if (cached != null) return cached.orElse(null);

        Integer resolved = resolveThreshold(item, itemId);
        resolvedThresholdCache.put(itemId, Optional.ofNullable(resolved));
        return resolved;
    }

    private static Integer resolveThreshold(Item item, String itemId) {
        Rules r = rules;

        Integer exact = r.exactThresholds.get(itemId);
        if (exact != null) return Math.max(1, exact);

        for (Map.Entry<OreDictRule, Integer> entry : r.tagThresholds) {
            if (itemMatchesOreDictRule(item, entry.getKey())) {
                return Math.max(1, entry.getValue());
            }
        }

        for (Map.Entry<Pattern, Integer> entry : r.patternThresholds) {
            if (entry.getKey().matcher(itemId).matches()) {
                return Math.max(1, entry.getValue());
            }
        }

        if (r.defaultOverride != null) {
            return r.defaultOverride;
        }

        Integer apiValue = com.aryangpt007.journeymode.api.JourneyModeAPI.queryThresholdProviders(itemId);
        if (apiValue != null) {
            return Math.max(1, apiValue);
        }

        return null; // calculator decides
    }

    /**
     * Get the threshold override for an exact key (base item id, e.g. "minecraft:diamond", OR a
     * full hybrid sub-type key, e.g. "minecraft:potion|{Potion:...}"). Exact-only lookup - tag and
     * pattern rules only apply to base item ids and are resolved via {@link #getThresholdOverride(Item)}.
     * Used for hybrid-key overrides written by "/journeymode threshold hand".
     */
    @Nullable
    public static Integer getThresholdOverride(String key) {
        Integer value = rules.exactThresholds.get(key);
        return value == null ? null : Math.max(1, value);
    }

    public static int getMaxThresholdCap() {
        return rules.maxThresholdCap;
    }

    @Nullable
    public static Integer getDefaultOverride() {
        return rules.defaultOverride;
    }

    /**
     * Get all blacklisted items (for debugging/admin purposes)
     */
    public static Set<String> getBlacklist() {
        return Collections.unmodifiableSet(rules.exactBlacklist);
    }

    /**
     * Get all threshold overrides (for debugging/admin purposes)
     */
    public static Map<String, Integer> getThresholdOverrides() {
        return Collections.unmodifiableMap(rules.exactThresholds);
    }

    private static String itemId(Item item) {
        return item.getRegistryName() == null ? null : item.getRegistryName().toString();
    }

    // ---------------------------------------------------------------- §2 network sync (server -> client)

    /** Raw-string snapshot of the current rules, suitable for putting on the wire. */
    public static final class SyncSnapshot {
        private final List<String> exactBlacklist;
        private final List<String> tagBlacklist;
        private final List<String> patternBlacklist;
        private final Map<String, Integer> exactThresholds;
        private final List<Map.Entry<String, Integer>> tagThresholds;
        private final List<Map.Entry<String, Integer>> patternThresholds;
        private final Integer defaultOverride;
        private final int maxThresholdCap;

        public SyncSnapshot(List<String> exactBlacklist, List<String> tagBlacklist, List<String> patternBlacklist,
                             Map<String, Integer> exactThresholds, List<Map.Entry<String, Integer>> tagThresholds,
                             List<Map.Entry<String, Integer>> patternThresholds,
                             Integer defaultOverride, int maxThresholdCap) {
            this.exactBlacklist = exactBlacklist;
            this.tagBlacklist = tagBlacklist;
            this.patternBlacklist = patternBlacklist;
            this.exactThresholds = exactThresholds;
            this.tagThresholds = tagThresholds;
            this.patternThresholds = patternThresholds;
            this.defaultOverride = defaultOverride;
            this.maxThresholdCap = maxThresholdCap;
        }

        public List<String> getExactBlacklist() { return exactBlacklist; }
        public List<String> getTagBlacklist() { return tagBlacklist; }
        public List<String> getPatternBlacklist() { return patternBlacklist; }
        public Map<String, Integer> getExactThresholds() { return exactThresholds; }
        public List<Map.Entry<String, Integer>> getTagThresholds() { return tagThresholds; }
        public List<Map.Entry<String, Integer>> getPatternThresholds() { return patternThresholds; }
        public Integer getDefaultOverride() { return defaultOverride; }
        public int getMaxThresholdCap() { return maxThresholdCap; }
    }

    public static SyncSnapshot buildSyncSnapshot() {
        Rules r = rules;
        List<String> tagBlacklistIds = new ArrayList<String>();
        for (OreDictRule rule : r.tagBlacklist) tagBlacklistIds.add(rule.tagKey);

        List<String> patternBlacklistStrs = new ArrayList<String>();
        for (Pattern p : r.patternBlacklist) patternBlacklistStrs.add(p.pattern());

        List<Map.Entry<String, Integer>> tagThresholdEntries = new ArrayList<Map.Entry<String, Integer>>();
        for (Map.Entry<OreDictRule, Integer> e : r.tagThresholds) {
            tagThresholdEntries.add(new AbstractMap.SimpleEntry<String, Integer>(e.getKey().tagKey, e.getValue()));
        }

        List<Map.Entry<String, Integer>> patternThresholdEntries = new ArrayList<Map.Entry<String, Integer>>();
        for (Map.Entry<Pattern, Integer> e : r.patternThresholds) {
            patternThresholdEntries.add(new AbstractMap.SimpleEntry<String, Integer>(e.getKey().pattern(), e.getValue()));
        }

        return new SyncSnapshot(
            new ArrayList<String>(r.exactBlacklist),
            tagBlacklistIds,
            patternBlacklistStrs,
            new LinkedHashMap<String, Integer>(r.exactThresholds),
            tagThresholdEntries,
            patternThresholdEntries,
            r.defaultOverride, r.maxThresholdCap
        );
    }

    /**
     * Client-side only: replace the in-memory rules with a server-pushed snapshot. In
     * singleplayer/integrated-server this is a harmless no-op overwrite (client and "server"
     * share the same static state in one JVM); on a dedicated server this is the only way the
     * client ever learns the server owner's blacklist/threshold rules. Once applied, the client
     * must not fall back to re-reading its own local config files for the session - this replaces
     * `rules` outright rather than merging.
     */
    public static synchronized void applySyncedRules(SyncSnapshot snapshot) {
        Rules synced = new Rules();
        synced.exactBlacklist.addAll(snapshot.getExactBlacklist());
        for (String tagId : snapshot.getTagBlacklist()) {
            OreDictRule rule = resolveTagRule("#" + tagId);
            if (rule != null) synced.tagBlacklist.add(rule);
        }
        for (String pattern : snapshot.getPatternBlacklist()) {
            try {
                synced.patternBlacklist.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException ignored) {}
        }

        synced.exactThresholds.putAll(snapshot.getExactThresholds());
        for (Map.Entry<String, Integer> e : snapshot.getTagThresholds()) {
            OreDictRule rule = resolveTagRule("#" + e.getKey());
            if (rule != null) synced.tagThresholds.add(new AbstractMap.SimpleEntry<OreDictRule, Integer>(rule, e.getValue()));
        }
        for (Map.Entry<String, Integer> e : snapshot.getPatternThresholds()) {
            try {
                synced.patternThresholds.add(new AbstractMap.SimpleEntry<Pattern, Integer>(Pattern.compile(e.getKey()), e.getValue()));
            } catch (PatternSyntaxException ignored) {}
        }

        synced.defaultOverride = snapshot.getDefaultOverride();
        synced.maxThresholdCap = snapshot.getMaxThresholdCap();

        rules = synced;
        rulesGeneration++;
        resolvedThresholdCache.clear();
        resolvedBlacklistCache.clear();
        itemToOreDictNames = null;
        LOGGER.info("Applied server-synced Journey Mode config rules ({} exact/{} tag/{} pattern threshold rules).",
            synced.exactThresholds.size(), synced.tagThresholds.size(), synced.patternThresholds.size());
    }

    // ---------------------------------------------------------------- synchronized writers
    // All command-driven config mutations funnel through these two methods - never a second
    // file handle, per the "one synchronized writer" project rule.

    public interface JsonMutator {
        void mutate(JsonObject json);
    }

    public static synchronized void mutateThresholdsFile(JsonMutator mutator) {
        JsonObject json = readJson(THRESHOLDS_FILE);
        if (json == null) json = new JsonObject();
        if (!json.has("thresholds")) json.add("thresholds", new JsonObject());
        mutator.mutate(json);
        writeJsonAtomically(THRESHOLDS_FILE, json);
        reloadAll();
    }

    public static synchronized void mutateBlacklistFile(JsonMutator mutator) {
        JsonObject json = readJson(BLACKLIST_FILE);
        if (json == null) json = new JsonObject();
        if (!json.has("blacklisted_items")) json.add("blacklisted_items", new JsonArray());
        mutator.mutate(json);
        writeJsonAtomically(BLACKLIST_FILE, json);
        reloadAll();
    }

    public static void setExactThreshold(final String itemId, final int value) {
        mutateThresholdsFile(new JsonMutator() {
            @Override
            public void mutate(JsonObject json) {
                json.getAsJsonObject("thresholds").addProperty(itemId, value);
            }
        });
    }

    public static boolean removeExactThreshold(final String itemId) {
        final boolean[] removed = {false};
        mutateThresholdsFile(new JsonMutator() {
            @Override
            public void mutate(JsonObject json) {
                JsonObject thresholds = json.getAsJsonObject("thresholds");
                removed[0] = thresholds.has(itemId);
                thresholds.remove(itemId);
            }
        });
        return removed[0];
    }

    public static void setDefaultOverride(@Nullable final Integer value) {
        mutateThresholdsFile(new JsonMutator() {
            @Override
            public void mutate(JsonObject json) {
                if (value == null) {
                    json.add("default_override", JsonNull.INSTANCE);
                } else {
                    json.addProperty("default_override", value);
                }
            }
        });
    }

    public static void setMaxThresholdCap(final int value) {
        mutateThresholdsFile(new JsonMutator() {
            @Override
            public void mutate(JsonObject json) {
                json.addProperty("max_threshold_cap", Math.max(1, value));
            }
        });
    }

    // ---------------------------------------------------------------- io helpers

    @Nullable
    private static JsonObject readJson(File file) {
        if (!file.exists()) return null;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.error("Failed to parse " + file.getName() + " - leaving existing rules in place rather than wiping them on a bad read.", e);
            return null;
        }
    }

    private static void writeJsonAtomically(File file, JsonObject json) {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Failed to write " + file.getName(), e);
        }
    }
}
