package net.unfamily.repae2bridge.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;

import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;

/**
 * Encodes a Matter Foundry recipe into the AE2 Pattern Encoding Terminal as a processing pattern,
 * including item stack counts and Replication matter inputs (not only GUI {@code RENDER_ONLY} icons).
 */
public final class FoundryPatternEncoding {

    private FoundryPatternEncoding() {
    }

    public static void encodeToTerminal(PatternEncodingTermMenu menu, MatterFoundryRecipe recipe) {
        List<List<GenericStack>> inputs = new ArrayList<>();

        for (MatterFoundryRecipe.CountedIngredient counted : recipe.getInputs()) {
            List<GenericStack> options = FoundryRecipeLists.expandIngredient(counted.ingredient(), counted.count())
                    .stream()
                    .map(GenericStack::fromItemStack)
                    .filter(Objects::nonNull)
                    .toList();
            if (!options.isEmpty()) {
                inputs.add(options);
            }
        }

        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            var type = cost.resolve();
            if (type == null) {
                continue;
            }
            ReplicationMatterKey key = ReplicationMatterKey.of(type);
            if (key == null) {
                continue;
            }
            long amount = Math.max(1L, cost.amount());
            inputs.add(List.of(new GenericStack(key, amount)));
        }

        List<GenericStack> outputs = new ArrayList<>();
        GenericStack result = GenericStack.fromItemStack(recipe.getResultItem());
        if (result != null) {
            outputs.add(result);
        }

        EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
    }
}
