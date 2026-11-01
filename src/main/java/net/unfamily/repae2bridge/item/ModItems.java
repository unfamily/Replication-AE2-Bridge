package net.unfamily.repae2bridge.item;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.parts.CableRepAe2BridgePart;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;

import appeng.api.parts.PartModels;
import appeng.items.parts.PartItem;
import appeng.items.parts.PartModelsHelper;

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

    public static final DeferredHolder<Item, Item> FLUIXING_REPLICA_ALLOY = ITEMS.register("fluixing_replica_alloy",
        () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, PartItem<CableRepAe2BridgePart>> CABLE_REP_AE2_BRIDGE = ITEMS.register(
            "cable_rep_ae2_bridge",
            () -> new PartItem<>(new Item.Properties(), CableRepAe2BridgePart.class, CableRepAe2BridgePart::new));

    static {
        PartModels.registerModels(PartModelsHelper.createModels(CableRepAe2BridgePart.class));
    }

    public static final DeferredHolder<Item, Item> REPLICA_PROCESSOR = ITEMS.register("replica_processor",
        () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> MATTER_INFUSED_CRYSTAL = ITEMS.register("matter_infused_crystal",
        () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> MATTER_CELL_HOUSING = ITEMS.register("matter_cell_housing",
        () -> new Item(new Item.Properties()));

    /** MEGA Cells housing (shown/craftable when megacells is loaded). */
    public static final DeferredHolder<Item, Item> MEGA_MATTER_CELL_HOUSING = ITEMS.register("mega_matter_cell_housing",
        () -> new Item(new Item.Properties()));

    // MatterKeyType stores 8 units/byte. AE2_bytes = matter_capacity / 8, bytesPerType = 1.
    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_1K = ITEMS.register(
            "matter_storage_cell_1k",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 0.5, 1 * 1024 / 8, 1));

    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_4K = ITEMS.register(
            "matter_storage_cell_4k",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 1.0, 4 * 1024 / 8, 1));

    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_16K = ITEMS.register(
            "matter_storage_cell_16k",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 1.5, 16 * 1024 / 8, 1));

    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_64K = ITEMS.register(
            "matter_storage_cell_64k",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 2.0, 64 * 1024 / 8, 1));

    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_256K = ITEMS.register(
            "matter_storage_cell_256k",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 2.5, 256 * 1024 / 8, 1));

    public static final DeferredHolder<Item, MatterCreativeCellItem> MATTER_CELL_CREATIVE = ITEMS.register(
            "matter_storage_cell_creative",
            () -> new MatterCreativeCellItem(new Item.Properties().stacksTo(1)));

    // MEGA tiers (1M–256M matter capacity) — gated in creative/recipes via MegaCellsCompat
    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_1M = ITEMS.register(
            "mega_matter_storage_cell_1m",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 3.0, 1024 * 1024 / 8, 1));

    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_4M = ITEMS.register(
            "mega_matter_storage_cell_4m",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 3.5, 4 * 1024 * 1024 / 8, 1));

    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_16M = ITEMS.register(
            "mega_matter_storage_cell_16m",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 4.0, 16 * 1024 * 1024 / 8, 1));

    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_64M = ITEMS.register(
            "mega_matter_storage_cell_64m",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 4.5, 64 * 1024 * 1024 / 8, 1));

    public static final DeferredHolder<Item, MatterStorageCell> MATTER_CELL_256M = ITEMS.register(
            "mega_matter_storage_cell_256m",
            () -> new MatterStorageCell(new Item.Properties().stacksTo(1), 5.0, 256 * 1024 * 1024 / 8, 1));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
