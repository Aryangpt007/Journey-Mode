package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pack-maker strip rules for item normalization: {@code config/Journey Mode/normalization.json}.
 *
 * <p>Normalization keeps an item's data except what it explicitly strips (see NormalizationRules /
 * ComponentNormalizationRules). Those classes hold the <b>built-in floor</b> - vanilla wear state
 * and the vanilla container duplication gate - and nothing here can remove from it. This file can
 * only <em>add</em> stripping, so a mistake in it can merge two catalog entries but can never
 * reopen a duplication hole.
 *
 * <p>Why a config file: modded items keep contents, charge and storage references under names no
 * code can predict (Draconic's {@code BCTileData} holds a block's whole inventory, Mekanism's
 * {@code mekData} its tanks and modules, AE2's {@code ae2:storage_cell_inv} a storage cell's
 * contents). The defaults below were read out of the real mod jars, and anything the defaults
 * miss can be fixed by a pack maker without a new build.
 *
 * <p>Loader-independent on purpose - plain File and Gson, no Forge/Fabric types, nothing newer
 * than Java 8 or Gson 2.2 - so this class is byte-identical across every environment and cannot
 * drift between them.
 */
public final class NormalizationConfig {

    private static final Logger LOGGER = LogManager.getLogger("journeymode");
    private static final String FILE_NAME = "normalization.json";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * Bumped whenever defaultJson() gains entries. An existing file with a lower version gets the
     * new entries merged in (except any the user opted out of with keep_*), so upgrading the mod
     * never leaves a pack on stale defaults.
     */
    public static final int DEFAULTS_VERSION = 1;

    /** Rules as parsed. Immutable once built; swapped atomically on load/sync. */
    private static final class Rules {
        final Set<String> keys = new HashSet<String>();               // lower-cased, any depth
        final List<String> keysContaining = new ArrayList<String>();  // lower-cased substrings
        final Set<String> components = new HashSet<String>();         // exact ids
        final List<String> componentPrefixes = new ArrayList<String>(); // from "ns:*"
        final Map<String, Rules> mods = new HashMap<String, Rules>(); // item namespace -> rules
    }

    private static volatile Rules rules = parseRules(parse(defaultJson()));
    private static volatile String syncText = toSyncJson(rules);

    private NormalizationConfig() {}

    // ------------------------------------------------------------------------------------ queries

    /** True if an NBT key (at any depth) on an item from {@code itemNamespace} is to be stripped. */
    public static boolean isKeyStripped(String itemNamespace, String key) {
        if (key == null) return false;
        String lower = key.toLowerCase(Locale.ROOT);
        Rules r = rules;
        if (matchesKey(r, lower)) return true;
        if (itemNamespace != null) {
            Rules mod = r.mods.get(itemNamespace.toLowerCase(Locale.ROOT));
            if (mod != null && matchesKey(mod, lower)) return true;
        }
        return false;
    }

    /** True if a data component (by registry id) on an item from {@code itemNamespace} is to be stripped. */
    public static boolean isComponentStripped(String itemNamespace, String componentId) {
        if (componentId == null) return false;
        String id = componentId.toLowerCase(Locale.ROOT);
        Rules r = rules;
        if (matchesComponent(r, id)) return true;
        if (itemNamespace != null) {
            Rules mod = r.mods.get(itemNamespace.toLowerCase(Locale.ROOT));
            if (mod != null && matchesComponent(mod, id)) return true;
        }
        return false;
    }

    /** Compact JSON of the active rules, for the server -> client config sync. */
    public static String syncText() {
        return syncText;
    }

    private static boolean matchesKey(Rules r, String lowerKey) {
        if (r.keys.contains(lowerKey)) return true;
        for (int i = 0; i < r.keysContaining.size(); i++) {
            if (lowerKey.contains(r.keysContaining.get(i))) return true;
        }
        return false;
    }

