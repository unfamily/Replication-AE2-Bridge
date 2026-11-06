package net.unfamily.repae2bridge.ae2;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Crafting surface used by chamber / Matter Assembler jobs for both block and cable bridges.
 */
public interface IAe2BridgeCraftHost {

    Level getLevel();

    BlockPos getBlockPos();

    boolean drainMatterForAe2Internal(ItemStack patternStack, int count);

    default boolean drainOneMatterForAe2Internal(ItemStack patternStack) {
        return drainMatterForAe2Internal(patternStack, 1);
    }

    int insertCraftedToAe2(ItemStack stack, int count);

    boolean receiveItemFromReplicator(ItemStack stack);

    boolean deliverMatterAssemblerReplicationUnit(ItemStack stack, UUID taskId, BlockPos assemblerPos);

    boolean hasPendingReplicationTask(UUID taskId);

    boolean ensureMatterReservedForWorker(UUID taskId, BlockPos workerPos);

    /** Cancel AE2-linked chamber/MA / pending when a crafting CPU is cancelled. */
    default void cancelAe2LinkedReplicationWork() {
    }
}
