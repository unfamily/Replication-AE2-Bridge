package net.unfamily.repae2bridge.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.compat.FoundryRecipeLayout;

/**
 * Draws slot frames + arrow for the Matter Foundry JEI category.
 */
public final class FoundryJeiBackground implements IDrawable {

    public static final Identifier SLOT =
            Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "textures/gui/jei/slot.png");
    public static final Identifier ARROW =
            Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "textures/gui/jei/arrow.png");

    private final int inputSlots;
    private final int matterSlots;

    public FoundryJeiBackground(int inputSlots, int matterSlots) {
        this.inputSlots = Math.min(FoundryRecipeLayout.MAX_INPUTS, Math.max(0, inputSlots));
        this.matterSlots = Math.min(FoundryRecipeLayout.MAX_MATTER_SLOTS, Math.max(0, matterSlots));
    }

    /** Background sized for max layout (recipe draw overlays slots per recipe). */
    public FoundryJeiBackground() {
        this(FoundryRecipeLayout.MAX_INPUTS, FoundryRecipeLayout.MAX_MATTER_SLOTS);
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
    public void draw(GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset) {
        for (int i = 0; i < inputSlots; i++) {
            blitSlot(guiGraphics, xOffset + FoundryRecipeLayout.inputX(i), yOffset + FoundryRecipeLayout.INPUT_Y);
        }
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ARROW,
                xOffset + FoundryRecipeLayout.ARROW_X, yOffset + FoundryRecipeLayout.ARROW_Y,
                0.0F, 0.0F,
                FoundryRecipeLayout.ARROW_W, FoundryRecipeLayout.ARROW_H,
                FoundryRecipeLayout.ARROW_W, FoundryRecipeLayout.ARROW_H);
        blitSlot(guiGraphics, xOffset + FoundryRecipeLayout.OUTPUT_X, yOffset + FoundryRecipeLayout.OUTPUT_Y);
        for (int i = 0; i < matterSlots; i++) {
            blitSlot(guiGraphics,
                    xOffset + FoundryRecipeLayout.matterX(i),
                    yOffset + FoundryRecipeLayout.matterY(i));
        }
    }

    private static void blitSlot(GuiGraphicsExtractor g, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 0.0F, 0.0F,
                FoundryRecipeLayout.SLOT_SIZE, FoundryRecipeLayout.SLOT_SIZE,
                FoundryRecipeLayout.SLOT_SIZE, FoundryRecipeLayout.SLOT_SIZE);
    }
}
