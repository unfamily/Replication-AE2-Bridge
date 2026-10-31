package net.unfamily.repae2bridge.item;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry handler for all items in the mod.
 * Custom matter items (from KubeJS / other addons) are registered by
 * {@link CustomMatterAutoRegistrar} during the matter-types RegisterEvent.
 */
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RepAE2Bridge.MOD_ID);

    /** Dynamically registered custom matter items (registry path -> Item). */
    public static final Map<String, Item> CUSTOM_MATTER_ITEMS = new HashMap<>();

    public static final DeferredItem<Item> EARTH_MATTER = ITEMS.registerItem("earth", MatterItem::new);

    public static final DeferredItem<Item> NETHER_MATTER = ITEMS.registerItem("nether", MatterItem::new);

    public static final DeferredItem<Item> ORGANIC_MATTER = ITEMS.registerItem("organic", MatterItem::new);

    public static final DeferredItem<Item> ENDER_MATTER = ITEMS.registerItem("ender", MatterItem::new);

    public static final DeferredItem<Item> METALLIC_MATTER = ITEMS.registerItem("metallic", MatterItem::new);

    public static final DeferredItem<Item> PRECIOUS_MATTER = ITEMS.registerItem("precious", MatterItem::new);

    public static final DeferredItem<Item> LIVING_MATTER = ITEMS.registerItem("living", MatterItem::new);

    public static final DeferredItem<Item> QUANTUM_MATTER = ITEMS.registerItem("quantum", MatterItem::new);

    public static final DeferredItem<Item> MATTER_CELL_HOUSING = ITEMS.registerItem("matter_cell_housing", Item::new);

    public static final DeferredItem<Item> FLUIXING_REPLICA_ALLOY = ITEMS.registerItem("fluixing_replica_alloy", Item::new);

    public static final DeferredItem<Item> REPLICA_PROCESSOR = ITEMS.registerItem("replica_processor", Item::new);

    public static final DeferredItem<Item> MATTER_INFUSED_CRYSTAL =
            ITEMS.registerItem("matter_infused_crystal", Item::new);

    /** MEGACells-style upgraded housing (`mega_*_cell_housing`). */
    public static final DeferredItem<Item> MEGA_MATTER_CELL_HOUSING =
            ITEMS.registerItem("mega_matter_cell_housing", Item::new);

    // MatterKeyType stores 8 units/byte (same as items). Keep ~1K/4K/... matter capacity by
    // using AE2_bytes = matter_capacity / 8, and bytesPerType = 1 (8 matter of type overhead).
    public static final DeferredItem<MatterStorageCell> MATTER_CELL_1K = ITEMS.registerItem(
            "matter_storage_cell_1k",
            p -> new MatterStorageCell(p.stacksTo(1), 0.5, 1 * 1024 / 8, 1));

    public static final DeferredItem<MatterStorageCell> MATTER_CELL_4K = ITEMS.registerItem(
            "matter_storage_cell_4k",
            p -> new MatterStorageCell(p.stacksTo(1), 1.0, 4 * 1024 / 8, 1));

    public static final DeferredItem<MatterStorageCell> MATTER_CELL_16K = ITEMS.registerItem(
            "matter_storage_cell_16k",
            p -> new MatterStorageCell(p.stacksTo(1), 1.5, 16 * 1024 / 8, 1));

    public static final DeferredItem<MatterStorageCell> MATTER_CELL_64K = ITEMS.registerItem(
            "matter_storage_cell_64k",
            p -> new MatterStorageCell(p.stacksTo(1), 2.0, 64 * 1024 / 8, 1));

    public static final DeferredItem<MatterStorageCell> MATTER_CELL_256K = ITEMS.registerItem(
            "matter_storage_cell_256k",
            p -> new MatterStorageCell(p.stacksTo(1), 2.5, 256 * 1024 / 8, 1));

    public static final DeferredItem<MatterCreativeCellItem> MATTER_CELL_CREATIVE = ITEMS.registerItem(
            "matter_storage_cell_creative",
            p -> new MatterCreativeCellItem(p.stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
