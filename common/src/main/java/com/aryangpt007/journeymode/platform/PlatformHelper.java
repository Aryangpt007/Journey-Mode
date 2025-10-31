package com.aryangpt007.journeymode.platform;

import com.aryangpt007.journeymode.core.JourneyData;

/**
 * Platform abstraction interface for loader-specific operations.
 * Implementations provided by each platform module (NeoForge, Forge, Fabric).
 * 
 * This interface allows common code to call platform-specific functionality
 * without depending on any loader APIs.
 */
public interface PlatformHelper {
    
    /**
     * Get Journey Mode data for a player
     * @param player The player object (platform-specific type)
     * @return The player's Journey Mode data
     */
    JourneyData getPlayerData(Object player);
    
    /**
     * Save Journey Mode data for a player
     * @param player The player object (platform-specific type)
     * @param data The data to save
     */
    void savePlayerData(Object player, JourneyData data);
    
    /**
     * Send a packet to the client
     * @param player The player to send to (platform-specific type)
     * @param packet The packet to send (platform-specific type)
     */
    void sendToClient(Object player, Object packet);
    
    /**
     * Send a packet to the server
     * @param packet The packet to send (platform-specific type)
     */
    void sendToServer(Object packet);
    
    /**
     * Open the Journey Mode GUI for a player
     * @param player The player (platform-specific type)
     */
    void openJourneyGui(Object player);
    
    /**
     * Get all recipes from the level
     * @param level The level (platform-specific type)
     * @return List of recipe holders (platform-specific type)
     */
    Object getAllRecipes(Object level);
    
    /**
     * Get the recipe manager from a level
     * @param level The level (platform-specific type)
     * @return The recipe manager (platform-specific type)
     */
    Object getRecipeManager(Object level);
    
    /**
     * Get the registry access from a level
     * @param level The level (platform-specific type)
     * @return The registry access (platform-specific type)
     */
    Object getRegistryAccess(Object level);
    
    /**
     * Get the item ID as a string
     * @param item The item (platform-specific type)
     * @return The item ID (e.g., "minecraft:diamond")
     */
    String getItemId(Object item);
    
    /**
     * Get an item by its ID
     * @param itemId The item ID (e.g., "minecraft:diamond")
     * @return The item (platform-specific type)
     */
    Object getItemById(String itemId);
    
    /**
     * Get the default max stack size for an item
     * @param item The item (platform-specific type)
     * @return The max stack size
     */
    int getMaxStackSize(Object item);
    
    /**
     * Check if an item is blacklisted from Journey Mode
     * @param itemId The item ID
     * @return true if blacklisted
     */
    boolean isBlacklisted(String itemId);
    
    /**
     * Get custom threshold override for an item (from config)
     * @param itemId The item ID
     * @return The threshold override, or null if not set
     */
    Integer getThresholdOverride(String itemId);
    
    /**
     * Get the current platform name
     * @return "neoforge", "forge", or "fabric"
     */
    String getPlatformName();
    
    /**
     * Check if running on the client side
     * @return true if client
     */
    boolean isClient();
    
    /**
     * Check if running on the server side
     * @return true if server
     */
    boolean isServer();
}
