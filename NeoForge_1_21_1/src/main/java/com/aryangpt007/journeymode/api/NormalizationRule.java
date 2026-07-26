package com.aryangpt007.journeymode.api;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Third-party extension point: contribute additional Data Components to preserve when Journey
 * Mode normalizes an ItemStack into its hybrid sub-type key. Register via
 * {@link JourneyModeAPI#registerNormalizationRule}.
 *
 * Unlike pre-1.20.5 Forge/NeoForge (which normalized NBT tags on {@code CompoundTag}), 1.21.1
 * has no ItemStack NBT tags at all - subtype data lives in typed {@link DataComponentType}s, so
 * this rule operates on the full original {@link ItemStack} and returns component TYPES rather
 * than NBT key names.
 *
 * SECURITY NOTE: components in {@link JourneyModeAPI#DENYLISTED_COMPONENTS} (container/
 * block-entity/bundle data) are stripped even if a rule requests them - whitelisting them here
 * would reopen the shulker-box/bundle duplication vector the hybrid-key system exists to close.
 */
public interface NormalizationRule {
    /**
     * @param original the full, un-normalized stack being deposited/queried.
     * @return additional component types (beyond Journey Mode's own built-in whitelist) whose
     *         values should be copied onto the normalized stack, if present.
     */
    Set<DataComponentType<?>> getAdditionalComponentsToPreserve(ItemStack original);
}
