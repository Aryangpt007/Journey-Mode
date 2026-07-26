package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Loads unlock-threshold overrides shipped by datapacks/modpacks, from any namespace, under
 * '<namespace>/journeymode/thresholds/*.json' (each file: {"thresholds": {"item:id": N, ...}}).
 * Precedence (see ConfigHandler): config file rules > datapack thresholds (this class) > the
 * recipe-depth calculator. Exact item ids only in v1 - no tag/regex support at the datapack
 * layer, to keep this first pass simple; the config file already covers that need.
 */
public class DatapackThresholdLoader implements IdentifiableResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-datapack-thresholds");
    public static final DatapackThresholdLoader INSTANCE = new DatapackThresholdLoader();
    private static final ResourceLocation ID = new ResourceLocation("journeymode", "threshold_loader");

    private static volatile Map<String, Integer> loadedThresholds = Collections.emptyMap();

    private DatapackThresholdLoader() {}

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier, ResourceManager resourceManager,
                                           ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler,
                                           Executor prepareExecutor, Executor applyExecutor) {
        return CompletableFuture.supplyAsync(() -> prepare(resourceManager), prepareExecutor)
            .thenCompose(barrier::wait)
            .thenAcceptAsync(this::apply, applyExecutor);
    }

    private Map<String, Integer> prepare(ResourceManager resourceManager) {
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

        return result;
    }

    private void apply(Map<String, Integer> loaded) {
        loadedThresholds = loaded;
        LOGGER.info("Loaded {} datapack-provided threshold overrides", loaded.size());
        ConfigHandler.onDatapackThresholdsReloaded();
    }

    public static Map<String, Integer> getDatapackThresholds() {
        return loadedThresholds;
    }
}
