package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;

/**
 * §9 Progress tooltips. Entirely client-side: reads the already-synced clientPlayerData, no
 * new packets. Normalizes the hovered stack first so potions/enchanted books show the right
 * entry's progress rather than the base item's. Registered against Fabric API's
 * ItemTooltipCallback from JourneyModeClient#onInitializeClient.
 */
public class TooltipHandler {

    public static void onItemTooltip(ItemStack stack, net.minecraft.world.item.TooltipFlag context, java.util.List<net.minecraft.network.chat.Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        JourneyDataAttachment data = GlobalDataHandler.getPlayerData(mc.player);
        if (!data.isEnabled() || !data.isShowTooltips()) return;

        if (stack.isEmpty()) return;

        String key = JourneyDataAttachment.getItemKey(stack);
        if (data.isUnlocked(key)) {
            lines.add(new TextComponent("§aJourney: Unlocked"));
            return;
        }

        int collected = data.getCollectedCount(stack);
        if (collected <= 0) return; // don't clutter tooltips for items with zero progress

        data.initializeCalculator(mc.level.getRecipeManager(), mc.level.registryAccess());
        int threshold = data.getThreshold(stack.getItem());
        lines.add(new TextComponent("§7Journey: " + collected + "/" + threshold + " researched"));
    }
}
