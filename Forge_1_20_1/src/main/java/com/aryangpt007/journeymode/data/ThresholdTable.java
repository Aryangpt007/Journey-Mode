package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * One shared recipe-depth calculator per side, populated once when recipes load.
 *
 * <p>Two problems this replaces:
 *
 * <p><b>Duplicate indexes.</b> The calculator used to be an instance field on
 * {@link JourneyDataAttachment} <em>and</em> on {@link TeamData}, each lazily building its own
 * full copy of the recipe index and its own depth cache. A twenty-player server could therefore
 * hold twenty-odd independent copies of the same index, each built separately on that player's
 * first deposit. One shared instance collapses them.
 *
 * <p><b>Work during gameplay.</b> Even shared, the first query still walked the recipe graph on
 * whatever thread asked - which on a large modpack is a visible stall on the render or server
 * thread. {@link #bakeAll} does that walk once, at recipe-load time, inside the loading screen
 * the player is already sitting through.
 *
 * <p>Only the calculator's own contribution is baked. Config is still consulted live in front of
 * it on every query (see {@code RecipeDepthCalculator.calculateThreshold}), so
 * {@code /journeymode threshold} keeps taking effect immediately without a rebake - which the
 * settled precedence rule requires.
 */
public final class ThresholdTable {

    private static final Logger LOGGER = LogManager.getLogger("journeymode");

    private static RecipeDepthCalculator calculator;
    /** Identity of the RecipeManager the current calculator was built from. */
    private static RecipeManager boundTo;

    private ThresholdTable() {}

    /**
     * Point the shared calculator at these recipes, rebuilding if they are a different
     * RecipeManager than last time (world switch, or client reconnecting to another server).
     * A {@code /reload} keeps the same RecipeManager instance, so that path must call
     * {@link #invalidate} explicitly - identity alone cannot see it.
     */
    public static synchronized void bind(RecipeManager recipeManager, RegistryAccess registryAccess) {
        if (calculator == null || boundTo != recipeManager) {
            calculator = new RecipeDepthCalculator(recipeManager, registryAccess);
            boundTo = recipeManager;
        }
    }

    /** The shared calculator, or null if nothing has bound one yet. */
    public static synchronized RecipeDepthCalculator get() {
        return calculator;
    }

    /** Drop the shared calculator so the next bind rebuilds it. Call on recipe reload. */
    public static synchronized void invalidate() {
        calculator = null;
        boundTo = null;
    }

    /**
     * Resolve every registered item once, populating the shared depth cache.
     *
     * <p>Cheap despite appearances: the calculator memoizes depth per item, so a full sweep is one
     * traversal of the recipe graph amortised across all items rather than one traversal per item.
     * The elapsed time is logged deliberately - it is the measurement that decides whether this
     * ever needs to move off the loading screen onto a background thread.
     */
    public static synchronized void bakeAll() {
        if (calculator == null) return;

        // A blanket override short-circuits calculateThreshold before it ever reaches the graph,
        // so there is nothing to bake and walking the registry would be pure waste. Same reasoning
        // as the settled decision that a blanket override skips the calculator entirely.
        if (ConfigHandler.getDefaultOverride() != null) {
            LOGGER.info("Journey Mode: blanket threshold override is active - skipping the recipe bake.");
            return;
        }

        long start = System.nanoTime();
        int resolved = 0;
        int failed = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            try {
                calculator.calculateThreshold(item);
                resolved++;
            } catch (Throwable t) {
                // One pathological item must never abort the bake for everything else.
                failed++;
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000L;
        if (failed > 0) {
            LOGGER.warn("Journey Mode: baked thresholds for {} items in {} ms ({} item(s) threw and were skipped).", resolved, ms, failed);
        } else {
            LOGGER.info("Journey Mode: baked thresholds for {} items in {} ms.", resolved, ms);
        }
    }

    /** Bind and bake in one call - the shape every lifecycle hook wants. */
    public static synchronized void rebuild(RecipeManager recipeManager, RegistryAccess registryAccess) {
        invalidate();
        bind(recipeManager, registryAccess);
        bakeAll();
    }
}
