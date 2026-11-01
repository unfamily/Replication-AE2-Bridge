package net.unfamily.repae2bridge.compat.rei;

import java.util.List;
import java.util.Locale;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.me.common.StackSizeRenderer;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.compat.FoundryRecipeLayout;

public class MatterFoundryReiCategory implements DisplayCategory<MatterFoundryReiDisplay> {

    public static final CategoryIdentifier<MatterFoundryReiDisplay> CATEGORY_ID =
            CategoryIdentifier.of(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_foundry"));

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
        List<Long> amounts = display.getMatterAmounts();
        for (var matter : display.getMatterInputs()) {
            if (m >= FoundryRecipeLayout.MAX_MATTER_SLOTS) {
                break;
            }
            int slotX = ox + FoundryRecipeLayout.matterX(m);
            int slotY = oy + FoundryRecipeLayout.matterY(m);
            widgets.add(Widgets.createSlot(new Point(slotX, slotY)).entries(matter));
            long amount = m < amounts.size() ? amounts.get(m) : 0L;
            String label = formatCompact(Math.max(0L, amount));
            int itemX = slotX + FoundryRecipeLayout.ITEM_INSET;
            int itemY = slotY + FoundryRecipeLayout.ITEM_INSET;
            widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) ->
                    StackSizeRenderer.renderSizeLabel(
                            graphics, Minecraft.getInstance().font, itemX, itemY, label)));
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

    private static String formatCompact(long amount) {
        if (amount < 1_000L) {
            return Long.toString(amount);
        }
        double scaled = amount;
        char[] suffixes = { 'k', 'M', 'B', 'T' };
        int suffix = -1;
        while (scaled >= 1_000d && suffix < suffixes.length - 1) {
            scaled /= 1_000d;
            suffix++;
        }
        String number = scaled >= 100d
                ? String.format(Locale.ROOT, "%.0f", scaled)
                : String.format(Locale.ROOT, "%.1f", scaled);
        if (number.endsWith(".0")) {
            number = number.substring(0, number.length() - 2);
        }
        return number + suffixes[suffix];
    }
}
