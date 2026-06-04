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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

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
     * Helper to get a normalized ItemStack, preserving only subtype NBT tags.
     */
    public static ItemStack getNormalizedStack(ItemStack original) {
        if (original.isEmpty()) return original;
        ItemStack normalized = new ItemStack(original.getItem());
        if (original.hasTag()) {
            CompoundTag originalTag = original.getTag();
            CompoundTag normalizedTag = new CompoundTag();
            boolean hasSubtype = false;
            
            if (originalTag.contains("Potion")) {
                normalizedTag.putString("Potion", originalTag.getString("Potion"));
                hasSubtype = true;
            }
            if (originalTag.contains("CustomPotionEffects")) {
                normalizedTag.put("CustomPotionEffects", originalTag.get("CustomPotionEffects"));
                hasSubtype = true;
            }
            if (originalTag.contains("CustomPotionColor")) {
                normalizedTag.putInt("CustomPotionColor", originalTag.getInt("CustomPotionColor"));
                hasSubtype = true;
            }
            if (originalTag.contains("StoredEnchantments")) {
                normalizedTag.put("StoredEnchantments", originalTag.get("StoredEnchantments"));
                hasSubtype = true;
            }
            if (originalTag.contains("Effects")) {
                normalizedTag.put("Effects", originalTag.get("Effects"));
                hasSubtype = true;
            }
            
            if (hasSubtype) {
                normalized.setTag(normalizedTag);
            }
        }
        return normalized;
    }

    /**
     * Helper to generate a structured key from an ItemStack, preserving subtype NBT tags.
     */
    public static String getItemKey(ItemStack stack) {
        ItemStack normalized = getNormalizedStack(stack);
        String baseId = Registry.ITEM.getKey(normalized.getItem()).toString();
        if (normalized.hasTag() && !normalized.getTag().isEmpty()) {
            return baseId + "|" + normalized.getTag().toString();
        }
        return baseId;
    }

    /**
     * Helper to reconstruct an ItemStack from a structured key.
     */
    public static ItemStack itemStackFromKey(String key) {
        if (key == null || key.isEmpty()) return ItemStack.EMPTY;
        int delimiter = key.indexOf('|');
        if (delimiter == -1) {
            Item item = Registry.ITEM.get(new ResourceLocation(key));
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
        String itemId = key.substring(0, delimiter);
        String nbtStr = key.substring(delimiter + 1);
        try {
            Item item = Registry.ITEM.get(new ResourceLocation(itemId));
            if (item == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(item);
            stack.setTag(TagParser.parseTag(nbtStr));
            return stack;
        } catch (Exception e) {
            Item item = Registry.ITEM.get(new ResourceLocation(itemId));
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
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
        
        String key = getItemKey(stack);
        int currentCount = collectedCounts.getOrDefault(key, 0);
        int newCount = currentCount + stack.getCount();
        collectedCounts.put(key, newCount);

        int threshold = getThreshold(stack.getItem());
        
        // Check if we just reached the threshold
        if (currentCount < threshold && newCount >= threshold) {
            unlockedItems.add(key);
            unlockTimestamps.put(key, System.currentTimeMillis());
            return true; // Item was just unlocked
        }
        return false;
    }

    /**
     * Check if an item is unlocked for infinite retrieval (base fallback)
     */
    public boolean isUnlocked(Item item) {
        String itemId = Registry.ITEM.getKey(item).toString();
        if (unlockedItems.contains(itemId)) return true;
        for (String key : unlockedItems) {
            if (key.startsWith(itemId + "|")) return true;
        }
        return false;
    }

    /**
     * Check if a specific key is unlocked
     */
    public boolean isUnlocked(String key) {
        return unlockedItems.contains(key);
    }

    /**
     * Check if a specific ItemStack is unlocked
     */
    public boolean isUnlocked(ItemStack stack) {
        return isUnlocked(getItemKey(stack));
    }

    /**
     * Get the current collection count for an item (base fallback)
     */
    public int getCollectedCount(Item item) {
        String itemId = Registry.ITEM.getKey(item).toString();
        return collectedCounts.getOrDefault(itemId, 0);
    }

    /**
     * Get the current collection count for a specific ItemStack
     */
    public int getCollectedCount(ItemStack stack) {
        return collectedCounts.getOrDefault(getItemKey(stack), 0);
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
     * Get progress percentage for an item (0-100) (base fallback)
     */
    public int getProgress(Item item) {
        int count = getCollectedCount(item);
        int threshold = getThreshold(item);
        return Math.min(100, (count * 100) / threshold);
    }

    /**
     * Get progress percentage for a specific ItemStack
     */
    public int getProgress(ItemStack stack) {
        int count = getCollectedCount(stack);
        int threshold = getThreshold(stack.getItem());
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
