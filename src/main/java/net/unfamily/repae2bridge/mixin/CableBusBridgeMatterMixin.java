package net.unfamily.repae2bridge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import appeng.api.parts.IPart;
import appeng.blockentity.networking.CableBusBlockEntity;

import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.network.IMatterTanksConsumer;
import com.buuz135.replication.api.network.IMatterTanksSupplier;

import net.minecraft.core.Direction;

import net.unfamily.repae2bridge.parts.CableRepAe2BridgePart;

/**
 * Expose cable-bridge AE2 matter tanks to Replication's MatterNetwork holder scan
 * (same role as {@code RepAE2BridgeBlockEntity#getTanks()}).
 */
@Mixin(value = CableBusBlockEntity.class, remap = false)
public abstract class CableBusBridgeMatterMixin implements IMatterTanksSupplier, IMatterTanksConsumer {

    @Override
    public List<? extends IMatterTank> getTanks() {
        CableRepAe2BridgePart part = rep_ae2_bridge$findBridgePart();
        if (part == null) {
            return List.of();
        }
        return part.getAe2MatterTanks();
    }

    @Override
    public int getPriority() {
        CableRepAe2BridgePart part = rep_ae2_bridge$findBridgePart();
        return part != null ? part.getPriority() : 0;
    }

    @Unique
    private CableRepAe2BridgePart rep_ae2_bridge$findBridgePart() {
        CableBusBlockEntity self = (CableBusBlockEntity) (Object) this;
        for (Direction side : Direction.values()) {
            IPart part = self.getPart(side);
            if (part instanceof CableRepAe2BridgePart bridge) {
                return bridge;
            }
        }
        return null;
    }
}
