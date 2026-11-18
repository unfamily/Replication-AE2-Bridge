package net.unfamily.repae2bridge.ae2;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IManagedGridNode;

import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;

/**
 * Shared AE2 managed-node save safety for Sable sub-level / world unload.
 */
public final class Ae2ManagedNodeSave {

    private Ae2ManagedNodeSave() {
    }

    public static boolean shouldSkipSave(@Nullable IManagedGridNode node) {
        return RepAE2BridgeBlockEntity.isWorldUnloading()
                || node == null
                || node.getNode() == null;
    }

    /**
     * Soft unload: never destroy the node here — report whether NBT serialization must be skipped.
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
