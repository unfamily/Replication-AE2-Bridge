package net.unfamily.repae2bridge.item;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;

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

/**
 * Registry handler for all items in the mod
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, RepAE2Bridge.MOD_ID);

    // Creative Mode Tabs
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RepAE2Bridge.MOD_ID);

    // Earth Matter
    public static final DeferredHolder<Item, Item> EARTH_MATTER = ITEMS.register("earth", 
        () -> new MatterItem(new Item.Properties()));
    
    // Nether Matter
    public static final DeferredHolder<Item, Item> NETHER_MATTER = ITEMS.register("nether", 
        () -> new MatterItem(new Item.Properties()));
        
    // Organic Matter
    public static final DeferredHolder<Item, Item> ORGANIC_MATTER = ITEMS.register("organic", 
        () -> new MatterItem(new Item.Properties()));
        
    // Ender Matter
    public static final DeferredHolder<Item, Item> ENDER_MATTER = ITEMS.register("ender", 
        () -> new MatterItem(new Item.Properties()));
        
    // Metallic Matter
    public static final DeferredHolder<Item, Item> METALLIC_MATTER = ITEMS.register("metallic", 
        () -> new MatterItem(new Item.Properties()));
        
    // Precious Matter
    public static final DeferredHolder<Item, Item> PRECIOUS_MATTER = ITEMS.register("precious", 
        () -> new MatterItem(new Item.Properties()));
        
    // Living Matter
    public static final DeferredHolder<Item, Item> LIVING_MATTER = ITEMS.register("living", 
        () -> new MatterItem(new Item.Properties()));
        
    // Quantum Matter
    public static final DeferredHolder<Item, Item> QUANTUM_MATTER = ITEMS.register("quantum",
        () -> new MatterItem(new Item.Properties()));

    // Our custom creative tab (defined after items to avoid forward reference issues)
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> REP_AE2_BRIDGE_TAB = CREATIVE_MODE_TABS.register("rep_ae2_bridge_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rep_ae2_bridge.rep_ae2_bridge_tab"))
            .icon(() -> new ItemStack(ModBlocks.REPAE2BRIDGE.get()))
            .displayItems((parameters, output) -> {
                // Add the bridge block
                output.accept(ModBlocks.REPAE2BRIDGE.get());

                // Add all matter items
                output.accept(EARTH_MATTER.get());
                output.accept(NETHER_MATTER.get());
                output.accept(ORGANIC_MATTER.get());
                output.accept(ENDER_MATTER.get());
                output.accept(METALLIC_MATTER.get());
                output.accept(PRECIOUS_MATTER.get());
                output.accept(LIVING_MATTER.get());
                output.accept(QUANTUM_MATTER.get());
            })
            .build());

    /**
     * Register all items and creative tabs
     * @param eventBus The mod event bus
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
