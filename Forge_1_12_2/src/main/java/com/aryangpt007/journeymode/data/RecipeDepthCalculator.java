package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.CraftingManager;

import java.util.*;

/**
 * Calculates unlock thresholds based on recipe depth and stack size
 * Can be overridden by configuration
 */
public class RecipeDepthCalculator {
    private final Map<Item, Integer> depthCache = new HashMap<>();
    private final Set<Item> calculating = new HashSet<>(); // For cycle detection
    private Map<Item, List<IRecipe>> recipesByOutput = null;
    
    public RecipeDepthCalculator() {
    }

    /**
     * Calculate the unlock threshold for an item based on its recipe depth and stack size
     * Config overrides take priority over recipe-based calculation
     */
    public synchronized int calculateThreshold(Item item) {
        if (item.getRegistryName() == null) {
            return item.getItemStackLimit();
        }
        
        // Check for config override first
        String itemId = item.getRegistryName().toString();
        Integer configOverride = ConfigHandler.getThresholdOverride(itemId);
        if (configOverride != null) {
            return Math.max(1, configOverride); // Ensure at least 1
        }
        
        // Use recipe-based calculation
        int stackSize = item.getItemStackLimit();
        
        // Items that only stack to 1 always require just 1
        if (stackSize <= 1) {
            return 1;
        }
        
        int depth = getRecipeDepth(item);
        
        switch (depth) {
            case 0:
                return stackSize;                      // Raw materials: full stack
            case 1:
                return Math.max(1, stackSize / 2);     // 50% of stack
            case 2:
                return Math.max(1, stackSize / 4);     // 25% of stack
            default:
                return 1;                             // Depth 3+: just 1
        }
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
            return 0; // Treat cyclic items as raw to break the cycle
        }

        calculating.add(item);
        
        try {
            // Find all recipes that produce this item
            List<IRecipe> recipesForItem = findRecipesProducing(item);
            
            if (recipesForItem.isEmpty()) {
                // No recipe = raw material
                depthCache.put(item, 0);
                return 0;
            }
            
            // Calculate minimum depth across all recipes (use easiest recipe)
            int minDepth = Integer.MAX_VALUE;
            
            for (IRecipe recipe : recipesForItem) {
                try {
                    int recipeDepth = calculateRecipeDepth(recipe);
                    minDepth = Math.min(minDepth, recipeDepth);
                } catch (Throwable t) {
                    // Ignore buggy recipe during calculation
                }
            }
            
            int depth = minDepth == Integer.MAX_VALUE ? 0 : minDepth;
            depthCache.put(item, depth);
            return depth;
            
        } finally {
            calculating.remove(item);
        }
    }

    /**
     * Calculate the depth of a specific recipe (max depth of ingredients + 1)
     */
    private int calculateRecipeDepth(IRecipe recipe) {
        int maxIngredientDepth = 0;
        
        try {
            List<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients == null) return 1;
            
            for (Ingredient ingredient : ingredients) {
                try {
                    if (ingredient == null || ingredient == Ingredient.EMPTY) continue;
                    
                    // Get all possible items for this ingredient
                    ItemStack[] possibleItems = ingredient.getMatchingStacks();
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
    private List<IRecipe> findRecipesProducing(Item item) {
        if (recipesByOutput == null) {
            recipesByOutput = new HashMap<>();
            try {
                for (IRecipe recipe : CraftingManager.REGISTRY) {
                    try {
                        ItemStack output = recipe.getRecipeOutput();
                        if (output != null && !output.isEmpty()) {
                            recipesByOutput.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(recipe);
                        }
                    } catch (Throwable t) {
                        // Skip buggy recipes during indexing
                    }
                }
            } catch (Throwable t) {
                // In case REGISTRY itself throws
            }
        }
        
        return recipesByOutput.getOrDefault(item, Collections.emptyList());
    }

    /**
     * Clear the cache (call when recipes reload)
     */
    public synchronized void clearCache() {
        depthCache.clear();
        recipesByOutput = null;
    }
}
