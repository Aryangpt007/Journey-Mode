package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Client-side guard around catalog entries rebuilt from a stored key.
 *
 * <p>A catalog key is reconstructed into a real ItemStack for display, and that stack is then
 * handed to third-party rendering and tooltip code. If the owning mod considers the stack
 * malformed - which happens whenever normalization dropped NBT the mod treats as mandatory - its
 * tooltip or model code can throw, and an unguarded throw on the render thread takes the client
 * down. Worse, the entry is permanent, so the crash repeats every time the cursor crosses that
 * slot and the player's only escape is wiping all their progress.
 *
 * <p>Every entry that throws is recorded here, rendered as an inert placeholder from then on, and
 * logged exactly once. The set is per-session and deliberately never auto-cleared: a key that has
 * already thrown must not be retried, or the guard would just convert one crash into one throw per
 * frame.
 *
 * <p>This is containment, not a fix. The underlying cause is the allowlist normalization; see
 * roadmap item 1.
 */
public final class SafeCatalogRender {

    private static final Logger LOGGER = LogManager.getLogger("journeymode");
    private static final Set<String> BROKEN = new HashSet<String>();

    private SafeCatalogRender() {}

    public static boolean isBroken(String key) {
        return BROKEN.contains(key);
    }

    public static Set<String> brokenKeys() {
        return Collections.unmodifiableSet(BROKEN);
    }

    /** Rebuild a stack from a key, or EMPTY if the entry is (or just became) broken. */
    public static ItemStack stackFor(String key) {
        if (BROKEN.contains(key)) return ItemStack.EMPTY;
        try {
            ItemStack stack = JourneyDataAttachment.itemStackFromKey(key);
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Throwable t) {
            markBroken(key, "reconstruction", t);
            return ItemStack.EMPTY;
        }
    }

    /** Display name for a catalog entry, falling back to the raw key so the player can still purge it. */
    public static String nameFor(String key, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return key;
        try {
            return stack.getHoverName().getString();
        } catch (Throwable t) {
            markBroken(key, "name resolution", t);
            return key;
        }
    }

    public static void markBroken(String key, String stage, Throwable t) {
        if (BROKEN.add(key)) {
            LOGGER.error("Journey Mode: catalog entry '" + key + "' threw during " + stage
                + ". It will render as a placeholder for the rest of this session. "
                + "Remove it with: /journeymode purge " + key, t);
        }
    }
}
