package net.unfamily.repae2bridge.compat.rei;

import java.util.List;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.compat.FoundryRecipeLayout;

public class MatterFoundryReiCategory implements DisplayCategory<MatterFoundryReiDisplay> {

    public static final CategoryIdentifier<MatterFoundryReiDisplay> CATEGORY_ID =
            CategoryIdentifier.of(Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_foundry"));

    @Override
    public CategoryIdentifier<? extends MatterFoundryReiDisplay> getCategoryIdentifier() {
        return CATEGORY_ID;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.rep_ae2_bridge.matter_foundry");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(ModBlocks.MATTER_FOUNDRY.get());
    }

    @Override
    public List<Widget> setupDisplay(MatterFoundryReiDisplay display, Rectangle bounds) {
        List<Widget> widgets = new java.util.ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        int ox = bounds.getX();
        int oy = bounds.getY();

        int i = 0;
        for (var input : display.getItemInputs()) {
            if (i >= FoundryRecipeLayout.MAX_INPUTS) {
                break;
            }
            widgets.add(Widgets.createSlot(new Point(ox + FoundryRecipeLayout.inputX(i), oy + FoundryRecipeLayout.INPUT_Y))
                    .entries(input).markInput());
            i++;
        }
        widgets.add(Widgets.createArrow(new Point(ox + FoundryRecipeLayout.ARROW_X, oy + FoundryRecipeLayout.ARROW_Y)));
        if (!display.getOutputEntries().isEmpty()) {
            widgets.add(Widgets.createSlot(new Point(ox + FoundryRecipeLayout.OUTPUT_X, oy + FoundryRecipeLayout.OUTPUT_Y))
                    .entries(display.getOutputEntries().getFirst()).markOutput());
        }

        int m = 0;
        for (var matter : display.getMatterInputs()) {
            if (m >= FoundryRecipeLayout.MAX_MATTER_SLOTS) {
                break;
            }
            widgets.add(Widgets.createSlot(new Point(
                            ox + FoundryRecipeLayout.matterX(m),
                            oy + FoundryRecipeLayout.matterY(m)))
                    .entries(matter).markInput());
            m++;
        }

        widgets.add(Widgets.createLabel(
                        new Point(ox + FoundryRecipeLayout.TEXT_MARGIN, oy + FoundryRecipeLayout.TEXT_ENERGY_Y),
                        Component.literal(display.getEnergy() + " FE"))
                .leftAligned().noShadow().color(0x404040, 0xBBBBBB));
        widgets.add(Widgets.createLabel(
                        new Point(ox + FoundryRecipeLayout.TEXT_MARGIN, oy + FoundryRecipeLayout.TEXT_TIME_Y),
                        Component.literal(display.getTicks() + " t"))
                .leftAligned().noShadow().color(0x404040, 0xBBBBBB));
        return widgets;
    }

    @Override
    public int getDisplayHeight() {
        return FoundryRecipeLayout.HEIGHT;
    }

    @Override
    public int getDisplayWidth(MatterFoundryReiDisplay display) {
        return FoundryRecipeLayout.WIDTH;
    }
}
