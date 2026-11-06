package net.unfamily.repae2bridge.parts;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Insert-only sink for vanilla Replicators delivering to a cable-bridge task source.
 * Immediately forwards stacks into AE2 (same role as the block bridge output inventory).
 */
public final class CableBridgeAe2ItemHandler implements IItemHandler {

    private final CableRepAe2BridgePart cable;

    CableBridgeAe2ItemHandler(CableRepAe2BridgePart cable) {
        this.cable = cable;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot != 0) {
            return stack;
        }
        if (simulate) {
            return cable.canReceiveItemFromReplicator(stack) ? ItemStack.EMPTY : stack;
        }
        return cable.receiveItemFromReplicator(stack.copy()) ? ItemStack.EMPTY : stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return !stack.isEmpty();
    }
}
