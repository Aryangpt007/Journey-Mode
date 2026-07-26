package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * §9 Progress tooltips. Entirely client-side: reads the already-synced client player
 * capability, no new packets. Normalizes the hovered stack first so potions/enchanted books
 * show the right entry's progress rather than the base item's.
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
            event.getToolTip().add(new StringTextComponent("§aJourney: Unlocked"));
            return;
        }

        int collected = data.getCollectedCount(stack);
        if (collected <= 0) return; // don't clutter tooltips for items with zero progress

        data.initializeCalculator(mc.level.getRecipeManager());
        int threshold = data.getThreshold(stack.getItem());
        event.getToolTip().add(new StringTextComponent("§7Journey: " + collected + "/" + threshold + " researched"));
    }
}
