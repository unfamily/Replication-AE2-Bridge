package net.unfamily.repae2bridge.ae2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.network.MatterNetwork;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.block.entity.BridgeDeviceFederation;
import net.unfamily.repae2bridge.block.entity.MatterAssemblerBlockEntity;
import net.unfamily.repae2bridge.block.entity.ReplicationUnitBlockEntity;

/**
 * Assigns pending Replication-network tasks (terminal or AE2-sourced) to idle AE2 chambers / MAs.
 * Shared by block and cable bridges — peer-aware like {@code findTaskForReplicator} federation,
 * with a single assigner per AE2 grid per game tick.
 */
public final class BridgeReplicationTaskAssign {

    private static final ThreadLocal<Boolean> SEARCHING_PEERS = ThreadLocal.withInitial(() -> false);

    /** Per-grid last tick that already ran assign (identity of IGrid). */
    private static final WeakHashMap<IGrid, Long> LAST_ASSIGN_TICK = new WeakHashMap<>();

    private BridgeReplicationTaskAssign() {
    }

    public static void assign(
            @Nullable MatterNetwork network,
            @Nullable IGrid grid,
            @Nullable Level level,
            @Nullable BlockPos bridgePos) {
        if (network == null || grid == null || level == null || level.isClientSide() || bridgePos == null) {
            return;
        }

        // One assigner per AE2 grid per tick — avoids block+cable race on the same craft
        long gameTime = level.getGameTime();
        synchronized (LAST_ASSIGN_TICK) {
            Long last = LAST_ASSIGN_TICK.get(grid);
            if (last != null && last == gameTime) {
                return;
            }
            LAST_ASSIGN_TICK.put(grid, gameTime);
        }

        List<ReplicationUnitBlockEntity> idleChambers = new ArrayList<>();
        int onlineChamberLeaders = 0;
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit == null || !unit.isFormed() || !unit.isChamberLeader() || !unit.getMainNode().isOnline()) {
                continue;
            }
            onlineChamberLeaders++;
            if (unit.isIdle()) {
                idleChambers.add(unit);
            }
        }

        List<MatterAssemblerBlockEntity> onlineAssemblers = new ArrayList<>();
        List<MatterAssemblerBlockEntity> idle = new ArrayList<>();
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler == null || !assembler.getMainNode().isOnline()) {
                continue;
            }
            onlineAssemblers.add(assembler);
            if (assembler.isIdle()) {
                idle.add(assembler);
            }
        }
        if (idle.isEmpty() && idleChambers.isEmpty()) {
            return;
        }
        idle.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        int onlineWorkerCount = onlineAssemblers.size() + onlineChamberLeaders;

        Map<String, Integer> chamberClaimed = new HashMap<>();
        for (int ci = 0; ci < idleChambers.size(); ci++) {
            ReplicationUnitBlockEntity chamber = idleChambers.get(ci);
            TaskPick pick = findTask(network, chamber.getBlockPos(), onlineWorkerCount);
            if (pick == null) {
                continue;
            }
            IReplicationTask task = pick.task();
            MatterNetwork taskNetwork = pick.network();
            ItemStack pattern = task.getReplicatingStack();
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            String taskKey = task.getUuid().toString();
            int left = task.getTotalAmount() - task.getCurrentAmount() - chamberClaimed.getOrDefault(taskKey, 0);
            if (left <= 0) {
                continue;
            }
            int chambersLeft = idleChambers.size() - ci;
            int batch = Math.max(1, (left + chambersLeft - 1) / chambersLeft);
            if (!chamber.tryStartJob(pattern, batch, bridgePos, task.getUuid())) {
                continue;
            }
            chamberClaimed.merge(taskKey, batch, Integer::sum);
            task.acceptReplicator(chamber.getBlockPos());
            task.storeMatterStacksFor(level, chamber.getBlockPos(), taskNetwork);
            if (!task.getStoredMatterStack().containsKey(chamber.getBlockPos().asLong())) {
                task.getReplicatorsOnTask().remove(chamber.getBlockPos().asLong());
                chamberClaimed.merge(taskKey, -batch, Integer::sum);
                chamber.abortJob();
            }
        }

        for (MatterAssemblerBlockEntity assembler : idle) {
            TaskPick pick = findTask(network, assembler.getBlockPos(), onlineWorkerCount);
            if (pick == null) {
                continue;
            }
            IReplicationTask task = pick.task();
            MatterNetwork taskNetwork = pick.network();
            ItemStack pattern = task.getReplicatingStack();
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            if (!assembler.tryStartJob(pattern, 1, bridgePos, task.getUuid())) {
                continue;
            }
            task.acceptReplicator(assembler.getBlockPos());
            task.storeMatterStacksFor(level, assembler.getBlockPos(), taskNetwork);
            if (!task.getStoredMatterStack().containsKey(assembler.getBlockPos().asLong())) {
                task.getReplicatorsOnTask().remove(assembler.getBlockPos().asLong());
                assembler.abortJob();
            }
        }
    }

    private static int computeBudget(MatterNetwork taskNetwork, int onlineAssemblerCount) {
        // Use raw local replicators of the task's network — not AE2-federated getReplicators()
        int workers = BridgeDeviceFederation.localReplicators(taskNetwork).size()
                + Math.max(0, onlineAssemblerCount);
        long multipleTasks = 0;
        for (IReplicationTask pending : taskNetwork.getTaskManager().getPendingTasks().values()) {
            if (pending.getMode() == IReplicationTask.Mode.MULTIPLE) {
                multipleTasks++;
            }
        }
        long reservedForSingle = taskNetwork.getTaskManager().getPendingTasks().size() - multipleTasks;
        workers -= (int) reservedForSingle;
        if (multipleTasks == 0) {
            multipleTasks = 1;
        }
        return Math.max(1, (int) Math.ceil(workers / (double) multipleTasks));
    }

    @Nullable
    private static TaskPick findTask(MatterNetwork network, BlockPos workerPos, int onlineWorkerCount) {
        TaskPick local = findTaskOnNetwork(network, workerPos, onlineWorkerCount);
        if (local != null) {
            return local;
        }
        if (!Config.federateDevicesAcrossAe2 || SEARCHING_PEERS.get()) {
            return null;
        }
        SEARCHING_PEERS.set(true);
        try {
            for (MatterNetwork peer : BridgeDeviceFederation.peerNetworks(network)) {
                TaskPick peerPick = findTaskOnNetwork(peer, workerPos, onlineWorkerCount);
                if (peerPick != null) {
                    return peerPick;
                }
            }
        } finally {
            SEARCHING_PEERS.set(false);
        }
        return null;
    }

    @Nullable
    private static TaskPick findTaskOnNetwork(
            MatterNetwork network, BlockPos workerPos, int onlineWorkerCount) {
        int maxWorkersPerMultipleTask = computeBudget(network, onlineWorkerCount);
        for (IReplicationTask task : network.getTaskManager().getPendingTasks().values()) {
            if (task.canAcceptReplicator(workerPos, maxWorkersPerMultipleTask)) {
                return new TaskPick(task, network);
            }
        }
        return null;
    }

    private record TaskPick(IReplicationTask task, MatterNetwork network) {
    }
}
