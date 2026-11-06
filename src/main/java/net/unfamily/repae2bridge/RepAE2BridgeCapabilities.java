package net.unfamily.repae2bridge;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.network.IMatterTanksSupplier;
import com.buuz135.replication.block.tile.BaseMatterTankBlockEntity;
import com.buuz135.replication.block.tile.DisintegratorBlockEntity;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.core.definitions.AEBlockEntities;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import net.unfamily.repae2bridge.ae2.matter.CompositeMatterHandler;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.block.entity.ModBlockEntities;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.parts.CableRepAe2BridgePart;

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

        // Cable bridge: Replicators insert to task source via Item.BLOCK @ UP on the cable bus.
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                AEBlockEntities.CABLE_BUS.get(),
                (be, side) -> {
                    if (side != null && side != Direction.UP) {
                        return null;
                    }
                    if (!(be instanceof IPartHost host)) {
                        return null;
                    }
                    for (Direction face : Direction.values()) {
                        IPart part = host.getPart(face);
                        if (part instanceof CableRepAe2BridgePart cable) {
                            return cable.getReplicationItemSink();
                        }
                    }
                    return null;
                });

        // Replication never registers MATTER_HANDLER; AE2 import/export/storage buses need it.
        // Resolve BE from the level when callers pass null (hoppers / some pipes).
        event.registerBlock(
                ReplicationRegistry.Capabilities.MATTER_HANDLER,
                (level, pos, state, be, side) -> {
                    if (be == null && level != null) {
                        be = level.getBlockEntity(pos);
                    }
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

        // Matter Foundry: registerBlockEntity so hoppers (BE=null) still resolve the handler.
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.MATTER_FOUNDRY_BE.get(),
                (foundry, side) -> foundry.getFeBuffer());

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.MATTER_FOUNDRY_BE.get(),
                (foundry, side) -> foundry.getAutomationItemHandler(side));

        event.registerBlockEntity(
                ReplicationRegistry.Capabilities.MATTER_HANDLER,
                ModBlockEntities.MATTER_FOUNDRY_BE.get(),
                (foundry, side) -> foundry.getMatterHandler());
    }
}
