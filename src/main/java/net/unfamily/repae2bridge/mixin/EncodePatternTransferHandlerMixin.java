package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;

import appeng.menu.me.items.PatternEncodingTermMenu;

import net.unfamily.repae2bridge.compat.FoundryPatternEncoding;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;

/**
 * JEI + on Pattern Encoding Terminal: Foundry recipes must encode item counts and matter keys.
 * AE2's default path only reads {@code INPUT} slots (matter is {@code RENDER_ONLY}).
 */
@Mixin(targets = "appeng.integration.modules.jei.transfer.EncodePatternTransferHandler", remap = false)
public abstract class EncodePatternTransferHandlerMixin {

    @Inject(method = "transferRecipe", at = @At("HEAD"), cancellable = true)
    private void repAe2Bridge$encodeFoundryRecipe(
            PatternEncodingTermMenu menu,
            Object recipeBase,
            IRecipeSlotsView slotsView,
            Player player,
            boolean maxTransfer,
            boolean doTransfer,
            CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (!(recipeBase instanceof RecipeHolder<?> holder)
                || !(holder.value() instanceof MatterFoundryRecipe foundryRecipe)) {
            return;
        }
        if (doTransfer) {
            FoundryPatternEncoding.encodeToTerminal(menu, foundryRecipe);
        }
        cir.setReturnValue(null);
    }
}
