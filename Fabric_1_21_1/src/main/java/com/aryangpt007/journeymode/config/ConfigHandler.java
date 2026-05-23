package com.aryangpt007.journeymode.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration handler for Journey Mode on Fabric
 */
public class ConfigHandler {
    
    private static final Logger LOGGER = LogManager.getLogger("journeymode-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Paths for JSON config files
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("Journey Mode");
    private static final Path BLACKLIST_FILE = CONFIG_DIR.resolve("blacklist.json");
    private static final Path THRESHOLDS_FILE = CONFIG_DIR.resolve("custom_thresholds.json");
    
    // Cached values for performance
    private static final Set<String> blacklistCache = new HashSet<>();
    private static final Map<String, Integer> thresholdCache = new HashMap<>();
    
    /**
     * Initialize the configurations
     */
    public static void initialize() {
        // Ensure config directory exists
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create Journey Mode config directory", e);
        }
        
        // Initialize JSON config files
        initializeBlacklist();
        initializeThresholds();
    }
    
    /**
     * Initialize blacklist.json file if it doesn't exist
     */
    private static void initializeBlacklist() {
        if (!Files.exists(BLACKLIST_FILE)) {
            JsonObject defaultBlacklist = new JsonObject();
            defaultBlacklist.addProperty("_comment", "Add item IDs to blacklist them from Journey Mode. Format: 'minecraft:item_id' or 'modid:item_id'");
            
            JsonArray items = new JsonArray();
            items.add("minecraft:bedrock");
            items.add("minecraft:barrier");
            items.add("minecraft:command_block");
            items.add("minecraft:structure_void");
            
            defaultBlacklist.add("blacklisted_items", items);
            
            try {
                Files.writeString(BLACKLIST_FILE, GSON.toJson(defaultBlacklist));
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
        if (!Files.exists(THRESHOLDS_FILE)) {
            JsonObject defaultThresholds = new JsonObject();
            defaultThresholds.addProperty("_comment", "Override unlock thresholds for specific items. If not listed, recipe-based calculation is used.");
            
            JsonObject thresholds = new JsonObject();
            thresholds.addProperty("minecraft:diamond", 10);
            thresholds.addProperty("minecraft:netherite_ingot", 5);
            thresholds.addProperty("minecraft:elytra", 1);
            
            defaultThresholds.add("thresholds", thresholds);
            
            try {
                Files.writeString(THRESHOLDS_FILE, GSON.toJson(defaultThresholds));
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
        
        if (!Files.exists(BLACKLIST_FILE)) {
            return;
        }
        
        try {
            String content = Files.readString(BLACKLIST_FILE);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
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
        
        if (!Files.exists(THRESHOLDS_FILE)) {
            return;
        }
        
        try {
            String content = Files.readString(THRESHOLDS_FILE);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
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
     */
    public static boolean isBlacklisted(String itemId) {
        return blacklistCache.contains(itemId);
    }
    
    /**
     * Get the threshold override for an item, if present
     */
    @Nullable
    public static Integer getThresholdOverride(String itemId) {
        return thresholdCache.get(itemId);
    }
    
    /**
     * Get all blacklisted items
     */
    public static Set<String> getBlacklist() {
        return Collections.unmodifiableSet(blacklistCache);
    }
    
    /**
     * Get all threshold overrides
     */
    public static Map<String, Integer> getThresholdOverrides() {
        return Collections.unmodifiableMap(thresholdCache);
    }
}
