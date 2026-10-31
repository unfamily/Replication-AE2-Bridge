package net.unfamily.repae2bridge.ae2.matter;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.api.matter_fluid.IMatterHandler;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.matter_fluid.MatterStack;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Exposes multiple {@link IMatterTank}s as a single {@link IMatterHandler} for AE2 buses.
 */
public final class CompositeMatterHandler implements IMatterHandler {

    private final List<IMatterTank> tanks;

    public CompositeMatterHandler(List<? extends IMatterTank> tanks) {
        this.tanks = new ArrayList<>(tanks);
    }

    /**
     * Always returns a non-null handler so block capabilities stay visible to AE2 buses
     * even before a Foundry recipe matches (0 tanks → inserts rejected until recipe tanks appear).
     */
    public static IMatterHandler of(@Nullable List<? extends IMatterTank> tanks) {
        if (tanks == null || tanks.isEmpty()) {
            return new CompositeMatterHandler(List.of());
        }
        if (tanks.size() == 1 && tanks.get(0) instanceof IMatterHandler handler) {
            return handler;
        }
        return new CompositeMatterHandler(tanks);
    }

    @Override
    public int getTanks() {
        return tanks.size();
    }

    @Override
    public MatterStack getMatterInTank(int tank) {
        if (tank < 0 || tank >= tanks.size()) {
            return MatterStack.EMPTY;
        }
        MatterStack stack = tanks.get(tank).getMatter();
        return stack == null ? MatterStack.EMPTY : stack;
    }

    @Override
    public double getTankCapacity(int tank) {
        if (tank < 0 || tank >= tanks.size()) {
            return 0;
        }
        return tanks.get(tank).getCapacity();
    }

    @Override
    public boolean isMatterValid(int tank, MatterStack stack) {
        if (tank < 0 || tank >= tanks.size()) {
            return false;
        }
        return tanks.get(tank).isMatterValid(stack);
    }

    @Override
    public double fill(MatterStack resource, IFluidHandler.FluidAction action) {
        if (resource == null || resource.isEmpty()) {
            return 0;
        }
        double remaining = resource.getAmount();
        double filledTotal = 0;
        for (IMatterTank tank : tanks) {
            if (remaining <= 0) {
                break;
            }
            if (!tank.isMatterValid(resource)) {
                continue;
            }
            MatterStack slice = new MatterStack(resource.getMatterType(), remaining);
            double filled = tank.fill(slice, action);
            if (filled <= 0) {
                continue;
            }
            filledTotal += filled;
            remaining -= filled;
        }
        return filledTotal;
    }

    @Override
    public MatterStack drain(MatterStack resource, IFluidHandler.FluidAction action) {
        if (resource == null || resource.isEmpty()) {
            return MatterStack.EMPTY;
        }
        double remaining = resource.getAmount();
        double drainedTotal = 0;
        var type = resource.getMatterType();
        for (IMatterTank tank : tanks) {
            if (remaining <= 0) {
                break;
            }
            MatterStack inTank = tank.getMatter();
            if (inTank == null || inTank.isEmpty() || !inTank.getMatterType().equals(type)) {
                continue;
            }
            MatterStack drained = tank.drain(new MatterStack(type, remaining), action);
            if (drained == null || drained.isEmpty()) {
                continue;
            }
            drainedTotal += drained.getAmount();
            remaining -= drained.getAmount();
        }
        return drainedTotal <= 0 ? MatterStack.EMPTY : new MatterStack(type, drainedTotal);
    }

    @Override
    public MatterStack drain(double maxDrain, IFluidHandler.FluidAction action) {
        if (maxDrain <= 0) {
            return MatterStack.EMPTY;
        }
        for (IMatterTank tank : tanks) {
            MatterStack inTank = tank.getMatter();
            if (inTank == null || inTank.isEmpty()) {
                continue;
            }
            return drain(new MatterStack(inTank.getMatterType(), maxDrain), action);
        }
        return MatterStack.EMPTY;
    }
}
