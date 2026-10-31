package net.unfamily.repae2bridge.client.gui;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;

import guideme.PageAnchor;

import net.unfamily.repae2bridge.menu.MatterFoundryMenu;

/**
 * Matter Foundry screen: upgrades, priority, craft progress, FE energy bar.
 */
public class MatterFoundryScreen extends UpgradeableScreen<MatterFoundryMenu> {

    private final ProgressBar craftProgress;
    private final ProgressBar energyBar;

    public MatterFoundryScreen(MatterFoundryMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        widgets.addOpenPriorityButton();

        this.craftProgress = new ProgressBar(this.menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        widgets.add("progressBar", this.craftProgress);

        this.energyBar = new ProgressBar(menu.getEnergyProgressProvider(), style.getImage("energyBar"),
                ProgressBar.Direction.VERTICAL);
        widgets.add("energyBar", this.energyBar);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        int max = Math.max(1, menu.getMaxProgress());
        int cur = menu.getCurrentProgress();
        int pct = Math.min(100, cur * 100 / max);
        this.craftProgress.setFullMsg(Component.literal(pct + "%"));

        int eMax = Math.max(1, menu.getEnergyCapacity());
        int eCur = menu.getEnergyStored();
        this.energyBar.setFullMsg(Component.literal(eCur + " / " + eMax + " FE"));
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
