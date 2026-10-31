package net.unfamily.repae2bridge.compat.rei;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.InputIngredient;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;

import net.minecraft.world.item.ItemStack;

import net.unfamily.repae2bridge.menu.MatterFoundryMenu;

/**
 * REI + transfer: Foundry item inputs only (skips matter display entries).
 */
public final class MatterFoundryReiTransferHandler implements SimpleTransferHandler {

    @Override
    public ApplicabilityResult checkApplicable(Context context) {
        if (!(context.getMenu() instanceof MatterFoundryMenu)) {
            return ApplicabilityResult.createNotApplicable();
        }
        if (!context.getDisplay().getCategoryIdentifier().equals(MatterFoundryReiCategory.CATEGORY_ID)) {
            return ApplicabilityResult.createNotApplicable();
        }
        return ApplicabilityResult.createApplicable();
    }

    @Override
    public Iterable<SlotAccessor> getInputSlots(Context context) {
        MatterFoundryMenu menu = (MatterFoundryMenu) context.getMenu();
        return menu.getFoundryInputSlots().stream().map(SlotAccessor::fromSlot).collect(Collectors.toList());
    }

    @Override
    public Iterable<SlotAccessor> getInventorySlots(Context context) {
        MatterFoundryMenu menu = (MatterFoundryMenu) context.getMenu();
        return menu.getPlayerSlotsForTransfer().stream().map(SlotAccessor::fromSlot).collect(Collectors.toList());
    }

    @Override
    public List<InputIngredient<ItemStack>> getInputsIndexed(Context context) {
        if (!(context.getDisplay() instanceof MatterFoundryReiDisplay display)) {
            return SimpleTransferHandler.super.getInputsIndexed(context);
        }
        List<InputIngredient<ItemStack>> indexed = new ArrayList<>();
        int i = 0;
        for (var ingredient : display.getItemInputs()) {
            List<ItemStack> stacks = new ArrayList<>();
            for (EntryStack<?> entry : ingredient) {
                Object value = entry.getValue();
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    stacks.add(stack.copy());
                }
            }
            indexed.add(InputIngredient.of(i, i, stacks));
            i++;
        }
        return indexed;
    }

    @Override
    public TransferHandler.Result handle(Context context) {
        return SimpleTransferHandler.super.handle(context);
    }
}
