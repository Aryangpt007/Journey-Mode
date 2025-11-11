package com.aryangpt007.journeymode.platform.fabric;

import com.aryangpt007.journeymode.core.JourneyData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fabric data handler using file-based storage
 */
public class FabricDataHandler {
    
    private static final String DATA_DIR = "journeymode";
    private static final String DATA_FILE_EXT = ".json";
    
    // Cache for runtime data
    private static final Map<UUID, JourneyData> dataCache = new HashMap<>();
    
    /**
     * Get the data directory path
     */
    private static Path getDataDir() {
        Path dataDir = FabricLoader.getInstance().getGameDir().resolve(DATA_DIR);
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Journey Mode data directory", e);
        }
        return dataDir;
    }
    
    /**
     * Get the file path for a player's data
     */
    private static Path getPlayerDataFile(UUID playerId) {
        return getDataDir().resolve(playerId.toString() + DATA_FILE_EXT);
    }
    
    /**
     * Get Journey Mode data for a player
     */
    public static JourneyData getJourneyData(Player player) {
        UUID playerId = player.getUUID();
        
        // Check cache first
        if (dataCache.containsKey(playerId)) {
            return dataCache.get(playerId);
        }
        
        // Load from file
        Path dataFile = getPlayerDataFile(playerId);
        if (Files.exists(dataFile)) {
            try {
                String jsonData = Files.readString(dataFile);
                JourneyData data = JourneyData.fromJsonString(jsonData);
                dataCache.put(playerId, data);
                return data;
            } catch (Exception e) {
                // If deserialization fails, create new data
            }
        }
        
        // Create new data
        JourneyData data = new JourneyData();
        dataCache.put(playerId, data);
        return data;
    }
    
    /**
     * Save Journey Mode data for a player
     */
    public static void saveJourneyData(Player player, JourneyData data) {
        UUID playerId = player.getUUID();
        dataCache.put(playerId, data);
        
        // Save to file
        Path dataFile = getPlayerDataFile(playerId);
        try {
            Files.writeString(dataFile, data.toJsonString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save Journey Mode data for player " + playerId, e);
        }
    }
    
    /**
     * Clear cache for a player (call on logout)
     */
    public static void clearCache(UUID playerId) {
        dataCache.remove(playerId);
    }
}
