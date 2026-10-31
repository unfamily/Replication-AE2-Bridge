package net.unfamily.repae2bridge.menu;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

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
 * Foundry GUI: 5 input slots + 1 output + upgrade panel + progress/energy sync.
 */
public class MatterFoundryMenu extends UpgradeableMenu<MatterFoundryBlockEntity> implements IProgressProvider {

    public static final MenuType<MatterFoundryMenu> TYPE = MenuTypeBuilder
            .create(MatterFoundryMenu::new, MatterFoundryBlockEntity.class)
            .buildUnregistered(Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_foundry"));

    public static final SlotSemantic FOUNDRY_IN_0 = SlotSemantics.register("FOUNDRY_IN_0", false);
    public static final SlotSemantic FOUNDRY_IN_1 = SlotSemantics.register("FOUNDRY_IN_1", false);
    public static final SlotSemantic FOUNDRY_IN_2 = SlotSemantics.register("FOUNDRY_IN_2", false);
    public static final SlotSemantic FOUNDRY_IN_3 = SlotSemantics.register("FOUNDRY_IN_3", false);
    public static final SlotSemantic FOUNDRY_IN_4 = SlotSemantics.register("FOUNDRY_IN_4", false);

    // UpgradeableMenu already uses sync ids 0,1,5,6 — keep Foundry ids clear of those.
    @GuiSync(10)
    public int craftingProgress;

    @GuiSync(11)
    public int craftingMaxProgress;

    @GuiSync(12)
    public int energyStored;

    @GuiSync(13)
    public int energyCapacity;

    public MatterFoundryMenu(int id, Inventory playerInventory, MatterFoundryBlockEntity host) {
        super(TYPE, id, playerInventory, host);

        var inputs = host.getInputInventory();
        this.addSlot(new AppEngSlot(inputs, 0), FOUNDRY_IN_0);
        this.addSlot(new AppEngSlot(inputs, 1), FOUNDRY_IN_1);
        this.addSlot(new AppEngSlot(inputs, 2), FOUNDRY_IN_2);
        this.addSlot(new AppEngSlot(inputs, 3), FOUNDRY_IN_3);
        this.addSlot(new AppEngSlot(inputs, 4), FOUNDRY_IN_4);
        this.addSlot(new OutputSlot(host.getOutputInventory(), 0, null), SlotSemantics.MACHINE_OUTPUT);
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

    /** Progress provider for the FE bar (0..capacity). */
    public IProgressProvider getEnergyProgressProvider() {
        return energyProgressProvider;
    }

    private final IProgressProvider energyProgressProvider = new IProgressProvider() {
        @Override
        public int getCurrentProgress() {
            return energyStored;
        }

        @Override
        public int getMaxProgress() {
            return Math.max(1, energyCapacity);
        }
    };
}
