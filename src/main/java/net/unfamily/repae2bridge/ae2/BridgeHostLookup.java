package net.unfamily.repae2bridge.ae2;

import org.jetbrains.annotations.Nullable;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.parts.CableRepAe2BridgePart;

/**
 * Resolves a block or cable AE2-Replication bridge at {@code bridgePos}.
 */
public final class BridgeHostLookup {

    private BridgeHostLookup() {
    }

    @Nullable
    public static IAe2BridgeCraftHost find(@Nullable Level level, @Nullable BlockPos bridgePos) {
        if (level == null || bridgePos == null || !level.isLoaded(bridgePos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(bridgePos);
        if (be instanceof RepAE2BridgeBlockEntity bridge) {
            return bridge;
        }
        if (be instanceof IPartHost host) {
            for (Direction face : Direction.values()) {
                IPart part = host.getPart(face);
                if (part instanceof CableRepAe2BridgePart cable) {
                    return cable;
                }
            }
        }
        return null;
    }
}
