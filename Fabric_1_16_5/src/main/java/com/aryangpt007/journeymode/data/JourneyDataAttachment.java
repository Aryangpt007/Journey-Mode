package com.aryangpt007.journeymode.data;

import com.mojang.serialization.Codec;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;

/**
 * Stores player's Journey Mode data - tracked items and unlocked items for infinite access
 */
public class JourneyDataAttachment {
    private static final Gson GSON = new Gson();

    public static final Codec<JourneyDataAttachment> CODEC = Codec.STRING.xmap(
        jsonString -> {
            try {
                return fromJsonString(jsonString);
            } catch (Exception e) {
                return new JourneyDataAttachment();
            }
        },
        attachment -> attachment.toJsonString()
    );

    private final Map<String, Integer> collectedCounts; // Item ID -> count collected
    private final Set<String> unlockedItems; // Items unlocked for infinite access
    private final Map<String, Long> unlockTimestamps; // Item ID -> unlock timestamp (milliseconds)
    private boolean enabled; // Whether Journey Mode is enabled for this player

    @Deprecated // Use dynamic threshold via RecipeDepthCalculator
    public static final int UNLOCK_THRESHOLD = 30; // Fallback value
    
    private RecipeDepthCalculator recipeCalculator; // Lazily initialized

    public JourneyDataAttachment() {
        this.collectedCounts = new HashMap<>();
        this.unlockedItems = new HashSet<>();
        this.unlockTimestamps = new HashMap<>();
        this.enabled = true; // Default to enabled
    }

    private JourneyDataAttachment(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps, boolean enabled) {
        this.collectedCounts = new HashMap<>(collectedCounts);
        this.unlockedItems = new HashSet<>(unlockedItems);
        this.unlockTimestamps = new HashMap<>(unlockTimestamps);
        this.enabled = enabled;
    }

    /**
     * Serialize to JSON string
     */
    public String toJsonString() {
        JsonObject json = new JsonObject();
        
        JsonObject countsJson = new JsonObject();
        collectedCounts.forEach(countsJson::addProperty);
        json.add("collected_counts", countsJson);
        
        JsonArray unlockedJson = new JsonArray();
        unlockedItems.forEach(unlockedJson::add);
        json.add("unlocked_items", unlockedJson);
        
        JsonObject timestampsJson = new JsonObject();
        unlockTimestamps.forEach(timestampsJson::addProperty);
        json.add("unlock_timestamps", timestampsJson);
        
        json.addProperty("enabled", enabled);
        
        return GSON.toJson(json);
    }

    /**
     * Deserialize from JSON string
     */
    public static JourneyDataAttachment fromJsonString(String jsonString) {
        JourneyDataAttachment attachment = new JourneyDataAttachment();
        try {
            JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
            
            if (json.has("collected_counts")) {
                JsonObject countsJson = json.getAsJsonObject("collected_counts");
                countsJson.entrySet().forEach(entry -> 
                    attachment.collectedCounts.put(entry.getKey(), entry.getValue().getAsInt())
                );
            }
            
            if (json.has("unlocked_items")) {
                JsonArray unlockedJson = json.getAsJsonArray("unlocked_items");
                unlockedJson.forEach(element -> 
                    attachment.unlockedItems.add(element.getAsString())
                );
            }
            
            if (json.has("unlock_timestamps")) {
                JsonObject timestampsJson = json.getAsJsonObject("unlock_timestamps");
                timestampsJson.entrySet().forEach(entry -> 
                    attachment.unlockTimestamps.put(entry.getKey(), entry.getValue().getAsLong())
                );
            }
            
            if (json.has("enabled")) {
                attachment.enabled = json.get("enabled").getAsBoolean();
            }
        } catch (Exception e) {
            // Keep default empty attachment
        }
        return attachment;
    }
    
    /**
     * Initialize the recipe calculator (called when needed)
     */
    public void initializeCalculator(RecipeManager recipeManager, RegistryAccess registryAccess) {
        if (this.recipeCalculator == null) {
            this.recipeCalculator = new RecipeDepthCalculator(recipeManager, registryAccess);
        }
    }
    
