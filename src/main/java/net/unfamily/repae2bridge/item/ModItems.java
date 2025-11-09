package net.unfamily.repae2bridge.item;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.unfamily.repae2bridge.block.ModBlocks;

/**
 * Base class for all virtual matter items.
 * These items will disappear if left in the player's inventory.
 */
class MatterItem extends Item {
    public MatterItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // Makes the item disappear if it's in a player's inventory
        if (entity instanceof Player && !level.isClientSide()) {
            // Remove the item from inventory
            stack.setCount(0);
        }
    }
}

@Mod.EventBusSubscriber(modid = RepAE2Bridge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RepAE2Bridge.MOD_ID);

    // Creative Tab Resource Key
    public static final ResourceKey<CreativeModeTab> REP_AE2_BRIDGE_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation(RepAE2Bridge.MOD_ID, "rep_ae2_bridge_tab"));

    @SubscribeEvent
    public static void registerTabs(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            helper.register(REP_AE2_BRIDGE_TAB, CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rep_ae2_bridge.rep_ae2_bridge_tab"))
                    .icon(() -> new ItemStack(ModBlocks.REP_AE2_BRIDGE_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        // Add all matter items
                        output.accept(ModItems.EARTH_MATTER.get());
                        output.accept(ModItems.NETHER_MATTER.get());
                        output.accept(ModItems.ORGANIC_MATTER.get());
                        output.accept(ModItems.ENDER_MATTER.get());
                        output.accept(ModItems.METALLIC_MATTER.get());
                        output.accept(ModItems.PRECIOUS_MATTER.get());
                        output.accept(ModItems.LIVING_MATTER.get());
                        output.accept(ModItems.QUANTUM_MATTER.get());
                        // Add bridge block
                        output.accept(ModBlocks.REP_AE2_BRIDGE_ITEM.get());
                    })
                    .build());
        });
    }

    // Earth Matter
    public static final RegistryObject<Item> EARTH_MATTER = ITEMS.register("earth",
            () -> new MatterItem(new Item.Properties()));

    // Nether Matter
    public static final RegistryObject<Item> NETHER_MATTER = ITEMS.register("nether",
            () -> new MatterItem(new Item.Properties()));

    // Organic Matter
    public static final RegistryObject<Item> ORGANIC_MATTER = ITEMS.register("organic",
            () -> new MatterItem(new Item.Properties()));

    // Ender Matter
    public static final RegistryObject<Item> ENDER_MATTER = ITEMS.register("ender",
            () -> new MatterItem(new Item.Properties()));

    // Metallic Matter
    public static final RegistryObject<Item> METALLIC_MATTER = ITEMS.register("metallic",
            () -> new MatterItem(new Item.Properties()));

    // Precious Matter
    public static final RegistryObject<Item> PRECIOUS_MATTER = ITEMS.register("precious",
            () -> new MatterItem(new Item.Properties()));

    // Living Matter
    public static final RegistryObject<Item> LIVING_MATTER = ITEMS.register("living",
            () -> new MatterItem(new Item.Properties()));

    // Quantum Matter
    public static final RegistryObject<Item> QUANTUM_MATTER = ITEMS.register("quantum",
            () -> new QuantumMatterItem(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
