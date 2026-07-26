package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 *
 * Registered via ResourceManagerHelper (Fabric API's synchronous reload listener), rather than
 * Forge's AddReloadListenerEvent + SimplePreparableReloadListener.
 */
public class DatapackThresholdLoader implements SimpleSynchronousResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-datapack");
    private static final ResourceLocation ID = new ResourceLocation(JourneyMode.MODID, "datapack_thresholds");
    public static final DatapackThresholdLoader INSTANCE = new DatapackThresholdLoader();

    private static volatile Map<String, Integer> loadedThresholds = Collections.emptyMap();

    private DatapackThresholdLoader() {}

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<String, Integer> result = new HashMap<>();

        for (Map.Entry<ResourceLocation, Resource> entry :
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

        loadedThresholds = result;
        LOGGER.info("Loaded {} datapack-provided threshold overrides", result.size());
        ConfigHandler.onDatapackThresholdsReloaded();
    }

    public static Map<String, Integer> getDatapackThresholds() {
        return loadedThresholds;
    }
}
