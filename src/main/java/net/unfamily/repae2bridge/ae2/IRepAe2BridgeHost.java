package net.unfamily.repae2bridge.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import appeng.api.networking.security.IActionHost;
import appeng.helpers.IPriorityHost;

/**
 * Shared host for block and cable-part forms of the AE2-Replication bridge.
 */
public interface IRepAe2BridgeHost extends IPriorityHost, IActionHost {

    boolean isFeedReplicationEnergy();

    void setFeedReplicationEnergy(boolean enabled);

    Level getLevel();

    BlockPos getBlockPos();
}
