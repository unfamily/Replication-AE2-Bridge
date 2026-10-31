package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;

/**
 * When an AE2 crafting job is cancelled, cancel bridge-sourced Replication tasks and
 * recover craft-buffer matter into AE2 cells. Does not alter AE2's vanilla inventory refund.
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMixin {

    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Inject(method = "cancel", at = @At("HEAD"))
    private void repAe2Bridge$cancelLinkedReplication(CallbackInfo ci) {
        // Do not require cluster.isActive() — cancel often runs while the cluster is already tearing down.
        if (cluster == null) {
            return;
        }
        var grid = cluster.getGrid();
        if (grid == null) {
            return;
        }
        for (RepAE2BridgeBlockEntity bridge : grid.getMachines(RepAE2BridgeBlockEntity.class)) {
            if (bridge != null) {
                bridge.cancelAe2LinkedReplicationWork();
            }
        }
    }
}
