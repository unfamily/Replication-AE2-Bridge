package net.unfamily.repae2bridge.block.custom;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.unfamily.repae2bridge.block.entity.FluixingReplicaAlloyBlockEntity;

/**
 * Opaque cube meshed by the chunk (vanilla lighting). A client BER draws only the
 * pulsed overlay on top — no server logic.
 */
public class FluixingReplicaAlloyBlock extends Block implements EntityBlock {

    public FluixingReplicaAlloyBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluixingReplicaAlloyBlockEntity(pos, state);
    }
}
