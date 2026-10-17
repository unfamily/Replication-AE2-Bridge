package net.unfamily.repae2bridge.client.gui;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;

import guideme.PageAnchor;

import net.unfamily.repae2bridge.menu.MatterAssemblerMenu;

/**
 * Matter Assembler: upgrade panel + priority tab + player inventory.
 */
public class MatterAssemblerScreen extends UpgradeableScreen<MatterAssemblerMenu> {

    public MatterAssemblerScreen(MatterAssemblerMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        widgets.addOpenPriorityButton();
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
