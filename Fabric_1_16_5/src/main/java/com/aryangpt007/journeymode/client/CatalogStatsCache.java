package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * §10 Catalog Statistics. "Total researchable" requires a full registry scan (minus
 * blacklist), which would stutter the GUI if redone on every open in a large modpack - so it's
 * computed once and cached here, invalidated only when the blacklist rules actually change
 * (config reload / sync), not on every screen open.
 */
public final class CatalogStatsCache {
    private static Integer totalResearchable = null;
    private static Map<String, Integer> perNamespaceResearchable = null;
    private static int cachedForGeneration = -1;

    private CatalogStatsCache() {}

    private static void computeIfNeeded() {
        int currentGeneration = ConfigHandler.getRulesGeneration();
        if (totalResearchable != null && cachedForGeneration == currentGeneration) return;

        int total = 0;
        Map<String, Integer> perNamespace = new LinkedHashMap<>();

        for (Item item : Registry.ITEM) {
            if (item == Items.AIR) continue;
            if (ConfigHandler.isBlacklisted(item)) continue;

            ResourceLocation id = Registry.ITEM.getKey(item);
            total++;
            Integer existing = perNamespace.get(id.getNamespace());
            perNamespace.put(id.getNamespace(), existing == null ? 1 : existing + 1);
        }

        totalResearchable = total;
        perNamespaceResearchable = perNamespace;
        cachedForGeneration = currentGeneration;
    }

    public static int getTotalResearchable() {
        computeIfNeeded();
        return totalResearchable;
    }

    public static Map<String, Integer> getPerNamespaceResearchable() {
        computeIfNeeded();
        return perNamespaceResearchable;
    }

    // §10: both values below are derived from the player's unlocked set, which only changes on
    // a sync from the server. The Stats tab re-renders every frame, so recomputing them per frame
    // meant copying the whole unlocked set - and its timestamp map - 60 times a second on a large
    // catalog. Memoized against JourneyData's syncGeneration counter, which is bumped by exactly
    // the one code path that can change this data client-side.
    private static int cachedForSyncGeneration = -1;
    private static Map<String, Integer> perNamespaceUnlocked = null;
    private static String mostRecentlyUnlockedKey = null;

    private static void computePlayerViewIfNeeded(JourneyDataAttachment data) {
        int generation = data.getSyncGeneration();
        if (perNamespaceUnlocked != null && cachedForSyncGeneration == generation) return;

        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : data.getUnlockedItems()) {
            String baseId = key.contains("|") ? key.substring(0, key.indexOf('|')) : key;
            int colon = baseId.indexOf(':');
            String namespace = colon >= 0 ? baseId.substring(0, colon) : "minecraft";
            Integer existing = result.get(namespace);
            result.put(namespace, existing == null ? 1 : existing + 1);
        }
        perNamespaceUnlocked = result;

        String latestKey = null;
        long latest = Long.MIN_VALUE;
        for (Map.Entry<String, Long> entry : data.getUnlockTimestamps().entrySet()) {
            if (entry.getValue() > latest) {
                latest = entry.getValue();
                latestKey = entry.getKey();
            }
        }
        mostRecentlyUnlockedKey = latestKey;

        cachedForSyncGeneration = generation;
    }

    /** Per-namespace unlocked counts. Never mutate the returned map - it is the cached instance. */
    public static Map<String, Integer> getPerNamespaceUnlocked(JourneyDataAttachment data) {
        computePlayerViewIfNeeded(data);
        return perNamespaceUnlocked;
    }

    /** Hybrid key of the most recently unlocked item, or null if nothing is unlocked yet. */
    public static String getMostRecentlyUnlockedKey(JourneyDataAttachment data) {
        computePlayerViewIfNeeded(data);
        return mostRecentlyUnlockedKey;
    }
}
