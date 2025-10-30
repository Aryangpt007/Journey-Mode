package com.aryangpt007.journeymode.data;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;

/**
 * Calculates unlock thresholds based on recipe depth and stack size
 */
public class RecipeDepthCalculator {
    private final Map<Item, Integer> depthCache = new HashMap<>();
    private final Set<Item> calculating = new HashSet<>(); // For cycle detection
    private final RecipeManager recipeManager;
    private final RegistryAccess registryAccess;

    public RecipeDepthCalculator(RecipeManager recipeManager, RegistryAccess registryAccess) {
        this.recipeManager = recipeManager;
        this.registryAccess = registryAccess;
    }

    /**
     * Calculate the unlock threshold for an item based on its recipe depth and stack size
     * 
     * Rules:
     * - Stack size 1: Always requires 1 item
     * - Raw materials (depth 0): Requires full stack size
     * - Depth 1: Requires 50% of stack size
     * - Depth 2: Requires 25% of stack size
     * - Depth 3+: Requires 1 item
     */
    public int calculateThreshold(Item item) {
        int stackSize = item.getDefaultMaxStackSize();
        
        // Items that only stack to 1 always require just 1
        if (stackSize == 1) {
            return 1;
        }
        
        int depth = getRecipeDepth(item);
        
        return switch (depth) {
            case 0 -> stackSize;                      // Raw materials: full stack
            case 1 -> Math.max(1, stackSize / 2);     // 50% of stack
            case 2 -> Math.max(1, stackSize / 4);     // 25% of stack
            default -> 1;                             // Depth 3+: just 1
        };
    }

    /**
     * Get the recipe depth of an item (how many crafting steps from raw materials)
     * Returns 0 for raw materials (no recipe)
     */
    public int getRecipeDepth(Item item) {
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
            List<RecipeHolder<?>> recipesForItem = findRecipesProducing(item);
            
            if (recipesForItem.isEmpty()) {
                // No recipe = raw material
                depthCache.put(item, 0);
                return 0;
            }
            
            // Calculate minimum depth across all recipes (use easiest recipe)
            int minDepth = Integer.MAX_VALUE;
            
            for (RecipeHolder<?> holder : recipesForItem) {
                Recipe<?> recipe = holder.value();
                int recipeDepth = calculateRecipeDepth(recipe);
                minDepth = Math.min(minDepth, recipeDepth);
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
    private int calculateRecipeDepth(Recipe<?> recipe) {
        int maxIngredientDepth = 0;
        
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            
            // Get all possible items for this ingredient
            ItemStack[] possibleItems = ingredient.getItems();
            if (possibleItems.length == 0) continue;
            
            // Use the minimum depth among possible items (easiest option)
            int minItemDepth = Integer.MAX_VALUE;
            for (ItemStack stack : possibleItems) {
                int itemDepth = getRecipeDepth(stack.getItem());
                minItemDepth = Math.min(minItemDepth, itemDepth);
            }
            
            if (minItemDepth != Integer.MAX_VALUE) {
                maxIngredientDepth = Math.max(maxIngredientDepth, minItemDepth);
            }
        }
        
        return maxIngredientDepth + 1;
    }

    /**
     * Find all recipes that produce a specific item
     */
    private List<RecipeHolder<?>> findRecipesProducing(Item item) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack output = recipe.getResultItem(registryAccess);
            
            if (!output.isEmpty() && output.is(item)) {
                result.add(holder);
            }
        }
        
        return result;
    }

    /**
     * Clear the cache (call when recipes reload)
     */
    public void clearCache() {
        depthCache.clear();
    }

    /**
     * Get debug info about an item's unlock requirements
     */
    public String getDebugInfo(Item item) {
        int depth = getRecipeDepth(item);
        int threshold = calculateThreshold(item);
        int stackSize = item.getDefaultMaxStackSize();
        
        String type = depth == 0 ? "Raw Material" : "Crafted (Depth " + depth + ")";
        
        return String.format("%s - Stack: %d, Depth: %d, Threshold: %d, Type: %s",
            item.toString(), stackSize, depth, threshold, type);
    }
}
