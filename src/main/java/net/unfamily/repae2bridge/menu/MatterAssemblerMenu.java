package net.unfamily.repae2bridge.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.UpgradeableMenu;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.entity.MatterAssemblerBlockEntity;

/**
 * Minimal GUI: acceleration-card upgrades + player inventory. Priority is a sub-menu.
 */
public class MatterAssemblerMenu extends UpgradeableMenu<MatterAssemblerBlockEntity> {

    public static final MenuType<MatterAssemblerMenu> TYPE = MenuTypeBuilder
            .create(MatterAssemblerMenu::new, MatterAssemblerBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_assembler"));

    public MatterAssemblerMenu(int id, Inventory playerInventory, MatterAssemblerBlockEntity host) {
        super(TYPE, id, playerInventory, host);
    }
}