    /**
     * Get the unlock threshold for a specific item (dynamic based on recipe depth and stack size)
     */
    public int getThreshold(Item item) {
        if (recipeCalculator != null) {
            return recipeCalculator.calculateThreshold(item);
        }
        // Fallback if calculator not initialized
        return item.getMaxStackSize() == 1 ? 1 : UNLOCK_THRESHOLD;
    }

    /**
     * Deposit items into Journey Mode tracking
     * @param stack The ItemStack to deposit
     * @param recipeManager The recipe manager for threshold calculation
     * @param registryAccess Registry access for recipes
     * @return true if this deposit unlocked the item
     */
    public boolean depositItem(ItemStack stack, RecipeManager recipeManager, RegistryAccess registryAccess) {
        initializeCalculator(recipeManager, registryAccess);
        
        String itemId = Registry.ITEM.getKey(stack.getItem()).toString();
        int currentCount = collectedCounts.getOrDefault(itemId, 0);
        int newCount = currentCount + stack.getCount();
        collectedCounts.put(itemId, newCount);

        int threshold = getThreshold(stack.getItem());
        
        // Check if we just reached the threshold
        if (currentCount < threshold && newCount >= threshold) {
            unlockedItems.add(itemId);
            unlockTimestamps.put(itemId, System.currentTimeMillis());
            return true; // Item was just unlocked
        }
        return false;
    }

    /**
     * Check if an item is unlocked for infinite retrieval
     */
    public boolean isUnlocked(Item item) {
        String itemId = Registry.ITEM.getKey(item).toString();
        return unlockedItems.contains(itemId);
    }

    /**
     * Get the current collection count for an item
     */
    public int getCollectedCount(Item item) {
        String itemId = Registry.ITEM.getKey(item).toString();
        return collectedCounts.getOrDefault(itemId, 0);
    }

    /**
     * Get all unlocked items
     */
    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }
    
    /**
     * Get unlocked items sorted by timestamp (most recent first)
     */
    public List<String> getUnlockedItemsSorted() {
        List<String> sortedItems = new ArrayList<>(unlockedItems);
        sortedItems.sort((a, b) -> {
            long timeA = unlockTimestamps.getOrDefault(a, 0L);
            long timeB = unlockTimestamps.getOrDefault(b, 0L);
            return Long.compare(timeB, timeA); // Most recent first
        });
        return sortedItems;
    }
    
    /**
     * Get all unlock timestamps
     */
    public Map<String, Long> getUnlockTimestamps() {
        return new HashMap<>(unlockTimestamps);
    }

    /**
     * Get progress percentage for an item (0-100)
     */
    public int getProgress(Item item) {
        int count = getCollectedCount(item);
        int threshold = getThreshold(item);
        return Math.min(100, (count * 100) / threshold);
    }

    /**
     * Get all collected items and their counts
     */
    public Map<String, Integer> getAllCollectedCounts() {
        return new HashMap<>(collectedCounts);
    }
    
    /**
     * Update data from server sync packet
     */
    public void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps) {
        this.collectedCounts.clear();
        this.collectedCounts.putAll(counts);
        this.unlockedItems.clear();
        this.unlockedItems.addAll(unlocked);
        this.unlockTimestamps.clear();
        this.unlockTimestamps.putAll(timestamps);
    }
    
    /**
     * Check if Journey Mode is enabled for this player
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Set whether Journey Mode is enabled for this player
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Copy all data from another attachment instance
     */
    public void copyFrom(JourneyDataAttachment other) {
        this.collectedCounts.clear();
        this.collectedCounts.putAll(other.collectedCounts);
        this.unlockedItems.clear();
        this.unlockedItems.addAll(other.unlockedItems);
        this.unlockTimestamps.clear();
        this.unlockTimestamps.putAll(other.unlockTimestamps);
        this.enabled = other.enabled;
    }

    /**
     * Clear all progress/unlocks but keep enabled status
     */
    public void reset() {
        this.collectedCounts.clear();
        this.unlockedItems.clear();
        this.unlockTimestamps.clear();
    }
}
