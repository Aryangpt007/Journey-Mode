package com.aryangpt007.journeymode.logic;

import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.platform.Platform;

/**
 * Core business logic for Journey Mode unlocking system.
 * Pure logic class with no loader dependencies.
 */
public class UnlockLogic {
    
    /**
     * Process a deposit of items
     * @param player The player (platform-specific type)
     * @param itemStack The item stack to deposit (platform-specific type)
     * @param level The level (platform-specific type)
     * @return true if this deposit unlocked the item, false otherwise
     */
    public static boolean processDeposit(Object player, Object itemStack, Object level) {
        JourneyData data = Platform.get().getPlayerData(player);
        
        // Check if Journey Mode is enabled for this player
        if (!data.isEnabled()) {
            return false;
        }
        
        String itemId = Platform.get().getItemId(itemStack);
        
        // Check blacklist
        if (Platform.get().isBlacklisted(itemId)) {
            return false;
        }
        
        // Get the item and calculate threshold
        Object item = Platform.get().getItemById(itemId);
        Object recipeManager = Platform.get().getRecipeManager(level);
        Object registryAccess = Platform.get().getRegistryAccess(level);
        
        ThresholdCalculator calculator = new ThresholdCalculator(recipeManager, registryAccess);
        int threshold = calculator.calculateThreshold(item);
        
        // Get stack count (we'll need to add a method to Platform for this)
        // For now, assume we can get it somehow
        // TODO: Add getStackCount to PlatformHelper
        int count = 1; // Placeholder
        
        // Deposit the items
        boolean unlocked = data.depositItem(itemId, count, threshold);
        
        // Save the updated data
        Platform.get().savePlayerData(player, data);
        
        return unlocked;
    }
    
    /**
     * Check if an item is unlocked for a player
     * @param player The player (platform-specific type)
     * @param itemId The item ID
     * @return true if unlocked
     */
    public static boolean isUnlocked(Object player, String itemId) {
        JourneyData data = Platform.get().getPlayerData(player);
        return data.isUnlocked(itemId);
    }
    
    /**
     * Get the collected count for an item
     * @param player The player (platform-specific type)
     * @param itemId The item ID
     * @return The count collected
     */
    public static int getCollectedCount(Object player, String itemId) {
        JourneyData data = Platform.get().getPlayerData(player);
        return data.getCollectedCount(itemId);
    }
    
    /**
     * Toggle Journey Mode for a player
     * @param player The player (platform-specific type)
     * @param enabled true to enable, false to disable
     */
    public static void setEnabled(Object player, boolean enabled) {
        JourneyData data = Platform.get().getPlayerData(player);
        data.setEnabled(enabled);
        Platform.get().savePlayerData(player, data);
    }
    
    /**
     * Check if Journey Mode is enabled for a player
     * @param player The player (platform-specific type)
     * @return true if enabled
     */
    public static boolean isEnabled(Object player) {
        JourneyData data = Platform.get().getPlayerData(player);
        return data.isEnabled();
    }
}
