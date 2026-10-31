package net.unfamily.repae2bridge.ae2;

import org.jetbrains.annotations.NotNull;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.matter_fluid.MatterStack;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;

/**
 * Virtual Replication matter tank backed by AE2 {@link ReplicationMatterKey} storage on the bridge grid.
 */
public final class Ae2MatterTank implements IMatterTank {

    /** Soft cap so Replication UI math stays sane with creative cells. */
    private static final double DISPLAY_CAP = 1_000_000_000_000d;

    private final RepAE2BridgeBlockEntity bridge;
    private final IMatterType matterType;

    public Ae2MatterTank(RepAE2BridgeBlockEntity bridge, IMatterType matterType) {
        this.bridge = bridge;
        this.matterType = matterType;
    }

    public IMatterType getMatterType() {
        return matterType;
    }

    @Override
    public @NotNull MatterStack getMatter() {
        if (matterType == null || matterType.equals(MatterType.EMPTY)) {
            return MatterStack.EMPTY;
        }
        long amount = bridge.getAe2MatterAmount(matterType);
        if (amount <= 0) {
            return MatterStack.EMPTY;
        }
        return new MatterStack(matterType, Math.min((double) amount, DISPLAY_CAP));
    }

    @Override
    public double getMatterAmount() {
        return getMatter().getAmount();
    }

    @Override
    public double getCapacity() {
        long stored = bridge.getAe2MatterAmount(matterType);
        long free = bridge.getAe2MatterFreeSpace(matterType);
        double capacity = (double) stored + (double) free;
        if (capacity <= 0) {
            return DISPLAY_CAP;
        }
        return Math.min(capacity, DISPLAY_CAP);
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
        long amount = (long) Math.floor(resource.getAmount());
        if (amount <= 0) {
            return 0;
        }
        long inserted = bridge.insertAe2Matter(matterType, amount, !action.execute());
        if (inserted > 0 && action.execute()) {
            bridge.notifyAe2MatterChanged(matterType);
        }
        return inserted;
    }

    @Override
    public @NotNull MatterStack drain(double maxDrain, IFluidHandler.FluidAction action) {
        if (maxDrain <= 0 || matterType.equals(MatterType.EMPTY)) {
            return MatterStack.EMPTY;
        }
        long amount = (long) Math.floor(maxDrain);
        if (amount <= 0) {
            return MatterStack.EMPTY;
        }
        long extracted = bridge.extractAe2Matter(matterType, amount, !action.execute());
        if (extracted <= 0) {
            return MatterStack.EMPTY;
        }
        if (action.execute()) {
            bridge.notifyAe2MatterChanged(matterType);
        }
        return new MatterStack(matterType, extracted);
    }

    @Override
    public @NotNull MatterStack drain(MatterStack resource, IFluidHandler.FluidAction action) {
        if (!isMatterValid(resource)) {
            return MatterStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }
}
