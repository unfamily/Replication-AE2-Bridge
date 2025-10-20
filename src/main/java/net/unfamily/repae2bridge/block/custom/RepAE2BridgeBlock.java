package net.unfamily.repae2bridge.block.custom;

import com.buuz135.replication.Replication;
import com.hrznstudio.titanium.block.BasicTileBlock;
import com.hrznstudio.titanium.block_network.INetworkDirectionalConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;

import javax.annotation.Nonnull;

/**
 * Blocco bridge che collega la rete AE2 con la rete di Replication.
 * Non ha rotazione perché è identico da tutti i lati.
 * Implementa INetworkDirectionalConnection per connettersi alla rete di Replication.
 */
public class RepAE2BridgeBlock extends BasicTileBlock<RepAE2BridgeBlockEntity> implements INetworkDirectionalConnection {

    // Shape del blocco - un cubo completo
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public RepAE2BridgeBlock() {
        super("rep_ae2_bridge", 
              BlockBehaviour.Properties.of()
                  .strength(0.3F, 0.3F)  // Molto facile da rompere
                  .sound(SoundType.COPPER)  // Suono del rame
                  .noOcclusion(),  // Mantiene la trasparenza
              RepAE2BridgeBlockEntity.class);
        setItemGroup(Replication.TAB);
    }

    @Override
    public BlockEntityType.BlockEntitySupplier<?> getTileEntityFactory() {
        return RepAE2BridgeBlockEntity::new;
    }

    @Nonnull
    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter world, @Nonnull BlockPos pos, @Nonnull CollisionContext selectionContext) {
        return SHAPE;
    }

    @Nonnull
    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter world, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return SHAPE;
    }

    /**
     * Definisce da quali lati il blocco può connettersi alla rete di Replication.
     * Il bridge può connettersi da tutti i lati.
     */
    @Override
    public boolean canConnect(BlockState state, Direction direction) {
        // Il bridge può connettersi da tutti i lati
        return true;
    }

    /**
     * Gestisce la rimozione del blocco, droppando se stesso e il contenuto dell'inventario
     */
    @Override
    public void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState newState, boolean moving) {
        // Se il blocco è stato rimosso o sostituito
        if (!state.is(newState.getBlock())) {
            // Droppa il blocco stesso
            if (!level.isClientSide) {
                ItemStack itemStack = new ItemStack(this);
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), itemStack);
            }
        }
        
        super.onRemove(state, level, pos, newState, moving);
    }

    /**
     * Permette di raccogliere il blocco con qualsiasi strumento
     */
    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return true;
    }
}

