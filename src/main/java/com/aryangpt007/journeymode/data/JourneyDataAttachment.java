package com.aryangpt007.journeymode.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

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

    @Deprecated // Use dynamic threshold via RecipeDepthCalculator
    public static final int UNLOCK_THRESHOLD = 30; // Fallback value
    
    private RecipeDepthCalculator recipeCalculator; // Lazily initialized

    public JourneyDataAttachment() {
        this.collectedCounts = new HashMap<>();
        this.unlockedItems = new HashSet<>();
    }

    private JourneyDataAttachment(Map<String, Integer> collectedCounts, List<String> unlockedItems) {
        this.collectedCounts = new HashMap<>(collectedCounts);
        this.unlockedItems = new HashSet<>(unlockedItems);
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
        return item.getDefaultMaxStackSize() == 1 ? 1 : UNLOCK_THRESHOLD;
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
        
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int currentCount = collectedCounts.getOrDefault(itemId, 0);
        int newCount = currentCount + stack.getCount();
        collectedCounts.put(itemId, newCount);

        int threshold = getThreshold(stack.getItem());
        
        // Check if we just reached the threshold
        if (currentCount < threshold && newCount >= threshold) {
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
        int threshold = getThreshold(item);
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
    public void updateFromSync(Map<String, Integer> counts, Set<String> unlocked) {
        this.collectedCounts.clear();
        this.collectedCounts.putAll(counts);
        this.unlockedItems.clear();
        this.unlockedItems.addAll(unlocked);
    }
}
