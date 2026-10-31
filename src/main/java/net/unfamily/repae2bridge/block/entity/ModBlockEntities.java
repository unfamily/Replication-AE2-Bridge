package net.unfamily.repae2bridge.block.entity;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RepAE2Bridge.MOD_ID);

    public static final Supplier<BlockEntityType<RepAE2BridgeBlockEntity>> REPAE2BRIDGE_BE =
            BLOCK_ENTITIES.register("bridge_be", () -> BlockEntityType.Builder.of(
                    RepAE2BridgeBlockEntity::new, ModBlocks.REPAE2BRIDGE.get()).build(null));

    public static final Supplier<BlockEntityType<MatterAssemblerBlockEntity>> MATTER_ASSEMBLER_BE =
            BLOCK_ENTITIES.register("matter_assembler", () -> BlockEntityType.Builder.of(
                    MatterAssemblerBlockEntity::new, ModBlocks.MATTER_ASSEMBLER.get()).build(null));

    public static final Supplier<BlockEntityType<MatterFoundryBlockEntity>> MATTER_FOUNDRY_BE =
            BLOCK_ENTITIES.register("matter_foundry", () -> BlockEntityType.Builder.of(
                    MatterFoundryBlockEntity::new, ModBlocks.MATTER_FOUNDRY.get()).build(null));

    public static final Supplier<BlockEntityType<ReplicationUnitBlockEntity>> REPLICATION_UNIT_BE =
            BLOCK_ENTITIES.register("replication_unit", () -> BlockEntityType.Builder.of(
                    ReplicationUnitBlockEntity::new, ModBlocks.REPLICATION_UNIT.get()).build(null));

    public static final Supplier<BlockEntityType<ChamberFrameBlockEntity>> CHAMBER_FRAME_BE =
            BLOCK_ENTITIES.register("chamber_frame", () -> BlockEntityType.Builder.of(
                    ChamberFrameBlockEntity::new, ModBlocks.CHAMBER_FRAME.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
