package net.unfamily.repae2bridge.block.entity;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.BaseInternalInventory;

/**
 * Thin AE2 {@link appeng.api.inventories.InternalInventory} view over a {@link MatterFoundryBlockEntity.FoundryItemHandler}.
 */
public class FoundrySlotInventory extends BaseInternalInventory {

    private final MatterFoundryBlockEntity.FoundryItemHandler handler;

    public FoundrySlotInventory(MatterFoundryBlockEntity.FoundryItemHandler handler) {
        this.handler = handler;
    }

    @Override
    public int size() {
        return handler.size();
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        return handler.getStack(slotIndex);
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        handler.setStack(slotIndex, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack);
    }
}
