package net.unfamily.repae2bridge.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.compat.MegaCellsCompat;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RepAE2Bridge.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> REP_AE2_BRIDGE_TAB =
            CREATIVE_MODE_TABS.register("rep_ae2_bridge_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.rep_ae2_bridge.rep_ae2_bridge_tab"))
                            .icon(() -> new ItemStack(ModBlocks.REPAE2BRIDGE.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.EARTH_MATTER.get());
                                output.accept(ModItems.NETHER_MATTER.get());
                                output.accept(ModItems.ORGANIC_MATTER.get());
                                output.accept(ModItems.ENDER_MATTER.get());
                                output.accept(ModItems.METALLIC_MATTER.get());
                                output.accept(ModItems.PRECIOUS_MATTER.get());
                                output.accept(ModItems.LIVING_MATTER.get());
                                output.accept(ModItems.QUANTUM_MATTER.get());
                                for (var custom : ModItems.CUSTOM_MATTER_ITEMS.values()) {
                                    output.accept(custom);
                                }
                                output.accept(ModBlocks.REPAE2BRIDGE.get());
                                output.accept(ModBlocks.MATTER_ASSEMBLER.get());
                                output.accept(ModBlocks.MATTER_FOUNDRY.get());
                                output.accept(ModBlocks.CHAMBER_FRAME.get());
                                output.accept(ModBlocks.REPLICATION_UNIT.get());
                                output.accept(ModItems.FLUIXING_REPLICA_ALLOY.get());
                                output.accept(ModItems.REPLICA_PROCESSOR.get());
                                output.accept(ModItems.MATTER_CELL_HOUSING.get());
                                if (MegaCellsCompat.isLoaded()) {
                                    output.accept(ModItems.MEGA_MATTER_CELL_HOUSING.get());
                                }
                                output.accept(ModItems.MATTER_CELL_1K.get());
                                output.accept(ModItems.MATTER_CELL_4K.get());
                                output.accept(ModItems.MATTER_CELL_16K.get());
                                output.accept(ModItems.MATTER_CELL_64K.get());
                                output.accept(ModItems.MATTER_CELL_256K.get());
                                output.accept(ModItems.MATTER_CELL_CREATIVE.get());
                                if (ModList.get().isLoaded("guideme")) {
                                    output.accept(guideme.Guides.createGuideItem(
                                            Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "guide")));
                                }
                            })
                            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
