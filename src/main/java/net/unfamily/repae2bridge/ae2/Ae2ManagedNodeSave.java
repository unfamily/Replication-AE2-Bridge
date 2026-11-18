package net.unfamily.repae2bridge.ae2;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IManagedGridNode;

import net.unfamily.repae2bridge.compat.SableCompat;

/**
 * Shared AE2 managed-node save safety for Sable sub-level / world unload.
 * <p>
 * Soft unload must NOT skip {@code saveToNBT} merely because the world is stopping —
 * Sable pause-save with a live node at plot coords succeeds; exit must match that path.
 * Never destroy the node here (destroy/removeElement at plot coords is the silent-kill vector).
 */
public final class Ae2ManagedNodeSave {

    private Ae2ManagedNodeSave() {
    }

    /**
     * Skip AE2 NBT only when there is no live node to serialize.
     * World-unloading alone is not a reason to skip (align with successful pause-save).
     */
    public static boolean shouldSkipSave(@Nullable IManagedGridNode node) {
        return node == null || node.getNode() == null;
    }

    /**
     * Report whether NBT serialization must be skipped. Never destroys the node.
     * Emits {@code prefix_SKIP_AE2} when skipping; caller emits {@code prefix_AE2_DONE} after a successful save.
     */
    public static boolean prepareForSave(@Nullable IManagedGridNode node, String breadcrumbPrefix) {
        boolean skip = shouldSkipSave(node);
        if (skip) {
            SableCompat.breadcrumb(breadcrumbPrefix + "_SKIP_AE2");
        }
        return skip;
    }
}
