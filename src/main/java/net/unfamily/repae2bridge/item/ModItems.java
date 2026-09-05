package net.unfamily.repae2bridge.item;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry handler for all items in the mod.
 * Custom matter items (from KubeJS / other addons) are registered by
 * {@link CustomMatterAutoRegistrar} during the matter-types RegisterEvent.
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, RepAE2Bridge.MOD_ID);

    /** Dynamically registered custom matter items (registry path -> Item). */
    public static final Map<String, Item> CUSTOM_MATTER_ITEMS = new HashMap<>();

    public static final DeferredHolder<Item, Item> EARTH_MATTER = ITEMS.register("earth",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> NETHER_MATTER = ITEMS.register("nether",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> ORGANIC_MATTER = ITEMS.register("organic",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> ENDER_MATTER = ITEMS.register("ender",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> METALLIC_MATTER = ITEMS.register("metallic",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> PRECIOUS_MATTER = ITEMS.register("precious",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> LIVING_MATTER = ITEMS.register("living",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> QUANTUM_MATTER = ITEMS.register("quantum",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> UNIVERSAL_MATTER = ITEMS.register("universal_matter",
        () -> new UniversalMatterItem(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
