package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads unlock-threshold overrides shipped by datapacks/modpacks, from any namespace, under
 * '<namespace>/journeymode/thresholds/*.json' (each file: {"thresholds": {"item:id": N, ...}}).
 * Precedence (see ConfigHandler): config file rules > datapack thresholds (this class) > the
 * recipe-depth calculator. Exact item ids only in v1 - no tag/regex support at the datapack
 * layer, to keep this first pass simple; the config file already covers that need.
 */
public class DatapackThresholdLoader extends SimplePreparableReloadListener<Map<String, Integer>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DatapackThresholdLoader INSTANCE = new DatapackThresholdLoader();

    private static volatile Map<String, Integer> loadedThresholds = Collections.emptyMap();

    private DatapackThresholdLoader() {}

    @Override
    protected Map<String, Integer> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, Integer> result = new HashMap<>();

        for (Map.Entry<Identifier, Resource> entry :
                resourceManager.listResources("journeymode/thresholds", loc -> loc.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("thresholds")) continue;
                for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("thresholds").entrySet()) {
                    result.put(t.getKey(), Math.max(1, t.getValue().getAsInt()));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse datapack threshold file {}", entry.getKey(), e);
            }
        }

        return result;
    }

    @Override
    protected void apply(Map<String, Integer> loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
        loadedThresholds = loaded;
        LOGGER.info("Loaded {} datapack-provided threshold overrides", loaded.size());
        ConfigHandler.onDatapackThresholdsReloaded();
        // A /reload rewrites recipes in place on the SAME RecipeManager instance, so
        // ThresholdTable's identity check cannot see the change. This listener already runs at
        // exactly the right moment (datapack apply), so it is where the shared calculator gets
        // dropped. Without this the baked depths would outlive the recipes they came from - and
        // because the calculator is now shared rather than per-player, that staleness would be
        // worse than the pre-1.8.2 behaviour, not better.
        com.aryangpt007.journeymode.data.ThresholdTable.invalidate();
    }

    public static Map<String, Integer> getDatapackThresholds() {
        return loadedThresholds;
    }
}
