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
    private boolean showTooltips = true; // Per-player tooltip display preference (/journeymode tooltips)
    private String teamId; // null = no team; see TeamDataHandler for the per-world team registry
    // Client-display-only: the team's human-readable name, refreshed on every sync. Never
    // persisted (recomputed from the server each time) - unlike teamId, which IS persisted and
    // drives server-side deposit/fetch routing.
    private String teamDisplayName;

    // §11 Visual Polish state - client-side only, never persisted.
    private boolean hasSyncedOnce = false;
    private Set<String> pendingUnlockCelebration = Collections.emptySet();

    @Deprecated // Use dynamic threshold via RecipeDepthCalculator
    public static final int UNLOCK_THRESHOLD = 30; // Fallback value
    

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
    /**
     * Build the JSON form directly. GlobalDataHandler used to call toJsonString() and then
     * immediately re-parse the result back into a JsonObject on every save - a pure
     * serialize/parse round trip with no purpose, once per deposit.
     */
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

    public String toJsonString() {
        return GSON.toJson(toJsonObject());
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

            if (json.has("show_tooltips")) {
                attachment.showTooltips = json.get("show_tooltips").getAsBoolean();
            } // else: absent in old files - keep the default (true), no data loss either way

            if (json.has("team_id") && !json.get("team_id").isJsonNull()) {
                attachment.teamId = json.get("team_id").getAsString();
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
        // Delegates to the one shared calculator (see ThresholdTable). This used to build a
        // per-instance calculator, so every player carried their own full copy of the recipe
        // index and their own depth cache.
        ThresholdTable.bind(recipeManager, registryAccess);
    }
    
    /**
     * Get the unlock threshold for a specific item (dynamic based on recipe depth and stack size)
     */
    public int getThreshold(Item item) {
        RecipeDepthCalculator calculator = ThresholdTable.get();
        if (calculator != null) {
            return calculator.calculateThreshold(item);
        }
        // Fallback if nothing has bound recipes yet (no world loaded)
        return item.getMaxStackSize() == 1 ? 1 : UNLOCK_THRESHOLD;
    }

    /**
     * Helper to get a normalized ItemStack, preserving only subtype NBT tags.
     */
    public static ItemStack getNormalizedStack(ItemStack original) {
        if (original.isEmpty()) return original;
        ItemStack normalized = new ItemStack(original.getItem());
        if (original.hasTag()) {
            // Deny-by-exception (see NormalizationRules). 1.8.1 did the opposite - an allowlist of
            // a handful of vanilla keys - which silently discarded every modded NBT identity and is
            // what broke Apotheosis gems, TACZ guns, Iron's Spells scrolls and GregTech hammers.
            String itemNamespace = NormalizationRules.namespaceOf(Registry.ITEM.getKey(original.getItem()).toString());
            CompoundTag cleaned = NormalizationRules.strip(original.getTag(), itemNamespace);

            // Third-party rules may contribute keys, but every one is re-checked against the
            // strip rules first. JourneyModeAPI.DENYLISTED_KEYS is only four entries and does
            // NOT cover ForgeCaps, backpacks, tanks and the rest of the container gate - taking
            // the API at its word here would reopen the duplication hole outright.
            for (String key : com.aryangpt007.journeymode.api.JourneyModeAPI.collectAdditionalNormalizationKeys(original.getTag())) {
                if (NormalizationRules.isStripped(key, key, itemNamespace)) continue;
                if (original.getTag().contains(key)
                        && !cleaned.contains(key)) {
                    cleaned.put(key, original.getTag().get(key).copy());
                }
            }

            if (!cleaned.isEmpty()) {
                normalized.setTag(cleaned);
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
            // Canonical (recursively key-sorted) rather than toString(), whose order follows
            // HashMap bucket layout and is therefore input-dependent once tags are copied from the
            // source stack instead of rebuilt. See NormalizationRules.canonical.
            return baseId + "|" + NormalizationRules.canonical(normalized.getTag());
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
            CompoundTag parsed = TagParser.parseTag(nbt);
            CompoundTag cleaned = NormalizationRules.strip(parsed, NormalizationRules.namespaceOf(itemId));
            if (cleaned.isEmpty()) return itemId;
            return itemId + "|" + NormalizationRules.canonical(cleaned);
        } catch (Exception e) {
            return key;
        }
    }

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
        catalogChanged();

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
     * OP-granted unlock (/journeymode grant). Adds the key directly, bypassing deposit progress.
     */
    public void grant(String key) {
        unlockedItems.add(key);
        unlockTimestamps.put(key, System.currentTimeMillis());
        catalogChanged();
    }

    /**
     * OP-revoked unlock (/journeymode revoke). Resets collected progress for this key to 0 too -
     * otherwise the very next deposit (or a pending-unlock check) would instantly re-unlock it,
     * making the revoke a no-op.
     */
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

    /**
     * Re-evaluate existing partial progress against current thresholds and promote anything
     * that now qualifies. Thresholds can drop after the fact (/journeymode all, /journeymode
     * threshold, a rarity/datapack change) - already-unlocked items are never re-locked, but a
     * lower threshold can make old progress newly sufficient. Called lazily (on data load, menu
     * open) rather than iterating every player at command time.
     */
    public void checkPendingUnlocks(RecipeManager recipeManager, RegistryAccess registryAccess) {
        initializeCalculator(recipeManager, registryAccess);
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

    /**
     * Get all unlocked items
     */
    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }
    
    /**
     * Get unlocked items sorted by timestamp (most recent first)
     */
    // The sorted view is rebuilt only when the catalog actually changes. It used to allocate a
    // fresh list of every unlocked key and run an O(n log n) sort - with two map lookups per
    // comparison - on every call, and the Journey tab reaches this twice per rendered frame
    // (item grid + tooltip pass). That is the same per-frame-recompute shape that caused the
    // 1.8.0 deposit-screen freeze, just in a different method.
    private java.util.List<String> sortedCache = null;
    private int sortedCacheGeneration = -1;

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
        // long math: a collected count past ~21M would overflow int and report a negative
        // percentage. Thresholds are always >= 1 (calculateThreshold clamps), so no /0 here.
        return (int) Math.min(100L, (long) count * 100L / threshold);
    }

    /**
     * Get progress percentage for a specific ItemStack
     */
    public int getProgress(ItemStack stack) {
        int count = getCollectedCount(stack);
        int threshold = getThreshold(stack.getItem());
        // long math: a collected count past ~21M would overflow int and report a negative
        // percentage. Thresholds are always >= 1 (calculateThreshold clamps), so no /0 here.
        return (int) Math.min(100L, (long) count * 100L / threshold);
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
        // §11 Visual Polish: remember which keys are newly-unlocked-since-last-sync so the
        // client can show a message on the transition, without needing a dedicated
        // "newly_unlocked" packet field - the full unlocked set is already synced every time.
        // The very first sync after connecting (client capability starts empty) must be silent,
        // or every already-unlocked item would "celebrate" on login.
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

    /** Client-side only: keys that just transitioned to unlocked on the most recent sync. Clears itself on read. */
    public Set<String> getAndClearNewlyUnlocked() {
        Set<String> result = pendingUnlockCelebration;
        pendingUnlockCelebration = Collections.emptySet();
        return result;
    }

    /**
     * Update data from server sync packet, including per-player flags. The previous
     * 3-argument overload never carried `enabled`/`showTooltips`, so the client's own capability
     * instance (a separate object from the server's, even in singleplayer) silently never
     * reflected /journeymode off or a tooltip preference change - this overload is now the one
     * actually used by the packet handler.
     */
    public void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps, boolean enabled, boolean showTooltips) {
        updateFromSync(counts, unlocked, timestamps);
        this.enabled = enabled;
        this.showTooltips = showTooltips;
    }

    /** As above, plus the team display name for the client-side badge (§1); empty string = no team. */
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

    /** Client-side only: the team's display name for the badge, or null if not on a team. */
    public String getTeamDisplayName() {
        return teamDisplayName;
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
     * Per-player tooltip display preference (/journeymode tooltips on|off). Client-rendering-only
     * setting, but stored/synced through the same attachment as everything else rather than a
     * separate global config value, so each player can have their own preference.
     */
    public boolean isShowTooltips() {
        return showTooltips;
    }

    public void setShowTooltips(boolean showTooltips) {
        this.showTooltips = showTooltips;
    }

    /**
     * §1 Shared Team Catalogs: null if not in a team, otherwise the id of the team whose shared
     * TeamData (see TeamDataHandler) is authoritative for this player's deposits/unlocks. This
     * flag itself is personal data (persisted in the game-dir-scoped file, like everything else
     * on this class) - the actual team progress it points to lives in the per-world teams file.
     */
    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
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
        catalogChanged();
        this.enabled = other.enabled;
        this.showTooltips = other.showTooltips;
        this.teamId = other.teamId;
    }

    /**
     * Clear all progress/unlocks but keep enabled status
     */
    public void reset() {
        this.collectedCounts.clear();
        this.unlockedItems.clear();
        this.unlockTimestamps.clear();
        catalogChanged();
    }
}
