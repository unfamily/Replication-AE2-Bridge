package net.unfamily.repae2bridge.item;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;

import net.unfamily.repae2bridge.ae2.cells.MatterCreativeCellHandler;

/**
 * Creative matter cell: exposes every registered matter type in infinite quantity.
 */
public class MatterCreativeCellItem extends AEBaseItem implements ICellWorkbenchItem {

    public MatterCreativeCellItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isEditable(ItemStack is) {
        return false;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines,
            TooltipFlag tooltipFlags) {
        lines.add(Component.translatable("item.rep_ae2_bridge.matter_storage_cell_creative.tooltip"));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return MatterCreativeCellHandler.INSTANCE.getTooltipImage(stack);
    }
}
