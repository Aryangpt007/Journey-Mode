package com.aryangpt007.journeymode.fabric.platform;

import com.aryangpt007.journeymode.core.JourneyData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fabric data handler for storing player Journey Mode data.
 * Uses a simple in-memory map with NBT serialization for persistence.
 */
public class FabricDataHandler {
    private static final Map<UUID, JourneyData> PLAYER_DATA = new HashMap<>();
    private static final String DATA_KEY = "journeymode_data";
    private static final String ENABLED_KEY = "journeymode_enabled";
    
    /**
     * Get Journey Mode data for a player
     */
    public static JourneyData getJourneyData(Player player) {
        return PLAYER_DATA.computeIfAbsent(player.getUUID(), uuid -> new JourneyData());
    }
    
    /**
     * Save Journey Mode data for a player
     */
    public static void saveJourneyData(Player player, JourneyData data) {
        PLAYER_DATA.put(player.getUUID(), data);
    }
    
    /**
     * Load player data from NBT
     */
    public static void loadFromNBT(Player player, CompoundTag tag) {
        if (tag.contains(DATA_KEY)) {
            String jsonData = tag.getString(DATA_KEY);
            try {
                JourneyData data = JourneyData.fromJsonString(jsonData);
                PLAYER_DATA.put(player.getUUID(), data);
            } catch (Exception e) {
                // If loading fails, create new data
                PLAYER_DATA.put(player.getUUID(), new JourneyData());
            }
        }
        
        // Load enabled state
        if (tag.contains(ENABLED_KEY)) {
            JourneyData data = getJourneyData(player);
            // Store enabled state in data if we add that field
        }
    }
    
    /**
     * Save player data to NBT
     */
    public static void saveToNBT(Player player, CompoundTag tag) {
        JourneyData data = PLAYER_DATA.get(player.getUUID());
        if (data != null) {
            tag.putString(DATA_KEY, data.toJsonString());
        }
    }
    
    /**
     * Check if Journey Mode is enabled for a player
     */
    public static boolean isJourneyModeEnabled(Player player) {
        // For now, always enabled - can be extended later
        return true;
    }
    
    /**
     * Set Journey Mode enabled state for a player
     */
    public static void setJourneyModeEnabled(Player player, boolean enabled) {
        // Store in player data - can be extended later
    }
    
    /**
     * Remove player data when they disconnect
     */
    public static void removePlayerData(UUID playerUUID) {
        PLAYER_DATA.remove(playerUUID);
    }
}
