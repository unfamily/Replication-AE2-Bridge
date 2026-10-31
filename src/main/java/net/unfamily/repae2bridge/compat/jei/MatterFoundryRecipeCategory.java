package net.unfamily.repae2bridge.compat.jei;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.compat.FoundryRecipeLayout;
import net.unfamily.repae2bridge.compat.FoundryRecipeLists;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;
import net.unfamily.repae2bridge.util.MatterTypeUtil;

public class MatterFoundryRecipeCategory implements IRecipeCategory<RecipeHolder<MatterFoundryRecipe>> {

    public static final Identifier UID = Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_foundry");
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final IRecipeType<RecipeHolder<MatterFoundryRecipe>> RECIPE_TYPE =
            IRecipeType.create(UID, (Class) RecipeHolder.class);

    private final IDrawable background;
    private final IDrawable icon;

    public MatterFoundryRecipeCategory(IGuiHelper helper) {
        this.background = new FoundryJeiBackground();
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModBlocks.MATTER_FOUNDRY.get()));
    }

    @Override
    public IRecipeType<RecipeHolder<MatterFoundryRecipe>> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public int getWidth() {
        return FoundryRecipeLayout.WIDTH;
    }

    @Override
    public int getHeight() {
        return FoundryRecipeLayout.HEIGHT;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.rep_ae2_bridge.matter_foundry");
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MatterFoundryRecipe> holder, IFocusGroup focuses) {
        MatterFoundryRecipe recipe = holder.value();
        int i = 0;
        for (MatterFoundryRecipe.CountedIngredient input : recipe.getInputs()) {
            if (i >= FoundryRecipeLayout.MAX_INPUTS) {
                break;
            }
            List<ItemStack> stacks = FoundryRecipeLists.expandIngredient(input.ingredient(), input.count());
            if (!stacks.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT,
                                FoundryRecipeLayout.inputX(i) + FoundryRecipeLayout.ITEM_INSET,
                                FoundryRecipeLayout.INPUT_Y + FoundryRecipeLayout.ITEM_INSET)
                        .addItemStacks(stacks);
            }
            i++;
        }

        int m = 0;
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            var type = cost.resolve();
            if (type == null) {
                continue;
            }
            var display = MatterTypeUtil.getDisplayItemForMatterType(type);
            if (display == null) {
                continue;
            }
            ItemStack stack = new ItemStack(display);
            stack.setCount(Math.min(64, Math.max(1, (int) Math.ceil(cost.amount()))));
            int mx = FoundryRecipeLayout.matterX(m);
            if (mx + FoundryRecipeLayout.SLOT_SIZE > FoundryRecipeLayout.WIDTH) {
                break;
            }
            builder.addSlot(RecipeIngredientRole.RENDER_ONLY,
                            mx + FoundryRecipeLayout.ITEM_INSET,
                            FoundryRecipeLayout.MATTER_Y + FoundryRecipeLayout.ITEM_INSET)
                    .add(VanillaTypes.ITEM_STACK, stack);
            m++;
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT,
                        FoundryRecipeLayout.OUTPUT_X + FoundryRecipeLayout.ITEM_INSET,
                        FoundryRecipeLayout.OUTPUT_Y + FoundryRecipeLayout.ITEM_INSET)
                .add(VanillaTypes.ITEM_STACK, recipe.getResultItem());
    }

    @Override
    public void draw(RecipeHolder<MatterFoundryRecipe> holder, IRecipeSlotsView recipeSlotsView,
            GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        MatterFoundryRecipe recipe = holder.value();
        var font = Minecraft.getInstance().font;
        int energy = recipe.getEnergy();
        int ticks = Math.max(1, energy / Math.max(1, Config.matterFoundryEnergyPerTick));
        guiGraphics.text(font, Component.translatable("jei.rep_ae2_bridge.matter_foundry.energy", energy),
                FoundryRecipeLayout.TEXT_MARGIN, FoundryRecipeLayout.TEXT_ENERGY_Y, 0xFF404040, false);
        guiGraphics.text(font, Component.translatable("jei.rep_ae2_bridge.matter_foundry.time", ticks),
                FoundryRecipeLayout.TEXT_MARGIN, FoundryRecipeLayout.TEXT_TIME_Y, 0xFF404040, false);
    }
}
