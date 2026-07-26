package com.aryangpt007.journeymode.api;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static registry for third-party integration. Keep this class dependency-free (no imports
 * outside net.minecraft/this package) so other mods can compileOnly against it cheaply.
 */
public final class JourneyModeAPI {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-api");

    /** Components that can never be whitelisted by a NormalizationRule, full stop - container,
     *  block-entity, and mob-storage data are the known dupe vectors (shulker boxes, chest
     *  boats/minecarts, buckets of fish/tadpoles, charged crossbows, bee nests, decorated pots). */
    public static final Set<DataComponentType<?>> DENYLISTED_COMPONENTS = Set.of(
        DataComponents.CONTAINER,
        DataComponents.CONTAINER_LOOT,
        DataComponents.BLOCK_ENTITY_DATA,
        DataComponents.BUCKET_ENTITY_DATA,
        DataComponents.ENTITY_DATA,
        DataComponents.CHARGED_PROJECTILES,
        DataComponents.BEES,
        DataComponents.POT_DECORATIONS
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

    /** Internal: union of every rule's requested components, with denylisted ones hard-filtered out. */
    public static Set<DataComponentType<?>> collectAdditionalNormalizationComponents(ItemStack originalStack) {
        if (NORMALIZATION_RULES.isEmpty()) return Set.of();
        Set<DataComponentType<?>> components = new HashSet<>();
        for (NormalizationRule rule : NORMALIZATION_RULES) {
            try {
                Set<DataComponentType<?>> requested = rule.getAdditionalComponentsToPreserve(originalStack);
                if (requested == null) continue;
                for (DataComponentType<?> component : requested) {
                    if (DENYLISTED_COMPONENTS.contains(component)) {
                        LOGGER.warn("NormalizationRule {} requested denylisted component '{}' - ignored.", rule.getClass().getName(), component);
                        continue;
                    }
                    components.add(component);
                }
            } catch (Exception e) {
                LOGGER.error("NormalizationRule {} threw while resolving additional components", rule.getClass().getName(), e);
            }
        }
        return components;
    }
}
