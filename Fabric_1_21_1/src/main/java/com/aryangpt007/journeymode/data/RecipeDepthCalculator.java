package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
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
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        Rarity rarity = RARITY_OVERRIDES.getOrDefault(itemId, item.getDefaultInstance().getRarity());
        int divisor = rarityDivisor(rarity);
        return divisor == Integer.MAX_VALUE ? 1 : Math.max(1, stackSize / divisor);
    }

    private final Map<Item, Integer> depthCache = new HashMap<>();
    private final Set<Item> calculating = new HashSet<>(); // For cycle detection
    // Items whose depth was resolved while a cycle was open somewhere on the active call
    // stack. Their result depends on an assumed depth-0 for the item that triggered the
    // cycle break, so it is order-derived rather than purely structural (see resolveDepth).
    private final Set<Item> cycleTainted = new HashSet<>();
    // Depths that were resolved through a cycle break (or a bounded-work cutoff). Kept for
    // diagnostics only - unlike 1.8.0 these ARE memoized, see resolveDepth().
    private final Set<Item> provisionalDepths = new HashSet<>();
    /** Hard ceiling on recursion. The `calculating` guard already makes infinite recursion
     *  impossible, but a legitimately long ingredient chain in a large modpack can still nest
     *  deep enough to overflow the render thread's stack. Every depth >= 3 collapses to the same
     *  threshold (see calculateThreshold), so cutting off far beyond that costs nothing real. */
    private static final int MAX_RECURSION_DEPTH = 64;
    /** Hard ceiling on how much graph a single top-level query may walk. Bounds the worst case
     *  on a recipe graph no one anticipated; with memoization it is never reached in practice. */
    private static final int MAX_NODE_VISITS = 250000;
    private int recursionDepth = 0;
    private int nodeBudget = MAX_NODE_VISITS;
    private boolean resolving = false;
    private final RecipeManager recipeManager;
    private final RegistryAccess registryAccess;
    private Map<Item, List<RecipeHolder<?>>> recipesByOutput = null;

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
        int rawStackSize = item.getDefaultMaxStackSize();

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
        // Each top-level query starts from clean guard state and a fresh work budget. Recursion
        // below goes through resolveDepth(), never back through here, so this can never reset
        // state mid-walk; the flag is purely defensive against a future caller re-entering.
        if (resolving) {
            return resolveDepth(item);
        }
        resolving = true;
        calculating.clear();
        cycleTainted.clear();
        recursionDepth = 0;
        nodeBudget = MAX_NODE_VISITS;
        try {
            return resolveDepth(item);
        } finally {
            resolving = false;
        }
    }

    private int resolveDepth(Item item) {
        Integer cached = depthCache.get(item);
        if (cached != null) {
            return cached;
        }

        // Detect cycles
        if (calculating.contains(item)) {
            // Every item currently on the active resolution stack has its result tainted by
            // this cycle break, directly or transitively.
            cycleTainted.addAll(calculating);
            cycleTainted.add(item);
            return 0; // Treat cyclic items as raw to break the cycle
        }

        // Bounded-work guards. Neither is expected to fire on a sane recipe graph; they exist so
        // that no modpack, however pathological, can turn a threshold lookup into a hang or a
        // StackOverflowError on the render thread.
        if (recursionDepth >= MAX_RECURSION_DEPTH || nodeBudget <= 0) {
            cycleTainted.addAll(calculating);
            cycleTainted.add(item);
            return 0;
        }
        nodeBudget--;

        calculating.add(item);
        recursionDepth++;

        try {
            // Find all recipes that produce this item
            List<RecipeHolder<?>> recipesForItem = findRecipesProducing(item);

            if (recipesForItem.isEmpty()) {
                // No recipe = raw material
                depthCache.put(item, 0);
                return 0;
            }

            // Calculate minimum depth across all recipes (use easiest recipe)
            int minDepth = Integer.MAX_VALUE;

            for (RecipeHolder<?> holder : recipesForItem) {
                try {
                    Recipe<?> recipe = holder.value();
                    int recipeDepth = calculateRecipeDepth(recipe);
                    minDepth = Math.min(minDepth, recipeDepth);
                    // A crafted item can never resolve below 1, so no later recipe can beat this
                    // - stop before walking the rest of a possibly enormous recipe list.
                    if (minDepth <= 1) break;
                } catch (Throwable t) {
                    // Ignore buggy recipe during calculation
                }
            }

            int depth = minDepth == Integer.MAX_VALUE ? 0 : minDepth;
            if (cycleTainted.remove(item)) {
                // Resolved through a cycle break, so the value depends on the order the graph
                // happened to be walked in rather than purely on its structure. 1.8.0 refused to
                // memoize these, which turned every query for such an item into a full re-walk of
                // its subgraph - and since the deposit screen asks once per frame, that froze the
                // game outright on modpacks carrying reverse-crafting recipes (planks -> door,
                // door -> planks). Memoized since 1.8.1: the threshold this feeds is a soft
                // heuristic that config/datapack/API rules override outright, so a stable
                // order-derived depth beats a permanent performance cliff. Flagged for debug info.
                provisionalDepths.add(item);
            } else {
                provisionalDepths.remove(item);
            }
            depthCache.put(item, depth);
            return depth;

        } finally {
            recursionDepth--;
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
                            int itemDepth = resolveDepth(stack.getItem());
                            minItemDepth = Math.min(minItemDepth, itemDepth);
                            // Nothing beats a raw material, and one broad tag ingredient can
                            // expand to hundreds of stacks - stop as soon as the answer is known.
                            if (minItemDepth == 0) break;
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
    private List<RecipeHolder<?>> findRecipesProducing(Item item) {
        if (recipesByOutput == null) {
            recipesByOutput = new HashMap<>();
            try {
                for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                    try {
                        Recipe<?> recipe = holder.value();
                        ItemStack output = recipe.getResultItem(registryAccess);
                        if (output != null && !output.isEmpty()) {
                            recipesByOutput.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(holder);
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
        calculating.clear();
        provisionalDepths.clear();
        recursionDepth = 0;
        nodeBudget = MAX_NODE_VISITS;
        resolving = false;
        recipesByOutput = null;
    }

    /**
     * Get debug info about an item's unlock requirements
     */
    public synchronized String getDebugInfo(Item item) {
        int depth = getRecipeDepth(item);
        int threshold = calculateThreshold(item);
        int stackSize = item.getDefaultMaxStackSize();

        String type = depth == 0 ? "Raw Material" : "Crafted (Depth " + depth + ")";
        if (provisionalDepths.contains(item)) {
            type = type + ", provisional (cyclic recipe graph)";
        }

        return String.format("%s - Stack: %d, Depth: %d, Threshold: %d, Type: %s",
            BuiltInRegistries.ITEM.getKey(item), stackSize, depth, threshold, type);
    }
}
