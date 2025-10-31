package com.aryangpt007.journeymode.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration handler for Journey Mode.
 * Manages blacklist and custom threshold configurations.
 * Pure Java class with no loader dependencies.
 */
public class ConfigHandler {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Cached values for performance
    private static Set<String> blacklistCache = new HashSet<>();
    private static Map<String, Integer> thresholdCache = new HashMap<>();
    
    // Config file paths (set by platform)
    private static Path configDir;
    private static Path blacklistFile;
    private static Path thresholdsFile;
    
    /**
     * Initialize the config handler with paths
     * @param configDirectory The config directory path
     */
    public static void initialize(Path configDirectory) {
        configDir = configDirectory;
        blacklistFile = configDir.resolve("blacklist.json");
        thresholdsFile = configDir.resolve("custom_thresholds.json");
        
        // Ensure config directory exists
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            System.err.println("Failed to create Journey Mode config directory: " + e.getMessage());
        }
        
        // Initialize JSON config files
        initializeBlacklist();
        initializeThresholds();
        
        // Load configs
        loadBlacklist();
        loadThresholds();
    }
    
    /**
     * Initialize blacklist.json file if it doesn't exist
     */
    private static void initializeBlacklist() {
        if (blacklistFile == null || Files.exists(blacklistFile)) {
            return;
        }
        
        JsonObject defaultBlacklist = new JsonObject();
        defaultBlacklist.addProperty("_comment", "Add item IDs to blacklist them from Journey Mode. Format: 'minecraft:item_id' or 'modid:item_id'");
        
        JsonArray items = new JsonArray();
        // Add some common examples
        items.add("minecraft:bedrock");
        items.add("minecraft:barrier");
        items.add("minecraft:command_block");
        items.add("minecraft:structure_void");
        
        defaultBlacklist.add("blacklisted_items", items);
        
        try {
            Files.writeString(blacklistFile, GSON.toJson(defaultBlacklist));
            System.out.println("Created default blacklist.json");
        } catch (IOException e) {
            System.err.println("Failed to create blacklist.json: " + e.getMessage());
        }
    }
    
    /**
     * Initialize custom_thresholds.json file if it doesn't exist
     */
    private static void initializeThresholds() {
        if (thresholdsFile == null || Files.exists(thresholdsFile)) {
            return;
        }
        
        JsonObject defaultThresholds = new JsonObject();
        defaultThresholds.addProperty("_comment", "Set custom unlock thresholds for specific items. Format: 'item_id': threshold_number");
        
        JsonObject thresholds = new JsonObject();
        // Add some examples
        thresholds.addProperty("minecraft:diamond", 10);
        thresholds.addProperty("minecraft:netherite_ingot", 5);
        thresholds.addProperty("minecraft:emerald", 15);
        
        defaultThresholds.add("thresholds", thresholds);
        
        try {
            Files.writeString(thresholdsFile, GSON.toJson(defaultThresholds));
            System.out.println("Created default custom_thresholds.json");
        } catch (IOException e) {
            System.err.println("Failed to create custom_thresholds.json: " + e.getMessage());
        }
    }
    
    /**
     * Load blacklist from JSON file
     */
    public static void loadBlacklist() {
        if (blacklistFile == null || !Files.exists(blacklistFile)) {
            return;
        }
        
        try {
            String content = Files.readString(blacklistFile);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            blacklistCache.clear();
            
            if (json.has("blacklisted_items")) {
                JsonArray items = json.getAsJsonArray("blacklisted_items");
                for (int i = 0; i < items.size(); i++) {
                    blacklistCache.add(items.get(i).getAsString());
                }
            }
            
            System.out.println("Loaded " + blacklistCache.size() + " blacklisted items");
        } catch (Exception e) {
            System.err.println("Failed to load blacklist.json: " + e.getMessage());
        }
    }
    
    /**
     * Load custom thresholds from JSON file
     */
    public static void loadThresholds() {
        if (thresholdsFile == null || !Files.exists(thresholdsFile)) {
            return;
        }
        
        try {
            String content = Files.readString(thresholdsFile);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            thresholdCache.clear();
            
            if (json.has("thresholds")) {
                JsonObject thresholds = json.getAsJsonObject("thresholds");
                for (String key : thresholds.keySet()) {
                    thresholdCache.put(key, thresholds.get(key).getAsInt());
                }
            }
            
            System.out.println("Loaded " + thresholdCache.size() + " custom thresholds");
        } catch (Exception e) {
            System.err.println("Failed to load custom_thresholds.json: " + e.getMessage());
        }
    }
    
    /**
     * Reload all configurations
     */
    public static void reload() {
        loadBlacklist();
        loadThresholds();
    }
    
    /**
     * Check if an item is blacklisted
     * @param itemId The item ID
     * @return true if blacklisted
     */
    public static boolean isBlacklisted(String itemId) {
        return blacklistCache.contains(itemId);
    }
    
    /**
     * Get custom threshold override for an item
     * @param itemId The item ID
     * @return The threshold override, or null if not set
     */
    public static Integer getThresholdOverride(String itemId) {
        return thresholdCache.get(itemId);
    }
    
    /**
     * Get all blacklisted items
     * @return Set of blacklisted item IDs
     */
    public static Set<String> getBlacklistedItems() {
        return new HashSet<>(blacklistCache);
    }
    
    /**
     * Get all custom thresholds
     * @return Map of item ID to threshold
     */
    public static Map<String, Integer> getCustomThresholds() {
        return new HashMap<>(thresholdCache);
    }
}
