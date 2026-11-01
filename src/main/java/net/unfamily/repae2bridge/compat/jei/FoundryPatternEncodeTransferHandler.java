package net.unfamily.repae2bridge.compat.jei;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.menu.me.items.PatternEncodingTermMenu;

import net.unfamily.repae2bridge.compat.FoundryPatternEncoding;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;

/**
 * JEI + on the Pattern Encoding Terminal for Foundry recipes:
 * forces processing mode and fills item + matter inputs.
 * AE2 1.21 has no JEI encode handler, so this must be registered by us.
 */
public final class FoundryPatternEncodeTransferHandler
        implements IRecipeTransferHandler<PatternEncodingTermMenu, RecipeHolder<MatterFoundryRecipe>> {

    @Override
    public Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override
    public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        return Optional.of(PatternEncodingTermMenu.TYPE);
    }

    @Override
    public RecipeType<RecipeHolder<MatterFoundryRecipe>> getRecipeType() {
        return MatterFoundryRecipeCategory.RECIPE_TYPE;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            PatternEncodingTermMenu container,
            RecipeHolder<MatterFoundryRecipe> recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        if (doTransfer) {
            FoundryPatternEncoding.encodeToTerminal(container, recipe.value());
        }
        return null;
    }
}
