package com.aryangpt007.journeymode.data;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

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
 * <p>This inverts what 1.8.1 did. That version used an allowlist of six vanilla keys and discarded
 * everything else, which meant every modded NBT identity was silently thrown away: Apotheosis gems
 * lost their affix, TACZ guns lost the gun, Iron's Spells scrolls lost the spell (and then crashed
 * the client from inside the mod's own tooltip code), and a GregTech hammer lost its AoE. The rule
 * is now deny-by-exception: keep everything except what is explicitly wear state or dangerous.
 *
 * <p><b>The container denylist is a duplication gate, not tidiness.</b> Under an allowlist, an
 * unlisted key was dropped by default, so a shulker box's contents could never ride along. Under a
 * denylist they would - and since an unlocked item can be withdrawn infinitely, that is an
 * unbounded item duplicator. Everything in {@link #CONTAINER_PATHS} exists for that reason and
 * must not be removed without understanding it.
 */
public final class NormalizationRules {

    private NormalizationRules() {}

    /**
     * Wear state: changes as the item is used, and is never part of what the item <em>is</em>.
     * Stripping these is the whole point - "durability, charge or some sort of internal count
     * should not be carried over while duplicating" (maintainer, 2026-08-08).
     */
    private static final Set<String> WEAR_KEYS = new HashSet<>(Arrays.asList(
        "Damage",          // vanilla durability (1.13+ stores it in NBT, not metadata)
        "RepairCost",      // anvil work penalty - accumulates with use
        "Energy", "energy", "EnergyStored", "energyStored",
        "Charge", "charge", "FluxEnergy", "power", "Power",
        "Fuel", "fuel", "Burn", "burnTime"
    ));

    /**
     * Settled identity decisions (maintainer, 2026-08-08):
     * <ul>
     *   <li>enchantments on an item collapse to the base item - so {@code Enchantments} is
     *       stripped. This also closes a balance hole: unstackables cost one deposit, so keeping
     *       enchantments would have let one Sharpness V sword buy unlimited copies.</li>
     *   <li>anvil renames do not carry over - so {@code display.Name} is stripped. Keeping it
     *       would also let a player inflate their own catalog with an anvil.</li>
     * </ul>
     * {@code StoredEnchantments} is deliberately NOT here: an enchanted book <em>is</em> its
     * enchantment, and books stay separate entries.
     */
    private static final Set<String> IDENTITY_EXCLUDED_KEYS = new HashSet<>(Arrays.asList(
        "Enchantments"
    ));

    /** Nested paths to strip, expressed as dotted paths from the tag root. */
    private static final Set<String> WEAR_PATHS = new HashSet<>(Arrays.asList(
        "display.Name"     // anvil rename; display.Lore / display.color stay (they are identity)
    ));

    /**
     * Anything that can hold other items. Duplicating one of these duplicates its contents, which
     * is an infinite item source. Deny-by-default is gone, so this list is now load-bearing.
     *
     * <p>{@code ForgeCaps} is the awkward one: on 1.12.2-1.20.1 it holds capability data for most
     * mods, which is where both inventories <em>and</em> stored energy live. It is stripped whole
     * rather than picked apart, because getting it wrong costs a duplication exploit and the only
     * thing lost is charge - which we strip anyway.
     */
    private static final Set<String> CONTAINER_PATHS = new HashSet<>(Arrays.asList(
        "Items", "Inventory", "BlockEntityTag", "ChargedProjectiles", "Bundle", "BundleContents",
        "Contents", "items", "inventory", "backpack", "Backpack", "Cells", "cells",
        "ForgeCaps", "neoforge:attachments", "Stock", "StoredItems", "tank", "Tank", "Fluids"
    ));

    /**
     * Volatile: changes without the item meaningfully changing, and would fragment the catalog
     * into thousands of near-duplicate entries.
     */
    private static final Set<String> VOLATILE_KEYS = new HashSet<>(Arrays.asList(
        "UUID", "UUIDMost", "UUIDLeast", "Owner", "OwnerUUID", "LastUser",
        "Time", "time", "Timestamp", "timestamp", "Age", "Seed", "seed",
        "x", "y", "z", "Dimension", "dim", "Pos", "BlockPos"
    ));

    /** SNBT keys matching this need no quoting; anything else must be quoted to re-parse. */
    private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");

    /**
     * Strip wear state and dangerous data from a tag, returning a fresh tag. Never mutates the
     * input - the caller's stack must be left exactly as it was.
     */
    public static CompoundTag strip(CompoundTag original) {
        return stripInto(original, "");
    }

    private static CompoundTag stripInto(CompoundTag source, String prefix) {
        CompoundTag out = new CompoundTag();
        for (String key : source.getAllKeys()) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (isStripped(key, path)) continue;

            Tag value = source.get(key);
            if (value instanceof CompoundTag nested) {
                CompoundTag cleaned = stripInto(nested, path);
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
        if (CONTAINER_PATHS.contains(key) || CONTAINER_PATHS.contains(path)) return true;
        if (WEAR_KEYS.contains(key)) return true;
        if (WEAR_PATHS.contains(path)) return true;
        if (IDENTITY_EXCLUDED_KEYS.contains(key)) return true;
        if (VOLATILE_KEYS.contains(key)) return true;
        // Common modded spellings of stored energy, matched loosely rather than enumerated - a
        // missed one only costs a fragmented catalog, never a dupe.
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith("energy") || lower.endsWith("durability") || lower.endsWith("mana")) return true;
        return false;
    }

    /**
     * Serialize a tag with keys sorted, recursively.
     *
     * <p>Required for key stability. {@code CompoundTag} is backed by a plain {@code HashMap}
     * (verified against the 1.20.1 jar), so its {@code toString()} order follows hash-bucket
     * layout. Under the old allowlist that was stable by accident, because the tag was always
     * rebuilt fresh with the same one or two insertions. A denylist copies whatever the source
     * stack had, so insertion history - and therefore key order - becomes input-dependent, and
     * the same logical item could serialize two different ways. That would mean duplicate catalog
     * entries and orphaned unlocks.
     */
    public static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
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
        if (tag instanceof ListTag list) {
            // List order is meaningful in NBT - do NOT sort it, only canonicalise the elements.
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(canonical((Tag) list.get(i)));
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
