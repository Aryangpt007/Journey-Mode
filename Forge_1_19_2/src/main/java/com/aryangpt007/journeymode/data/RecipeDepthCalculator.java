package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;

/**
 * Calculates unlock thresholds based on recipe depth and stack size
 * Can be overridden by configuration
 */
public class RecipeDepthCalculator {
    /** Depth-0 threshold is derived from stack size, but never above this baseline.
     *  Prevents stack-size-inflation mods (e.g. Bigger Stacks) from blowing thresholds up
     *  to whatever they set an item's max stack size to (observed: 9,999). */
    private static final int MAX_BASELINE_STACK_SIZE = 64;

    // §7 Rarity-Aware Thresholds: only applies to depth-0 (recipe-less) stackable items.
    // Table constant, not scattered logic - tune here, nowhere else.
    private static int rarityDivisor(Rarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> 4;
            case RARE -> 16;
            case EPIC -> Integer.MAX_VALUE; // collapses to threshold 1, see applyRarity()
            default -> 1; // COMMON
        };
    }

    // Vanilla's own Rarity assignments are inconsistent for scarcity purposes (e.g. Nether Star
    // is UNCOMMON despite being end-game-boss-only) - override the known offenders rather than
    // trusting Item.getDefaultInstance().getRarity() blindly for these specific ids.
    private static final Map<String, Rarity> RARITY_OVERRIDES = Map.of(
        "minecraft:nether_star", Rarity.RARE,
        "minecraft:heart_of_the_sea", Rarity.RARE,
        "minecraft:echo_shard", Rarity.RARE,
        "minecraft:enchanted_golden_apple", Rarity.EPIC,
        "minecraft:dragon_egg", Rarity.EPIC,
        "minecraft:sniffer_egg", Rarity.RARE,
        "minecraft:wither_skeleton_skull", Rarity.RARE,
        "minecraft:dragon_head", Rarity.EPIC
    );

    private static int applyRarity(Item item, int stackSize) {
        String itemId = Registry.ITEM.getKey(item).toString();
        Rarity rarity = RARITY_OVERRIDES.getOrDefault(itemId, item.getDefaultInstance().getRarity());
        int divisor = rarityDivisor(rarity);
        return divisor == Integer.MAX_VALUE ? 1 : Math.max(1, stackSize / divisor);
    }

    private final Map<Item, Integer> depthCache = new HashMap<>();
    private final Set<Item> calculating = new HashSet<>(); // For cycle detection
    // Items whose depth was resolved while a cycle was open somewhere on the active call
    // stack. Their result depends on an assumed depth-0 for the item that triggered the
    // cycle break, so it must never be memoized as if it were a stable, final depth.
    private final Set<Item> cycleTainted = new HashSet<>();
    private final RecipeManager recipeManager;
    private final RegistryAccess registryAccess;
    private Map<Item, List<Recipe<?>>> recipesByOutput = null;

    public RecipeDepthCalculator(RecipeManager recipeManager, RegistryAccess registryAccess) {
        this.recipeManager = recipeManager;
        this.registryAccess = registryAccess;
    }

    /**
     * Calculate the unlock threshold for an item based on its recipe depth and stack size
     * Config overrides take priority over recipe-based calculation
     *
     * Rules:
     * - Stack size 1: Always requires 1 item
     * - Raw materials (depth 0): Requires full stack size
     * - Depth 1: Requires 50% of stack size
     * - Depth 2: Requires 25% of stack size
     * - Depth 3+: Requires 1 item
     */
    public synchronized int calculateThreshold(Item item) {
        // Check for config override first (also covers tag/regex rules, default_override,
        // datapack thresholds, and dev-API ThresholdProviders - see ConfigHandler).
        Integer configOverride = ConfigHandler.getThresholdOverride(item);
        if (configOverride != null) {
            return Math.max(1, configOverride); // Ensure at least 1
        }

        // Use recipe-based calculation
        int rawStackSize = item.getMaxStackSize();

        // Items that only stack to 1 always require just 1
        if (rawStackSize == 1) {
            return 1;
        }

        // Clamp to a sane baseline before using it in threshold math (see MAX_BASELINE_STACK_SIZE).
        int stackSize = Math.min(rawStackSize, MAX_BASELINE_STACK_SIZE);

        int depth = getRecipeDepth(item);

        return switch (depth) {
            case 0 -> applyRarity(item, stackSize);   // Raw materials: full stack, scaled by rarity (§7)
            case 1 -> Math.max(1, stackSize / 2);     // 50% of stack
            case 2 -> Math.max(1, stackSize / 4);     // 25% of stack
            default -> 1;                             // Depth 3+: just 1
        };
    }

    /**
     * Get the recipe depth of an item (how many crafting steps from raw materials)
     * Returns 0 for raw materials (no recipe)
     */
    public synchronized int getRecipeDepth(Item item) {
        if (depthCache.containsKey(item)) {
            return depthCache.get(item);
        }

        // Detect cycles
        if (calculating.contains(item)) {
            // Every item currently on the active resolution stack has its result tainted by
            // this cycle break (directly or transitively) - none of them may be memoized as
            // a stable depth, or a restart could resolve the same recipe graph in a different
            // order and silently produce a different (and equally "correct") cached value.
            cycleTainted.addAll(calculating);
            cycleTainted.add(item);
            return 0; // Treat cyclic items as raw to break the cycle
        }

        calculating.add(item);

        try {
            // Find all recipes that produce this item
            List<Recipe<?>> recipesForItem = findRecipesProducing(item);

            if (recipesForItem.isEmpty()) {
                // No recipe = raw material
                depthCache.put(item, 0);
                return 0;
            }

            // Calculate minimum depth across all recipes (use easiest recipe)
            int minDepth = Integer.MAX_VALUE;

            for (Recipe<?> recipe : recipesForItem) {
                try {
                    int recipeDepth = calculateRecipeDepth(recipe);
                    minDepth = Math.min(minDepth, recipeDepth);
                } catch (Throwable t) {
                    // Ignore buggy recipe during calculation
                }
            }

            int depth = minDepth == Integer.MAX_VALUE ? 0 : minDepth;
            if (!cycleTainted.remove(item)) {
                depthCache.put(item, depth);
            }
            // else: tainted by a cycle somewhere below this item - deliberately not cached,
            // will be recomputed fresh next time it's queried.
            return depth;

        } finally {
            calculating.remove(item);
        }
    }

    /**
     * Calculate the depth of a specific recipe (max depth of ingredients + 1)
     */
    private int calculateRecipeDepth(Recipe<?> recipe) {
        int maxIngredientDepth = 0;

        try {
            List<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients == null) return 1;

            for (Ingredient ingredient : ingredients) {
                try {
                    if (ingredient == null || ingredient.isEmpty()) continue;

                    // Get all possible items for this ingredient
                    ItemStack[] possibleItems = ingredient.getItems();
                    if (possibleItems == null || possibleItems.length == 0) continue;

                    // Use the minimum depth among possible items (easiest option)
                    int minItemDepth = Integer.MAX_VALUE;
                    for (ItemStack stack : possibleItems) {
                        try {
                            if (stack == null || stack.isEmpty()) continue;
                            int itemDepth = getRecipeDepth(stack.getItem());
                            minItemDepth = Math.min(minItemDepth, itemDepth);
                        } catch (Throwable t) {
                            // Ignore buggy items inside the ingredient
                        }
                    }

                    if (minItemDepth != Integer.MAX_VALUE) {
                        maxIngredientDepth = Math.max(maxIngredientDepth, minItemDepth);
                    }
                } catch (Throwable t) {
                    // Ignore buggy ingredients
                }
            }
        } catch (Throwable t) {
            // Log/ignore and treat this recipe as having no ingredients / depth 0
            return 0;
        }

        return maxIngredientDepth + 1;
    }

    /**
     * Find all recipes that produce a specific item
     * Uses a lazily-built index map to optimize performance from O(N) to O(1)
     */
    private List<Recipe<?>> findRecipesProducing(Item item) {
        if (recipesByOutput == null) {
            recipesByOutput = new HashMap<>();
            try {
                for (Recipe<?> recipe : recipeManager.getRecipes()) {
                    try {
                        ItemStack output = recipe.getResultItem();
                        if (output != null && !output.isEmpty()) {
                            recipesByOutput.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(recipe);
                        }
                    } catch (Throwable t) {
                        // Skip buggy recipes during indexing
                    }
                }
            } catch (Throwable t) {
                // In case getRecipes() itself throws
            }
        }

        return recipesByOutput.getOrDefault(item, Collections.emptyList());
    }

    /**
     * Clear the cache (call when recipes reload)
     */
    public synchronized void clearCache() {
        depthCache.clear();
        cycleTainted.clear();
        recipesByOutput = null;
    }

    /**
     * Get debug info about an item's unlock requirements
     */
    public synchronized String getDebugInfo(Item item) {
        int depth = getRecipeDepth(item);
        int threshold = calculateThreshold(item);
        int stackSize = item.getMaxStackSize();

        String type = depth == 0 ? "Raw Material" : "Crafted (Depth " + depth + ")";

        return String.format("%s - Stack: %d, Depth: %d, Threshold: %d, Type: %s",
            item.toString(), stackSize, depth, threshold, type);
    }
}
