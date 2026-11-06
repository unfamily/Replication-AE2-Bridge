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

import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.element.NetworkElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
     * Bridge AE2 pattern paths must bind tasks to chips on this Titanium network alone.
     */
    public static List<NetworkElement> localChipSuppliers(@Nullable MatterNetwork network) {
        if (network == null) {
            return List.of();
        }
        List<NetworkElement> raw =
                ((MatterNetworkAccessor) (Object) network).rep_ae2_bridge$getChipSuppliersRaw();
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

    @Nullable
    private static MatterNetwork resolveCableNetwork(CableRepAe2BridgePart cable) {
        MatterNetwork network = cable.getMatterNetwork();
        if (network != null) {
            return network;
        }
        // Part may be online on AE2 before the Titanium element is registered.
        cable.ensureMatterNetworkRegistered();
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
