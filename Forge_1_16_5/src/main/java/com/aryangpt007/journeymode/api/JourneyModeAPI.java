package com.aryangpt007.journeymode.api;

import net.minecraft.nbt.CompoundNBT;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
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

    /** Components/NBT keys that can never be whitelisted by a NormalizationRule, full stop. */
    public static final Set<String> DENYLISTED_KEYS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList("BlockEntityTag", "Items", "Inventory", "ChargedProjectiles")));

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

    /** Internal: union of every rule's requested keys, with denylisted keys hard-filtered out. */
    public static Set<String> collectAdditionalNormalizationKeys(CompoundNBT originalTag) {
        if (NORMALIZATION_RULES.isEmpty()) return Collections.emptySet();
        Set<String> keys = new HashSet<>();
        for (NormalizationRule rule : NORMALIZATION_RULES) {
            try {
                Set<String> requested = rule.getAdditionalKeysToPreserve(originalTag);
                if (requested == null) continue;
                for (String key : requested) {
                    if (DENYLISTED_KEYS.contains(key)) {
                        LOGGER.warn("NormalizationRule {} requested denylisted key '{}' - ignored.", rule.getClass().getName(), key);
                        continue;
                    }
                    keys.add(key);
                }
            } catch (Exception e) {
                LOGGER.error("NormalizationRule {} threw while resolving additional keys", rule.getClass().getName(), e);
            }
        }
        return keys;
    }
}