    private static boolean matchesComponent(Rules r, String id) {
        if (r.components.contains(id)) return true;
        for (int i = 0; i < r.componentPrefixes.size(); i++) {
            if (id.startsWith(r.componentPrefixes.get(i))) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------------- load and sync

    /** (Re)load from disk, creating or upgrading the file as needed. Never throws. */
    public static synchronized void load() {
        JsonObject json;
        try {
            File dir = ConfigHandler.getConfigDir();
            if (!dir.exists() && !dir.mkdirs()) {
                LOGGER.warn("Could not create {} - using built-in normalization defaults.", dir);
                install(parse(defaultJson()), "built-in defaults");
                return;
            }
            File file = new File(dir, FILE_NAME);
            if (!file.exists()) {
                writeText(file, defaultJson());
                install(parse(defaultJson()), "new " + FILE_NAME);
                return;
            }
            json = parse(readText(file));
            if (mergeNewDefaults(json)) {
                writeText(file, pretty(json));
            }
        } catch (Exception e) {
            // A broken file must never weaken the duplication protection: fall back to the
            // defaults rather than to "strip nothing".
            LOGGER.error("Failed to read {} - using built-in normalization defaults until it is fixed.", FILE_NAME, e);
            install(parse(defaultJson()), "built-in defaults (file unreadable)");
            return;
        }
        install(json, FILE_NAME);
    }

    /** Client side: replace local rules with the server's. */
    public static synchronized void applySynced(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            install(parse(text), "server");
        } catch (Exception e) {
            LOGGER.error("Ignoring malformed normalization rules from the server.", e);
        }
    }

    private static void install(JsonObject json, String source) {
        Rules parsed = parseRules(json);
        rules = parsed;
        syncText = toSyncJson(parsed);
        LOGGER.info("Journey Mode normalization rules loaded from {}: {} key, {} key-substring, {} component rule(s), {} mod section(s).",
            source, parsed.keys.size(), parsed.keysContaining.size(),
            parsed.components.size() + parsed.componentPrefixes.size(), parsed.mods.size());
    }

    // ---------------------------------------------------------------------------- defaults merge

    /**
     * Add any built-in default the file does not have yet, if the file predates DEFAULTS_VERSION.
     * Entries listed under keep_keys / keep_components are the user's explicit opt-outs and are
     * never re-added. @return true if the file changed and should be rewritten.
     */
    private static boolean mergeNewDefaults(JsonObject json) {
        int version = json.has("defaults_version") ? json.get("defaults_version").getAsInt() : 0;
        if (version >= DEFAULTS_VERSION) return false;

        JsonObject defaults = parse(defaultJson());
        Set<String> keepKeys = lowerSet(json.get("keep_keys"));
        Set<String> keepComponents = lowerSet(json.get("keep_components"));
        int added = mergeSection(json, defaults, keepKeys, keepComponents);

        JsonObject mods = objectOrCreate(json, "mods");
        JsonObject defaultMods = defaults.getAsJsonObject("mods");
        for (Map.Entry<String, JsonElement> e : defaultMods.entrySet()) {
            JsonObject target = objectOrCreate(mods, e.getKey());
            added += mergeSection(target, e.getValue().getAsJsonObject(), keepKeys, keepComponents);
        }
        json.add("defaults_version", new JsonPrimitive(DEFAULTS_VERSION));
        LOGGER.info("Upgraded {} from defaults_version {} to {}: {} new default rule(s) added.",
            FILE_NAME, version, DEFAULTS_VERSION, added);
        return true;
    }

    private static int mergeSection(JsonObject target, JsonObject defaults, Set<String> keepKeys, Set<String> keepComponents) {
        int added = 0;
        String[] lists = {"strip_keys", "strip_keys_containing", "strip_components"};
        for (int i = 0; i < lists.length; i++) {
            String list = lists[i];
            if (!defaults.has(list)) continue;
            Set<String> keep = list.equals("strip_components") ? keepComponents : keepKeys;
            JsonArray existing = arrayOrCreate(target, list);
            Set<String> present = lowerSet(existing);
            for (JsonElement d : defaults.getAsJsonArray(list)) {
                String value = d.getAsString();
                String lower = value.toLowerCase(Locale.ROOT);
                if (present.contains(lower) || keep.contains(lower)) continue;
                existing.add(new JsonPrimitive(value));
                present.add(lower);
                added++;
            }
        }
        return added;
    }

    // ------------------------------------------------------------------------------- parsing

    private static Rules parseRules(JsonObject json) {
        Rules r = new Rules();
        Set<String> keepKeys = lowerSet(json.get("keep_keys"));
        Set<String> keepComponents = lowerSet(json.get("keep_components"));
        fillSection(r, json, keepKeys, keepComponents);
        if (json.has("mods") && json.get("mods").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("mods").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                Rules mod = new Rules();
                fillSection(mod, e.getValue().getAsJsonObject(), keepKeys, keepComponents);
                r.mods.put(e.getKey().toLowerCase(Locale.ROOT), mod);
            }
        }
        return r;
    }

    private static void fillSection(Rules r, JsonObject json, Set<String> keepKeys, Set<String> keepComponents) {
        for (String k : lowerSet(json.get("strip_keys"))) {
            if (!keepKeys.contains(k)) r.keys.add(k);
        }
        for (String k : lowerSet(json.get("strip_keys_containing"))) {
            // An empty substring would match every key and strip the item bare.
            if (!k.isEmpty() && !keepKeys.contains(k)) r.keysContaining.add(k);
        }
        for (String c : lowerSet(json.get("strip_components"))) {
            if (keepComponents.contains(c)) continue;
            if (c.endsWith(":*")) {
                r.componentPrefixes.add(c.substring(0, c.length() - 1)); // keep the ':'
            } else if (!c.isEmpty()) {
                r.components.add(c);
            }
        }
        Collections.sort(r.keysContaining);
        Collections.sort(r.componentPrefixes);
    }

