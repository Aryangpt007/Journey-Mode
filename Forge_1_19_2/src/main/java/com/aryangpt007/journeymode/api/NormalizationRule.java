package com.aryangpt007.journeymode.api;

import net.minecraft.nbt.CompoundTag;

import java.util.Set;

/**
 * Third-party extension point: contribute additional NBT keys to preserve when Journey Mode
 * normalizes an ItemStack into its hybrid sub-type key. Register via
 * {@link JourneyModeAPI#registerNormalizationRule}.
 *
 * SECURITY NOTE: keys in {@link JourneyModeAPI#DENYLISTED_KEYS} (container/block-entity data)
 * are stripped even if a rule requests them - whitelisting them here would reopen the
 * shulker-box duplication vector the hybrid-key system exists to close.
 */
public interface NormalizationRule {
    /**
     * @param originalTag the full, un-normalized tag on the deposited/queried stack.
     * @return additional top-level key names (beyond Journey Mode's own built-in whitelist)
     *         whose values should be copied into the normalized stack, if present.
     */
    Set<String> getAdditionalKeysToPreserve(CompoundTag originalTag);
}
