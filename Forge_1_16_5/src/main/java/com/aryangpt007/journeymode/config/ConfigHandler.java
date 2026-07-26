package com.aryangpt007.journeymode.config;

import com.aryangpt007.journeymode.data.DatapackThresholdLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.item.Item;
import net.minecraft.tags.ITag;
import net.minecraft.tags.TagCollectionManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.ForgeConfigSpec;
import javax.annotation.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Configuration handler for Journey Mode.
 * blacklist.json and custom_thresholds.json each accept three key shapes, checked in this
 * priority order: exact item id > item tag (#namespace:path) > regex/wildcard pattern.
 * Thresholds additionally fall back to a "default_override" before the recipe-depth
 * calculator, and to datapack-provided thresholds (<namespace>/journeymode/thresholds/*.json)
 * below the config file but above the calculator.
 *
 * 1.16.5 has no TagKey class (added in later versions) - tag rules are stored as a raw
 * ResourceLocation and resolved into an ITag<Item> lazily via TagCollectionManager on every
 * query, since datapack tags load after mod init and can be reloaded (matches the "resolve
 * lazily" instruction in the tracker rather than caching a stale/empty tag at parse time).
 */
public class ConfigHandler {

    private static final Logger LOGGER = LogManager.getLogger("journeymode");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("Journey Mode");
    private static final Path BLACKLIST_FILE = CONFIG_DIR.resolve("blacklist.json");
    private static final Path THRESHOLDS_FILE = CONFIG_DIR.resolve("custom_thresholds.json");

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final int DEFAULT_MAX_THRESHOLD_CAP = 64;

    // ---- Resolved rule storage (rebuilt on every load/reload) ----
    private static final class Rules {
        Set<String> exactBlacklist = new HashSet<>();
        List<ResourceLocation> tagBlacklist = new ArrayList<>();
        List<Pattern> patternBlacklist = new ArrayList<>();

        Map<String, Integer> exactThresholds = new LinkedHashMap<>();
        List<Map.Entry<ResourceLocation, Integer>> tagThresholds = new ArrayList<>();
        List<Map.Entry<Pattern, Integer>> patternThresholds = new ArrayList<>();

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

    // Per-item resolution cache; cleared whenever config, or datapacks, reload.
    private static final Map<String, Optional<Integer>> resolvedThresholdCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> resolvedBlacklistCache = new ConcurrentHashMap<>();

    static {
        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);

        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create Journey Mode config directory", e);
        }

        initializeBlacklistFile();
        initializeThresholdsFile();
        reloadAll();
    }

    public static void onConfigLoad(ModConfig.ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            reloadAll();
        }
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
        LOGGER.info("Journey Mode config (re)loaded: {} exact/{} tag/{} pattern blacklist rules, {} exact/{} tag/{} pattern threshold rules, default_override={}, max_threshold_cap={}",
            loaded.exactBlacklist.size(), loaded.tagBlacklist.size(), loaded.patternBlacklist.size(),
            loaded.exactThresholds.size(), loaded.tagThresholds.size(), loaded.patternThresholds.size(),
            loaded.defaultOverride, loaded.maxThresholdCap);
    }

    /** Called when the datapack threshold reload listener finishes (see DatapackThresholdLoader). */
    public static void onDatapackThresholdsReloaded() {
        resolvedThresholdCache.clear();
    }

    // ---------------------------------------------------------------- init/default files

    private static void initializeBlacklistFile() {
        if (!Files.exists(BLACKLIST_FILE)) {
            JsonObject defaultBlacklist = new JsonObject();
            defaultBlacklist.addProperty("_comment",
                "Blacklist entries accept three forms, checked in this order: exact item id " +
                "('minecraft:bedrock'), item tag ('#forge:ingots'), or a regex/wildcard pattern " +
                "('gtceu:.*_dust'). All three live in the same 'blacklisted_items' list.");

            JsonArray items = new JsonArray();
            items.add("minecraft:bedrock");
            items.add("minecraft:barrier");
            items.add("minecraft:command_block");
            items.add("minecraft:structure_void");
            defaultBlacklist.add("blacklisted_items", items);

            writeJsonAtomically(BLACKLIST_FILE, defaultBlacklist);
            LOGGER.info("Created default blacklist.json");
        }
    }

    private static void initializeThresholdsFile() {
        if (!Files.exists(THRESHOLDS_FILE)) {
            JsonObject defaultThresholds = new JsonObject();
            defaultThresholds.addProperty("_comment",
                "Keys accept three forms, checked in this order: exact item id, item tag " +
                "('#forge:ingots'), or a regex/wildcard pattern ('gtceu:.*_circuit'). " +
                "'default_override' (if set) applies to every item with no matching rule, " +
                "before falling back to the recipe-depth calculator. 'max_threshold_cap' " +
                "bounds what /journeymode threshold and /journeymode all will accept.");

            JsonObject thresholds = new JsonObject();
            thresholds.addProperty("minecraft:diamond", 10);
            thresholds.addProperty("minecraft:netherite_ingot", 5);
            thresholds.addProperty("minecraft:elytra", 1);
            defaultThresholds.add("thresholds", thresholds);
            defaultThresholds.add("default_override", com.google.gson.JsonNull.INSTANCE);
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
            classifyRuleKey(key,
                exact -> target.exactBlacklist.add(exact),
                tag -> target.tagBlacklist.add(tag),
                pattern -> target.patternBlacklist.add(pattern));
        }
    }

    private static void loadThresholdsInto(Rules target) {
        JsonObject json = readJson(THRESHOLDS_FILE);
        if (json == null) return;

        if (json.has("thresholds")) {
            JsonObject thresholds = json.getAsJsonObject("thresholds");
            for (Map.Entry<String, JsonElement> entry : thresholds.entrySet()) {
                int value = entry.getValue().getAsInt();
                classifyRuleKey(entry.getKey(),
                    exact -> target.exactThresholds.put(exact, value),
                    tag -> target.tagThresholds.add(new AbstractMap.SimpleEntry<>(tag, value)),
                    pattern -> target.patternThresholds.add(new AbstractMap.SimpleEntry<>(pattern, value)));
            }
        }

        if (json.has("default_override") && json.get("default_override").isJsonPrimitive()) {
            target.defaultOverride = Math.max(1, json.get("default_override").getAsInt());
        }

        if (json.has("max_threshold_cap") && json.get("max_threshold_cap").isJsonPrimitive()) {
            target.maxThresholdCap = Math.max(1, json.get("max_threshold_cap").getAsInt());
        }
    }

    /** Exact id / tag / pattern, in that priority for classification purposes (not lookup order). */
    private static void classifyRuleKey(String key, Consumer<String> onExact, Consumer<ResourceLocation> onTag, Consumer<Pattern> onPattern) {
        if (key.startsWith("#")) {
            String tagId = key.substring(1);
            try {
                onTag.accept(new ResourceLocation(tagId));
            } catch (Exception e) {
                LOGGER.warn("Ignoring invalid tag rule '{}': {}", key, e.getMessage());
            }
            return;
        }

        if (isPlainExactId(key)) {
            onExact.accept(key);
            return;
        }

        try {
            onPattern.accept(compilePatternRule(key));
        } catch (PatternSyntaxException e) {
            LOGGER.warn("Ignoring invalid pattern rule '{}': {}", key, e.getMessage());
        }
    }

    private static boolean isPlainExactId(String key) {
        return key.matches("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");
    }

    /**
     * Compile a rule key that isn't an exact id or a tag into a Pattern. If it only contains
     * glob-safe characters (letters/digits/id punctuation plus '*'), '*' is treated as a glob
     * wildcard and everything else is escaped literally. Otherwise the key is compiled as a
     * raw regex as-is, so pack authors who want real regex (anchors, character classes, etc.)
     * are not fighting glob translation.
     */
    private static Pattern compilePatternRule(String key) {
        boolean globSafe = key.matches("[a-zA-Z0-9_./:\\-*]+");
        if (!globSafe) {
            return Pattern.compile(key);
        }
        StringBuilder regex = new StringBuilder();
        for (char c : key.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    // ---------------------------------------------------------------- tag resolution

    /** Resolved lazily on every call - datapack tags load after mod init and can reload. */
    private static boolean itemHasTag(Item item, ResourceLocation tagId) {
        ITag<Item> tag = TagCollectionManager.getInstance().getItems().getTagOrEmpty(tagId);
        return item.is(tag);
    }

    // ---------------------------------------------------------------- queries

    public static boolean isBlacklisted(Item item) {
        String itemId = itemId(item);
        Boolean cached = resolvedBlacklistCache.get(itemId);
        if (cached != null) return cached;

        Rules r = rules;
        boolean result = r.exactBlacklist.contains(itemId);
        if (!result) {
            for (ResourceLocation tag : r.tagBlacklist) {
                if (itemHasTag(item, tag)) { result = true; break; }
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
     * Full precedence chain: exact > tag > regex/wildcard > default_override > datapack >
     * dev-API ThresholdProvider > (null, meaning "let the calculator decide").
     */
    @Nullable
    public static Integer getThresholdOverride(Item item) {
        String itemId = itemId(item);
        Optional<Integer> cached = resolvedThresholdCache.get(itemId);
        if (cached != null) return cached.isPresent() ? cached.get() : null;

        Integer resolved = resolveThreshold(item, itemId);
        resolvedThresholdCache.put(itemId, Optional.ofNullable(resolved));
        return resolved;
    }

    private static Integer resolveThreshold(Item item, String itemId) {
        Rules r = rules;

        Integer exact = r.exactThresholds.get(itemId);
        if (exact != null) return Math.max(1, exact);

        for (Map.Entry<ResourceLocation, Integer> entry : r.tagThresholds) {
            if (itemHasTag(item, entry.getKey())) {
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

        Integer datapackValue = DatapackThresholdLoader.getDatapackThresholds().get(itemId);
        if (datapackValue != null) {
            return Math.max(1, datapackValue);
        }

        Integer apiValue = com.aryangpt007.journeymode.api.JourneyModeAPI.queryThresholdProviders(itemId);
        if (apiValue != null) {
            return Math.max(1, apiValue);
        }

        return null; // calculator decides
    }

    public static int getMaxThresholdCap() {
        return rules.maxThresholdCap;
    }

    @Nullable
    public static Integer getDefaultOverride() {
        return rules.defaultOverride;
    }

    public static Set<String> getExactBlacklist() {
        return Collections.unmodifiableSet(rules.exactBlacklist);
    }

    public static Map<String, Integer> getExactThresholdOverrides() {
        return Collections.unmodifiableMap(rules.exactThresholds);
    }

    private static String itemId(Item item) {
        return Registry.ITEM.getKey(item).toString();
    }

    // ---------------------------------------------------------------- §2 network sync (server -> client)

    /** Raw-string snapshot of the current rules, suitable for putting on the wire. Plain final
     *  class with getters (not a record) - this project's Java 8 toolchain target. */
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

        public List<String> exactBlacklist() { return exactBlacklist; }
        public List<String> tagBlacklist() { return tagBlacklist; }
        public List<String> patternBlacklist() { return patternBlacklist; }
        public Map<String, Integer> exactThresholds() { return exactThresholds; }
        public List<Map.Entry<String, Integer>> tagThresholds() { return tagThresholds; }
        public List<Map.Entry<String, Integer>> patternThresholds() { return patternThresholds; }
        public Integer defaultOverride() { return defaultOverride; }
        public int maxThresholdCap() { return maxThresholdCap; }
    }

    public static SyncSnapshot buildSyncSnapshot() {
        Rules r = rules;

        List<String> tagBlacklistIds = new ArrayList<>();
        for (ResourceLocation loc : r.tagBlacklist) tagBlacklistIds.add(loc.toString());

        List<String> patternBlacklistStrs = new ArrayList<>();
        for (Pattern p : r.patternBlacklist) patternBlacklistStrs.add(p.pattern());

        List<Map.Entry<String, Integer>> tagThresholdEntries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> e : r.tagThresholds) {
            tagThresholdEntries.add(new AbstractMap.SimpleEntry<>(e.getKey().toString(), e.getValue()));
        }

        List<Map.Entry<String, Integer>> patternThresholdEntries = new ArrayList<>();
        for (Map.Entry<Pattern, Integer> e : r.patternThresholds) {
            patternThresholdEntries.add(new AbstractMap.SimpleEntry<>(e.getKey().pattern(), e.getValue()));
        }

        return new SyncSnapshot(
            new ArrayList<>(r.exactBlacklist),
            tagBlacklistIds,
            patternBlacklistStrs,
            new LinkedHashMap<>(r.exactThresholds),
            tagThresholdEntries,
            patternThresholdEntries,
            r.defaultOverride, r.maxThresholdCap
        );
    }

    /**
     * Client-side only: replace the in-memory rules with a server-pushed snapshot. In
     * singleplayer/integrated-server this is a harmless no-op overwrite (client and "server"
     * share the same static state in one JVM); on a dedicated server this is the only way the
     * client ever learns the server owner's blacklist/threshold rules. Once applied, the
     * client must not fall back to re-reading its own local config files for the session -
     * this replaces `rules` outright rather than merging.
     */
    public static synchronized void applySyncedRules(SyncSnapshot snapshot) {
        Rules synced = new Rules();
        synced.exactBlacklist.addAll(snapshot.exactBlacklist());
        for (String tagId : snapshot.tagBlacklist()) {
            try {
                synced.tagBlacklist.add(new ResourceLocation(tagId));
            } catch (Exception ignored) {}
        }
        for (String pattern : snapshot.patternBlacklist()) {
            try {
                synced.patternBlacklist.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException ignored) {}
        }

        synced.exactThresholds.putAll(snapshot.exactThresholds());
        for (Map.Entry<String, Integer> e : snapshot.tagThresholds()) {
            try {
                synced.tagThresholds.add(new AbstractMap.SimpleEntry<>(new ResourceLocation(e.getKey()), e.getValue()));
            } catch (Exception ignored) {}
        }
        for (Map.Entry<String, Integer> e : snapshot.patternThresholds()) {
            try {
                synced.patternThresholds.add(new AbstractMap.SimpleEntry<>(Pattern.compile(e.getKey()), e.getValue()));
            } catch (PatternSyntaxException ignored) {}
        }

        synced.defaultOverride = snapshot.defaultOverride();
        synced.maxThresholdCap = snapshot.maxThresholdCap();

        rules = synced;
        rulesGeneration++;
        resolvedThresholdCache.clear();
        resolvedBlacklistCache.clear();
        LOGGER.info("Applied server-synced Journey Mode config rules ({} exact/{} tag/{} pattern threshold rules).",
            synced.exactThresholds.size(), synced.tagThresholds.size(), synced.patternThresholds.size());
    }

    // ---------------------------------------------------------------- synchronized writers
    // All command-driven config mutations funnel through these two methods - never a second
    // file handle, per the "one synchronized writer" project rule.

    public static synchronized void mutateThresholdsFile(Consumer<JsonObject> mutator) {
        JsonObject json = readJson(THRESHOLDS_FILE);
        if (json == null) json = new JsonObject();
        if (!json.has("thresholds")) json.add("thresholds", new JsonObject());
        mutator.accept(json);
        writeJsonAtomically(THRESHOLDS_FILE, json);
        reloadAll();
    }

    public static synchronized void mutateBlacklistFile(Consumer<JsonObject> mutator) {
        JsonObject json = readJson(BLACKLIST_FILE);
        if (json == null) json = new JsonObject();
        if (!json.has("blacklisted_items")) json.add("blacklisted_items", new JsonArray());
        mutator.accept(json);
        writeJsonAtomically(BLACKLIST_FILE, json);
        reloadAll();
    }

    public static void setExactThreshold(String itemId, int value) {
        mutateThresholdsFile(json -> json.getAsJsonObject("thresholds").addProperty(itemId, value));
    }

    public static boolean removeExactThreshold(String itemId) {
        boolean[] removed = {false};
        mutateThresholdsFile(json -> {
            JsonObject thresholds = json.getAsJsonObject("thresholds");
            removed[0] = thresholds.has(itemId);
            thresholds.remove(itemId);
        });
        return removed[0];
    }

    public static void setDefaultOverride(@Nullable Integer value) {
        mutateThresholdsFile(json -> {
            if (value == null) {
                json.add("default_override", com.google.gson.JsonNull.INSTANCE);
            } else {
                json.addProperty("default_override", value);
            }
        });
    }

    public static void setMaxThresholdCap(int value) {
        mutateThresholdsFile(json -> json.addProperty("max_threshold_cap", Math.max(1, value)));
    }

    // ---------------------------------------------------------------- io helpers

    @Nullable
    private static JsonObject readJson(Path file) {
        if (!Files.exists(file)) return null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.error("Failed to parse {} - leaving existing rules in place rather than wiping them on a bad read.", file.getFileName(), e);
            return null;
        }
    }

    private static void writeJsonAtomically(Path file, JsonObject json) {
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Failed to write {}", file.getFileName(), e);
        }
    }
}
