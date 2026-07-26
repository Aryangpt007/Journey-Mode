package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

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
            perNamespace.merge(id.getNamespace(), 1, Integer::sum);
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

    /** Per-namespace unlocked counts, computed live from the (small) unlocked set - cheap, no cache needed. */
    public static Map<String, Integer> getPerNamespaceUnlocked(JourneyDataAttachment data) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : data.getUnlockedItems()) {
            String baseId = key.contains("|") ? key.substring(0, key.indexOf('|')) : key;
            int colon = baseId.indexOf(':');
            String namespace = colon >= 0 ? baseId.substring(0, colon) : "minecraft";
            result.merge(namespace, 1, Integer::sum);
        }
        return result;
    }
}
