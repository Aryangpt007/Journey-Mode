package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IFutureReloadListener;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Loads unlock-threshold overrides shipped by datapacks/modpacks, from any namespace, under
 * 'journeymode/thresholds/*.json' (each file: {"thresholds": {"item:id": N, ...}}).
 * Precedence (see ConfigHandler): config file rules > datapack thresholds (this class) > the
 * recipe-depth calculator. Exact item ids only in v1 - no tag/regex support at the datapack
 * layer, to keep this first pass simple; the config file already covers that need.
 *
 * 1.16.5 note: the convenience base class equivalent to 1.20.1's SimplePreparableReloadListener
 * (net.minecraft.client.resources.ReloadListener) lives in the CLIENT package and is not present
 * on a dedicated server. This class implements IFutureReloadListener directly instead, using the
 * same prepare-on-background/apply-on-main-thread shape by hand so it is safe to register from
 * common (server-reachable) code via AddReloadListenerEvent.
 */
public class DatapackThresholdLoader implements IFutureReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-datapacks");
    public static final DatapackThresholdLoader INSTANCE = new DatapackThresholdLoader();

    private static volatile Map<String, Integer> loadedThresholds = Collections.emptyMap();

    private DatapackThresholdLoader() {}

    @Override
    public CompletableFuture<Void> reload(IStage stage, IResourceManager resourceManager,
                                           IProfiler prepareProfiler, IProfiler applyProfiler,
                                           Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> prepare(resourceManager), backgroundExecutor)
            .thenCompose(stage::wait)
            .thenAcceptAsync(this::apply, gameExecutor);
    }

    private Map<String, Integer> prepare(IResourceManager resourceManager) {
        Map<String, Integer> result = new HashMap<>();

        Collection<ResourceLocation> candidates = resourceManager.listResources("journeymode/thresholds", path -> path.endsWith(".json"));
        for (ResourceLocation loc : candidates) {
            try (IResource resource = resourceManager.getResource(loc);
                 Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                if (!json.has("thresholds")) continue;
                for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("thresholds").entrySet()) {
                    result.put(t.getKey(), Math.max(1, t.getValue().getAsInt()));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse datapack threshold file {}", loc, e);
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
