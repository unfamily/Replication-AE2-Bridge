package net.unfamily.repae2bridge.block.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.network.IMatterTanksSupplier;
import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.element.NetworkElement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.unfamily.repae2bridge.ae2.Ae2MatterTank;

/**
 * Local-network matter helpers for bridge block/cable. Does not cross-link distinct
 * Replication networks (each bridge only sees its own MatterNetwork holders).
 */
public final class BridgeMatterFederation {

    private BridgeMatterFederation() {
    }

    /** Holders on a single network, skipping bridge BEs and self pos. */
    public static void forEachLocalHolder(
            MatterNetwork network, BlockPos selfPos, Consumer<NetworkElement> consumer) {
        if (network == null) {
            return;
        }
        Set<NetworkElement> seen = new LinkedHashSet<>();
        for (NetworkElement element : network.getMatterStacksHolders()) {
            if (!seen.add(element)) {
                continue;
            }
            if (!element.getLevel().isLoaded(element.getPos())) {
                continue;
            }
            if (selfPos != null && element.getPos().equals(selfPos)) {
                continue;
            }
            BlockEntity tile = element.getLevel().getBlockEntity(element.getPos());
            if (tile instanceof RepAE2BridgeBlockEntity) {
                continue;
            }
            consumer.accept(element);
        }
    }

    public static List<NetworkElement> localHolders(MatterNetwork network, BlockPos selfPos) {
        List<NetworkElement> out = new ArrayList<>();
        forEachLocalHolder(network, selfPos, out::add);
        return out;
    }

    /**
     * Sum matter of {@code type} from physical tanks on holders (skips {@link Ae2MatterTank}).
     */
    public static long sumPhysicalMatter(Iterable<NetworkElement> holders, IMatterType type) {
        long amount = 0;
        for (NetworkElement element : holders) {
            BlockEntity tile = element.getLevel().getBlockEntity(element.getPos());
            if (!(tile instanceof IMatterTanksSupplier tanksSupplier)) {
                continue;
            }
            for (IMatterTank tank : tanksSupplier.getTanks()) {
                if (tank instanceof Ae2MatterTank) {
                    continue;
                }
                if (!tank.getMatter().isEmpty() && tank.getMatter().getMatterType().equals(type)) {
                    amount += (long) tank.getMatterAmount();
                }
            }
        }
        return amount;
    }

    /**
     * Drain {@code type} from physical tanks (skips {@link Ae2MatterTank}).
     */
    public static long drainPhysicalMatter(
            Iterable<NetworkElement> holders, IMatterType type, long amount, boolean simulate) {
        if (amount <= 0 || type == null) {
            return 0;
        }
        long remaining = amount;
        long drainedTotal = 0;
        for (NetworkElement element : holders) {
            if (remaining <= 0) {
                break;
            }
            BlockEntity tile = element.getLevel().getBlockEntity(element.getPos());
            if (!(tile instanceof IMatterTanksSupplier tanksSupplier)) {
                continue;
            }
            for (IMatterTank tank : tanksSupplier.getTanks()) {
                if (remaining <= 0) {
                    break;
                }
                if (tank instanceof Ae2MatterTank) {
                    continue;
                }
                var content = tank.getMatter();
                if (content.isEmpty() || !content.getMatterType().equals(type)) {
                    continue;
                }
                var drained = tank.drain(
                        new com.buuz135.replication.api.matter_fluid.MatterStack(type, remaining),
                        simulate
                                ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                long got = (long) Math.floor(drained.getAmount());
                if (got > 0) {
                    drainedTotal += got;
                    remaining -= got;
                }
            }
        }
        return drainedTotal;
    }

    /**
     * Fill physical tanks with {@code type} (skips {@link Ae2MatterTank}).
     */
    public static long fillPhysicalMatter(
            Iterable<NetworkElement> holders, IMatterType type, long amount, boolean simulate) {
        if (amount <= 0 || type == null) {
            return 0;
        }
        long remaining = amount;
        long filledTotal = 0;
        for (NetworkElement element : holders) {
            if (remaining <= 0) {
                break;
            }
            BlockEntity tile = element.getLevel().getBlockEntity(element.getPos());
            if (!(tile instanceof IMatterTanksSupplier tanksSupplier)) {
                continue;
            }
            for (IMatterTank tank : tanksSupplier.getTanks()) {
                if (remaining <= 0) {
                    break;
                }
                if (tank instanceof Ae2MatterTank) {
                    continue;
                }
                double filled = tank.fill(
                        new com.buuz135.replication.api.matter_fluid.MatterStack(type, remaining),
                        simulate
                                ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                long got = (long) Math.floor(filled);
                if (got > 0) {
                    filledTotal += got;
                    remaining -= got;
                }
            }
        }
        return filledTotal;
    }
}
