package net.unfamily.repae2bridge.block.custom;

import java.util.ArrayList;
import java.util.List;

import com.buuz135.replication.block.MatterPipeBlock;
import com.hrznstudio.titanium.block_network.INetworkDirectionalConnection;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import net.unfamily.repae2bridge.block.entity.MatterFoundryBlockEntity;
import net.unfamily.repae2bridge.block.entity.ModBlockEntities;
import net.unfamily.repae2bridge.particle.ModParticles;

/**
 * Matter Foundry block: horizontal-facing, active boolean, EntityBlock with ticker.
 * Implements {@link INetworkDirectionalConnection} so Replication matter pipes open toward it.
 */
public class MatterFoundryBlock extends Block implements EntityBlock, INetworkDirectionalConnection {

    public static final MapCodec<MatterFoundryBlock> CODEC = simpleCodec(MatterFoundryBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public MatterFoundryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, ACTIVE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof MatterFoundryBlockEntity foundry) {
            foundry.tryClaimOwner(player);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean canConnect(Level level, BlockPos pos, BlockState state, Direction direction) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            nudgeAdjacentPipes(level, pos);
            if (level.getBlockEntity(pos) instanceof MatterFoundryBlockEntity foundry) {
                foundry.requestNetworkRescan();
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block blockIn, BlockPos fromPos,
            boolean isMoving) {
        super.neighborChanged(state, level, pos, blockIn, fromPos, isMoving);
        if (level.isClientSide()) {
            return;
        }
        nudgeAdjacentPipes(level, pos);
        if (level.getBlockEntity(pos) instanceof MatterFoundryBlockEntity foundry) {
            foundry.requestNetworkRescan();
        }
    }

    private static void nudgeAdjacentPipes(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof MatterPipeBlock)) {
                continue;
            }
            level.sendBlockUpdated(neighborPos, neighborState, neighborState, 3);
            var prop = MatterPipeBlock.DIRECTIONS.get(direction.getOpposite());
            if (prop != null && neighborState.hasProperty(prop)) {
                level.setBlock(neighborPos, neighborState.setValue(prop, true), 3);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MatterFoundryBlockEntity foundry) {
            foundry.tryClaimOwner(player);
            foundry.openGui(player);
        }
        return InteractionResult.SUCCESS;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MatterFoundryBlockEntity foundry) {
                List<ItemStack> drops = new ArrayList<>();
                foundry.addDrops(drops);
                for (ItemStack drop : drops) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drop);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Same spawn offset as AE2 VibrationChamber: front face, jittered; smoke + green flame while active.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }

        Direction front = state.getValue(HorizontalDirectionalBlock.FACING);
        Direction top = Direction.UP;

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        final int westX = front.getStepY() * top.getStepZ() - front.getStepZ() * top.getStepY();
        final int westY = front.getStepZ() * top.getStepX() - front.getStepX() * top.getStepZ();
        final int westZ = front.getStepX() * top.getStepY() - front.getStepY() * top.getStepX();

        x += front.getStepX() * 0.6;
        y += front.getStepY() * 0.6;
        z += front.getStepZ() * 0.6;

        final double ox = random.nextDouble();
        final double oy = random.nextDouble() * 0.2;

        x += top.getStepX() * (-0.3 + oy);
        y += top.getStepY() * (-0.3 + oy);
        z += top.getStepZ() * (-0.3 + oy);

        x += westX * (0.3 * ox - 0.15);
        y += westY * (0.3 * ox - 0.15);
        z += westZ * (0.3 * ox - 0.15);

        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        level.addParticle(ModParticles.GREEN_FLAME.get(), x, y, z, 0.0, 0.0, 0.0);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatterFoundryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.MATTER_FOUNDRY_BE.get(),
                MatterFoundryBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> actual, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == actual ? (BlockEntityTicker<A>) ticker : null;
    }
}
