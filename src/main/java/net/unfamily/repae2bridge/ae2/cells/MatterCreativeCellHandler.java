package net.unfamily.repae2bridge.ae2.cells;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.items.storage.StorageCellTooltipComponent;

import net.unfamily.repae2bridge.item.MatterCreativeCellItem;

public final class MatterCreativeCellHandler implements ICellHandler {

    public static final MatterCreativeCellHandler INSTANCE = new MatterCreativeCellHandler();

    private MatterCreativeCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack is) {
        return !is.isEmpty() && is.getItem() instanceof MatterCreativeCellItem;
    }

    @Override
    public StorageCell getCellInventory(ItemStack is, ISaveProvider container) {
        if (isCell(is)) {
            return new MatterCreativeCellInventory(is);
        }
        return null;
    }

    public Optional<TooltipComponent> getTooltipImage(ItemStack is) {
        if (!isCell(is)) {
            return Optional.empty();
        }
        return Optional.of(new StorageCellTooltipComponent(List.of(), Collections.emptyList(), false, false));
    }
}
