package net.unfamily.repae2bridge.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.items.EncodingModePanel;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;

import net.unfamily.repae2bridge.client.gui.MatterEncodingPanel;
import net.unfamily.repae2bridge.menu.MatterEncodingMenuAccess;

/**
 * Adds the Matter disintegration encoding tab to the AE2 Pattern Encoding Terminal.
 * Extends {@link AEBaseScreen} so the inherited {@code widgets} field is accessible.
 */
@Mixin(value = PatternEncodingTermScreen.class, remap = false)
public abstract class PatternEncodingTermScreenMixin extends AEBaseScreen<PatternEncodingTermMenu> {

    @Shadow
    @Final
    private Map<EncodingMode, EncodingModePanel> modePanels;

    @Shadow
    @Final
    private Map<EncodingMode, TabButton> modeTabButtons;

    @Unique
    private TabButton repAe2Bridge$matterTab;

    @Unique
    private MatterEncodingPanel repAe2Bridge$matterPanel;

    public PatternEncodingTermScreenMixin(PatternEncodingTermMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void repAe2Bridge$addMatterTab(PatternEncodingTermMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style, CallbackInfo ci) {
        Item blueprintItem = BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("replication", "matter_blueprint"));
        ItemStack blueprint = blueprintItem == null ? ItemStack.EMPTY : new ItemStack(blueprintItem);

        repAe2Bridge$matterPanel = new MatterEncodingPanel(
                (PatternEncodingTermScreen<?>) (Object) this, this.widgets);
        this.widgets.add("modePanel4", repAe2Bridge$matterPanel);

        Component tooltip = Component.translatable("gui.rep_ae2_bridge.matter_encoding");
        repAe2Bridge$matterTab = new TabButton(blueprint, tooltip, btn -> {
            if (menu instanceof MatterEncodingMenuAccess access) {
                access.repAe2Bridge$setMatterMode(true);
            }
        });
        repAe2Bridge$matterTab.setStyle(TabButton.Style.HORIZONTAL);
        this.widgets.add("modeTabButton4", repAe2Bridge$matterTab);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void repAe2Bridge$updateMatterTab(CallbackInfo ci) {
        PatternEncodingTermMenu menu = getMenu();
        boolean matterMode = menu instanceof MatterEncodingMenuAccess access && access.repAe2Bridge$isMatterMode();

        if (repAe2Bridge$matterTab != null) {
            repAe2Bridge$matterTab.setSelected(matterMode);
        }

        if (matterMode && modePanels != null) {
            // Hide AE2 panels first — StonecuttingEncodingPanel.setVisible(false) hides
            // STONECUTTING_INPUT; MatterEncodingPanel must re-show it afterwards.
            for (EncodingModePanel panel : modePanels.values()) {
                panel.setVisible(false);
            }
            if (modeTabButtons != null) {
                for (TabButton tab : modeTabButtons.values()) {
                    tab.setSelected(false);
                }
            }
        }

        if (repAe2Bridge$matterPanel != null) {
            repAe2Bridge$matterPanel.setVisible(matterMode);
        }
    }
}
