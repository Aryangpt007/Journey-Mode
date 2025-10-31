package com.aryangpt007.journeymode.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration handler for Journey Mode
 * Uses TOML for simple config and JSON files for complex data (like ProjectE)
 */
public class ConfigHandler {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Paths for JSON config files
    private static final Path CONFIG_DIR = FMLPaths.getOrCreateGameRelativePath(FMLPaths.CONFIGDIR.get().resolve("Journey Mode"));
    private static final Path BLACKLIST_FILE = CONFIG_DIR.resolve("blacklist.json");
    private static final Path THRESHOLDS_FILE = CONFIG_DIR.resolve("custom_thresholds.json");
    
    // TOML config (for simple settings if needed in future)
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    
    // Cached values for performance
    private static Set<String> blacklistCache = new HashSet<>();
    private static Map<String, Integer> thresholdCache = new HashMap<>();
    
    static {
        // Build a minimal TOML config (actual data is in JSON files)
        SPEC = BUILDER.build();
    }
    
    /**
     * Register the config with the mod container
     */
    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
        
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
     * Handle config loading and reloading events
     */
    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            // Reload JSON files when config reloads
            loadBlacklist();
            loadThresholds();
        }
    }
    
    /**
     * Initialize blacklist.json file if it doesn't exist
     */
    private static void initializeBlacklist() {
        if (!Files.exists(BLACKLIST_FILE)) {
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
            // Add some examples
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
    private static void loadBlacklist() {
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
    private static void loadThresholds() {
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
