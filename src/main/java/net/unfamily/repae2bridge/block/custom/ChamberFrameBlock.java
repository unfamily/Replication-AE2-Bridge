package net.unfamily.repae2bridge.block.custom;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import net.unfamily.repae2bridge.block.chamber.ChamberStructure;
import net.unfamily.repae2bridge.block.entity.ChamberFrameBlockEntity;

/**
 * Chamber frame: base texture on corners and stubs;
 * column texture on through-segments (both neighbors on one axis — includes rectangle borders);
 * interior texture when surrounded on both axes of a flat plane (e.g. 5×5 → border columns + inner 3×3 INT).
 * When the chamber is formed, the frame BE exposes an AE2 grid node for cable connection.
 */
public class ChamberFrameBlock extends Block implements EntityBlock {

    public static final MapCodec<ChamberFrameBlock> CODEC = simpleCodec(ChamberFrameBlock::new);

    public static final EnumProperty<ChamberColumn> COLUMN = EnumProperty.create("column", ChamberColumn.class);

    public ChamberFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLUMN, ChamberColumn.NONE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLUMN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return computeState(state, level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BlockState computed = computeState(state, level, pos);
        if (computed != state) {
            level.setBlock(pos, computed, 3);
        }
        if (!level.isClientSide()) {
            ChamberStructure.notifyNearbyUnits(level, pos);
            if (level.getBlockEntity(pos) instanceof ChamberFrameBlockEntity frame) {
                frame.refreshChamberLink();
            }
        }
    }

    private BlockState computeState(BlockState state, LevelAccessor level, BlockPos pos) {
        boolean xPos = isFrame(level, pos.east());
        boolean xNeg = isFrame(level, pos.west());
        boolean yPos = isFrame(level, pos.above());
        boolean yNeg = isFrame(level, pos.below());
        boolean zPos = isFrame(level, pos.south());
        boolean zNeg = isFrame(level, pos.north());

        boolean xBoth = xPos && xNeg;
        boolean yBoth = yPos && yNeg;
        boolean zBoth = zPos && zNeg;

        ChamberColumn column = ChamberColumn.NONE;
        // Plane interior first (both axes through) → INT; else one through-axis → column
        // even if a side neighbor exists (rectangle border cells of a filled 5×5 face).
        if ((xBoth && zBoth) || (xBoth && yBoth) || (yBoth && zBoth)) {
            column = ChamberColumn.INT;
        } else if (yBoth) {
            column = ChamberColumn.Y;
        } else if (xBoth) {
            column = ChamberColumn.X;
        } else if (zBoth) {
            column = ChamberColumn.Z;
        }
        return state.setValue(COLUMN, column);
    }

    private boolean isFrame(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ChamberFrameBlock;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChamberFrameBlockEntity(pos, state);
    }
}
