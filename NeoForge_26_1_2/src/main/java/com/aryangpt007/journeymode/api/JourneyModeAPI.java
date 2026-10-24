package com.aryangpt007.journeymode.api;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static registry for third-party integration. Keep this class's own dependency surface small
 * (net.minecraft + this package only) so other mods can compileOnly against it cheaply.
 */
public final class JourneyModeAPI {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Components that can never be whitelisted by a NormalizationRule, full stop - whitelisting
     *  any of these would reopen the shulker-box/bundle/container-item duplication vector the
     *  hybrid-key normalization system exists to close. */
    public static final Set<DataComponentType<?>> DENYLISTED_COMPONENTS = Set.of(
        DataComponents.CONTAINER,
        DataComponents.BLOCK_ENTITY_DATA,
        DataComponents.CONTAINER_LOOT,
        DataComponents.BUNDLE_CONTENTS
    );

    private static final List<ThresholdProvider> THRESHOLD_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<NormalizationRule> NORMALIZATION_RULES = new CopyOnWriteArrayList<>();

    private JourneyModeAPI() {}

    public static void registerThresholdProvider(ThresholdProvider provider) {
        THRESHOLD_PROVIDERS.add(provider);
    }

    public static void registerNormalizationRule(NormalizationRule rule) {
        NORMALIZATION_RULES.add(rule);
    }

    /** Internal: first non-null answer wins, in registration order. */
    public static Integer queryThresholdProviders(String itemKey) {
        for (ThresholdProvider provider : THRESHOLD_PROVIDERS) {
            try {
                Integer result = provider.getThreshold(itemKey);
                if (result != null) return result;
            } catch (Exception e) {
                LOGGER.error("ThresholdProvider {} threw while resolving {}", provider.getClass().getName(), itemKey, e);
            }
        }
        return null;
    }

    /** Internal: union of every rule's requested component types, with denylisted ones hard-filtered out. */
    public static Set<DataComponentType<?>> collectAdditionalNormalizationComponents(ItemStack original) {
        if (NORMALIZATION_RULES.isEmpty()) return Set.of();
        Set<DataComponentType<?>> components = new HashSet<>();
        for (NormalizationRule rule : NORMALIZATION_RULES) {
            try {
                Set<DataComponentType<?>> requested = rule.getAdditionalComponentsToPreserve(original);
                if (requested == null) continue;
                for (DataComponentType<?> type : requested) {
                    if (DENYLISTED_COMPONENTS.contains(type)) {
                        LOGGER.warn("NormalizationRule {} requested denylisted component '{}' - ignored.", rule.getClass().getName(), type);
                        continue;
                    }
                    components.add(type);
                }
            } catch (Exception e) {
                LOGGER.error("NormalizationRule {} threw while resolving additional components", rule.getClass().getName(), e);
            }
        }
        return components;
    }
}
