package net.unfamily.repae2bridge.block.entity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import appeng.api.inventories.BaseInternalInventory;

/**
 * AE2 {@link appeng.api.inventories.InternalInventory} view over a {@link ItemStackHandler}.
 */
public final class FoundrySlotInventory extends BaseInternalInventory {

    private final ItemStackHandler handler;

    public FoundrySlotInventory(ItemStackHandler handler) {
        this.handler = handler;
    }

    @Override
    public int size() {
        return handler.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        return handler.getStackInSlot(slotIndex);
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        handler.setStackInSlot(slotIndex, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack);
    }
}
