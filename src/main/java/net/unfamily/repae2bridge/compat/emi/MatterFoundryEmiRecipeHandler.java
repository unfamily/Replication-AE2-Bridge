package net.unfamily.repae2bridge.compat.emi;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;

import net.minecraft.world.inventory.Slot;

import net.unfamily.repae2bridge.menu.MatterFoundryMenu;

/**
 * EMI + transfer into Foundry item inputs only (matter is display-only / not in {@link EmiRecipe#getInputs()}).
 */
public final class MatterFoundryEmiRecipeHandler implements StandardRecipeHandler<MatterFoundryMenu> {

    @Override
    public List<Slot> getInputSources(MatterFoundryMenu handler) {
        // Player inventory + current Foundry inputs (same idea as typical machine handlers).
        List<Slot> slots = new ArrayList<>();
        slots.addAll(handler.getPlayerSlotsForTransfer());
        slots.addAll(handler.getFoundryInputSlots());
        return slots;
    }

    @Override
    public List<Slot> getCraftingSlots(MatterFoundryMenu handler) {
        return handler.getFoundryInputSlots();
    }

    @Override
    public List<Slot> getCraftingSlots(EmiRecipe recipe, MatterFoundryMenu handler) {
        List<Slot> all = handler.getFoundryInputSlots();
        int n = recipe.getInputs().size();
        if (n <= 0 || n >= all.size()) {
            return all;
        }
        return all.subList(0, n);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe instanceof MatterFoundryEmiRecipe
                || recipe.getCategory() == RepAE2BridgeEmiPlugin.FOUNDRY;
    }
}
