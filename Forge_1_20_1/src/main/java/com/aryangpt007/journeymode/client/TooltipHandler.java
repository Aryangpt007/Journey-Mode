package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * §9 Progress tooltips. Entirely client-side: reads the already-synced clientPlayerData, no
 * new packets. Normalizes the hovered stack first so potions/enchanted books show the right
 * entry's progress rather than the base item's.
 */
public class TooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        JourneyDataAttachment data = mc.player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
        if (data == null || !data.isEnabled() || !data.isShowTooltips()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        String key = JourneyDataAttachment.getItemKey(stack);
        if (data.isUnlocked(key)) {
            event.getToolTip().add(Component.literal("§aJourney: Unlocked"));
            return;
        }

        int collected = data.getCollectedCount(stack);
        if (collected <= 0) return; // don't clutter tooltips for items with zero progress

        data.initializeCalculator(mc.level.getRecipeManager(), mc.level.registryAccess());
        int threshold = thresholdFor(data, stack.getItem());
        event.getToolTip().add(Component.literal("§7Journey: " + collected + "/" + threshold + " researched"));
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
