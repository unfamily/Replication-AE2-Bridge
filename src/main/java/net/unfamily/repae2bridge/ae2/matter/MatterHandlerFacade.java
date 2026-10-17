package net.unfamily.repae2bridge.ae2.matter;

import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.api.matter_fluid.IMatterHandler;
import com.buuz135.replication.api.matter_fluid.MatterStack;
import com.google.common.primitives.Ints;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.storage.ExternalStorageFacade;

import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;

/**
 * Adapts Replication {@link IMatterHandler} to AE2 {@link ExternalStorageFacade}.
 */
public final class MatterHandlerFacade extends ExternalStorageFacade {

    private static final long MAX_REPORTED_AMOUNT = 1L << 42;

    private final IMatterHandler handler;

    public MatterHandlerFacade(IMatterHandler handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    @Override
    public int getSlots() {
        return handler.getTanks();
    }

    @Nullable
    @Override
    public GenericStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= handler.getTanks()) {
            return null;
        }
        MatterStack matter = handler.getMatterInTank(slot);
        if (matter == null || matter.isEmpty()) {
            return null;
        }
        ReplicationMatterKey key = ReplicationMatterKey.of(matter.getMatterType());
        if (key == null) {
            return null;
        }
        long amount = Math.max(0L, (long) Math.floor(matter.getAmount()));
        return amount <= 0 ? null : new GenericStack(key, amount);
    }

    @Override
    public AEKeyType getKeyType() {
        return MatterKeyType.INSTANCE;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (int i = 0; i < handler.getTanks(); i++) {
            GenericStack stack = getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            if (extractableOnly && extractExternal(stack.what(), 1, Actionable.SIMULATE) <= 0) {
                continue;
            }
            out.add(stack.what(), Math.min(stack.amount(), MAX_REPORTED_AMOUNT));
        }
    }

    @Override
    protected int insertExternal(AEKey what, int amount, Actionable mode) {
        if (!(what instanceof ReplicationMatterKey matterKey) || amount <= 0) {
            return 0;
        }
        var type = matterKey.resolveMatterType();
        if (type == null) {
            return 0;
        }
        double filled = handler.fill(new MatterStack(type, amount), toFluidAction(mode));
        return Ints.saturatedCast((long) Math.floor(filled));
    }

    @Override
    protected int extractExternal(AEKey what, int amount, Actionable mode) {
        if (!(what instanceof ReplicationMatterKey matterKey) || amount <= 0) {
            return 0;
        }
        var type = matterKey.resolveMatterType();
        if (type == null) {
            return 0;
        }
        MatterStack drained = handler.drain(new MatterStack(type, amount), toFluidAction(mode));
        if (drained == null || drained.isEmpty()) {
            return 0;
        }
        return Ints.saturatedCast((long) Math.floor(drained.getAmount()));
    }

    @Override
    public boolean containsAnyFuzzy(Set<AEKey> keys) {
        for (int i = 0; i < handler.getTanks(); i++) {
            MatterStack matter = handler.getMatterInTank(i);
            if (matter == null || matter.isEmpty()) {
                continue;
            }
            ReplicationMatterKey key = ReplicationMatterKey.of(matter.getMatterType());
            if (key != null && keys.contains(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Component getDescription() {
        return MatterKeyType.INSTANCE.getDescription();
    }

    public long insert(AEKey what, long amount, Actionable mode) {
        return insertExternal(what, Ints.saturatedCast(amount), mode);
    }

    public long extract(AEKey what, long amount, Actionable mode) {
        return extractExternal(what, Ints.saturatedCast(amount), mode);
    }

    static IFluidHandler.FluidAction toFluidAction(Actionable mode) {
        return mode == Actionable.SIMULATE ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
    }
}
