package net.unfamily.repae2bridge.compat.emi;

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
        return handler.getPlayerSlotsForTransfer();
    }

    @Override
    public List<Slot> getCraftingSlots(MatterFoundryMenu handler) {
        return handler.getFoundryInputSlots();
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe.getCategory() == RepAE2BridgeEmiPlugin.FOUNDRY;
    }
}
