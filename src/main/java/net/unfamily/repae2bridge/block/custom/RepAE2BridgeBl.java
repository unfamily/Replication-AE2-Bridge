package net.unfamily.repae2bridge.block.custom;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.hrznstudio.titanium.block.BasicTileBlock;
import com.hrznstudio.titanium.block_network.INetworkDirectionalConnection;
import com.buuz135.replication.block.MatterPipeBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import net.unfamily.repae2bridge.item.MatterItem;


public class RepAE2BridgeBl extends BasicTileBlock<RepAE2BridgeBlockEntity> implements INetworkDirectionalConnection {
    public static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);
    public static final MapCodec<RepAE2BridgeBl> CODEC = simpleCodec(RepAE2BridgeBl::new);

    private static final Logger LOGGER = LogUtils.getLogger();

    // Add a property to display the connection status
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public RepAE2BridgeBl(Properties properties) {
        super(properties, RepAE2BridgeBlockEntity.class);
        // Set the default state with connection to false
        registerDefaultState(this.getStateDefinition().any().setValue(CONNECTED, false));
        
        // Register the block as connectable with Replication pipes
        // (Added in case the static module in ModBlocks is not executed in time)
        registerWithReplicationMod();
    }
    
    /**
     * Register this block as connectable by Replication pipes
     */
    private void registerWithReplicationMod() {
        try {
            // Check if the MatterPipeBlock class already exists
            if (MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS != null) {
                // Add a predicate for this specific block
                MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block -> block instanceof RepAE2BridgeBl);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to register block with Replication: " + e.getMessage());
        }
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONNECTED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends BasicTileBlock<RepAE2BridgeBlockEntity>> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntityType.BlockEntitySupplier<?> getTileEntityFactory() {
        return (pos, state) -> new RepAE2BridgeBlockEntity(pos, state);
    }
    
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, entity, stack);
        
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof RepAE2BridgeBlockEntity blockEntity) {
                blockEntity.handleNeighborChanged(pos);
            }
            updateNeighbors(level, pos, state);
        }
    }
    
    private void updateNeighbors(Level level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, 3);

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);

            // Adjacent bridges: notify with explicit fromPos so their anti-cascade guard works.
            // (MC 26 neighborChanged no longer carries BlockPos fromPos.)
            if (level.getBlockEntity(neighborPos) instanceof RepAE2BridgeBlockEntity otherBridge) {
                otherBridge.handleNeighborChanged(pos);
            } else {
                level.neighborChanged(neighborPos, state.getBlock(), null);
            }

            if (neighborState.getBlock() instanceof MatterPipeBlock) {
                level.sendBlockUpdated(neighborPos, neighborState, neighborState, 3);

                if (neighborState.hasProperty(MatterPipeBlock.DIRECTIONS.get(direction.getOpposite()))) {
                    level.setBlock(neighborPos, neighborState.setValue(
                            MatterPipeBlock.DIRECTIONS.get(direction.getOpposite()),
                            true), 3);
                }
            }
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block blockIn,
            @Nullable Orientation orientation, boolean isMoving) {
        super.neighborChanged(state, level, pos, blockIn, orientation, isMoving);

        if (level.isClientSide()) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof RepAE2BridgeBlockEntity blockEntity)) {
            return;
        }

        // MC 26 dropped fromPos; Orientation is often null. Prefer a resolved source when available,
        // otherwise do a soft refresh that never re-fans neighbor updates (avoids bridge↔bridge TPS storms).
        BlockPos fromPos = resolveNeighborSource(pos, orientation);
        if (fromPos != null) {
            blockEntity.handleNeighborChanged(fromPos);
            if (level.getBlockState(fromPos).getBlock() instanceof MatterPipeBlock) {
                BlockState pipeState = level.getBlockState(fromPos);
                level.sendBlockUpdated(fromPos, pipeState, pipeState, 3);
            }
        } else {
            blockEntity.handleNeighborChangedUnknownSource();
        }

        boolean isConnected = blockEntity.isActive() && blockEntity.getNetwork() != null;
        if (state.getValue(CONNECTED) != isConnected) {
            level.setBlock(pos, state.setValue(CONNECTED, isConnected), 3);
        }
    }

    /**
     * Best-effort source position for a neighbor update. Orientation.front points toward the
     * update direction when present; opposite is the neighbor that changed.
     */
    @Nullable
    private static BlockPos resolveNeighborSource(BlockPos pos, @Nullable Orientation orientation) {
        if (orientation == null) {
            return null;
        }
        return pos.relative(orientation.getFront().getOpposite());
    }
    
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        if (level.getBlockEntity(pos) instanceof RepAE2BridgeBlockEntity blockEntity) {
            dropInventory(level, pos, blockEntity);
            blockEntity.disconnectFromNetworks();
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this);
        }
    }

    /**
     * Drop real inventory contents only. Virtual matter items are not dropped.
     * The block item itself comes from the loot table ({@code data/.../loot_table/blocks/rep_ae2_bridge.json}).
     */
    private void dropInventory(Level level, BlockPos pos, RepAE2BridgeBlockEntity blockEntity) {
        var inventory = blockEntity.getOutput();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof MatterItem) {
                stack.setCount(0);
                continue;
            }
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
            stack.setCount(0);
        }
    }

    @Override
    public boolean canConnect(Level level, BlockPos pos, BlockState state, Direction direction) {
        return true;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        super.tick(state, level, pos, random);
        
        if (level.getBlockEntity(pos) instanceof RepAE2BridgeBlockEntity blockEntity) {
            if (blockEntity.needsNetworkRetry()) {
                blockEntity.onLoad();
            }
        }
    }
}
