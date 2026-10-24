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

    public JourneyData() {
        this.collectedCounts = new HashMap<>();
        this.unlockedItems = new HashSet<>();
        this.unlockTimestamps = new HashMap<>();
        this.enabled = true;
    }

    /**
     * Build the JSON form directly. GlobalDataHandler used to call toJsonString() and then
     * immediately re-parse the result back into a JsonObject on every save - a pure
     * serialize/parse round trip with no purpose, once per deposit.
     */
    @Override
    public JsonObject toJsonObject() {
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

        return json;
    }

    @Override
    public String toJsonString() {
        return GSON.toJson(toJsonObject());
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
            // Deny-by-exception (see NormalizationRules). 1.8.1 did the opposite - an allowlist of
            // a handful of vanilla keys - which silently discarded every modded NBT identity and is
            // what broke Apotheosis gems, TACZ guns, Iron's Spells scrolls and GregTech hammers.
            String itemNamespace = NormalizationRules.namespaceOf(original.getItem().getRegistryName().toString());
            NBTTagCompound cleaned = NormalizationRules.strip(original.getTagCompound(), itemNamespace);

            // Third-party rules may contribute keys, but every one is re-checked against the
            // strip rules first. JourneyModeAPI.DENYLISTED_KEYS is only four entries and does
            // NOT cover ForgeCaps, backpacks, tanks and the rest of the container gate - taking
            // the API at its word here would reopen the duplication hole outright.
            for (String key : com.aryangpt007.journeymode.api.JourneyModeAPI.collectAdditionalNormalizationKeys(original.getTagCompound())) {
                if (NormalizationRules.isStripped(key, key, itemNamespace)) continue;
                if (original.getTagCompound().hasKey(key)
                        && !cleaned.hasKey(key)) {
                    cleaned.setTag(key, original.getTagCompound().getTag(key).copy());
                }
            }

            if (!cleaned.getKeySet().isEmpty()) {
                normalized.setTagCompound(cleaned);
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
            // Canonical (recursively key-sorted) rather than toString(), whose order follows
            // HashMap bucket layout and is therefore input-dependent once tags are copied from the
            // source stack instead of rebuilt. See NormalizationRules.canonical.
            return baseId + "|" + NormalizationRules.canonical(normalized.getTagCompound());
        }
        return baseId;
    }

    /**
     * Helper to reconstruct an ItemStack from a structured key.
     */
    /**
     * Rewrite a stored key into its current canonical spelling, for the schema 2 to 3 migration.
     *
     * <p>Only the NBT half is touched: re-parsed, re-run through the current strip rules, and
     * re-emitted with sorted keys, so an old key lines up with what a fresh deposit produces today.
     *
     * <p>Two deliberate limits, both consequences of what 1.8.1 already threw away. A bare key (no
     * '|') stays bare - if the item's identity lived in NBT that 1.8.1 stripped, that information
     * is not in the file and cannot be invented, so the entry stays a base-item unlock and keeps
     * working. And if the NBT fails to parse, the key is returned untouched rather than dropped:
     * never lose an unlock to a parse error.
     */
    public static String canonicaliseKey(String key) {
        if (key == null || key.isEmpty()) return key;
        int delimiter = key.indexOf('|');
        if (delimiter == -1) return key;

        String itemId = key.substring(0, delimiter);
        String nbt = key.substring(delimiter + 1);
        try {
            NBTTagCompound parsed = net.minecraft.nbt.JsonToNBT.getTagFromJson(nbt);
            NBTTagCompound cleaned = NormalizationRules.strip(parsed, NormalizationRules.namespaceOf(itemId));
            if (cleaned.getKeySet().isEmpty()) return itemId;
            return itemId + "|" + NormalizationRules.canonical(cleaned);
        } catch (Exception e) {
            return key;
        }
    }

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
        RecipeDepthCalculator calculator = ThresholdTable.get();
        if (calculator != null) {
            return calculator.calculateThreshold(item);
        }
        // Fallback if nothing has bound recipes yet (no world loaded)
        return item.getItemStackLimit() == 1 ? 1 : UNLOCK_THRESHOLD;
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
        catalogChanged();

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

    /**
     * Vanilla caps a single custom-payload packet at 1 MiB, and a Journey sync carries the whole
     * catalog every time it is sent. On a mega-modpack, a player who unlocks tens of thousands of
     * keys would build a payload past that ceiling - and the client is kicked when it fails to
     * read one, on every join, permanently. So optional payload is shed before the packet is
     * built: timestamps first (they only drive the Journey grid's "most recent first" ordering
     * and the Stats tab's "Latest:" line), then deposit counts (a progress readout). The unlocked
     * set - the only part that actually gates item retrieval - is never truncated, and cannot
     * reach the ceiling on its own: 40,000 keys at a generous 25 bytes each is still under 1 MB.
     */
    public static final int MAX_SYNC_PAYLOAD_BYTES = 700000;

    /** Lower-bound byte estimate for a sync payload; see {@link #MAX_SYNC_PAYLOAD_BYTES}. */
    public static int estimateSyncBytes(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps) {
        long bytes = 0L;
        for (String key : unlocked) bytes += key.length() + 3L;
        for (String key : counts.keySet()) bytes += key.length() + 8L;
        for (String key : timestamps.keySet()) bytes += key.length() + 12L;
        return (int) Math.min(Integer.MAX_VALUE, bytes);
    }

    /**
     * Bumped on every sync from the server. Client-side derived views (the Journey tab's
     * filtered list, the Stats tab's per-namespace breakdown) memoize against this counter
     * instead of rebuilding themselves from the whole unlocked set on every rendered frame.
     */
    private int syncGeneration = 0;

    /** @see #syncGeneration */
    public int getSyncGeneration() {
        return syncGeneration;
    }

    /** Size of the unlocked set without the defensive copy getUnlockedItems() makes - that copy
     *  was being taken once per rendered frame just to read its size. */
    public int getUnlockedCount() {
        return unlockedItems.size();
    }

    @Override
    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }

    // The sorted view is rebuilt only when the catalog actually changes. It used to allocate a
    // fresh list of every unlocked key and run an O(n log n) sort - with two map lookups per
    // comparison - on every call, and the Journey tab reaches this twice per rendered frame
    // (item grid + tooltip pass). That is the same per-frame-recompute shape that caused the
    // 1.8.0 deposit-screen freeze, just in a different method.
    private java.util.List<String> sortedCache = null;
    private int sortedCacheGeneration = -1;

    @Override
    public java.util.List<String> getUnlockedItemsSorted() {
        if (sortedCache != null && sortedCacheGeneration == syncGeneration) {
            return sortedCache;
        }
        java.util.List<String> sortedItems = new java.util.ArrayList<String>(unlockedItems);
        java.util.Collections.sort(sortedItems, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                long timeA = unlockTimestamps.containsKey(a) ? unlockTimestamps.get(a) : 0L;
                long timeB = unlockTimestamps.containsKey(b) ? unlockTimestamps.get(b) : 0L;
                return Long.compare(timeB, timeA); // Most recent first
            }
        });
        sortedCache = java.util.Collections.unmodifiableList(sortedItems);
        sortedCacheGeneration = syncGeneration;
        return sortedCache;
    }

    /**
     * Invalidates every generation-keyed cache (the sorted catalog view above, the client's
     * per-namespace stats breakdown). Anything that mutates the three catalog maps must call
     * this, or a cached view will outlive the data it was built from.
     */
    private void catalogChanged() {
        syncGeneration++;
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
        // long math: a collected count past ~21M would overflow int and report a negative
        // percentage. Thresholds are always >= 1 (calculateThreshold clamps), so no /0 here.
        return (int) Math.min(100L, (long) count * 100L / threshold);
    }

    @Override
    public int getProgress(ItemStack stack) {
        int count = getCollectedCount(stack);
        int threshold = getThreshold(stack);
        if (threshold <= 0) return 100;
        // long math: a collected count past ~21M would overflow int and report a negative
        // percentage. Thresholds are always >= 1 (calculateThreshold clamps), so no /0 here.
        return (int) Math.min(100L, (long) count * 100L / threshold);
    }

    @Override
    public Map<String, Integer> getAllCollectedCounts() {
        return new HashMap<>(collectedCounts);
    }

    @Override
    public void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps) {
        // Section 11 Visual Polish: remember which keys are newly-unlocked-since-last-sync so
        // the client can show a message on the transition, without needing a
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
        this.syncGeneration++;
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

    /**
     * Merge a partial sync into the existing catalog instead of replacing it.
     *
     * <p>A deposit changes exactly one entry, but the full-sync path re-sends the whole catalog to
     * communicate that - thousands of keys to say one counter moved by one. A delta carries only
     * what changed.
     *
     * <p>Removals are deliberately <b>not</b> expressible here. purge / revoke / reset / team
     * join-leave all stay on the full-sync path, which keeps the merge unambiguous: a key absent
     * from a delta means "unchanged", never "deleted". Getting that wrong would silently drop
     * unlocks, so the ambiguity is removed by construction rather than by convention.
     */
    public void applyDelta(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps,
                           boolean enabled, boolean showTooltips, String teamDisplayName) {
        if (hasSyncedOnce) {
            Set<String> newKeys = new HashSet<String>(unlocked);
            newKeys.removeAll(this.unlockedItems);
            this.pendingUnlockCelebration = newKeys;
        } else {
            // A delta arriving before any full sync would leave the catalog incomplete. Treat it
            // as the first sync for celebration purposes; the login full-sync fills in the rest.
            this.pendingUnlockCelebration = Collections.emptySet();
            this.hasSyncedOnce = true;
        }

        this.collectedCounts.putAll(counts);
        this.unlockedItems.addAll(unlocked);
        this.unlockTimestamps.putAll(timestamps);
        this.enabled = enabled;
        this.showTooltips = showTooltips;
        this.teamDisplayName = (teamDisplayName == null || teamDisplayName.isEmpty()) ? null : teamDisplayName;
        this.syncGeneration++;
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
        catalogChanged();
        this.enabled = other.isEnabled();
        this.showTooltips = other.isShowTooltips();
        this.teamId = other.getTeamId();
    }

    @Override
    public void reset() {
        this.collectedCounts.clear();
        this.unlockedItems.clear();
        this.unlockTimestamps.clear();
        catalogChanged();
    }

    @Override
    public void grant(String key) {
        unlockedItems.add(key);
        unlockTimestamps.put(key, System.currentTimeMillis());
        catalogChanged();
    }

    @Override
    public void revoke(String key) {
        unlockedItems.remove(key);
        unlockTimestamps.remove(key);
        collectedCounts.put(key, 0);
        catalogChanged();
    }

    /**
     * Remove an entry entirely - unlock, timestamp and collected count. Unlike revoke(), which
     * zeroes the count so an op-revoked item can't instantly re-unlock, this erases the key
     * outright so it stops appearing anywhere: the catalog grid, the stats totals and the sync
     * payload. Used by /journeymode purge to get rid of an entry the client cannot render.
     *
     * @return true if the key was present in any of the three maps
     */
    public boolean purge(String key) {
        boolean had = unlockedItems.remove(key);
        had |= unlockTimestamps.remove(key) != null;
        had |= collectedCounts.remove(key) != null;
        if (had) catalogChanged();
        return had;
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
        catalogChanged();
    }
}
