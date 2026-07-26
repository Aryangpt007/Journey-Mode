package com.aryangpt007.journeymode.api;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Third-party extension point: contribute additional Data Components to preserve when Journey
 * Mode normalizes an ItemStack into its hybrid sub-type key. Register via
 * {@link JourneyModeAPI#registerNormalizationRule}.
 *
 * Adapted for 1.21.1's Data Components model (replaces the NBT-CompoundTag-keyed version of
 * this interface used on pre-1.20.5 environments).
 *
 * SECURITY NOTE: components in {@link JourneyModeAPI#DENYLISTED_COMPONENTS} (container/
 * block-entity/mob-storage data) are stripped even if a rule requests them - whitelisting them
 * here would reopen the shulker-box duplication vector the hybrid-key system exists to close.
 */
public interface NormalizationRule {
    /**
     * @param originalStack the full, un-normalized stack being deposited/queried.
     * @return additional component types (beyond Journey Mode's own built-in whitelist) whose
     *         values should be copied onto the normalized stack, if present.
     */
    Set<DataComponentType<?>> getAdditionalComponentsToPreserve(ItemStack originalStack);
}
