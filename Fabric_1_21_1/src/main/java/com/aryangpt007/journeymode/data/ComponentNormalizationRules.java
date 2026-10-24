package com.aryangpt007.journeymode.data;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

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

    /**
     * Built-in floor first, then the pack's normalization.json, which can only add to it (see
     * NormalizationConfig). That file is where modded containers live - Mekanism's items/tanks,
     * AE2's storage cell contents, Create's packages - none of which the vanilla list can know.
     */
    public static boolean isStripped(DataComponentType<?> type, String itemNamespace) {
        if (STRIPPED.contains(type)) return true;
        Object id = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return id != null && NormalizationConfig.isComponentStripped(itemNamespace, id.toString());
    }

    /** The same decision for a stored key, where only the component's id string is available. */
    public static boolean isStrippedId(String componentId, String itemNamespace) {
        String id = componentId.startsWith("!") ? componentId.substring(1) : componentId; // "!" = removed-from-default
        for (DataComponentType<?> type : STRIPPED) {
            Object key = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (key != null && key.toString().equals(id)) return true;
        }
        return NormalizationConfig.isComponentStripped(itemNamespace, id);
    }

    /**
     * NBT keys stripped inside minecraft:custom_data, where many mods still keep plain NBT. Same
     * floor as the NBT-era container gate and wear state, plus normalization.json's strip_keys.
     */
    private static final Set<String> CUSTOM_DATA_FLOOR = new HashSet<String>(Arrays.asList(
        "Items", "Inventory", "BlockEntityTag", "ForgeCaps", "neoforge:attachments", "Contents",
        "items", "inventory", "Bundle", "BundleContents", "ChargedProjectiles", "StoredItems",
        "Damage", "RepairCost", "Energy", "energy", "UUID", "UUIDMost", "UUIDLeast"
    ));

    /** Copy of a custom_data tag without stripped keys (any depth); emptied compounds are dropped. */
    public static CompoundTag stripCustomData(CompoundTag source, String itemNamespace) {
        CompoundTag out = new CompoundTag();
        for (String key : source.getAllKeys()) {
            if (CUSTOM_DATA_FLOOR.contains(key) || NormalizationConfig.isKeyStripped(itemNamespace, key)) continue;
            Tag value = source.get(key);
            if (value instanceof CompoundTag) {
                CompoundTag cleaned = stripCustomData((CompoundTag) value, itemNamespace);
                if (!cleaned.isEmpty()) out.put(key, cleaned);
            } else if (value != null) {
                out.put(key, value.copy());
            }
        }
        return out;
    }

    /**
     * Apply the same rules to a stored key's serialized stack ({id, count, components:{...}}) in
     * place, for re-keying catalogs written under older rules. @return true if any component survives.
     */
    public static boolean stripStoredComponents(CompoundTag stackTag, String itemNamespace) {
        Tag raw = stackTag.get("components");
        if (!(raw instanceof CompoundTag)) return false;
        CompoundTag components = (CompoundTag) raw;
        for (String id : new java.util.ArrayList<String>(components.getAllKeys())) {
            if (isStrippedId(id, itemNamespace)) {
                components.remove(id);
            } else if (id.equals("minecraft:custom_data") && components.get(id) instanceof CompoundTag) {
                CompoundTag cleaned = stripCustomData((CompoundTag) components.get(id), itemNamespace);
                if (cleaned.isEmpty()) components.remove(id); else components.put(id, cleaned);
            }
        }
        if (components.isEmpty()) {
            stackTag.remove("components");
            return false;
        }
        return true;
    }
}
