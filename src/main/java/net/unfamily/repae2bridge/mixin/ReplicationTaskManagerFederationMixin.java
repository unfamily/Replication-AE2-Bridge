package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.network.MatterNetwork;
import com.buuz135.replication.network.task.ReplicationTaskManager;

import net.minecraft.core.BlockPos;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.block.entity.BridgeDeviceFederation;

/**
 * Let replicators on network A pick up pending tasks created on peer network B
 * when both share an AE2 bridge grid.
 */
@Mixin(value = ReplicationTaskManager.class, remap = false)
public abstract class ReplicationTaskManagerFederationMixin {

    private static final ThreadLocal<Boolean> SEARCHING_PEERS = ThreadLocal.withInitial(() -> false);

    @Inject(method = "findTaskForReplicator", at = @At("RETURN"), cancellable = true)
    private void rep_ae2_bridge$peerTasks(
            BlockPos pos,
            MatterNetwork matterNetwork,
            CallbackInfoReturnable<IReplicationTask> cir) {
        if (SEARCHING_PEERS.get()
                || cir.getReturnValue() != null
                || matterNetwork == null
                || !Config.federateDevicesAcrossAe2) {
            return;
        }
        SEARCHING_PEERS.set(true);
        try {
            for (MatterNetwork peer : BridgeDeviceFederation.peerNetworks(matterNetwork)) {
                // Search peer pendingTasks with peer network context (budget via peer.getReplicators).
                // SEARCHING_PEERS skips nested federation so this stays local to the peer manager.
                IReplicationTask task = peer.getTaskManager().findTaskForReplicator(pos, peer);
                if (task != null) {
                    cir.setReturnValue(task);
                    return;
                }
            }
        } finally {
            SEARCHING_PEERS.set(false);
        }
    }
}
