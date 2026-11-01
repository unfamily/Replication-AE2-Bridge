package net.unfamily.repae2bridge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.element.NetworkElement;

@Mixin(value = MatterNetwork.class, remap = false)
public interface MatterNetworkAccessor {

    @Accessor("chipSuppliers")
    List<NetworkElement> rep_ae2_bridge$getChipSuppliersRaw();

    @Accessor("replicators")
    List<NetworkElement> rep_ae2_bridge$getReplicatorsRaw();

    @Accessor("matterStacksHolders")
    List<NetworkElement> rep_ae2_bridge$getMatterStacksHoldersRaw();

    @Accessor("queueNetworkElements")
    List<NetworkElement> rep_ae2_bridge$getQueueNetworkElementsRaw();
}
