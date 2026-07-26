package com.aryangpt007.journeymode.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Note: some Javadoc below refers to "the client's own capability instance (a separate object
 * from the server's, even in singleplayer)" - true even in singleplayer because the integrated
 * server and the client each run their own instance of this class; only network packets bridge
 * them, same as a dedicated server/remote-client pair.
 */

public interface IJourneyData {
    String toJsonString();
    void fromNBT(NBTTagCompound nbt);
    NBTTagCompound toNBT();
    
    int getThreshold(Item item);
    int getThreshold(ItemStack stack);
    boolean depositItem(ItemStack stack);
    boolean isUnlocked(Item item);
    boolean isUnlocked(ItemStack stack);
    boolean isUnlocked(String key);
    int getCollectedCount(Item item);
    int getCollectedCount(ItemStack stack);
    Set<String> getUnlockedItems();
    List<String> getUnlockedItemsSorted();
    Map<String, Long> getUnlockTimestamps();
    int getProgress(Item item);
    int getProgress(ItemStack stack);
    Map<String, Integer> getAllCollectedCounts();
    
    void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps);

    /**
     * Update data from server sync packet, including per-player flags. The previous 3-argument
     * overload never carried `enabled`/`showTooltips`, so the client's own capability instance
     * (a separate object from the server's, even in singleplayer) silently never reflected
     * /journeymode off or a tooltip preference change - this overload is the one actually used
     * by the packet handler now.
     */
    void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps, boolean enabled, boolean showTooltips);

    /**
     * As above, plus the team display name for the client-side badge (Section 1 Shared Team
     * Catalogs); empty string = no team. This is now the overload actually used by the packet
     * handler.
     */
    void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps, boolean enabled, boolean showTooltips, String teamDisplayName);

    /** Client-side only: the team's display name for the badge, or null if not on a team. */
    String getTeamDisplayName();

    /**
     * Client-side only: keys that just transitioned to unlocked on the most recent sync (Section
     * 11 Visual Polish). Clears itself on read.
     */
    Set<String> getAndClearNewlyUnlocked();

    boolean isEnabled();
    void setEnabled(boolean enabled);

    /**
     * Per-player tooltip display preference (/journeymode tooltips on|off). Client-rendering-only
     * setting, but stored/synced through the same data object as everything else rather than a
     * separate global config value, so each player can have their own preference.
     */
    boolean isShowTooltips();
    void setShowTooltips(boolean showTooltips);

    /**
     * Section 1 Shared Team Catalogs: null if not in a team, otherwise the id of the team whose
     * shared TeamData (see TeamDataHandler) is authoritative for this player's deposits/unlocks.
     * This flag itself is personal data (persisted alongside everything else on this class) -
     * the actual team progress it points to lives in the per-world teams file.
     */
    String getTeamId();
    void setTeamId(String teamId);

    void copyFrom(IJourneyData other);
    void reset();

    /** OP-granted unlock (/journeymode grant). Adds the key directly, bypassing deposit progress. */
    void grant(String key);

    /**
     * OP-revoked unlock (/journeymode revoke). Resets collected progress for this key to 0 too -
     * otherwise the very next deposit (or a pending-unlock check) would instantly re-unlock it,
     * making the revoke a no-op.
     */
    void revoke(String key);

    /**
     * Re-evaluate existing partial progress against current thresholds and promote anything that
     * now qualifies. Thresholds can drop after the fact (/journeymode all, /journeymode threshold) -
     * already-unlocked items are never re-locked, but a lower threshold can make old progress
     * newly sufficient. Called lazily (on data load) rather than iterating every player at command time.
     */
    void checkPendingUnlocks();
}
