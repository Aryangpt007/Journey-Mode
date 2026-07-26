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
import net.minecraft.nbt.JsonToNBT;
import net.minecraftforge.common.util.Constants;

import java.util.*;

public class JourneyData implements IJourneyData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Map<String, Integer> collectedCounts;
    private final Set<String> unlockedItems;
    private final Map<String, Long> unlockTimestamps;
    private boolean enabled;
    private boolean showTooltips = true; // Per-player tooltip display preference (/journeymode tooltips)
    private String teamId; // null = no team; see TeamDataHandler for the per-world team registry
    // Client-display-only: the team's human-readable name, refreshed on every sync. Never
    // persisted (recomputed from the server each time) - unlike teamId, which IS persisted and
    // drives server-side deposit/fetch routing.
    private String teamDisplayName;

    // Section 11 Visual Polish state - client-side only, never persisted.
    private boolean hasSyncedOnce = false;
    private Set<String> pendingUnlockCelebration = Collections.emptySet();

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
        json.addProperty("show_tooltips", showTooltips);
        if (teamId != null) {
            json.addProperty("team_id", teamId);
        }

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

            if (json.has("show_tooltips")) {
                data.showTooltips = json.get("show_tooltips").getAsBoolean();
            } // else: absent in old files - keep the default (true), no data loss either way

            if (json.has("team_id") && !json.get("team_id").isJsonNull()) {
                data.teamId = json.get("team_id").getAsString();
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

        if (nbt.hasKey("show_tooltips")) {
            showTooltips = nbt.getBoolean("show_tooltips");
        } else {
            showTooltips = true; // absent in old data - never break old saves
        }

        if (nbt.hasKey("team_id")) {
            teamId = nbt.getString("team_id");
        } else {
            teamId = null; // absent in old data - never break old saves
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
        nbt.setBoolean("show_tooltips", showTooltips);
        if (teamId != null) {
            nbt.setString("team_id", teamId);
        }
        return nbt;
    }

    /**
     * Helper to get a normalized ItemStack, preserving only subtype NBT tags or metadata.
     */
    public static ItemStack getNormalizedStack(ItemStack original) {
        if (original.isEmpty()) return original;
        ItemStack normalized = new ItemStack(original.getItem(), 1, original.getMetadata());
        if (original.hasTagCompound()) {
            NBTTagCompound originalTag = original.getTagCompound();
            NBTTagCompound normalizedTag = new NBTTagCompound();
            boolean hasSubtype = false;
            
            if (originalTag.hasKey("Potion")) {
                normalizedTag.setString("Potion", originalTag.getString("Potion"));
                hasSubtype = true;
            }
            if (originalTag.hasKey("CustomPotionEffects")) {
                normalizedTag.setTag("CustomPotionEffects", originalTag.getTag("CustomPotionEffects"));
                hasSubtype = true;
            }
            if (originalTag.hasKey("CustomPotionColor")) {
                normalizedTag.setInteger("CustomPotionColor", originalTag.getInteger("CustomPotionColor"));
                hasSubtype = true;
            }
            if (originalTag.hasKey("StoredEnchantments")) {
                normalizedTag.setTag("StoredEnchantments", originalTag.getTag("StoredEnchantments"));
                hasSubtype = true;
            }
            if (originalTag.hasKey("Effects")) {
                normalizedTag.setTag("Effects", originalTag.getTag("Effects"));
                hasSubtype = true;
            }

            // Third-party NormalizationRules may contribute additional keys (denylisted
            // container/block-entity keys are stripped inside the API before this returns).
            for (String key : com.aryangpt007.journeymode.api.JourneyModeAPI.collectAdditionalNormalizationKeys(originalTag)) {
                if (originalTag.hasKey(key)) {
                    normalizedTag.setTag(key, originalTag.getTag(key));
                    hasSubtype = true;
                }
            }

            if (hasSubtype) {
                normalized.setTagCompound(normalizedTag);
            }
        }
        return normalized;
    }

    /**
     * Helper to generate a structured key from an ItemStack, preserving subtype NBT tags and metadata.
     */
    public static String getItemKey(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return "";
        }
        ItemStack normalized = getNormalizedStack(stack);
        String baseId = normalized.getItem().getRegistryName().toString();
        // Preserve metadata for metadata-based subtypes (like colored wool/dyes)
        if (normalized.getItem().getHasSubtypes() && !normalized.getItem().isDamageable()) {
            baseId = baseId + ":" + normalized.getMetadata();
        }
        if (normalized.hasTagCompound() && !normalized.getTagCompound().getKeySet().isEmpty()) {
            return baseId + "|" + normalized.getTagCompound().toString();
        }
        return baseId;
    }

    /**
     * Helper to reconstruct an ItemStack from a structured key.
     */
    public static ItemStack itemStackFromKey(String key) {
        if (key == null || key.isEmpty()) return ItemStack.EMPTY;
        int delimiter = key.indexOf('|');
        String keyWithoutNbt = delimiter == -1 ? key : key.substring(0, delimiter);
        
        int colon = keyWithoutNbt.indexOf(':');
        int metadata = 0;
        String baseId = keyWithoutNbt;
        int lastColon = keyWithoutNbt.lastIndexOf(':');
        if (lastColon > 0 && lastColon != keyWithoutNbt.indexOf(':')) {
            try {
                metadata = Integer.parseInt(keyWithoutNbt.substring(lastColon + 1));
                baseId = keyWithoutNbt.substring(0, lastColon);
            } catch (NumberFormatException e) {
                // Not a metadata suffix
            }
        }
        
        Item item = Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(baseId));
        if (item == null || item == net.minecraft.init.Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, 1, metadata);
        
        if (delimiter != -1) {
            String nbtStr = key.substring(delimiter + 1);
            try {
                stack.setTagCompound(JsonToNBT.getTagFromJson(nbtStr));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        return stack;
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
        
        String key = getItemKey(stack);
        int currentCount = collectedCounts.getOrDefault(key, 0);
        int newCount = currentCount + stack.getCount();
        collectedCounts.put(key, newCount);

        int threshold = getThreshold(stack);
        
        if (currentCount < threshold && newCount >= threshold) {
            unlockedItems.add(key);
            unlockTimestamps.put(key, System.currentTimeMillis());
            return true;
        }
        return false;
    }

    @Override
    public boolean isUnlocked(Item item) {
        if (item.getRegistryName() == null) return false;
        String itemId = item.getRegistryName().toString();
        if (unlockedItems.contains(itemId)) return true;
        for (String key : unlockedItems) {
            if (key.startsWith(itemId + "|") || key.startsWith(itemId + ":")) return true;
        }
        return false;
    }

    @Override
    public boolean isUnlocked(ItemStack stack) {
        return isUnlocked(getItemKey(stack));
    }

    @Override
    public boolean isUnlocked(String key) {
        return unlockedItems.contains(key);
    }

    @Override
    public int getCollectedCount(Item item) {
        if (item.getRegistryName() == null) return 0;
        String itemId = item.getRegistryName().toString();
        return collectedCounts.getOrDefault(itemId, 0);
    }

    @Override
    public int getCollectedCount(ItemStack stack) {
        return collectedCounts.getOrDefault(getItemKey(stack), 0);
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
        // Section 11 Visual Polish: remember which keys are newly-unlocked-since-last-sync so
        // the client can play a sound/show a message on the transition, without needing a
        // dedicated "newly_unlocked" packet field - the full unlocked set is already synced
        // every time. The very first sync after connecting (client capability starts empty)
        // must be silent, or every already-unlocked item would "celebrate" on login.
        if (hasSyncedOnce) {
            Set<String> newKeys = new HashSet<>(unlocked);
            newKeys.removeAll(this.unlockedItems);
            this.pendingUnlockCelebration = newKeys;
        } else {
            this.pendingUnlockCelebration = Collections.emptySet();
            this.hasSyncedOnce = true;
        }

        this.collectedCounts.clear();
        this.collectedCounts.putAll(counts);
        this.unlockedItems.clear();
        this.unlockedItems.addAll(unlocked);
        this.unlockTimestamps.clear();
        this.unlockTimestamps.putAll(timestamps);
    }

    @Override
    public void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps, boolean enabled, boolean showTooltips) {
        updateFromSync(counts, unlocked, timestamps);
        this.enabled = enabled;
        this.showTooltips = showTooltips;
    }

    @Override
    public void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps, boolean enabled, boolean showTooltips, String teamDisplayName) {
        updateFromSync(counts, unlocked, timestamps, enabled, showTooltips);
        this.teamDisplayName = (teamDisplayName == null || teamDisplayName.isEmpty()) ? null : teamDisplayName;
    }

    @Override
    public String getTeamDisplayName() {
        return teamDisplayName;
    }

    @Override
    public Set<String> getAndClearNewlyUnlocked() {
        Set<String> result = pendingUnlockCelebration;
        pendingUnlockCelebration = Collections.emptySet();
        return result;
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
    public boolean isShowTooltips() {
        return showTooltips;
    }

    @Override
    public void setShowTooltips(boolean showTooltips) {
        this.showTooltips = showTooltips;
    }

    @Override
    public String getTeamId() {
        return teamId;
    }

    @Override
    public void setTeamId(String teamId) {
        this.teamId = teamId;
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
        this.showTooltips = other.isShowTooltips();
        this.teamId = other.getTeamId();
    }

    @Override
    public void reset() {
        this.collectedCounts.clear();
        this.unlockedItems.clear();
        this.unlockTimestamps.clear();
    }

    @Override
    public void grant(String key) {
        unlockedItems.add(key);
        unlockTimestamps.put(key, System.currentTimeMillis());
    }

    @Override
    public void revoke(String key) {
        unlockedItems.remove(key);
        unlockTimestamps.remove(key);
        collectedCounts.put(key, 0);
    }

    @Override
    public void checkPendingUnlocks() {
        for (Map.Entry<String, Integer> entry : new HashMap<>(collectedCounts).entrySet()) {
            String key = entry.getKey();
            if (unlockedItems.contains(key)) continue;
            ItemStack stack = itemStackFromKey(key);
            if (stack.isEmpty()) continue;
            if (entry.getValue() >= getThreshold(stack.getItem())) {
                unlockedItems.add(key);
                unlockTimestamps.put(key, System.currentTimeMillis());
            }
        }
    }
}
