package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.*;

public class JourneyData implements IJourneyData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Map<String, Integer> collectedCounts;
    private final Set<String> unlockedItems;
    private final Map<String, Long> unlockTimestamps;
    private boolean enabled;
    
    public static final int UNLOCK_THRESHOLD = 30;
    private final RecipeDepthCalculator recipeCalculator;

    public JourneyData() {
        this.collectedCounts = new HashMap<>();
        this.unlockedItems = new HashSet<>();
        this.unlockTimestamps = new HashMap<>();
        this.enabled = true;
        this.recipeCalculator = new RecipeDepthCalculator();
    }

    @Override
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

    public static JourneyData fromJsonString(String jsonString) {
        JourneyData data = new JourneyData();
        try {
            JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
            
            if (json.has("collected_counts")) {
                JsonObject countsJson = json.getAsJsonObject("collected_counts");
                countsJson.entrySet().forEach(entry -> 
                    data.collectedCounts.put(entry.getKey(), entry.getValue().getAsInt())
                );
            }
            
            if (json.has("unlocked_items")) {
                JsonArray unlockedJson = json.getAsJsonArray("unlocked_items");
                unlockedJson.forEach(element -> 
                    data.unlockedItems.add(element.getAsString())
                );
            }
            
            if (json.has("unlock_timestamps")) {
                JsonObject timestampsJson = json.getAsJsonObject("unlock_timestamps");
                timestampsJson.entrySet().forEach(entry -> 
                    data.unlockTimestamps.put(entry.getKey(), entry.getValue().getAsLong())
                );
            }
            
            if (json.has("enabled")) {
                data.enabled = json.get("enabled").getAsBoolean();
            }
        } catch (Exception e) {
            // Keep defaults
        }
        return data;
    }

    @Override
    public void fromNBT(NBTTagCompound nbt) {
        collectedCounts.clear();
        unlockedItems.clear();
        unlockTimestamps.clear();
        
        if (nbt.hasKey("collected_counts")) {
            NBTTagCompound counts = nbt.getCompoundTag("collected_counts");
            for (String key : counts.getKeySet()) {
                collectedCounts.put(key, counts.getInteger(key));
            }
        }
        
        if (nbt.hasKey("unlocked_items")) {
            NBTTagList unlockedList = nbt.getTagList("unlocked_items", Constants.NBT.TAG_STRING);
            for (int i = 0; i < unlockedList.tagCount(); i++) {
                unlockedItems.add(unlockedList.getStringTagAt(i));
            }
        }
        
        if (nbt.hasKey("unlock_timestamps")) {
            NBTTagCompound timestamps = nbt.getCompoundTag("unlock_timestamps");
            for (String key : timestamps.getKeySet()) {
                unlockTimestamps.put(key, timestamps.getLong(key));
            }
        }
        
        if (nbt.hasKey("enabled")) {
            enabled = nbt.getBoolean("enabled");
        } else {
            enabled = true;
        }
    }

    @Override
    public NBTTagCompound toNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        
        NBTTagCompound counts = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : collectedCounts.entrySet()) {
            counts.setInteger(entry.getKey(), entry.getValue());
        }
        nbt.setTag("collected_counts", counts);
        
        NBTTagList unlockedList = new NBTTagList();
        for (String item : unlockedItems) {
            unlockedList.appendTag(new net.minecraft.nbt.NBTTagString(item));
        }
        nbt.setTag("unlocked_items", unlockedList);
        
        NBTTagCompound timestamps = new NBTTagCompound();
        for (Map.Entry<String, Long> entry : unlockTimestamps.entrySet()) {
            timestamps.setLong(entry.getKey(), entry.getValue());
        }
        nbt.setTag("unlock_timestamps", timestamps);
        
        nbt.setBoolean("enabled", enabled);
        return nbt;
    }

    public static String getItemKey(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return "";
        }
        String baseId = stack.getItem().getRegistryName().toString();
        if (stack.getItem().getHasSubtypes() && !stack.getItem().isDamageable()) {
            return baseId + ":" + stack.getMetadata();
        }
        return baseId;
    }

    @Override
    public int getThreshold(Item item) {
        return recipeCalculator.calculateThreshold(item);
    }

    @Override
    public int getThreshold(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) return 30;
        String itemId = getItemKey(stack);
        Integer configOverride = ConfigHandler.getThresholdOverride(itemId);
        if (configOverride != null) {
            return Math.max(1, configOverride);
        }
        return getThreshold(stack.getItem());
    }

    @Override
    public boolean depositItem(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return false;
        }
        
        String itemId = getItemKey(stack);
        int currentCount = collectedCounts.getOrDefault(itemId, 0);
        int newCount = currentCount + stack.getCount();
        collectedCounts.put(itemId, newCount);

        int threshold = getThreshold(stack);
        
        if (currentCount < threshold && newCount >= threshold) {
            unlockedItems.add(itemId);
            unlockTimestamps.put(itemId, System.currentTimeMillis());
            return true;
        }
        return false;
    }

    @Override
    public boolean isUnlocked(Item item) {
        if (item.getRegistryName() == null) return false;
        String itemId = item.getRegistryName().toString();
        return unlockedItems.contains(itemId);
    }

    @Override
    public boolean isUnlocked(ItemStack stack) {
        String itemId = getItemKey(stack);
        return unlockedItems.contains(itemId);
    }

    @Override
    public int getCollectedCount(Item item) {
        if (item.getRegistryName() == null) return 0;
        String itemId = item.getRegistryName().toString();
        return collectedCounts.getOrDefault(itemId, 0);
    }

    @Override
    public int getCollectedCount(ItemStack stack) {
        String itemId = getItemKey(stack);
        return collectedCounts.getOrDefault(itemId, 0);
    }

    @Override
    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }

    @Override
    public List<String> getUnlockedItemsSorted() {
        List<String> sortedItems = new ArrayList<>(unlockedItems);
        sortedItems.sort((a, b) -> {
            long timeA = unlockTimestamps.getOrDefault(a, 0L);
            long timeB = unlockTimestamps.getOrDefault(b, 0L);
            return Long.compare(timeB, timeA);
        });
        return sortedItems;
    }

    @Override
    public Map<String, Long> getUnlockTimestamps() {
        return new HashMap<>(unlockTimestamps);
    }

    @Override
    public int getProgress(Item item) {
        int count = getCollectedCount(item);
        int threshold = getThreshold(item);
        if (threshold <= 0) return 100;
        return Math.min(100, (count * 100) / threshold);
    }

    @Override
    public int getProgress(ItemStack stack) {
        int count = getCollectedCount(stack);
        int threshold = getThreshold(stack);
        if (threshold <= 0) return 100;
        return Math.min(100, (count * 100) / threshold);
    }

    @Override
    public Map<String, Integer> getAllCollectedCounts() {
        return new HashMap<>(collectedCounts);
    }

    @Override
    public void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps) {
        this.collectedCounts.clear();
        this.collectedCounts.putAll(counts);
        this.unlockedItems.clear();
        this.unlockedItems.addAll(unlocked);
        this.unlockTimestamps.clear();
        this.unlockTimestamps.putAll(timestamps);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void copyFrom(IJourneyData other) {
        this.collectedCounts.clear();
        this.collectedCounts.putAll(other.getAllCollectedCounts());
        this.unlockedItems.clear();
        this.unlockedItems.addAll(other.getUnlockedItems());
        this.unlockTimestamps.clear();
        this.unlockTimestamps.putAll(other.getUnlockTimestamps());
        this.enabled = other.isEnabled();
    }

    @Override
    public void reset() {
        this.collectedCounts.clear();
        this.unlockedItems.clear();
        this.unlockTimestamps.clear();
    }
}
