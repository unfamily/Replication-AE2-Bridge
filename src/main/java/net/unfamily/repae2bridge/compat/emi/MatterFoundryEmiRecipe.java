package net.unfamily.repae2bridge.compat.emi;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.compat.FoundryRecipeLayout;
import net.unfamily.repae2bridge.compat.FoundryRecipeLists;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;
import net.unfamily.repae2bridge.util.MatterTypeUtil;

public class MatterFoundryEmiRecipe implements EmiRecipe {

    private final RecipeHolder<MatterFoundryRecipe> holder;
    private final List<EmiIngredient> itemInputs = new ArrayList<>();
    private final List<EmiIngredient> matterInputs = new ArrayList<>();
    private final List<EmiStack> outputs = new ArrayList<>();

    public MatterFoundryEmiRecipe(RecipeHolder<MatterFoundryRecipe> holder) {
        this.holder = holder;
        MatterFoundryRecipe recipe = holder.value();
        for (MatterFoundryRecipe.CountedIngredient input : recipe.getInputs()) {
            List<EmiStack> stacks = new ArrayList<>();
            for (ItemStack stack : FoundryRecipeLists.expandIngredient(input.ingredient(), input.count())) {
                stacks.add(EmiStack.of(stack));
            }
            if (!stacks.isEmpty()) {
                itemInputs.add(EmiIngredient.of(stacks));
            }
        }
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            var type = cost.resolve();
            if (type == null) {
                continue;
            }
            var display = MatterTypeUtil.getDisplayItemForMatterType(type);
            if (display != null) {
                ItemStack stack = new ItemStack(display);
                stack.setCount(Math.min(64, Math.max(1, (int) Math.ceil(cost.amount()))));
                matterInputs.add(EmiStack.of(stack));
            }
        }
        outputs.add(EmiStack.of(recipe.getResultItem()));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return RepAE2BridgeEmiPlugin.FOUNDRY;
    }

    @Override
    public @Nullable Identifier getId() {
        return holder.id().identifier();
    }

    @Override
    public List<EmiIngredient> getInputs() {
        List<EmiIngredient> all = new ArrayList<>(itemInputs.size() + matterInputs.size());
        all.addAll(itemInputs);
        all.addAll(matterInputs);
        return all;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return FoundryRecipeLayout.WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return FoundryRecipeLayout.HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int i = 0;
        for (EmiIngredient input : itemInputs) {
            if (i >= FoundryRecipeLayout.MAX_INPUTS) {
                break;
            }
            widgets.addSlot(input, FoundryRecipeLayout.inputX(i), FoundryRecipeLayout.INPUT_Y);
            i++;
        }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, FoundryRecipeLayout.ARROW_X, FoundryRecipeLayout.ARROW_Y);
        widgets.addSlot(outputs.getFirst(), FoundryRecipeLayout.OUTPUT_X, FoundryRecipeLayout.OUTPUT_Y)
                .recipeContext(this);

        int m = 0;
        for (EmiIngredient matter : matterInputs) {
            int mx = FoundryRecipeLayout.matterX(m);
            if (mx + FoundryRecipeLayout.SLOT_SIZE > FoundryRecipeLayout.WIDTH) {
                break;
            }
            widgets.addSlot(matter, mx, FoundryRecipeLayout.MATTER_Y);
            m++;
        }

        MatterFoundryRecipe recipe = holder.value();
        int ticks = Math.max(1, recipe.getEnergy() / Math.max(1, Config.matterFoundryEnergyPerTick));
        widgets.addText(Component.literal(recipe.getEnergy() + " FE"),
                FoundryRecipeLayout.TEXT_MARGIN, FoundryRecipeLayout.TEXT_ENERGY_Y, 0xFF404040, false);
        widgets.addText(Component.literal(ticks + " t"),
                FoundryRecipeLayout.TEXT_MARGIN, FoundryRecipeLayout.TEXT_TIME_Y, 0xFF404040, false);
    }
}
