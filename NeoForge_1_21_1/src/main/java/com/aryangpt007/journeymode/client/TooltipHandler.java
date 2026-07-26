package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * §9 Progress tooltips. Entirely client-side: reads the already-synced client-side data
 * attachment, no new packets. Normalizes the hovered stack first so potions/enchanted books
 * show the right entry's progress rather than the base item's.
 */
public class TooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        JourneyDataAttachment data = mc.player.getData(JourneyMode.JOURNEY_DATA);
        if (data == null || !data.isEnabled() || !data.isShowTooltips()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        String key = JourneyDataAttachment.getItemKey(stack, mc.level.registryAccess());
        if (data.isUnlocked(key)) {
            event.getToolTip().add(Component.literal("§aJourney: Unlocked"));
            return;
        }

        int collected = data.getCollectedCount(stack, mc.level.registryAccess());
        if (collected <= 0) return; // don't clutter tooltips for items with zero progress

        data.initializeCalculator(mc.level.getRecipeManager(), mc.level.registryAccess());
        int threshold = data.getThreshold(stack.getItem());
        event.getToolTip().add(Component.literal("§7Journey: " + collected + "/" + threshold + " researched"));
    }
}
