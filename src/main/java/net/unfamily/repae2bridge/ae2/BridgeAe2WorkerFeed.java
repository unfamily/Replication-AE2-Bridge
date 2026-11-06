package net.unfamily.repae2bridge.ae2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.unfamily.repae2bridge.block.entity.MatterAssemblerBlockEntity;
import net.unfamily.repae2bridge.block.entity.ReplicationUnitBlockEntity;

/**
 * Shared chamber / Matter Assembler assignment for block and cable bridges (AE2-internal bypass).
 */
public final class BridgeAe2WorkerFeed {

    private BridgeAe2WorkerFeed() {
    }

    public static boolean hasOnlineWorker(@Nullable IGrid grid) {
        if (grid == null) {
            return false;
        }
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit != null && unit.isFormed() && unit.isChamberLeader() && unit.getMainNode().isOnline()) {
                return true;
            }
        }
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler != null && assembler.getMainNode().isOnline()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Feeds pending AE2-internal units (keyed by item) to eligible chambers/MAs.
     *
     * @return units still pending for {@code item}
     */
    public static int feedItem(
            @Nullable IGrid grid,
            BlockPos bridgePos,
            ItemStack patternStack,
            int count,
            AtomicInteger feedCursor) {
        if (grid == null || bridgePos == null || patternStack == null || patternStack.isEmpty() || count <= 0) {
            return count;
        }
        Item item = patternStack.getItem();

        List<ReplicationUnitBlockEntity> chambers = new ArrayList<>();
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit == null || !unit.isFormed() || !unit.isChamberLeader() || !unit.getMainNode().isOnline()) {
                continue;
            }
            if (unit.isChamberWorkerReady()) {
                chambers.add(unit);
            } else if (!unit.isIdle()
                    && unit.getReplicationTaskId() == null
                    && bridgePos.equals(unit.getBridgePos())
                    && unit.getCraftingStack().getItem().equals(item)) {
                chambers.add(unit);
            }
        }
        List<MatterAssemblerBlockEntity> assemblers = new ArrayList<>();
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler == null || !assembler.getMainNode().isOnline()) {
                continue;
            }
            if (assembler.isIdle()) {
                assemblers.add(assembler);
            } else if (assembler.getReplicationTaskId() == null
                    && bridgePos.equals(assembler.getBridgePos())
                    && assembler.getCraftingStack().getItem().equals(item)) {
                assemblers.add(assembler);
            }
        }

        int n = chambers.size() + assemblers.size();
        if (n == 0) {
            return count;
        }

        int remaining = count;
        if (remaining < n) {
            while (remaining > 0) {
                int idx = Math.floorMod(feedCursor.getAndIncrement(), n);
                boolean ok;
                if (idx < chambers.size()) {
                    ok = assignChamber(chambers.get(idx), patternStack, 1, bridgePos);
                } else {
                    ok = assignAssembler(assemblers.get(idx - chambers.size()), patternStack, 1, bridgePos);
                }
                if (ok) {
                    remaining--;
                    continue;
                }
                boolean any = false;
                for (ReplicationUnitBlockEntity unit : chambers) {
                    if (assignChamber(unit, patternStack, 1, bridgePos)) {
                        remaining--;
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    for (MatterAssemblerBlockEntity assembler : assemblers) {
                        if (assignAssembler(assembler, patternStack, 1, bridgePos)) {
                            remaining--;
                            any = true;
                            break;
                        }
                    }
                }
                if (!any) {
                    break;
                }
            }
            return remaining;
        }

        int i = 0;
        for (ReplicationUnitBlockEntity unit : chambers) {
            int workersLeft = n - i;
            int batch = (remaining + workersLeft - 1) / workersLeft;
            if (batch > 0 && assignChamber(unit, patternStack, batch, bridgePos)) {
                remaining -= batch;
            }
            i++;
            if (remaining <= 0) {
                return 0;
            }
        }
        for (MatterAssemblerBlockEntity assembler : assemblers) {
            int workersLeft = n - i;
            int batch = (remaining + workersLeft - 1) / workersLeft;
            if (batch > 0 && assignAssembler(assembler, patternStack, batch, bridgePos)) {
                remaining -= batch;
            }
            i++;
            if (remaining <= 0) {
                return 0;
            }
        }
        return remaining;
    }

    /** Drain the whole pending map through {@link #feedItem}. */
    public static void feedAll(
            @Nullable IGrid grid,
            BlockPos bridgePos,
            Map<Item, Integer> pending,
            Map<Item, ItemStack> patternByItem,
            AtomicInteger feedCursor) {
        if (grid == null || pending == null || pending.isEmpty()) {
            return;
        }
        for (Item item : new ArrayList<>(pending.keySet())) {
            Integer boxed = pending.get(item);
            if (boxed == null || boxed <= 0) {
                pending.remove(item);
                continue;
            }
            ItemStack pattern = patternByItem != null ? patternByItem.get(item) : null;
            if (pattern == null || pattern.isEmpty()) {
                pattern = new ItemStack(item);
            }
            int left = feedItem(grid, bridgePos, pattern, boxed, feedCursor);
            if (left <= 0) {
                pending.remove(item);
            } else {
                pending.put(item, left);
            }
        }
    }

    private static boolean assignChamber(
            ReplicationUnitBlockEntity unit, ItemStack patternStack, int batch, BlockPos bridgePos) {
        if (unit.isChamberWorkerReady()) {
            return unit.tryStartJob(patternStack, batch, bridgePos, null);
        }
        int before = unit.getRemainingCount();
        if (!unit.extendAe2InternalJob(patternStack, batch, bridgePos)) {
            return false;
        }
        return unit.getRemainingCount() >= before;
    }

    private static boolean assignAssembler(
            MatterAssemblerBlockEntity assembler, ItemStack patternStack, int batch, BlockPos bridgePos) {
        if (assembler.isIdle()) {
            return assembler.tryStartJob(patternStack, batch, bridgePos, null);
        }
        int before = assembler.getRemainingCount();
        if (!assembler.extendAe2InternalJob(patternStack, batch, bridgePos)) {
            return false;
        }
        return assembler.getRemainingCount() >= before;
    }
}
