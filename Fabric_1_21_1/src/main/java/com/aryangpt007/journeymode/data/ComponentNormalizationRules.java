package com.aryangpt007.journeymode.data;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Component-era equivalent of the NBT NormalizationRules: decides which data components are
 * <em>identity</em> (kept) and which are <em>wear state</em> or dangerous (stripped).
 *
 * <p>Same inversion as the NBT versions. 1.8.1 copied four hardcoded vanilla components and
 * dropped everything else, so every modded component identity was silently discarded. This keeps
 * everything except what is explicitly excluded.
 *
 * <p><b>The container components are a duplication gate.</b> An unlocked item can be withdrawn
 * infinitely; if a shulker box or bundle carried its contents through normalization, that is an
 * unbounded item duplicator. Under the old allowlist they were excluded by default. They are not
 * any more, so this list is load-bearing.
 */
public final class ComponentNormalizationRules {

    private ComponentNormalizationRules() {}

    private static final Set<DataComponentType<?>> STRIPPED = new HashSet<DataComponentType<?>>(Arrays.asList(
        // --- wear state: durability and accumulated use
        DataComponents.DAMAGE,
        DataComponents.REPAIR_COST,

        // --- settled identity decisions (maintainer, 2026-08-08)
        DataComponents.ENCHANTMENTS,   // enchantments on an item collapse to the base item
        DataComponents.CUSTOM_NAME,    // anvil renames do not carry over
        // STORED_ENCHANTMENTS is deliberately absent: an enchanted book IS its enchantment,
        // and books stay separate entries.

        // --- duplication gate: anything that can hold other items
        DataComponents.CONTAINER,
        DataComponents.BUNDLE_CONTENTS,
        DataComponents.CHARGED_PROJECTILES,
        DataComponents.CONTAINER_LOOT,
        DataComponents.BLOCK_ENTITY_DATA
    ));

    public static boolean isStripped(DataComponentType<?> type) {
        return STRIPPED.contains(type);
    }
}
