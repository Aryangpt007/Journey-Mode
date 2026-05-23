package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private final Map<Item, Integer> depthCache = new HashMap<>();
    private final Set<Item> calculating = new HashSet<>(); // For cycle detection
    private final RecipeManager recipeManager;
    private final RegistryAccess registryAccess;
    private Map<Item, List<RecipeHolder<?>>> recipesByOutput = null;

    public RecipeDepthCalculator(RecipeManager recipeManager, RegistryAccess registryAccess) {
        this.recipeManager = recipeManager;
        this.registryAccess = registryAccess;
    }

    /**
     * Calculate the unlock threshold for an item based on its recipe depth and stack size
     */
    public synchronized int calculateThreshold(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        Integer configOverride = ConfigHandler.getThresholdOverride(itemId);
        if (configOverride != null) {
            return Math.max(1, configOverride);
        }
        
        int stackSize = item.getDefaultMaxStackSize();
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
     * Get the recipe depth of an item
     */
    public synchronized int getRecipeDepth(Item item) {
        if (depthCache.containsKey(item)) {
            return depthCache.get(item);
        }

        if (calculating.contains(item)) {
            return 0; // Cyclic items as raw
        }

        calculating.add(item);
        
        try {
            List<RecipeHolder<?>> recipesForItem = findRecipesProducing(item);
            
            if (recipesForItem.isEmpty()) {
                depthCache.put(item, 0);
                return 0;
            }
            
            int minDepth = Integer.MAX_VALUE;
            
            for (RecipeHolder<?> holder : recipesForItem) {
                try {
                    Recipe<?> recipe = holder.value();
                    int recipeDepth = calculateRecipeDepth(recipe);
                    minDepth = Math.min(minDepth, recipeDepth);
                } catch (Throwable t) {
                    // Ignore buggy recipe
                }
            }
            
            int depth = minDepth == Integer.MAX_VALUE ? 0 : minDepth;
            depthCache.put(item, depth);
            return depth;
            
        } finally {
            calculating.remove(item);
        }
    }

    private int calculateRecipeDepth(Recipe<?> recipe) {
        int maxIngredientDepth = 0;
        
        try {
            List<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients == null) return 1;
            
            for (Ingredient ingredient : ingredients) {
                try {
                    if (ingredient == null || ingredient.isEmpty()) continue;
                    
                    ItemStack[] possibleItems = ingredient.getItems();
                    if (possibleItems == null || possibleItems.length == 0) continue;
                    
                    int minItemDepth = Integer.MAX_VALUE;
                    for (ItemStack stack : possibleItems) {
                        try {
                            if (stack == null || stack.isEmpty()) continue;
                            int itemDepth = getRecipeDepth(stack.getItem());
                            minItemDepth = Math.min(minItemDepth, itemDepth);
                        } catch (Throwable t) {
                            // Ignore buggy items
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
            // Treat recipe as having no ingredients / depth 0
            return 0;
        }
        
        return maxIngredientDepth + 1;
    }

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
                        // Skip buggy recipes
                    }
                }
            } catch (Throwable t) {
                // In case getRecipes() itself throws
            }
        }
        
        return recipesByOutput.getOrDefault(item, Collections.emptyList());
    }

    public synchronized void clearCache() {
        depthCache.clear();
        recipesByOutput = null;
    }
}
