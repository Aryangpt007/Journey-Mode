package com.aryangpt007.journeymode.data;

import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which parts of an item's NBT are <em>identity</em> (kept, so two variants stay distinct
 * and a duplicated item still works) and which are <em>wear state</em> (stripped, so durability or
 * charge never carries through duplication).
 *
 * <p>This inverts what 1.8.1 did. That version used an allowlist of a handful of vanilla keys and
 * discarded everything else, which meant every modded NBT identity was silently thrown away:
 * Apotheosis gems lost their affix, TACZ guns lost the gun, Iron's Spells scrolls lost the spell
 * (and then crashed the client from inside the mod's own tooltip code), and a GregTech hammer lost
 * its AoE. The rule is now deny-by-exception: keep everything except explicit wear state or
 * dangerous data.
 *
 * <p><b>The container denylist is a duplication gate, not tidiness.</b> Under an allowlist an
 * unlisted key was dropped by default, so a shulker box's contents could never ride along. Under a
 * denylist they would - and since an unlocked item can be withdrawn infinitely, that is an
 * unbounded item duplicator. Everything in CONTAINER_PATHS exists for that reason and must not be
 * removed without understanding it.
 */
public final class NormalizationRules {

    private NormalizationRules() {}

    /**
     * Wear state: changes as the item is used, never part of what the item <em>is</em>. Stripping
     * these is the whole point - "durability, charge or some sort of internal count should not be
     * carried over while duplicating" (maintainer, 2026-08-08).
     */
    private static final Set<String> WEAR_KEYS = new HashSet<String>(Arrays.asList(
        "Damage",          // vanilla durability
        "RepairCost",      // anvil work penalty - accumulates with use
        "Energy", "energy", "EnergyStored", "energyStored",
        "Charge", "charge", "FluxEnergy", "power", "Power",
        "Fuel", "fuel", "Burn", "burnTime"
    ));

    /**
     * Settled identity decisions (maintainer, 2026-08-08): enchantments on an item collapse to the
     * base item, so Enchantments is stripped. That also closes a balance hole - unstackables cost
     * one deposit, so keeping enchantments would let one Sharpness V sword buy unlimited copies.
     * StoredEnchantments is deliberately NOT here: an enchanted book <em>is</em> its enchantment,
     * and books stay separate entries.
     */
    private static final Set<String> IDENTITY_EXCLUDED_KEYS = new HashSet<String>(Arrays.asList(
        "Enchantments", "ench"
    ));

    /** Nested paths to strip, dotted from the tag root. Anvil renames do not carry over. */
    private static final Set<String> WEAR_PATHS = new HashSet<String>(Arrays.asList(
        "display.Name"     // display.Lore and display.color stay - those are identity
    ));

    /**
     * Anything that can hold other items. Duplicating one duplicates its contents, which is an
     * infinite item source.
     *
     * <p>ForgeCaps is the awkward one: it holds capability data for most mods, which is where both
     * inventories <em>and</em> stored energy live. It is stripped whole rather than picked apart,
     * because getting it wrong costs a duplication exploit and the only thing lost is charge -
     * which is stripped anyway.
     */
    private static final Set<String> CONTAINER_PATHS = new HashSet<String>(Arrays.asList(
        "Items", "Inventory", "BlockEntityTag", "ChargedProjectiles", "Bundle", "BundleContents",
        "Contents", "items", "inventory", "backpack", "Backpack", "Cells", "cells",
        "ForgeCaps", "neoforge:attachments", "Stock", "StoredItems", "tank", "Tank", "Fluids"
    ));

    /** Volatile: changes without the item meaningfully changing; would fragment the catalog. */
    private static final Set<String> VOLATILE_KEYS = new HashSet<String>(Arrays.asList(
        "UUID", "UUIDMost", "UUIDLeast", "Owner", "OwnerUUID", "LastUser",
        "Time", "time", "Timestamp", "timestamp", "Age", "Seed", "seed",
        "x", "y", "z", "Dimension", "dim", "Pos", "BlockPos"
    ));

    /** SNBT keys matching this need no quoting; anything else must be quoted to re-parse. */
    private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");

    /**
     * Strip wear state and dangerous data, returning a fresh tag. Never mutates the input - the
     * caller's stack must be left exactly as it was.
     */
    public static CompoundNBT strip(CompoundNBT original) {
        return strip(original, null);
    }

    /**
     * @param itemNamespace namespace of the item the tag belongs to, so per-mod rules in
     *        normalization.json apply (see NormalizationConfig); null applies global rules only.
     */
    public static CompoundNBT strip(CompoundNBT original, String itemNamespace) {
        return stripInto(original, "", itemNamespace);
    }

    private static CompoundNBT stripInto(CompoundNBT source, String prefix, String itemNamespace) {
        CompoundNBT out = new CompoundNBT();
        for (String key : source.getAllKeys()) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (isStripped(key, path, itemNamespace)) continue;

            INBT value = source.get(key);
            if (value instanceof CompoundNBT) {
                CompoundNBT cleaned = stripInto((CompoundNBT) value, path, itemNamespace);
                // An emptied-out compound carries no identity; dropping it keeps keys shorter and
                // stops "{}" fragments making two otherwise-identical items look different.
                if (!cleaned.isEmpty()) out.put(key, cleaned);
            } else if (value != null) {
                out.put(key, value.copy());
            }
        }
        return out;
    }

    /**
     * Public because the API re-add path must run through it too. A third-party
     * NormalizationRule can request a key that JourneyModeAPI.DENYLISTED_KEYS (four
     * entries) happens not to cover but the container gate here does - re-adding it
     * blindly from the source tag would reopen the duplication hole this class exists
     * to close.
     */
    public static boolean isStripped(String key, String path) {
        return isStripped(key, path, null);
    }

    /** Built-in floor first, then the pack's normalization.json (which can only add). */
    public static boolean isStripped(String key, String path, String itemNamespace) {
        if (CONTAINER_PATHS.contains(key) || CONTAINER_PATHS.contains(path)) return true;
        if (WEAR_KEYS.contains(key)) return true;
        if (WEAR_PATHS.contains(path)) return true;
        if (IDENTITY_EXCLUDED_KEYS.contains(key)) return true;
        if (VOLATILE_KEYS.contains(key)) return true;
        // Common modded spellings of stored energy, matched loosely rather than enumerated - a
        // missed one only costs a fragmented catalog, never a dupe.
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith("energy") || lower.endsWith("durability") || lower.endsWith("mana")) return true;
        return NormalizationConfig.isKeyStripped(itemNamespace, key);
    }

    /** Namespace part of an item id ("minecraft:stone" -> "minecraft"); "minecraft" if none. */
    public static String namespaceOf(String itemId) {
        if (itemId == null) return "minecraft";
        int colon = itemId.indexOf(':');
        return colon > 0 ? itemId.substring(0, colon) : "minecraft";
    }

    /**
     * Serialize a tag with keys sorted, recursively.
     *
     * <p>Required for key stability. The compound tag type is backed by a plain HashMap, so its
     * toString() order follows hash-bucket layout. Under the old allowlist that was stable by
     * accident, because the tag was always rebuilt fresh with the same one or two insertions. A
     * denylist copies whatever the source stack had, so insertion history - and therefore key
     * order - becomes input-dependent, and the same logical item could serialize two different
     * ways. That means duplicate catalog entries and orphaned unlocks.
     */
    public static String canonical(INBT tag) {
        if (tag instanceof CompoundNBT) {
            CompoundNBT compound = (CompoundNBT) tag;
            List<String> keys = new ArrayList<String>(compound.getAllKeys());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(',');
                String key = keys.get(i);
                sb.append(quoteKeyIfNeeded(key)).append(':').append(canonical(compound.get(key)));
            }
            return sb.append('}').toString();
        }
                // ListTag only, NOT CollectionTag: the byte/int/long ARRAY tags are also collections,
        // and emitting them as a plain [a,b,c] loses the [B; / [I; / [L; prefix, so parsing the
        // key back would yield a list where the mod expects an array. Their own toString()
        // already emits the right form, so they fall through to the default below.
        if (tag instanceof ListNBT) {
            // List order is meaningful in NBT - do NOT sort it, only canonicalise the elements.
            ListNBT list = (ListNBT) tag;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(canonical((INBT) list.get(i)));
            }
            return sb.append(']').toString();
        }
        return tag == null ? "" : tag.toString();
    }

    private static String quoteKeyIfNeeded(String key) {
        if (SIMPLE_KEY.matcher(key).matches()) return key;
        return "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
