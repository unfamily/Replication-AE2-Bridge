package net.unfamily.repae2bridge.ae2;

import org.jetbrains.annotations.NotNull;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.matter_fluid.MatterStack;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Local bridge buffer that absorbs matter from AE2 {@code pushPattern} inputHolder
 * so Replication can see and drain it. Not backed by AE2 cells.
 */
public final class CraftMatterTank implements IMatterTank {

    private static final double CAPACITY = 1_000_000_000_000d;

    private final IMatterType matterType;
    private double amount;

    public CraftMatterTank(IMatterType matterType) {
        this.matterType = matterType;
        this.amount = 0;
    }

    public IMatterType getMatterType() {
        return matterType;
    }

    public void setAmount(double amount) {
        this.amount = Math.max(0, amount);
    }

    @Override
    public @NotNull MatterStack getMatter() {
        if (matterType == null || matterType.equals(MatterType.EMPTY) || amount <= 0) {
            return MatterStack.EMPTY;
        }
        return new MatterStack(matterType, amount);
    }

    @Override
    public double getMatterAmount() {
        return amount;
    }

    @Override
    public double getCapacity() {
        return CAPACITY;
    }

    @Override
    public boolean isMatterValid(MatterStack stack) {
        return stack != null && !stack.isEmpty() && matterType.equals(stack.getMatterType());
    }

    @Override
    public double fill(MatterStack resource, IFluidHandler.FluidAction action) {
        if (!isMatterValid(resource)) {
            return 0;
        }
        double toFill = Math.min(resource.getAmount(), CAPACITY - amount);
        if (toFill <= 0) {
            return 0;
        }
        if (action.execute()) {
            amount += toFill;
        }
        return toFill;
    }

    @Override
    public @NotNull MatterStack drain(double maxDrain, IFluidHandler.FluidAction action) {
        if (maxDrain <= 0 || amount <= 0 || matterType.equals(MatterType.EMPTY)) {
            return MatterStack.EMPTY;
        }
        double drained = Math.min(maxDrain, amount);
        if (action.execute()) {
            amount -= drained;
        }
        return new MatterStack(matterType, drained);
    }

    @Override
    public @NotNull MatterStack drain(MatterStack resource, IFluidHandler.FluidAction action) {
        if (!isMatterValid(resource)) {
            return MatterStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }
}
