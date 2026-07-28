package com.aryangpt007.journeymode.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Section 1 Shared Team Catalogs. Mirrors the progress-tracking shape of JourneyData
 * (independent collectedCounts/unlockedItems/unlockTimestamps maps) but shared across every
 * member - both deposits and unlocks pool, per the resolved design decision. Deliberately NOT a
 * subclass or shared-interface refactor of JourneyData: keeping the two independent avoids ever
 * accidentally aliasing a personal and team map together.
 */
public class TeamData {
    private final String id; // lowercase(name), unique within one world's teams file
    private String displayName;
    private UUID ownerUuid;
    private final Set<UUID> members = new HashSet<UUID>();

    private final Map<String, Integer> collectedCounts = new HashMap<String, Integer>();
    private final Set<String> unlockedItems = new HashSet<String>();
    private final Map<String, Long> unlockTimestamps = new HashMap<String, Long>();

    // Own calculator instance - 1.12.2's RecipeDepthCalculator takes no constructor args (it
    // reads CraftingManager.REGISTRY directly), unlike the 1.20.1 reference which needs a
    // RecipeManager/RegistryAccess pair threaded through. Nothing to lazily initialize here.
    private final RecipeDepthCalculator recipeCalculator = new RecipeDepthCalculator();

    public TeamData(String id, String displayName, UUID ownerUuid) {
        this.id = id;
        this.displayName = displayName;
        this.ownerUuid = ownerUuid;
        this.members.add(ownerUuid);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }
    public Set<UUID> getMembers() { return members; }

    public int getThreshold(Item item) {
        return recipeCalculator.calculateThreshold(item);
    }

    /** @return true if this deposit crossed the threshold and unlocked the item for the whole team. */
    public boolean depositItem(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return false;
        }

        String key = JourneyData.getItemKey(stack);
        int currentCount = collectedCounts.containsKey(key) ? collectedCounts.get(key) : 0;
        int newCount = currentCount + stack.getCount();
        collectedCounts.put(key, newCount);

        int threshold = getThreshold(stack.getItem());
        if (currentCount < threshold && newCount >= threshold) {
            unlockedItems.add(key);
            unlockTimestamps.put(key, System.currentTimeMillis());
            return true;
        }
        return false;
    }

    public boolean isUnlocked(String key) {
        return unlockedItems.contains(key);
    }

    public boolean isUnlocked(ItemStack stack) {
        return isUnlocked(JourneyData.getItemKey(stack));
    }

    public int getCollectedCount(ItemStack stack) {
        String key = JourneyData.getItemKey(stack);
        return collectedCounts.containsKey(key) ? collectedCounts.get(key) : 0;
    }

    public int getProgress(ItemStack stack) {
        int count = getCollectedCount(stack);
        int threshold = getThreshold(stack.getItem());
        if (threshold <= 0) return 100;
        // long math: a collected count past ~21M would overflow int and report a negative
        // percentage. Thresholds are always >= 1 (calculateThreshold clamps), so no /0 here.
        return (int) Math.min(100L, (long) count * 100L / threshold);
    }

    public Set<String> getUnlockedItems() {
        return new HashSet<String>(unlockedItems);
    }

    public Map<String, Integer> getAllCollectedCounts() {
        return new HashMap<String, Integer>(collectedCounts);
    }

    public Map<String, Long> getUnlockTimestamps() {
        return new HashMap<String, Long>(unlockTimestamps);
    }

    /** Same never-re-lock-only-instant-unlock semantics as JourneyData.checkPendingUnlocks. */
    public void checkPendingUnlocks() {
        for (Map.Entry<String, Integer> entry : new HashMap<String, Integer>(collectedCounts).entrySet()) {
            String key = entry.getKey();
            if (unlockedItems.contains(key)) continue;
            ItemStack stack = JourneyData.itemStackFromKey(key);
            if (stack.isEmpty()) continue;
            if (entry.getValue() >= getThreshold(stack.getItem())) {
                unlockedItems.add(key);
                unlockTimestamps.put(key, System.currentTimeMillis());
            }
        }
    }

    // ---------------------------------------------------------------- (de)serialization

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", displayName);
        json.addProperty("owner_uuid", ownerUuid.toString());

        JsonArray membersJson = new JsonArray();
        for (UUID uuid : members) {
            membersJson.add(uuid.toString());
        }
        json.add("members", membersJson);

        JsonObject countsJson = new JsonObject();
        for (Map.Entry<String, Integer> entry : collectedCounts.entrySet()) {
            countsJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("collected_counts", countsJson);

        JsonArray unlockedJson = new JsonArray();
        for (String key : unlockedItems) {
            unlockedJson.add(key);
        }
        json.add("unlocked_items", unlockedJson);

        JsonObject timestampsJson = new JsonObject();
        for (Map.Entry<String, Long> entry : unlockTimestamps.entrySet()) {
            timestampsJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("unlock_timestamps", timestampsJson);

        return json;
    }

    public static TeamData fromJson(String id, JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : id;
        UUID owner = UUID.fromString(json.get("owner_uuid").getAsString());
        TeamData team = new TeamData(id, name, owner);
        team.members.clear(); // constructor pre-added owner; load the real member list below

        if (json.has("members")) {
            for (JsonElement e : json.getAsJsonArray("members")) {
                team.members.add(UUID.fromString(e.getAsString()));
            }
        }
        if (team.members.isEmpty()) {
            team.members.add(owner); // defensive: never end up with an ownerless/memberless team
        }

        if (json.has("collected_counts")) {
            JsonObject counts = json.getAsJsonObject("collected_counts");
            for (Map.Entry<String, JsonElement> e : counts.entrySet()) {
                team.collectedCounts.put(e.getKey(), e.getValue().getAsInt());
            }
        }
        if (json.has("unlocked_items")) {
            for (JsonElement e : json.getAsJsonArray("unlocked_items")) {
                team.unlockedItems.add(e.getAsString());
            }
        }
        if (json.has("unlock_timestamps")) {
            JsonObject timestamps = json.getAsJsonObject("unlock_timestamps");
            for (Map.Entry<String, JsonElement> e : timestamps.entrySet()) {
                team.unlockTimestamps.put(e.getKey(), e.getValue().getAsLong());
            }
        }

        return team;
    }
}
