package com.aryangpt007.journeymode.logic;

import com.aryangpt007.journeymode.platform.Platform;

import java.util.*;

/**
 * Calculates unlock thresholds based on recipe depth and stack size.
 * Pure logic class that uses platform abstraction for Minecraft-specific operations.
 */
public class ThresholdCalculator {
    
    private final Map<Object, Integer> depthCache = new HashMap<>();
    private final Set<Object> calculating = new HashSet<>(); // For cycle detection
    private final Object recipeManager;
    private final Object registryAccess;
    
    public ThresholdCalculator(Object recipeManager, Object registryAccess) {
        this.recipeManager = recipeManager;
        this.registryAccess = registryAccess;
    }
    
    /**
     * Calculate the unlock threshold for an item
     * Config overrides take priority over recipe-based calculation
     * 
     * Rules:
     * - Stack size 1: Always requires 1 item
     * - Raw materials (depth 0): Requires full stack size
     * - Depth 1: Requires 50% of stack size
     * - Depth 2: Requires 25% of stack size
     * - Depth 3+: Requires 1 item
     * 
     * @param item The item (platform-specific type)
     * @return The threshold
     */
    public int calculateThreshold(Object item) {
        // Check for config override first
        String itemId = Platform.get().getItemId(item);
        Integer configOverride = Platform.get().getThresholdOverride(itemId);
        if (configOverride != null) {
            return Math.max(1, configOverride); // Ensure at least 1
        }
        
        // Use recipe-based calculation
        int stackSize = Platform.get().getMaxStackSize(item);
        
        // Items that only stack to 1 always require just 1
        if (stackSize == 1) {
            return 1;
        }
        
        int depth = getRecipeDepth(item);
        
        return switch (depth) {
            case 0 -> stackSize;                      // Raw materials: full stack
            case 1 -> Math.max(1, stackSize / 2);     // 50% of stack
            case 2 -> Math.max(1, stackSize / 4);     // 25% of stack
            default -> 1;                             // Depth 3+: just 1
        };
    }
    
    /**
     * Get the recipe depth of an item (how many crafting steps from raw materials)
     * Returns 0 for raw materials (no recipe)
     * 
     * @param item The item (platform-specific type)
     * @return The recipe depth
     */
    public int getRecipeDepth(Object item) {
        if (depthCache.containsKey(item)) {
            return depthCache.get(item);
        }
        
        // Detect cycles
        if (calculating.contains(item)) {
            return 0; // Treat cyclic items as raw to break the cycle
        }
        
        calculating.add(item);
        
        try {
            // Platform will provide list of recipes for this item
            // This is a placeholder - actual implementation will be in platform-specific code
            // For now, we'll return a default depth
            // TODO: Implement recipe depth calculation using platform abstraction
            
            // For now, use a simple heuristic based on item ID
            String itemId = Platform.get().getItemId(item);
            
            // Raw materials (common ores, logs, etc.)
            if (isRawMaterial(itemId)) {
                depthCache.put(item, 0);
                return 0;
            }
            
            // Default to depth 1 for crafted items
            depthCache.put(item, 1);
            return 1;
            
        } finally {
            calculating.remove(item);
        }
    }
    
    /**
     * Simple heuristic to detect raw materials
     * TODO: Replace with actual recipe-based detection
     */
    private boolean isRawMaterial(String itemId) {
        // Common raw materials
        return itemId.contains("_ore") ||
               itemId.contains("_log") ||
               itemId.contains("raw_") ||
               itemId.endsWith("_wool") ||
               itemId.equals("minecraft:cobblestone") ||
               itemId.equals("minecraft:dirt") ||
               itemId.equals("minecraft:sand") ||
               itemId.equals("minecraft:gravel") ||
               itemId.equals("minecraft:stone") ||
               itemId.equals("minecraft:netherrack") ||
               itemId.equals("minecraft:soul_sand") ||
               itemId.equals("minecraft:basalt");
    }
    
    /**
     * Clear the depth cache (call when recipes reload)
     */
    public void clearCache() {
        depthCache.clear();
    }
}
