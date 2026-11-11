package net.unfamily.repae2bridge.compat.rei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.compat.FoundryRecipeLists;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;
import net.unfamily.repae2bridge.util.MatterTypeUtil;

public class MatterFoundryReiDisplay implements Display {

    private final RecipeHolder<MatterFoundryRecipe> holder;
    private final List<EntryIngredient> itemInputs = new ArrayList<>();
    private final List<EntryIngredient> matterInputs = new ArrayList<>();
    private final List<Long> matterAmounts = new ArrayList<>();
    private final List<EntryIngredient> outputs = new ArrayList<>();

    public MatterFoundryReiDisplay(RecipeHolder<MatterFoundryRecipe> holder) {
        this.holder = holder;
        MatterFoundryRecipe recipe = holder.value();
        for (MatterFoundryRecipe.CountedIngredient input : recipe.getInputs()) {
            List<ItemStack> stacks = FoundryRecipeLists.expandIngredient(input.ingredient(), input.count());
            if (!stacks.isEmpty()) {
                itemInputs.add(EntryIngredients.ofItemStacks(stacks));
            }
        }
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            var type = cost.resolve();
            if (type == null) {
                continue;
            }
            var display = MatterTypeUtil.getDisplayItemForMatterType(type);
            if (display != null) {
                matterInputs.add(EntryIngredients.of(new ItemStack(display)));
                matterAmounts.add((long) Math.ceil(cost.amount()));
            }
        }
        outputs.add(EntryIngredients.of(recipe.getResultItem()));
    }

    public List<Long> getMatterAmounts() {
        return matterAmounts;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        List<EntryIngredient> all = new ArrayList<>(itemInputs.size() + matterInputs.size());
        all.addAll(itemInputs);
        all.addAll(matterInputs);
        return all;
    }

    public List<EntryIngredient> getItemInputs() {
        return itemInputs;
    }

    public List<EntryIngredient> getMatterInputs() {
        return matterInputs;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return outputs;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return MatterFoundryReiCategory.CATEGORY_ID;
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.of(holder.id().identifier());
    }

    @Override
    public DisplaySerializer<? extends Display> getSerializer() {
        return null; // client-only registration via DisplayRegistry
    }

    public int getEnergy() {
        return holder.value().getEnergy();
    }

    public int getTicks() {
        MatterFoundryRecipe recipe = holder.value();
        return Math.max(1, recipe.getEnergy() / Math.max(1, Config.matterFoundryEnergyPerTick));
    }
}
