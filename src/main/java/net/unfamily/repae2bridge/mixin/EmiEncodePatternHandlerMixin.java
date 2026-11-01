package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;

import net.unfamily.repae2bridge.compat.FoundryPatternEncoding;
import net.unfamily.repae2bridge.compat.emi.MatterFoundryEmiRecipe;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;

/**
 * EMI + on Pattern Encoding Terminal: Foundry must encode items + matter as processing.
 * AE2's default path only reads {@link EmiRecipe#getInputs()} (matter is display-only).
 */
@Mixin(value = EmiEncodePatternHandler.class, remap = false)
public abstract class EmiEncodePatternHandlerMixin {

    @Inject(method = "canCraft", at = @At("HEAD"), cancellable = true)
    private void repAe2Bridge$canCraftFoundry(
            EmiRecipe recipe,
            EmiCraftContext<PatternEncodingTermMenu> context,
            CallbackInfoReturnable<Boolean> cir) {
        if (resolveFoundry(recipe) == null) {
            return;
        }
        cir.setReturnValue(context.getType() == EmiCraftContext.Type.FILL_BUTTON);
    }

    @Inject(method = "craft", at = @At("HEAD"), cancellable = true)
    private void repAe2Bridge$craftFoundry(
            EmiRecipe recipe,
            EmiCraftContext<PatternEncodingTermMenu> context,
            CallbackInfoReturnable<Boolean> cir) {
        MatterFoundryRecipe foundry = resolveFoundry(recipe);
        if (foundry == null) {
            return;
        }
        if (context.getType() != EmiCraftContext.Type.FILL_BUTTON) {
            cir.setReturnValue(false);
            return;
        }
        FoundryPatternEncoding.encodeToTerminal(context.getScreenHandler(), foundry);
        cir.setReturnValue(true);
    }

    private static MatterFoundryRecipe resolveFoundry(EmiRecipe recipe) {
        if (recipe instanceof MatterFoundryEmiRecipe foundryRecipe) {
            return foundryRecipe.getRecipe();
        }
        RecipeHolder<?> backing = recipe.getBackingRecipe();
        if (backing != null && backing.value() instanceof MatterFoundryRecipe foundry) {
            return foundry;
        }
        return null;
    }
}
