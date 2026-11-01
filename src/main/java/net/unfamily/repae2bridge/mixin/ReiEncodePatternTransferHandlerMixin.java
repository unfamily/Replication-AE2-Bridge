package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.crafting.RecipeHolder;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.common.display.Display;

import appeng.integration.modules.rei.transfer.EncodePatternTransferHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;

import net.unfamily.repae2bridge.compat.FoundryPatternEncoding;
import net.unfamily.repae2bridge.compat.rei.MatterFoundryReiDisplay;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;

/**
 * REI + on Pattern Encoding Terminal: Foundry must encode items + matter as processing.
 * AE2's default path treats matter display stacks as processing item inputs.
 */
@Mixin(value = EncodePatternTransferHandler.class, remap = false)
public abstract class ReiEncodePatternTransferHandlerMixin {

    @Inject(
            method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Lnet/minecraft/world/item/crafting/RecipeHolder;Lme/shedaniel/rei/api/common/display/Display;Z)Lme/shedaniel/rei/api/client/registry/transfer/TransferHandler$Result;",
            at = @At("HEAD"),
            cancellable = true)
    private void repAe2Bridge$encodeFoundry(
            PatternEncodingTermMenu menu,
            RecipeHolder<?> recipeHolder,
            Display display,
            boolean doTransfer,
            CallbackInfoReturnable<TransferHandler.Result> cir) {
        MatterFoundryRecipe foundry = null;
        if (display instanceof MatterFoundryReiDisplay foundryDisplay) {
            foundry = foundryDisplay.getRecipe();
        } else if (recipeHolder != null && recipeHolder.value() instanceof MatterFoundryRecipe recipe) {
            foundry = recipe;
        }
        if (foundry == null) {
            return;
        }
        if (doTransfer) {
            FoundryPatternEncoding.encodeToTerminal(menu, foundry);
        }
        cir.setReturnValue(TransferHandler.Result.createSuccessful().blocksFurtherHandling());
    }
}
