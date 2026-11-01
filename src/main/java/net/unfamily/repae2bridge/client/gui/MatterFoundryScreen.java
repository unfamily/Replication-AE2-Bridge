package net.unfamily.repae2bridge.client.gui;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;

import guideme.PageAnchor;

import net.unfamily.repae2bridge.block.entity.FoundryMatterBuffer;
import net.unfamily.repae2bridge.block.entity.MatterFoundryBlockEntity;
import net.unfamily.repae2bridge.menu.MatterFoundryMenu;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipeInput;
import net.unfamily.repae2bridge.recipe.ModRecipes;
import net.unfamily.repae2bridge.util.MatterTypeUtil;

/**
 * Matter Foundry screen: upgrades, priority, craft progress, matter X/Y grid.
 */
public class MatterFoundryScreen extends UpgradeableScreen<MatterFoundryMenu> {

    /** Texture area (7,81)-(168,114): 9×2 matter display slots. */
    private static final int MATTER_ORIGIN_X = 7;
    private static final int MATTER_ORIGIN_Y = 81;
    private static final int MATTER_COLS = 9;
    private static final int MATTER_ROWS = 2;
    private static final int MATTER_SLOT_W = 18;
    private static final int MATTER_SLOT_H = 17;

    private static final float LABEL_SCALE = 0.5f;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    /** American short-scale: thousand / million / billion / trillion. */
    private static final char[] COMPACT_SUFFIXES = { 'k', 'M', 'B', 'T' };

    private final ProgressBar craftProgress;
    private final SettingToggleButton<YesNo> autoExportBtn;

    public MatterFoundryScreen(MatterFoundryMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        widgets.addOpenPriorityButton();

        // Left toolbar order: help (?), auto-export, dump (dump always last).
        this.autoExportBtn = new ServerSettingToggleButton<>(Settings.AUTO_EXPORT, YesNo.NO);
        addToLeftToolbar(this.autoExportBtn);
        addToLeftToolbar(new FoundryDumpMatterButton(menu::dumpMatterBuffer));

        this.craftProgress = new ProgressBar(this.menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        widgets.add("progressBar", this.craftProgress);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        int max = Math.max(1, menu.getMaxProgress());
        int cur = menu.getCurrentProgress();
        int pct = Math.min(100, cur * 100 / max);
        this.craftProgress.setFullMsg(Component.literal(pct + "%"));
        this.autoExportBtn.set(menu.getAutoExport());
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        if (!(menu.getHost() instanceof MatterFoundryBlockEntity foundry)) {
            return;
        }
        // drawFG runs with pose translated to (leftPos, topPos); mouse args are screen-absolute.
        int relMouseX = mouseX - offsetX;
        int relMouseY = mouseY - offsetY;

        // Menu slots are synced; BE clientRequiredMatter can lag — never show Y without a match.
        boolean showRequired = menuHasValidFoundryRecipe();
        List<FoundryMatterBuffer.DisplaySlot> slots = foundry.getMatterDisplaySlots(showRequired);
        for (int i = 0; i < Math.min(FoundryMatterBuffer.MAX_TYPES, slots.size()); i++) {
            FoundryMatterBuffer.DisplaySlot slot = slots.get(i);
            if (slot.isEmpty()) {
                continue;
            }
            int col = i % MATTER_COLS;
            int row = i / MATTER_COLS;
            if (row >= MATTER_ROWS) {
                break;
            }
            int x = MATTER_ORIGIN_X + col * MATTER_SLOT_W;
            int y = MATTER_ORIGIN_Y + row * MATTER_SLOT_H;
            int itemX = x + 1;
            int itemY = y;

            if (slot.type() != null) {
                var displayItem = MatterTypeUtil.getDisplayItemForMatterType(slot.type());
                if (displayItem != null) {
                    // No itemDecorations — X/Y is drawn as overlay on the icon.
                    guiGraphics.renderItem(new ItemStack(displayItem), itemX, itemY);
                }
            }

            long xAmt = (long) Math.floor(slot.buffered());
            long yAmt = showRequired ? (long) Math.ceil(slot.required()) : 0L;
            // Always X/Y while a recipe requirement exists (including 0/Y).
            String label = yAmt > 0
                    ? formatCompact(xAmt) + "/" + formatCompact(yAmt)
                    : formatCompact(xAmt);
            // Overlay on the icon (bottom of 16×16), same style as 26 GUI.
            drawSmallWhiteLabel(guiGraphics, label, itemX, itemY + 11);

            if (relMouseX >= x && relMouseX < x + MATTER_SLOT_W
                    && relMouseY >= y && relMouseY < y + MATTER_SLOT_H) {
                String name = slot.type() != null ? slot.type().getName() : "?";
                String full = yAmt > 0 ? (xAmt + "/" + yAmt) : String.valueOf(xAmt);
                // Relative coords: pose is already translated in renderLabels.
                drawTooltip(guiGraphics, relMouseX, relMouseY, List.of(Component.literal(name + ": " + full)));
            }
        }
    }

    /**
     * Uses menu input slots (container-synced) so stale BE {@code matterRequired} cannot show Y
     * when the player has already cleared/changed inputs.
     */
    private boolean menuHasValidFoundryRecipe() {
        if (getPlayer().level() == null) {
            return false;
        }
        List<Slot> inputs = menu.getFoundryInputSlots();
        ItemStack[] stacks = new ItemStack[inputs.size()];
        boolean hasAny = false;
        for (int i = 0; i < inputs.size(); i++) {
            stacks[i] = inputs.get(i).getItem();
            if (!stacks[i].isEmpty()) {
                hasAny = true;
            }
        }
        if (!hasAny) {
            return false;
        }
        MatterFoundryRecipeInput input = new MatterFoundryRecipeInput(stacks);
        var level = getPlayer().level();
        for (var holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.MATTER_FOUNDRY_TYPE.get())) {
            if (holder.value().matches(input, level)) {
                return true;
            }
        }
        return false;
    }

    private void drawSmallWhiteLabel(GuiGraphics guiGraphics, String label, int x, int y) {
        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 200);
        poseStack.scale(LABEL_SCALE, LABEL_SCALE, 1.0f);
        guiGraphics.drawString(this.font, label, 0, 0, LABEL_COLOR, true);
        poseStack.popPose();
    }

    /** Compact amount: 999, 1.2k, 15M, 3.4B, 1T (American short scale). */
    static String formatCompact(long amount) {
        long value = Math.max(0L, amount);
        if (value < 1_000L) {
            return Long.toString(value);
        }
        double scaled = value;
        int suffix = -1;
        while (scaled >= 1_000d && suffix < COMPACT_SUFFIXES.length - 1) {
            scaled /= 1_000d;
            suffix++;
        }
        String number;
        if (scaled >= 100d) {
            number = String.format(Locale.ROOT, "%.0f", scaled);
        } else if (scaled >= 10d) {
            number = String.format(Locale.ROOT, "%.1f", scaled);
            if (number.endsWith(".0")) {
                number = number.substring(0, number.length() - 2);
            }
        } else {
            number = String.format(Locale.ROOT, "%.1f", scaled);
            if (number.endsWith(".0")) {
                number = number.substring(0, number.length() - 2);
            }
        }
        return number + COMPACT_SUFFIXES[suffix];
    }

    @Override
    @Nullable
    protected PageAnchor getHelpTopic() {
        return AddonGuideHelp.topicForTarget(getMenu().getTarget());
    }

    @Override
    protected void openHelp() {
        AddonGuideHelp.open(getPlayer(), getHelpTopic());
    }
}
