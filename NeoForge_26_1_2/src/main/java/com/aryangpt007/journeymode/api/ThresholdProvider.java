package com.aryangpt007.journeymode.api;

/**
 * Third-party extension point: supply a threshold for an item before Journey Mode's own
 * recipe-depth calculator runs. Register via {@link JourneyModeAPI#registerThresholdProvider}.
 * Queried after config-file rules and datapack thresholds, so a server owner's config always
 * wins over a mod's suggestion.
 */
public interface ThresholdProvider {
    /**
     * @param itemKey the item's registry id (e.g. "minecraft:diamond"); NOT a hybrid sub-type key.
     * @return the suggested threshold, or null to defer to the next provider / the calculator.
     */
    Integer getThreshold(String itemKey);
}
