package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * One shared recipe-depth calculator per side, populated once when recipes load.
 *
 * <p>Two problems this replaces:
 *
 * <p><b>Duplicate indexes.</b> The calculator used to be an instance field on the player data
 * class <em>and</em> on TeamData, each lazily building its own full copy of the recipe index and
 * its own depth cache. A twenty-player server could therefore hold twenty-odd independent copies
 * of the same index, each built separately on that player's first deposit. One shared instance
 * collapses them.
 *
 * <p><b>Work during gameplay.</b> Even shared, the first query still walked the recipe graph on
 * whatever thread asked - on a large modpack, a visible stall on the render or server thread.
 * {@link #bakeAll} does that walk once, at recipe-load time, inside the loading screen the
 * player is already sitting through.
 *
 * <p>Only the calculator's own contribution is baked. Config is still consulted live in front of
 * it on every query, so {@code /journeymode threshold} keeps taking effect immediately without
 * a rebake - which the settled precedence rule requires.
 */
public final class ThresholdTable {

    private static final Logger LOGGER = LogManager.getLogger("journeymode");

    private static RecipeDepthCalculator calculator;

    private ThresholdTable() {}

    /**
     * 1.12.2 recipes live in the static CraftingManager registry, filled during mod loading, so
     * there is no RecipeManager to bind against and nothing to rebuild per world.
     */
    /**
     * No /reload invalidation exists on this version, and none is needed: 1.12.2 recipes live in
     * the static CraftingManager registry, populated during mod loading, and there is no datapack
     * recipe reload that can change them at runtime. The other eight environments hook
     * DatapackThresholdLoader for exactly that reason.
     */
    public static synchronized void bind() {
        if (calculator == null) {
            calculator = new RecipeDepthCalculator();
        }
    }

    /** The shared calculator, or null if nothing has bound one yet. */
    public static synchronized RecipeDepthCalculator get() {
        return calculator;
    }

    /** Drop the shared calculator so the next bind rebuilds it. Call on recipe reload. */
    public static synchronized void invalidate() {
        calculator = null;
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
        for (Item item : ForgeRegistries.ITEMS) {
            try {
                calculator.calculateThreshold(item);
                resolved++;
            } catch (Throwable t) {
                // One pathological item must never abort the bake for everything else.
                failed++;
            }
        }
        long ms = (System.nanoTime() - start) / 1000000L;
        if (failed > 0) {
            LOGGER.warn("Journey Mode: baked thresholds for " + resolved + " items in " + ms
                + " ms (" + failed + " item(s) threw and were skipped).");
        } else {
            LOGGER.info("Journey Mode: baked thresholds for " + resolved + " items in " + ms + " ms.");
        }
    }

    /** Bind and bake in one call - the shape every lifecycle hook wants. */
    public static synchronized void rebuild() {
        invalidate();
        bind();
        bakeAll();
    }
}
