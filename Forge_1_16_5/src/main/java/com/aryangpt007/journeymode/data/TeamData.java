package com.aryangpt007.journeymode.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.RecipeManager;

import java.util.*;

/**
 * §1 Shared Team Catalogs. Mirrors the progress-tracking shape of JourneyDataAttachment
 * (collected counts / unlocked items / timestamps) but shared across every member - both
 * deposits and unlocks pool, per the resolved design decision. Deliberately NOT a subclass or
 * shared-interface refactor of JourneyDataAttachment: keeping the two independent avoids ever
 * accidentally aliasing a personal and team map together.
 *
 * 1.16.5 note: no RegistryAccess parameter anywhere (that parameter was introduced in a later
 * version's RecipeDepthCalculator/JourneyDataAttachment) - this mirrors the 1.16.5
 * JourneyDataAttachment, which only ever takes a RecipeManager.
 */
public class TeamData {
    private final String id; // lowercase(name), unique within one world's teams file
    private String displayName;
    private UUID ownerUuid;
    private final Set<UUID> members = new HashSet<>();

    private final Map<String, Integer> collectedCounts = new HashMap<>();
    private final Set<String> unlockedItems = new HashSet<>();
    private final Map<String, Long> unlockTimestamps = new HashMap<>();

    private RecipeDepthCalculator recipeCalculator;

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

    public void initializeCalculator(RecipeManager recipeManager) {
        if (this.recipeCalculator == null) {
            this.recipeCalculator = new RecipeDepthCalculator(recipeManager);
        }
    }

    public int getThreshold(net.minecraft.item.Item item) {
        return recipeCalculator != null ? recipeCalculator.calculateThreshold(item) : 1;
    }

    /** @return true if this deposit crossed the threshold and unlocked the item for the whole team. */
    public boolean depositItem(ItemStack stack, RecipeManager recipeManager) {
        initializeCalculator(recipeManager);

        String key = JourneyDataAttachment.getItemKey(stack);
        int currentCount = collectedCounts.getOrDefault(key, 0);
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
        return isUnlocked(JourneyDataAttachment.getItemKey(stack));
    }

    public int getCollectedCount(ItemStack stack) {
        return collectedCounts.getOrDefault(JourneyDataAttachment.getItemKey(stack), 0);
    }

    public int getProgress(ItemStack stack) {
        int count = getCollectedCount(stack);
        int threshold = getThreshold(stack.getItem());
        // long math: a collected count past ~21M would overflow int and report a negative
        // percentage. Thresholds are always >= 1 (calculateThreshold clamps), so no /0 here.
        return (int) Math.min(100L, (long) count * 100L / threshold);
    }

    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }

    public Map<String, Integer> getAllCollectedCounts() {
        return new HashMap<>(collectedCounts);
    }

    public Map<String, Long> getUnlockTimestamps() {
        return new HashMap<>(unlockTimestamps);
    }

    /** Same never-re-lock-only-instant-unlock semantics as JourneyDataAttachment.checkPendingUnlocks. */
    public void checkPendingUnlocks(RecipeManager recipeManager) {
        initializeCalculator(recipeManager);
        for (Map.Entry<String, Integer> entry : new HashMap<>(collectedCounts).entrySet()) {
            String key = entry.getKey();
            if (unlockedItems.contains(key)) continue;
            ItemStack stack = JourneyDataAttachment.itemStackFromKey(key);
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
        collectedCounts.forEach(countsJson::addProperty);
        json.add("collected_counts", countsJson);

        JsonArray unlockedJson = new JsonArray();
        unlockedItems.forEach(unlockedJson::add);
        json.add("unlocked_items", unlockedJson);

        JsonObject timestampsJson = new JsonObject();
        unlockTimestamps.forEach(timestampsJson::addProperty);
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
            counts.entrySet().forEach(e -> team.collectedCounts.put(e.getKey(), e.getValue().getAsInt()));
        }
        if (json.has("unlocked_items")) {
            json.getAsJsonArray("unlocked_items").forEach(e -> team.unlockedItems.add(e.getAsString()));
        }
        if (json.has("unlock_timestamps")) {
            JsonObject timestamps = json.getAsJsonObject("unlock_timestamps");
            timestamps.entrySet().forEach(e -> team.unlockTimestamps.put(e.getKey(), e.getValue().getAsLong()));
        }

        return team;
    }
}
