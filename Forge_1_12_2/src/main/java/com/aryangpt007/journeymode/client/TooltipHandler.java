package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * §9 Progress tooltips. Entirely client-side: reads the already-synced player capability data,
 * zero new packets. Normalizes the hovered stack first so potions/enchanted books show the right
 * entry's progress rather than the base item's. Forge's ItemTooltipEvent has existed since the
 * 1.8 era in this same package, so the hook itself is unchanged from later versions.
 *
 * `show_tooltips` is a per-player preference on IJourneyData (not a global config value), so the
 * enable check below reads the player's own synced data rather than ConfigHandler.
 */
@SideOnly(Side.CLIENT)
public class TooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        IJourneyData data = mc.player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
        if (data == null || !data.isEnabled() || !data.isShowTooltips()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        String key = JourneyData.getItemKey(stack);
        if (key.isEmpty()) return;

        if (data.isUnlocked(key)) {
            // Bug fix: a unicode checkmark here (✔/✓) rendered as mojibake ("â€œ") on 1.12.2's
            // font renderer/string encoding - plain ASCII only, no symbols.
            event.getToolTip().add("§aJourney: Unlocked");
            return;
        }

        int collected = data.getCollectedCount(stack);
        if (collected <= 0) return; // don't clutter tooltips for items with zero progress

        int threshold = data.getThreshold(stack);
        event.getToolTip().add("§7Journey: " + collected + "/" + threshold + " researched");
    }
}
