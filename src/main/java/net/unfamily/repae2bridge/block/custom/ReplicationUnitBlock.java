package net.unfamily.repae2bridge.block.custom;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import net.unfamily.repae2bridge.block.chamber.ChamberStructure;
import net.unfamily.repae2bridge.block.entity.ModBlockEntities;
import net.unfamily.repae2bridge.block.entity.ReplicationUnitBlockEntity;

/**
 * Replication Unit: interior block of a Matter Assembling Chamber.
 * When the chamber is formed, the BER draws a colored fill inside this block only.
 */
public class ReplicationUnitBlock extends Block implements EntityBlock {

    public static final MapCodec<ReplicationUnitBlock> CODEC = simpleCodec(ReplicationUnitBlock::new);

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    /** AE2 grid online — drives client matter-pipe shader visibility. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    /** 0 = idle, 1 = running, 2 = anomaly */
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);

    public ReplicationUnitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FORMED, false)
                .setValue(POWERED, false)
                .setValue(MODE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, POWERED, MODE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof ReplicationUnitBlockEntity unit) {
            unit.getMainNode().setOwningPlayer(player);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ReplicationUnitBlockEntity unit) {
            unit.requestRecheck();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockEntity te = level.getBlockEntity(pos);
        if (te != null) {
            te.requestModelDataUpdate();
        }
        if (!level.isClientSide() && te instanceof ReplicationUnitBlockEntity unit) {
            // Local recheck only — avoid volume-wide notify cascades on neighbor updates.
            unit.requestRecheck();
        }
        // Keep formed/powered/mode in sync with BE (cube overlays + shader).
        if (te instanceof ReplicationUnitBlockEntity unit) {
            boolean formed = unit.isFormed();
            boolean powered = unit.isAe2Online();
            int mode = unit.getMode().ordinal();
            if (state.getValue(FORMED) != formed || state.getValue(POWERED) != powered
                    || state.getValue(MODE) != mode) {
                return state.setValue(FORMED, formed).setValue(POWERED, powered).setValue(MODE, mode);
            }
        }
        return state;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReplicationUnitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.REPLICATION_UNIT_BE.get(),
                ReplicationUnitBlockEntity::serverTick);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> actual, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == actual ? (BlockEntityTicker<A>) ticker : null;
    }
}
