package net.unfamily.repae2bridge.client.gui;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AECheckbox;

import guideme.PageAnchor;

import net.unfamily.repae2bridge.menu.BridgeMenu;

/**
 * Bridge screen: energy-feed lever (AECheckbox) + priority tab; no player inventory.
 */
public class BridgeScreen extends AEBaseScreen<BridgeMenu> {

    private final AECheckbox feedEnergyCheckbox;

    public BridgeScreen(BridgeMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.feedEnergyCheckbox = widgets.addCheckbox(
                "feedEnergy",
                Component.translatable("gui.rep_ae2_bridge.feed_replication_energy"),
                () -> {
                });
        this.feedEnergyCheckbox.setChangeListener(
                () -> menu.setFeedReplicationEnergy(this.feedEnergyCheckbox.isSelected()));
        widgets.addOpenPriorityButton();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.feedEnergyCheckbox.setSelected(menu.isFeedReplicationEnergy());
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
