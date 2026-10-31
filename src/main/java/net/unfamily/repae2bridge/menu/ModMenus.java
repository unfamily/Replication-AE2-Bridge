package net.unfamily.repae2bridge.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.unfamily.repae2bridge.RepAE2Bridge;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, RepAE2Bridge.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<BridgeMenu>> BRIDGE =
            MENUS.register("bridge", () -> BridgeMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<MatterAssemblerMenu>> MATTER_ASSEMBLER =
            MENUS.register("matter_assembler", () -> MatterAssemblerMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<MatterFoundryMenu>> MATTER_FOUNDRY =
            MENUS.register("matter_foundry", () -> MatterFoundryMenu.TYPE);

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
