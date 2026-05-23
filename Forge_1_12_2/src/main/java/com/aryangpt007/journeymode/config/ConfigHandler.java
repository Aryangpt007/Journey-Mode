package com.aryangpt007.journeymode.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Configuration handler for Journey Mode
 */
public class ConfigHandler {
    
    private static final Logger LOGGER = LogManager.getLogger("journeymode");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Paths for JSON config files
    private static final File CONFIG_DIR = new File("config/Journey Mode");
    private static final File BLACKLIST_FILE = new File(CONFIG_DIR, "blacklist.json");
    private static final File THRESHOLDS_FILE = new File(CONFIG_DIR, "custom_thresholds.json");
    
    // Cached values for performance
    private static Set<String> blacklistCache = new HashSet<>();
    private static Map<String, Integer> thresholdCache = new HashMap<>();
    
    /**
     * Register the config and ensure directories and files exist
     */
    public static void register() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        
        // Initialize JSON config files
        initializeBlacklist();
        initializeThresholds();
    }
    
    /**
     * Initialize blacklist.json file if it doesn't exist
     */
    private static void initializeBlacklist() {
        if (!BLACKLIST_FILE.exists()) {
            JsonObject defaultBlacklist = new JsonObject();
            defaultBlacklist.addProperty("_comment", "Add item IDs to blacklist them from Journey Mode. Format: 'minecraft:item_id' or 'modid:item_id'");
            
            JsonArray items = new JsonArray();
            // Add some common examples
            items.add("minecraft:bedrock");
            items.add("minecraft:barrier");
            items.add("minecraft:command_block");
            
            defaultBlacklist.add("blacklisted_items", items);
            
            try {
                Files.write(BLACKLIST_FILE.toPath(), GSON.toJson(defaultBlacklist).getBytes(StandardCharsets.UTF_8));
                LOGGER.info("Created default blacklist.json");
            } catch (IOException e) {
                LOGGER.error("Failed to create blacklist.json", e);
            }
        }
        loadBlacklist();
    }
    
    /**
     * Initialize custom_thresholds.json file if it doesn't exist
     */
    private static void initializeThresholds() {
        if (!THRESHOLDS_FILE.exists()) {
            JsonObject defaultThresholds = new JsonObject();
            defaultThresholds.addProperty("_comment", "Override unlock thresholds for specific items. If not listed, recipe-based calculation is used.");
            
            JsonObject thresholds = new JsonObject();
            // Add some examples
            thresholds.addProperty("minecraft:diamond", 10);
            thresholds.addProperty("minecraft:nether_star", 1);
            
            defaultThresholds.add("thresholds", thresholds);
            
            try {
                Files.write(THRESHOLDS_FILE.toPath(), GSON.toJson(defaultThresholds).getBytes(StandardCharsets.UTF_8));
                LOGGER.info("Created default custom_thresholds.json");
            } catch (IOException e) {
                LOGGER.error("Failed to create custom_thresholds.json", e);
            }
        }
        loadThresholds();
    }
    
    /**
     * Load blacklist from JSON file
     */
    public static void loadBlacklist() {
        blacklistCache.clear();
        
        if (!BLACKLIST_FILE.exists()) {
            return;
        }
        
        try {
            String content = new String(Files.readAllBytes(BLACKLIST_FILE.toPath()), StandardCharsets.UTF_8);
            JsonObject json = new JsonParser().parse(content).getAsJsonObject();
            
            if (json.has("blacklisted_items")) {
                JsonArray items = json.getAsJsonArray("blacklisted_items");
                items.forEach(element -> {
                    String itemId = element.getAsString();
                    blacklistCache.add(itemId);
                });
            }
            
            LOGGER.info("Loaded {} blacklisted items", blacklistCache.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load blacklist.json", e);
        }
    }
    
    /**
     * Load threshold overrides from JSON file
     */
    public static void loadThresholds() {
        thresholdCache.clear();
        
        if (!THRESHOLDS_FILE.exists()) {
            return;
        }
        
        try {
            String content = new String(Files.readAllBytes(THRESHOLDS_FILE.toPath()), StandardCharsets.UTF_8);
            JsonObject json = new JsonParser().parse(content).getAsJsonObject();
            
            if (json.has("thresholds")) {
                JsonObject thresholds = json.getAsJsonObject("thresholds");
                thresholds.entrySet().forEach(entry -> {
                    String itemId = entry.getKey();
                    int threshold = entry.getValue().getAsInt();
                    thresholdCache.put(itemId, threshold);
                });
            }
            
            LOGGER.info("Loaded {} threshold overrides", thresholdCache.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load custom_thresholds.json", e);
        }
    }
    
    /**
     * Check if an item is blacklisted
     * @param itemId The item registry ID (e.g., "minecraft:diamond")
     * @return true if the item is blacklisted
     */
    public static boolean isBlacklisted(String itemId) {
        return blacklistCache.contains(itemId);
    }
    
    /**
     * Get the threshold override for an item, if present
     * @param itemId The item registry ID
     * @return The threshold override, or null if not overridden
     */
    @Nullable
    public static Integer getThresholdOverride(String itemId) {
        return thresholdCache.get(itemId);
    }
    
    /**
     * Get all blacklisted items (for debugging/admin purposes)
     */
    public static Set<String> getBlacklist() {
        return Collections.unmodifiableSet(blacklistCache);
    }
    
    /**
     * Get all threshold overrides (for debugging/admin purposes)
     */
    public static Map<String, Integer> getThresholdOverrides() {
        return Collections.unmodifiableMap(thresholdCache);
    }
}
