package com.aryangpt007.journeymode.data;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Canonical (recursively key-sorted) serialization of a saved stack, so a catalog key is stable.
 *
 * <p>ItemStack.save() produces a CompoundTag, and CompoundTag is backed by a plain HashMap - its
 * toString() order follows hash-bucket layout. With the old four-component allowlist the tag was
 * always built from the same short fixed insertion sequence, so the order happened to be stable.
 * Now that components are copied from whatever the source stack carried, insertion history is
 * input-dependent and the same logical item could serialize two different ways: duplicate catalog
 * entries and orphaned unlocks.
 */
public final class ComponentKeys {

    private ComponentKeys() {}

    private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");

    public static String canonical(Tag tag) {
        if (tag instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) tag;
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
        if (tag instanceof ListTag) {
            // List order is meaningful - do NOT sort, only canonicalise the elements.
            ListTag list = (ListTag) tag;
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
