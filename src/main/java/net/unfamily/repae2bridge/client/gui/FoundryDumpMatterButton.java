package net.unfamily.repae2bridge.client.gui;

import java.util.List;

import net.minecraft.network.chat.Component;

import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.Icon;

/**
 * Left-toolbar button under help: voids all Foundry buffered matter.
 * Icon = 8th state in first column of {@code ae2:textures/guis/states.png}
 * ({@link Icon#CONDENSER_OUTPUT_TRASH}).
 */
public class FoundryDumpMatterButton extends IconButton {

    public FoundryDumpMatterButton(Runnable onPress) {
        super(btn -> onPress.run());
        setMessage(Component.translatable("gui.rep_ae2_bridge.matter_foundry.dump"));
    }

    @Override
    protected Icon getIcon() {
        return Icon.CONDENSER_OUTPUT_TRASH;
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("gui.rep_ae2_bridge.matter_foundry.dump"),
                Component.translatable("gui.rep_ae2_bridge.matter_foundry.dump.desc"));
    }
}
