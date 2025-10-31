package com.aryangpt007.journeymode.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Core data structure for Journey Mode - player's collected items and unlocks.
 * Pure Java class with no Minecraft or loader dependencies.
 * Serialization handled by platform-specific wrappers.
 */
public class JourneyData {
    
    private final Map<String, Integer> collectedCounts;  // Item ID -> count collected
    private final Set<String> unlockedItems;             // Items unlocked for infinite access
    private final Map<String, Long> unlockTimestamps;    // Item ID -> unlock timestamp (milliseconds)
    private boolean enabled;                             // Whether Journey Mode is enabled for this player
    
    public JourneyData() {
        this.collectedCounts = new HashMap<>();
        this.unlockedItems = new HashSet<>();
        this.unlockTimestamps = new HashMap<>();
        this.enabled = true; // Default to enabled
    }
    
    /**
     * Deposit items into Journey Mode tracking
     * @param itemId The item ID (e.g., "minecraft:diamond")
     * @param count The number of items to deposit
     * @param threshold The threshold needed to unlock
     * @return true if this deposit unlocked the item
     */
    public boolean depositItem(String itemId, int count, int threshold) {
        int currentCount = collectedCounts.getOrDefault(itemId, 0);
        int newCount = currentCount + count;
        collectedCounts.put(itemId, newCount);
        
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
     * @param itemId The item ID
     * @return true if unlocked
     */
    public boolean isUnlocked(String itemId) {
        return unlockedItems.contains(itemId);
    }
    
    /**
     * Get the current collection count for an item
     * @param itemId The item ID
     * @return The count collected
     */
    public int getCollectedCount(String itemId) {
        return collectedCounts.getOrDefault(itemId, 0);
    }
    
    /**
     * Get all unlocked items
     * @return Set of unlocked item IDs
     */
    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }
    
    /**
     * Get unlocked items sorted by timestamp (most recent first)
     * @return List of item IDs sorted by unlock time
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
     * Get unlock timestamp for an item
     * @param itemId The item ID
     * @return The timestamp (milliseconds since epoch), or 0 if not unlocked
     */
    public long getUnlockTimestamp(String itemId) {
        return unlockTimestamps.getOrDefault(itemId, 0L);
    }
    
    /**
     * Get all unlock timestamps
     * @return Map of item ID to timestamp
     */
    public Map<String, Long> getUnlockTimestamps() {
        return new HashMap<>(unlockTimestamps);
    }
    
    /**
     * Get all collected items and their counts
     * @return Map of item ID to count
     */
    public Map<String, Integer> getCollectedCounts() {
        return new HashMap<>(collectedCounts);
    }
    
    /**
     * Check if Journey Mode is enabled for this player
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Set whether Journey Mode is enabled for this player
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Get progress percentage for an item (0-100)
     * @param itemId The item ID
     * @param threshold The threshold needed to unlock
     * @return Progress percentage (0-100)
     */
    public int getProgress(String itemId, int threshold) {
        int count = getCollectedCount(itemId);
        return Math.min(100, (count * 100) / threshold);
    }
    
    /**
     * Serialize to JSON object
     * @return JSON representation
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        
        // Serialize collected counts
        JsonObject countsJson = new JsonObject();
        for (Map.Entry<String, Integer> entry : collectedCounts.entrySet()) {
            countsJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("collected_counts", countsJson);
        
        // Serialize unlocked items
        JsonArray unlockedJson = new JsonArray();
        for (String itemId : unlockedItems) {
            unlockedJson.add(itemId);
        }
        json.add("unlocked_items", unlockedJson);
        
        // Serialize timestamps
        JsonObject timestampsJson = new JsonObject();
        for (Map.Entry<String, Long> entry : unlockTimestamps.entrySet()) {
            timestampsJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("unlock_timestamps", timestampsJson);
        
        return json;
    }
    
    /**
     * Deserialize from JSON object
     * @param json The JSON object
     * @return The deserialized data
     */
    public static JourneyData fromJson(JsonObject json) {
        JourneyData data = new JourneyData();
        
        if (json.has("enabled")) {
            data.enabled = json.get("enabled").getAsBoolean();
        }
        
        if (json.has("collected_counts")) {
            JsonObject countsJson = json.getAsJsonObject("collected_counts");
            for (String key : countsJson.keySet()) {
                data.collectedCounts.put(key, countsJson.get(key).getAsInt());
            }
        }
        
        if (json.has("unlocked_items")) {
            JsonArray unlockedJson = json.getAsJsonArray("unlocked_items");
            for (int i = 0; i < unlockedJson.size(); i++) {
                data.unlockedItems.add(unlockedJson.get(i).getAsString());
            }
        }
        
        if (json.has("unlock_timestamps")) {
            JsonObject timestampsJson = json.getAsJsonObject("unlock_timestamps");
            for (String key : timestampsJson.keySet()) {
                data.unlockTimestamps.put(key, timestampsJson.get(key).getAsLong());
            }
        }
        
        return data;
    }
    
    /**
     * Serialize to JSON string
     * @return JSON string
     */
    public String toJsonString() {
        return toJson().toString();
    }
    
    /**
     * Deserialize from JSON string
     * @param json The JSON string
     * @return The deserialized data
     */
    public static JourneyData fromJsonString(String json) {
        return fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    
    /**
     * Update data from sync (for client-side syncing)
     * @param counts The collected counts
     * @param unlocked The unlocked items
     * @param timestamps The unlock timestamps
     */
    public void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps) {
        this.collectedCounts.clear();
        this.collectedCounts.putAll(counts);
        this.unlockedItems.clear();
        this.unlockedItems.addAll(unlocked);
        this.unlockTimestamps.clear();
        this.unlockTimestamps.putAll(timestamps);
    }
}
