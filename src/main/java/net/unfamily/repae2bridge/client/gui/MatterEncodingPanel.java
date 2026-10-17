package net.unfamily.repae2bridge.client.gui;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.ActionItems;
import appeng.api.stacks.GenericStack;
import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Tooltip;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;

import net.unfamily.repae2bridge.menu.MatterEncodingMenuAccess;

/**
 * Stonecutter-like preview of matter outputs with primary-output cycling.
 */
public final class MatterEncodingPanel implements ICompositeWidget {

    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 140, 124, 66);
    private static final Blitter BG_SLOT = BG.copy().src(124, 140, 20, 22);

    private static final int COLS = 4;
    private static final int ROWS = 2;

    private final PatternEncodingTermScreen<?> screen;
    private final PatternEncodingTermMenu menu;
    private final Scrollbar scrollbar;
    private final ActionButton cycleOutputBtn;
    private boolean visible;
    private int x;
    private int y;

    public MatterEncodingPanel(PatternEncodingTermScreen<?> screen, WidgetContainer widgets) {
        this.screen = screen;
        this.menu = screen.getMenu();
        this.scrollbar = widgets.addScrollBar("matterDisintegrationScrollbar", Scrollbar.SMALL);
        this.scrollbar.setRange(0, 0, COLS);
        this.scrollbar.setCaptureMouseWheel(false);
        this.scrollbar.setVisible(false);

        this.cycleOutputBtn = new ActionButton(
                ActionItems.S_CYCLE_PROCESSING_OUTPUT,
                act -> {
                    if (menu instanceof MatterEncodingMenuAccess access) {
                        access.repAe2Bridge$cycleMatterOutputs();
                    }
                });
        this.cycleOutputBtn.setHalfSize(true);
        this.cycleOutputBtn.setDisableBackground(true);
        widgets.add("matterCycleOutput", this.cycleOutputBtn);
        this.cycleOutputBtn.setVisibility(false);
    }

    @Override
    public void updateBeforeRender() {
        List<GenericStack> outputs = previewOutputs();
        int totalRows = (outputs.size() + COLS - 1) / COLS;
        scrollbar.setRange(0, Math.max(0, totalRows - ROWS), ROWS);

        if (visible && menu instanceof MatterEncodingMenuAccess access) {
            cycleOutputBtn.setVisibility(access.repAe2Bridge$canCycleMatterOutputs());
            // Keep amount visible: stonecutting defaults hide it and AE2 may re-apply.
            access.repAe2Bridge$ensureMatterInputAmountVisible();
        }
    }

    @Override
    public void drawBackgroundLayer(GuiGraphicsExtractor guiGraphics, Rect2i bounds, Point mouse) {
        BG.dest(bounds.getX() + 8, bounds.getY() + bounds.getHeight() - 165).blit(guiGraphics);
        drawOutputs(guiGraphics, bounds, mouse);
    }

    private void drawOutputs(GuiGraphicsExtractor guiGraphics, Rect2i bounds, Point mouse) {
        List<GenericStack> outputs = previewOutputs();
        int startIndex = scrollbar.getCurrentScroll() * COLS;
        int endIndex = startIndex + ROWS * COLS;

        for (int i = startIndex; i < endIndex && i < outputs.size(); ++i) {
            Rect2i slotBounds = getSlotBounds(i - startIndex);
            int renderX = bounds.getX() + slotBounds.getX();
            int renderY = bounds.getY() + slotBounds.getY();
            BG_SLOT.dest(renderX, renderY).blit(guiGraphics);

            GenericStack stack = outputs.get(i);
            ItemStack display = stack.what().wrapForDisplayOrFilter();
            if (!display.isEmpty()) {
                display.setCount((int) Math.min(Integer.MAX_VALUE, Math.max(1, stack.amount())));
                guiGraphics.item(display, renderX + 2, renderY + 2);
                guiGraphics.itemDecorations(Minecraft.getInstance().font, display, renderX + 2, renderY + 2);
            }
        }
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        return false;
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        GenericStack stack = getOutputAt(new Point(mouseX, mouseY));
        if (stack == null) {
            return null;
        }
        var lines = new java.util.ArrayList<Component>();
        lines.add(stack.what().getDisplayName());
        lines.add(Component.literal("x" + stack.amount()));
        if (previewOutputs().indexOf(stack) == 0) {
            lines.add(Component.translatable("gui.rep_ae2_bridge.matter_encoding.primary_output"));
        }
        return new Tooltip(lines);
    }

    @Nullable
    private GenericStack getOutputAt(Point point) {
        List<GenericStack> outputs = previewOutputs();
        if (outputs.isEmpty()) {
            return null;
        }
        int startIndex = scrollbar.getCurrentScroll() * COLS;
        int endIndex = startIndex + COLS * ROWS;
        for (int i = startIndex; i < endIndex && i < outputs.size(); ++i) {
            if (point.isIn(getSlotBounds(i - startIndex))) {
                return outputs.get(i);
            }
        }
        return null;
    }

    private Rect2i getSlotBounds(int index) {
        int col = index % COLS;
        int row = index / COLS;
        int slotX = x + 26 + col * BG_SLOT.getSrcWidth();
        int slotY = y + 12 + row * BG_SLOT.getSrcHeight();
        return new Rect2i(slotX, slotY, BG_SLOT.getSrcWidth(), BG_SLOT.getSrcHeight());
    }

    private List<GenericStack> previewOutputs() {
        if (menu instanceof MatterEncodingMenuAccess access) {
            return access.repAe2Bridge$getMatterPreviewOutputs();
        }
        return List.of();
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        return scrollbar.onMouseWheel(mousePos, delta);
    }

    @Override
    public void setPosition(Point position) {
        x = position.getX();
        y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(x, y, 124, 66);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        scrollbar.setVisible(visible);
        if (!visible) {
            cycleOutputBtn.setVisibility(false);
            // Do NOT hide STONECUTTING_INPUT here. AE2's StonecuttingEncodingPanel owns that
            // slot; hiding it after AE2 showed it breaks the real stonecutting tab.
            return;
        }
        if (menu instanceof MatterEncodingMenuAccess access) {
            cycleOutputBtn.setVisibility(access.repAe2Bridge$canCycleMatterOutputs());
        }
        // Reuse the stonecutting input slot position while matter mode is active only.
        screen.setSlotsHidden(SlotSemantics.STONECUTTING_INPUT, false);
        screen.setSlotsHidden(SlotSemantics.PROCESSING_INPUTS, true);
        screen.setSlotsHidden(SlotSemantics.PROCESSING_OUTPUTS, true);
    }
}
