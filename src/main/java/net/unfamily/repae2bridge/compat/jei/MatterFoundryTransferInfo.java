package net.unfamily.repae2bridge.compat.jei;

import java.util.List;
import java.util.Optional;

import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.types.IRecipeType;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.unfamily.repae2bridge.menu.MatterFoundryMenu;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;

/**
 * JEI + transfer: maps recipe INPUT item slots onto Foundry item inputs only.
 * Matter slots are {@code RENDER_ONLY} in the category, so they are never transferred.
 */
public final class MatterFoundryTransferInfo
        implements IRecipeTransferInfo<MatterFoundryMenu, RecipeHolder<MatterFoundryRecipe>> {

    @Override
    public Class<? extends MatterFoundryMenu> getContainerClass() {
        return MatterFoundryMenu.class;
    }

    @Override
    public Optional<MenuType<MatterFoundryMenu>> getMenuType() {
        return Optional.of(MatterFoundryMenu.TYPE);
    }

    @Override
    public IRecipeType<RecipeHolder<MatterFoundryRecipe>> getRecipeType() {
        return MatterFoundryRecipeCategory.RECIPE_TYPE;
    }

    @Override
    public boolean canHandle(MatterFoundryMenu container, RecipeHolder<MatterFoundryRecipe> recipe) {
        return true;
    }

    @Override
    public List<Slot> getRecipeSlots(MatterFoundryMenu container, RecipeHolder<MatterFoundryRecipe> recipe) {
        return container.getFoundryInputSlots();
    }

    @Override
    public List<Slot> getInventorySlots(MatterFoundryMenu container, RecipeHolder<MatterFoundryRecipe> recipe) {
        return container.getPlayerSlotsForTransfer();
    }
}