    private static Set<String> lowerSet(JsonElement element) {
        Set<String> out = new TreeSet<String>();
        if (element == null || !element.isJsonArray()) return out;
        for (JsonElement e : element.getAsJsonArray()) {
            if (e.isJsonPrimitive()) out.add(e.getAsString().trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    // ------------------------------------------------------------------------------- sync

    private static String toSyncJson(Rules r) {
        JsonObject json = section(r);
        JsonObject mods = new JsonObject();
        List<String> names = new ArrayList<String>(r.mods.keySet());
        Collections.sort(names);
        for (String mod : names) mods.add(mod, section(r.mods.get(mod)));
        json.add("mods", mods);
        return json.toString();
    }

    private static JsonObject section(Rules r) {
        JsonObject json = new JsonObject();
        json.add("strip_keys", array(new TreeSet<String>(r.keys)));
        json.add("strip_keys_containing", array(r.keysContaining));
        List<String> components = new ArrayList<String>(new TreeSet<String>(r.components));
        for (String prefix : r.componentPrefixes) components.add(prefix + "*");
        json.add("strip_components", array(components));
        return json;
    }

    // ------------------------------------------------------------------------- json helpers

    private static JsonArray array(Iterable<String> values) {
        JsonArray a = new JsonArray();
        for (String v : values) a.add(new JsonPrimitive(v));
        return a;
    }

    private static JsonObject objectOrCreate(JsonObject parent, String name) {
        if (!parent.has(name) || !parent.get(name).isJsonObject()) parent.add(name, new JsonObject());
        return parent.getAsJsonObject(name);
    }

    private static JsonArray arrayOrCreate(JsonObject parent, String name) {
        if (!parent.has(name) || !parent.get(name).isJsonArray()) parent.add(name, new JsonArray());
        return parent.getAsJsonArray(name);
    }

    private static JsonObject parse(String text) {
        return new JsonParser().parse(text).getAsJsonObject();
    }

    private static String pretty(JsonObject json) {
        return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json);
    }

    private static String readText(File file) throws java.io.IOException {
        Reader reader = new InputStreamReader(new FileInputStream(file), UTF8);
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private static void writeText(File file, String text) {
        try {
            File tmp = new File(file.getPath() + ".tmp");
            Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), UTF8);
            try {
                writer.write(text);
            } finally {
                writer.close();
            }
            // Same atomic-replace discipline as the catalog writer.
            if (file.exists() && !file.delete()) {
                LOGGER.warn("Could not replace {}", file);
                return;
            }
            if (!tmp.renameTo(file)) LOGGER.warn("Could not write {}", file);
        } catch (Exception e) {
            LOGGER.error("Failed to write {}", file, e);
        }
    }

    // ------------------------------------------------------------------------------- defaults

    /**
     * The shipped defaults. Every entry was read out of the named mod's compiled code (see the
     * note beside each group); none is guessed. Unverified candidates are deliberately left out.
     */
    private static String defaultJson() {
        return "{\n"
            + "  \"_readme\": [\n"
            + "    \"Extra data Journey Mode strips from items before they enter the catalog.\",\n"
            + "    \"Stripped data never comes back when you withdraw the item: contents, charge,\",\n"
            + "    \"storage links and other changing state. This file can only ADD stripping - the\",\n"
            + "    \"built-in vanilla rules (durability, shulker/bundle contents...) always apply.\",\n"
            + "    \"strip_keys: NBT keys, matched at any depth, ignoring case (1.20.4 and older; on\",\n"
            + "    \"  1.20.5+ they apply inside minecraft:custom_data).\",\n"
            + "    \"strip_keys_containing: strip any NBT key whose name contains this text.\",\n"
            + "    \"strip_components: data component ids (1.20.5+), e.g. mekanism:items, or mod:* for all.\",\n"
            + "    \"mods: the same three lists, applied only to items from that mod (by item id namespace).\",\n"
            + "    \"keep_keys / keep_components: opt out of a default here without it coming back on update.\",\n"
            + "    \"Changing this file re-sorts existing catalog entries automatically on next load.\",\n"
            + "    \"Apply changes with /journeymode reloadconfig.\"\n"
            + "  ],\n"
            + "  \"defaults_version\": " + DEFAULTS_VERSION + ",\n"
            + "  \"strip_keys\": [\n"
            // Brandon's Core / Draconic Evolution 1.12.2: block data kept on break - the block's
            // whole inventory plus its energy (verified: writeInventoryToNBT).
            + "    \"BCTileData\",\n"
            + "    \"InvItems\",\n"
            // Mekanism 1.12.2-1.20.1: item data wrapper - inventories, tanks, energy, modules,
            // QIO contents, personal-storage reference. Case-insensitive also covers CE's MekData.
            + "    \"mekData\",\n"
            + "    \"personalStorageId\",\n"
            + "    \"qioItemMap\",\n"
            + "    \"FluidTanks\",\n"
            + "    \"GasTanks\",\n"
            + "    \"InfusionTanks\",\n"
            + "    \"PigmentTanks\",\n"
            + "    \"SlurryTanks\",\n"
            // CoFH Core / Thermal: item and fluid inventories on retained blocks.
            + "    \"ItemInv\",\n"
            + "    \"TankInv\",\n"
            + "    \"StuffedInv\",\n"
            // Sophisticated Backpacks 1.20.1: reference to the backpack's stored inventory.
            + "    \"contentsUuid\",\n"
            + "    \"backpackUuid\",\n"
            // Ender IO 1.20.1: a soul vial's captured mob.
            + "    \"EntityStorage\"\n"
            + "  ],\n"
            + "  \"strip_keys_containing\": [\n"
            // Stored charge under any spelling (EnergyContainers, EnergyStored, MaxEnergyStored...).
            + "    \"energy\",\n"
            // Owner / binding / storage-reference ids; copies must not stay linked to the original.
            + "    \"uuid\"\n"
            + "  ],\n"
            + "  \"strip_components\": [\n"
            // Mekanism 1.21.1+: contents, charge, modules and storage/frequency links.
            + "    \"mekanism:items\", \"mekanism:fluids\", \"mekanism:gases\", \"mekanism:chemicals\",\n"
            + "    \"mekanism:infuse_types\", \"mekanism:pigments\", \"mekanism:slurries\", \"mekanism:energy\",\n"
            + "    \"mekanism:drive_contents\", \"mekanism:module_container\", \"mekanism:upgrades\",\n"
            + "    \"mekanism:storage_id\", \"mekanism:owner\", \"mekanism:security\", \"mekanism:heat_data\",\n"
            + "    \"mekanism:teleporter_frequency\", \"mekanism:inventory_frequency\", \"mekanism:qio_frequency\",\n"
            // Applied Energistics 2 1.21.1+: storage cell contents, crafting inventory, energy,
            // quantum-link pairing.
            + "    \"ae2:storage_cell_inv\", \"ae2:crafting_inv\", \"ae2:stored_energy\", \"ae2:entangled_singularity_id\",\n"
            // Create 1.21.1+: packages, toolboxes and minecart contraptions carry real items.
            + "    \"create:package_contents\", \"create:toolbox_inventory\", \"create:toolbox_uuid\",\n"
            + "    \"create:minecart_contraption_data\",\n"
            // Sophisticated Core 1.21.1+: storage reference, fluids, energy, stored XP, links.
            + "    \"sophisticatedcore:storage_uuid\", \"sophisticatedcore:fluid_contents\",\n"
            + "    \"sophisticatedcore:energy_stored\", \"sophisticatedcore:stored_xp\",\n"
            + "    \"sophisticatedcore:cooking_inventory\", \"sophisticatedcore:linked_storage_endpoint\",\n"
            + "    \"sophisticatedcore:linked_storage_primary_endpoint\", \"sophisticatedcore:ender_linker_target\",\n"
            // Ender IO 1.21.1+: tank contents, energy, captured soul.
            + "    \"enderio:item_fluid_content\", \"enderio:named_fluid_contents\", \"enderio:energy\", \"enderio:soul\",\n"
            // Apotheosis 1.21.1+: gems socketed into gear are items in their own right.
            + "    \"apotheosis:socketed_gems\",\n"
            // Vanilla 1.20.5+: bees inside a beehive/nest, lodestone compass location.
            + "    \"minecraft:bees\", \"minecraft:lodestone_tracker\"\n"
            + "  ],\n"
            + "  \"mods\": {\n"
            // Draconic Evolution: installed modules and purchased tool upgrades.
            + "    \"draconicevolution\": { \"strip_keys\": [\"DEUpgrades\", \"modules\", \"bc_managed_data\"] },\n"
            // TACZ: loaded / boxed ammunition (the gun and ammo type are kept: GunId, AmmoId).
            + "    \"tacz\": { \"strip_keys\": [\"AmmoCount\", \"GunCurrentAmmoCount\", \"HasBulletInBarrel\", \"DummyAmmo\"] },\n"
            // Applied Energistics 2 1.19-1.20.1: storage cell contents.
            + "    \"ae2\": { \"strip_keys\": [\"amts\", \"keys\"] },\n"
            // Refined Storage 1.x: disk id pointing at the disk's stored contents.
            + "    \"refinedstorage\": { \"strip_keys\": [\"Id\"] }\n"
            + "  },\n"
            + "  \"keep_keys\": [],\n"
            + "  \"keep_components\": []\n"
            + "}\n";
    }
}
