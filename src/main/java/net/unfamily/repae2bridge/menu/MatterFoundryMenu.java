package net.unfamily.repae2bridge.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.OutputSlot;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.entity.MatterFoundryBlockEntity;

/**
 * Foundry GUI: 5 input slots + 1 output + upgrade panel + progress/energy sync + matter dump.
 */
public class MatterFoundryMenu extends UpgradeableMenu<MatterFoundryBlockEntity> implements IProgressProvider {

    public static final MenuType<MatterFoundryMenu> TYPE = MenuTypeBuilder
            .create(MatterFoundryMenu::new, MatterFoundryBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_foundry"));

    public static final SlotSemantic FOUNDRY_IN_0 = SlotSemantics.register("FOUNDRY_IN_0", false);
    public static final SlotSemantic FOUNDRY_IN_1 = SlotSemantics.register("FOUNDRY_IN_1", false);
    public static final SlotSemantic FOUNDRY_IN_2 = SlotSemantics.register("FOUNDRY_IN_2", false);
    public static final SlotSemantic FOUNDRY_IN_3 = SlotSemantics.register("FOUNDRY_IN_3", false);
    public static final SlotSemantic FOUNDRY_IN_4 = SlotSemantics.register("FOUNDRY_IN_4", false);

    private static final String ACTION_DUMP_MATTER = "dumpMatterBuffer";

    // UpgradeableMenu already uses sync ids 0,1,5,6 — keep Foundry ids clear of those.
    @GuiSync(10)
    public int craftingProgress;

    @GuiSync(11)
    public int craftingMaxProgress;

    @GuiSync(12)
    public int energyStored;

    @GuiSync(13)
    public int energyCapacity;

    @GuiSync(14)
    public YesNo autoExport = YesNo.NO;

    public MatterFoundryMenu(int id, Inventory playerInventory, MatterFoundryBlockEntity host) {
        super(TYPE, id, playerInventory, host);

        registerClientAction(ACTION_DUMP_MATTER, this::dumpMatterBufferServer);

        var inputs = host.getInputInventory();
        this.addSlot(new AppEngSlot(inputs, 0), FOUNDRY_IN_0);
        this.addSlot(new AppEngSlot(inputs, 1), FOUNDRY_IN_1);
        this.addSlot(new AppEngSlot(inputs, 2), FOUNDRY_IN_2);
        this.addSlot(new AppEngSlot(inputs, 3), FOUNDRY_IN_3);
        this.addSlot(new AppEngSlot(inputs, 4), FOUNDRY_IN_4);
        this.addSlot(new OutputSlot(host.getOutputInventory(), 0, null), SlotSemantics.MACHINE_OUTPUT);
    }

    /** Called from the GUI dump button (client sends action; server clears buffer). */
    public void dumpMatterBuffer() {
        if (isClientSide()) {
            sendClientAction(ACTION_DUMP_MATTER);
            return;
        }
        dumpMatterBufferServer();
    }

    private void dumpMatterBufferServer() {
        if (getHost() != null) {
            getHost().dumpMatterBuffer();
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        // Only AUTO_EXPORT is registered on the Foundry (do not call super — it expects fuzzy/redstone).
        this.autoExport = cm.getSetting(Settings.AUTO_EXPORT);
    }

    public YesNo getAutoExport() {
        return autoExport;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && getHost() != null) {
            craftingProgress = getHost().getProgress();
            craftingMaxProgress = getHost().getMaxProgress();
            energyStored = getHost().getFeStored();
            energyCapacity = Math.max(1, getHost().getFeCapacity());
        }
        super.broadcastChanges();
    }

    @Override
    public int getCurrentProgress() {
        return craftingProgress;
    }

    @Override
    public int getMaxProgress() {
        return Math.max(1, craftingMaxProgress);
    }

    public int getEnergyStored() {
        return energyStored;
    }

    public int getEnergyCapacity() {
        return Math.max(1, energyCapacity);
    }

    /** Item input slots only (no matter buffer) for JEI/EMI/REI + transfer. */
    public List<Slot> getFoundryInputSlots() {
        List<Slot> slots = new ArrayList<>(5);
        slots.addAll(getSlots(FOUNDRY_IN_0));
        slots.addAll(getSlots(FOUNDRY_IN_1));
        slots.addAll(getSlots(FOUNDRY_IN_2));
        slots.addAll(getSlots(FOUNDRY_IN_3));
        slots.addAll(getSlots(FOUNDRY_IN_4));
        return slots;
    }

    /** Player inventory + hotbar used as ingredient sources for recipe transfer. */
    public List<Slot> getPlayerSlotsForTransfer() {
        List<Slot> slots = new ArrayList<>(36);
        slots.addAll(getSlots(SlotSemantics.PLAYER_INVENTORY));
        slots.addAll(getSlots(SlotSemantics.PLAYER_HOTBAR));
        return slots;
    }
}
