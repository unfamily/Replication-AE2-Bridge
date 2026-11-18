package net.unfamily.repae2bridge.block.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.element.NetworkElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.mixin.MatterNetworkAccessor;
import net.unfamily.repae2bridge.parts.CableRepAe2BridgePart;

/**
 * Discovers peer {@link MatterNetwork}s that share an AE2 grid through bridge
 * blocks/cables — without merging Titanium pipe graphs.
 */
public final class BridgeDeviceFederation {

    private BridgeDeviceFederation() {
    }

    /**
     * Local chip storages only — never the AE2-federated {@link MatterNetwork#getChipSuppliers()} list.
     * Use for matter/task binding that must stay on one Titanium network.
     */
    public static List<NetworkElement> localChipSuppliers(@Nullable MatterNetwork network) {
        if (network == null) {
            return List.of();
        }
        List<NetworkElement> raw =
                ((MatterNetworkAccessor) (Object) network).rep_ae2_bridge$getChipSuppliersRaw();
        return raw != null ? raw : List.of();
    }

    /**
     * Chip storages for AE2 pattern exposure: local + peer networks sharing the same AE2 grid
     * when {@link Config#federateDevicesAcrossAe2} is enabled (assembled Sable crafts often
     * keep Titanium islands while AE2 is continuous).
     */
    public static List<NetworkElement> chipsForAe2Patterns(@Nullable MatterNetwork network) {
        if (network == null) {
            return List.of();
        }
        if (!Config.federateDevicesAcrossAe2) {
            return localChipSuppliers(network);
        }
        java.util.LinkedHashMap<BlockPos, NetworkElement> merged = new java.util.LinkedHashMap<>();
        for (NetworkElement element : localChipSuppliers(network)) {
            if (element != null) {
                merged.put(element.getPos(), element);
            }
        }
        for (MatterNetwork peer : peerNetworks(network)) {
            for (NetworkElement element : localChipSuppliers(peer)) {
                if (element != null) {
                    merged.putIfAbsent(element.getPos(), element);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * Replication ChipStorage caches patterns in {@code cachedPatters}, filled by
     * {@link ChipStorageBlockEntity#cachePatterns()} on {@code setLevel} (often before NBT
     * inventory load) and on slot change. Without a refresh after load, AE2 sees empty
     * patterns until the player toggles a chip — call this periodically from bridges.
     */
    public static void refreshChipPatternCaches(@Nullable MatterNetwork network) {
        if (network == null) {
            return;
        }
        for (NetworkElement element : chipsForAe2Patterns(network)) {
            if (element == null) {
                continue;
            }
            Level level = element.getLevel();
            BlockPos pos = element.getPos();
            if (level == null || pos == null || !level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ChipStorageBlockEntity chipStorage) {
                chipStorage.cachePatterns();
            }
        }
    }

    /**
     * Content signature of completed patterns across federated chip storages.
     * Used to decide when AE2 crafting/storage providers must requestUpdate.
     */
    public static long patternSignature(@Nullable MatterNetwork network) {
        if (network == null) {
            return 0L;
        }
        long hash = 1L;
        for (NetworkElement element : chipsForAe2Patterns(network)) {
            if (element == null) {
                continue;
            }
            Level level = element.getLevel();
            BlockPos pos = element.getPos();
            if (level == null || pos == null || !level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof ChipStorageBlockEntity chipStorage)) {
                continue;
            }
            hash = 31L * hash + pos.asLong();
            for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                if (pattern == null) {
                    continue;
                }
                ItemStack stack = pattern.getStack();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                hash = 31L * hash + BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
                hash = 31L * hash + Float.hashCode(pattern.getCompletion());
            }
        }
        return hash;
    }

    /** Raw local replicators (not AE2-federated {@link MatterNetwork#getReplicators()}). */
    public static List<NetworkElement> localReplicators(@Nullable MatterNetwork network) {
        if (network == null) {
            return List.of();
        }
        List<NetworkElement> raw =
                ((MatterNetworkAccessor) (Object) network).rep_ae2_bridge$getReplicatorsRaw();
        return raw != null ? raw : List.of();
    }

    /** Distinct peer networks reachable via the same AE2 grid(s) as {@code source}. */
    public static List<MatterNetwork> peerNetworks(@Nullable MatterNetwork source) {
        if (source == null || !Config.federateDevicesAcrossAe2) {
            return List.of();
        }
        Set<IGrid> grids = collectAe2Grids(source);
        if (grids.isEmpty()) {
            return List.of();
        }

        String sourceId = source.getId();
        Set<MatterNetwork> peers = new LinkedHashSet<>();
        for (IGrid grid : grids) {
            collectPeersFromGrid(grid, sourceId, peers);
        }
        return new ArrayList<>(peers);
    }

    private static void collectPeersFromGrid(IGrid grid, String sourceId, Set<MatterNetwork> peers) {
        // Prefer node walk: getMachines(Part.class) can miss cable parts depending on AE2 indexing.
        for (IGridNode node : grid.getNodes()) {
            Object owner = node.getOwner();
            if (owner instanceof RepAE2BridgeBlockEntity bridge) {
                MatterNetwork network = bridge.getNetwork();
                if (network != null && !Objects.equals(network.getId(), sourceId)) {
                    peers.add(network);
                }
            } else if (owner instanceof CableRepAe2BridgePart cable) {
                MatterNetwork network = resolveCableNetwork(cable);
                if (network != null && !Objects.equals(network.getId(), sourceId)) {
                    peers.add(network);
                }
            }
        }
    }

    private static Set<IGrid> collectAe2Grids(MatterNetwork source) {
        Set<IGrid> grids = new LinkedHashSet<>();
        if (source.getGraph() == null) {
            return grids;
        }
        for (NetworkElement element : source.getGraph().getElements()) {
            Level level = element.getLevel();
            BlockPos pos = element.getPos();
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RepAE2BridgeBlockEntity bridge) {
                addGrid(grids, bridge.getActionableNode());
                continue;
            }
            if (be instanceof IPartHost host) {
                for (Direction side : Direction.values()) {
                    IPart part = host.getPart(side);
                    if (part instanceof CableRepAe2BridgePart cable) {
                        addCableGrid(grids, cable);
                    }
                }
            }
        }
        return grids;
    }

    private static void addCableGrid(Set<IGrid> grids, CableRepAe2BridgePart cable) {
        IGrid managed = cable.getMainNode().getGrid();
        if (managed != null) {
            grids.add(managed);
            return;
        }
        addGrid(grids, cable.getGridNode());
    }

    /**
     * Read-only peer lookup. Must NOT call {@code ensureMatterNetworkRegistered()} —
     * pattern/storage queries hit this path every AE2 tick and remounting Titanium here
     * thrashs the matter graph (broken validation even without Sable).
     */
    @Nullable
    private static MatterNetwork resolveCableNetwork(CableRepAe2BridgePart cable) {
        return cable.getMatterNetwork();
    }

    private static void addGrid(Set<IGrid> grids, @Nullable IGridNode node) {
        if (node == null) {
            return;
        }
        IGrid grid = node.getGrid();
        if (grid != null) {
            grids.add(grid);
        }
    }
}
