package com.aryangpt007.journeymode.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.Tag;
import net.minecraft.core.component.DataComponents;

import java.util.*;

/**
 * Stores player's Journey Mode data - tracked items and unlocked items
 */
public class JourneyDataAttachment {
    private static final Gson GSON = new Gson();

    private final Map<String, Integer> collectedCounts; // Item ID -> count collected
    private final Set<String> unlockedItems; // Items unlocked for infinite access
    private final Map<String, Long> unlockTimestamps; // Item ID -> unlock timestamp (milliseconds)
    private boolean enabled; // Whether Journey Mode is enabled for this player

    public static final int UNLOCK_THRESHOLD = 30; // Fallback value
    private RecipeDepthCalculator recipeCalculator; // Lazily initialized

    public JourneyDataAttachment() {
        this.collectedCounts = new HashMap<>();
        this.unlockedItems = new HashSet<>();
        this.unlockTimestamps = new HashMap<>();
        this.enabled = true; // Default to enabled
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
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
            
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
    
    public void initializeCalculator(RecipeManager recipeManager, RegistryAccess registryAccess) {
        if (this.recipeCalculator == null) {
            this.recipeCalculator = new RecipeDepthCalculator(recipeManager, registryAccess);
        }
    }
    
    public int getThreshold(Item item) {
        if (recipeCalculator != null) {
            return recipeCalculator.calculateThreshold(item);
        }
        return item.getDefaultMaxStackSize() == 1 ? 1 : UNLOCK_THRESHOLD;
    }

    /**
     * Helper to get a normalized ItemStack, preserving only subtype components.
     */
    public static ItemStack getNormalizedStack(ItemStack original) {
        if (original.isEmpty()) return original;
        ItemStack normalized = new ItemStack(original.getItem());
        if (original.has(DataComponents.POTION_CONTENTS)) {
            normalized.set(DataComponents.POTION_CONTENTS, original.get(DataComponents.POTION_CONTENTS));
        }
        if (original.has(DataComponents.STORED_ENCHANTMENTS)) {
            normalized.set(DataComponents.STORED_ENCHANTMENTS, original.get(DataComponents.STORED_ENCHANTMENTS));
        }
        if (original.has(DataComponents.INSTRUMENT)) {
            normalized.set(DataComponents.INSTRUMENT, original.get(DataComponents.INSTRUMENT));
        }
        if (original.has(DataComponents.SUSPICIOUS_STEW_EFFECTS)) {
            normalized.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, original.get(DataComponents.SUSPICIOUS_STEW_EFFECTS));
        }
        return normalized;
    }

    /**
     * Helper to generate a structured key from an ItemStack, preserving subtype components.
     */
    public static String getItemKey(ItemStack stack, RegistryAccess registryAccess) {
        ItemStack normalized = getNormalizedStack(stack);
        String baseId = BuiltInRegistries.ITEM.getKey(normalized.getItem()).toString();
        if (normalized.has(DataComponents.POTION_CONTENTS) ||
            normalized.has(DataComponents.STORED_ENCHANTMENTS) ||
            normalized.has(DataComponents.INSTRUMENT) ||
            normalized.has(DataComponents.SUSPICIOUS_STEW_EFFECTS)) {
            try {
                Tag tag = normalized.save(registryAccess);
                return baseId + "|" + tag.toString();
            } catch (Exception e) {
                // Fallback
            }
        }
        return baseId;
    }

    /**
     * Helper to reconstruct an ItemStack from a structured key.
     */
    public static ItemStack itemStackFromKey(String key, RegistryAccess registryAccess) {
        if (key == null || key.isEmpty()) return ItemStack.EMPTY;
        int delimiter = key.indexOf('|');
        if (delimiter == -1) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
        String itemId = key.substring(0, delimiter);
        String nbtStr = key.substring(delimiter + 1);
        try {
            CompoundTag tag = TagParser.parseTag(nbtStr);
            return ItemStack.parse(registryAccess, tag).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
    }

    public boolean depositItem(ItemStack stack, RecipeManager recipeManager, RegistryAccess registryAccess) {
        initializeCalculator(recipeManager, registryAccess);
        
        String key = getItemKey(stack, registryAccess);
        int currentCount = collectedCounts.getOrDefault(key, 0);
        int newCount = currentCount + stack.getCount();
        collectedCounts.put(key, newCount);

        int threshold = getThreshold(stack.getItem());
        
        if (currentCount < threshold && newCount >= threshold) {
            unlockedItems.add(key);
            unlockTimestamps.put(key, System.currentTimeMillis());
            return true; // Item was just unlocked
        }
        return false;
    }

    public boolean isUnlocked(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        if (unlockedItems.contains(itemId)) return true;
        for (String key : unlockedItems) {
            if (key.startsWith(itemId + "|")) return true;
        }
        return false;
    }

    public boolean isUnlocked(String key) {
        return unlockedItems.contains(key);
    }

    public boolean isUnlocked(ItemStack stack, RegistryAccess registryAccess) {
        return isUnlocked(getItemKey(stack, registryAccess));
    }

    public int getCollectedCount(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        return collectedCounts.getOrDefault(itemId, 0);
    }

    public int getCollectedCount(ItemStack stack, RegistryAccess registryAccess) {
        return collectedCounts.getOrDefault(getItemKey(stack, registryAccess), 0);
    }

    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }
    
    public List<String> getUnlockedItemsSorted() {
        List<String> sortedItems = new ArrayList<>(unlockedItems);
        sortedItems.sort((a, b) -> {
            long timeA = unlockTimestamps.getOrDefault(a, 0L);
            long timeB = unlockTimestamps.getOrDefault(b, 0L);
            return Long.compare(timeB, timeA);
        });
        return sortedItems;
    }
    
    public Map<String, Long> getUnlockTimestamps() {
        return new HashMap<>(unlockTimestamps);
    }

    public int getProgress(Item item) {
        int count = getCollectedCount(item);
        int threshold = getThreshold(item);
        return Math.min(100, (count * 100) / threshold);
    }

    public int getProgress(ItemStack stack, RegistryAccess registryAccess) {
        int count = getCollectedCount(stack, registryAccess);
        int threshold = getThreshold(stack.getItem());
        return Math.min(100, (count * 100) / threshold);
    }

    public Map<String, Integer> getAllCollectedCounts() {
        return new HashMap<>(collectedCounts);
    }
    
    public void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps) {
        this.collectedCounts.clear();
        this.collectedCounts.putAll(counts);
        this.unlockedItems.clear();
        this.unlockedItems.addAll(unlocked);
        this.unlockTimestamps.clear();
        this.unlockTimestamps.putAll(timestamps);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void copyFrom(JourneyDataAttachment other) {
        this.collectedCounts.clear();
        this.collectedCounts.putAll(other.collectedCounts);
        this.unlockedItems.clear();
        this.unlockedItems.addAll(other.unlockedItems);
        this.unlockTimestamps.clear();
        this.unlockTimestamps.putAll(other.unlockTimestamps);
        this.enabled = other.enabled;
    }

    public void reset() {
        this.collectedCounts.clear();
        this.unlockedItems.clear();
        this.unlockTimestamps.clear();
    }
}
