package net.unfamily.repae2bridge.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marker BE so the alloy block can use a client BER for pulsed overlay switching.
 */
public class FluixingReplicaAlloyBlockEntity extends BlockEntity {

    public FluixingReplicaAlloyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUIXING_REPLICA_ALLOY_BLOCK_BE.get(), pos, state);
    }
}
