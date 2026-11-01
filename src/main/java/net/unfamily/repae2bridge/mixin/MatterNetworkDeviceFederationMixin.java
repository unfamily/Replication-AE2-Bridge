package net.unfamily.repae2bridge.mixin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.element.NetworkElement;

import net.minecraft.core.BlockPos;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.block.entity.BridgeDeviceFederation;

/**
 * Expose chip storages / replicators from peer MatterNetworks that share an AE2 grid.
 */
@Mixin(value = MatterNetwork.class, remap = false)
public abstract class MatterNetworkDeviceFederationMixin {

    @Inject(method = "getChipSuppliers", at = @At("RETURN"), cancellable = true)
    private void rep_ae2_bridge$federateChipSuppliers(CallbackInfoReturnable<List<NetworkElement>> cir) {
        federate(cir, true);
    }

    @Inject(method = "getReplicators", at = @At("RETURN"), cancellable = true)
    private void rep_ae2_bridge$federateReplicators(CallbackInfoReturnable<List<NetworkElement>> cir) {
        federate(cir, false);
    }

    private void federate(CallbackInfoReturnable<List<NetworkElement>> cir, boolean chips) {
        if (!Config.federateDevicesAcrossAe2) {
            return;
        }
        MatterNetwork self = (MatterNetwork) (Object) this;
        List<MatterNetwork> peers = BridgeDeviceFederation.peerNetworks(self);
        if (peers.isEmpty()) {
            return;
        }

        Map<BlockPos, NetworkElement> merged = new LinkedHashMap<>();
        List<NetworkElement> local = cir.getReturnValue();
        if (local != null) {
            for (NetworkElement element : local) {
                if (element != null) {
                    merged.put(element.getPos(), element);
                }
            }
        }
        for (MatterNetwork peer : peers) {
            MatterNetworkAccessor access = (MatterNetworkAccessor) (Object) peer;
            List<NetworkElement> raw = chips
                    ? access.rep_ae2_bridge$getChipSuppliersRaw()
                    : access.rep_ae2_bridge$getReplicatorsRaw();
            if (raw == null) {
                continue;
            }
            for (NetworkElement element : raw) {
                if (element != null) {
                    merged.putIfAbsent(element.getPos(), element);
                }
            }
        }
        cir.setReturnValue(new ArrayList<>(merged.values()));
    }
}
