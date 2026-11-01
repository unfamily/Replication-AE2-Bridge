package net.unfamily.repae2bridge.compat.rei;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;

import appeng.menu.me.items.PatternEncodingTermMenu;

import net.unfamily.repae2bridge.compat.FoundryPatternEncoding;

/**
 * REI + on Pattern Encoding Terminal: encode Foundry item + matter as processing.
 */
public final class FoundryPatternReiTransferHandler implements TransferHandler {

    @Override
    public Result handle(Context context) {
        if (!(context.getMenu() instanceof PatternEncodingTermMenu menu)) {
            return Result.createNotApplicable();
        }
        if (!(context.getDisplay() instanceof MatterFoundryReiDisplay display)) {
            return Result.createNotApplicable();
        }
        if (!context.isActuallyCrafting()) {
            return Result.createSuccessful();
        }
        FoundryPatternEncoding.encodeToTerminal(menu, display.getRecipe());
        return Result.createSuccessful().blocksFurtherHandling();
    }
}
