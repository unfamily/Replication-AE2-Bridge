package net.unfamily.repae2bridge.ae2.matter;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.buuz135.replication.api.matter_fluid.IMatterHandler;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.matter_fluid.MatterStack;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import net.unfamily.repae2bridge.block.entity.MatterFoundryBlockEntity;

/**
 * Live Foundry matter capability: {@link #fill} writes the buffer directly so AE2 can push
 * matter before crafting items land (tank list may still be empty without a matched recipe).
 */
public final class FoundryMatterHandler implements IMatterHandler {

    private final MatterFoundryBlockEntity foundry;

    public FoundryMatterHandler(MatterFoundryBlockEntity foundry) {
        this.foundry = foundry;
    }

    private List<? extends IMatterTank> tanks() {
        return foundry.getTanks();
    }

    @Override
    public int getTanks() {
        // Keep at least one index so buses probe isMatterValid/fill even with an empty buffer.
        return Math.max(1, tanks().size());
    }

    @Override
    public @NotNull MatterStack getMatterInTank(int tank) {
        List<? extends IMatterTank> tanks = tanks();
        if (tank < 0 || tank >= tanks.size()) {
            return MatterStack.EMPTY;
        }
        MatterStack stack = tanks.get(tank).getMatter();
        return stack == null ? MatterStack.EMPTY : stack;
    }

    @Override
    public double getTankCapacity(int tank) {
        List<? extends IMatterTank> tanks = tanks();
        if (tank < 0 || tank >= tanks.size()) {
            // Open capacity advertised while waiting for a recipe / first insert.
            return foundry.getMatterBuffer().openInsertCap();
        }
        return tanks.get(tank).getCapacity();
    }

    @Override
    public boolean isMatterValid(int tank, @NotNull MatterStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return foundry.getMatterBuffer().roomFor(foundry.getCachedRecipe(), stack.getMatterType()) > 0;
    }

    @Override
    public double fill(MatterStack resource, IFluidHandler.FluidAction action) {
        if (resource == null || resource.isEmpty()) {
            return 0;
        }
        double inserted = foundry.getMatterBuffer().insert(
                resource.getMatterType(),
                resource.getAmount(),
                foundry.getCachedRecipe(),
                !action.execute());
        if (action.execute() && inserted > 0) {
            foundry.onExternalMatterInserted();
        }
        return inserted;
    }

    @Override
    public @NotNull MatterStack drain(MatterStack resource, IFluidHandler.FluidAction action) {
        // Craft buffer is not drained externally (persists across crafts / cancel).
        return MatterStack.EMPTY;
    }

    @Override
    public @NotNull MatterStack drain(double maxDrain, IFluidHandler.FluidAction action) {
        return MatterStack.EMPTY;
    }
}
