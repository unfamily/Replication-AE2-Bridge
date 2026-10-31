package net.unfamily.repae2bridge;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.network.IMatterTanksSupplier;
import com.buuz135.replication.block.tile.BaseMatterTankBlockEntity;
import com.buuz135.replication.block.tile.DisintegratorBlockEntity;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import net.unfamily.repae2bridge.ae2.matter.CompositeMatterHandler;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.block.entity.MatterFoundryBlockEntity;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;

/**
 * Registers bridge and Replication matter-handler capabilities for AE2 buses.
 */
public class RepAE2BridgeCapabilities {

    public static void register(RegisterCapabilitiesEvent event) {
        // Bridge block item capability
        event.registerBlock(Capabilities.Item.BLOCK, (level, blockPos, blockState, blockEntity, direction) -> {
            if (blockEntity instanceof RepAE2BridgeBlockEntity bridge
                    && (direction == Direction.UP || direction == null)) {
                return bridge.getOutput();
            }
            return null;
        }, ModBlocks.REPAE2BRIDGE.get());

        // Replication never registers MATTER_HANDLER; AE2 import/export/storage buses need it.
        event.registerBlock(
                ReplicationRegistry.Capabilities.MATTER_HANDLER,
                (level, pos, state, be, side) -> {
                    if (be instanceof BaseMatterTankBlockEntity<?> tank) {
                        return CompositeMatterHandler.of(tank.getTanks());
                    }
                    if (be instanceof DisintegratorBlockEntity disintegrator) {
                        return CompositeMatterHandler.of(disintegrator.getTanks());
                    }
                    if (be instanceof IMatterTanksSupplier supplier) {
                        return CompositeMatterHandler.of(supplier.getTanks());
                    }
                    return null;
                },
                ReplicationRegistry.Blocks.MATTER_TANK.getBlock(),
                ReplicationRegistry.Blocks.CREATIVE_MATTER_TANK.getBlock(),
                ReplicationRegistry.Blocks.DISINTEGRATOR.getBlock());

        // Matter Foundry energy capability
        event.registerBlock(
                Capabilities.Energy.BLOCK,
                (level, pos, state, be, side) -> {
                    if (be instanceof MatterFoundryBlockEntity foundry) {
                        return foundry.getFeBuffer();
                    }
                    return null;
                },
                ModBlocks.MATTER_FOUNDRY.get());

        // Matter Foundry item handler capability (input from sides, output from bottom)
        event.registerBlock(
                Capabilities.Item.BLOCK,
                (level, pos, state, be, side) -> {
                    if (be instanceof MatterFoundryBlockEntity foundry) {
                        if (side == Direction.DOWN) {
                            return foundry.getOutputSlot();
                        }
                        return foundry.getInputSlots();
                    }
                    return null;
                },
                ModBlocks.MATTER_FOUNDRY.get());
    }
}
