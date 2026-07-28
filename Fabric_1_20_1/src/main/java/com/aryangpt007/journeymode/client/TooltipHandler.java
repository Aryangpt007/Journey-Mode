package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * §9 Progress tooltips. Entirely client-side: reads the already-synced clientPlayerData, no
 * new packets. Normalizes the hovered stack first so potions/enchanted books show the right
 * entry's progress rather than the base item's. Registered via Fabric API's ItemTooltipCallback
 * (Forge's equivalent is ItemTooltipEvent).
 */
public class TooltipHandler {

    public static void onItemTooltip(ItemStack stack, net.minecraft.world.item.TooltipFlag context, java.util.List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        JourneyDataAttachment data = GlobalDataHandler.getPlayerData(mc.player);
        if (!data.isEnabled() || !data.isShowTooltips()) return;

        if (stack.isEmpty()) return;

        String key = JourneyDataAttachment.getItemKey(stack);
        if (data.isUnlocked(key)) {
            lines.add(Component.literal("§aJourney: Unlocked"));
            return;
        }

        int collected = data.getCollectedCount(stack);
        if (collected <= 0) return; // don't clutter tooltips for items with zero progress

        data.initializeCalculator(mc.level.getRecipeManager(), mc.level.registryAccess());
        int threshold = thresholdFor(data, stack.getItem());
        lines.add(Component.literal("§7Journey: " + collected + "/" + threshold + " researched"));
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register(TooltipHandler::onItemTooltip);
    }

    // A tooltip re-renders every frame while the cursor rests on a stack, so this threshold
    // lookup sits on exactly the same hot path that froze the deposit tab in 1.8.0. Cache the
    // resolved value per item and per config generation (rulesGeneration is bumped whenever
    // ConfigHandler's rules are reassigned, including a server config sync), so the recipe graph
    // is only ever walked on a genuine miss.
    private static net.minecraft.world.item.Item cachedThresholdItem = null;
    private static int cachedThresholdGeneration = -1;
    private static int cachedThreshold = 1;

    private static int thresholdFor(JourneyDataAttachment data, net.minecraft.world.item.Item item) {
        int generation = com.aryangpt007.journeymode.config.ConfigHandler.getRulesGeneration();
        if (item != cachedThresholdItem || generation != cachedThresholdGeneration) {
            cachedThreshold = Math.max(1, data.getThreshold(item));
            cachedThresholdItem = item;
            cachedThresholdGeneration = generation;
        }
        return cachedThreshold;
    }
}
