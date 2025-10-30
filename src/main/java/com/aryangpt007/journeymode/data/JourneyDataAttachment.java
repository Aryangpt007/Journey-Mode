package com.aryangpt007.journeymode.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Stores player's Journey Mode data - tracked items and unlocked items for infinite access
 */
public class JourneyDataAttachment {
    public static final Codec<JourneyDataAttachment> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("collected_counts").forGetter(d -> d.collectedCounts),
            Codec.list(Codec.STRING).fieldOf("unlocked_items").forGetter(d -> new ArrayList<>(d.unlockedItems))
        ).apply(instance, JourneyDataAttachment::new)
    );

    private final Map<String, Integer> collectedCounts; // Item ID -> count collected
    private final Set<String> unlockedItems; // Items unlocked for infinite access

    public static final int UNLOCK_THRESHOLD = 30; // Number of items needed to unlock

    public JourneyDataAttachment() {
        this.collectedCounts = new HashMap<>();
        this.unlockedItems = new HashSet<>();
    }

    private JourneyDataAttachment(Map<String, Integer> collectedCounts, List<String> unlockedItems) {
        this.collectedCounts = new HashMap<>(collectedCounts);
        this.unlockedItems = new HashSet<>(unlockedItems);
    }

    /**
     * Deposit items into Journey Mode tracking
     * @param stack The ItemStack to deposit
     * @return true if this deposit unlocked the item
     */
    public boolean depositItem(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int currentCount = collectedCounts.getOrDefault(itemId, 0);
        int newCount = currentCount + stack.getCount();
        collectedCounts.put(itemId, newCount);

        // Check if we just reached the threshold
        if (currentCount < UNLOCK_THRESHOLD && newCount >= UNLOCK_THRESHOLD) {
            unlockedItems.add(itemId);
            return true; // Item was just unlocked
        }
        return false;
    }

    /**
     * Check if an item is unlocked for infinite retrieval
     */
    public boolean isUnlocked(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        return unlockedItems.contains(itemId);
    }

    /**
     * Get the current collection count for an item
     */
    public int getCollectedCount(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        return collectedCounts.getOrDefault(itemId, 0);
    }

    /**
     * Get all unlocked items
     */
    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }

    /**
     * Get progress percentage for an item (0-100)
     */
    public int getProgress(Item item) {
        int count = getCollectedCount(item);
        return Math.min(100, (count * 100) / UNLOCK_THRESHOLD);
    }

    /**
     * Get all collected items and their counts
     */
    public Map<String, Integer> getAllCollectedCounts() {
        return new HashMap<>(collectedCounts);
    }
}
