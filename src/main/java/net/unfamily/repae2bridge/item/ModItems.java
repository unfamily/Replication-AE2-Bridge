package net.unfamily.repae2bridge.item;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.data.ReplicationBridgeLoader;
import com.buuz135.replication.ReplicationRegistry;
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
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

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
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, RepAE2Bridge.MOD_ID);

    // Map to store dynamically registered custom matter items
    public static final Map<String, DeferredHolder<Item, Item>> CUSTOM_MATTER_ITEMS = new HashMap<>();

    // Map to store expected matter type bindings (itemId -> expected matterId)
    private static final Map<String, String> EXPECTED_MATTER_BINDINGS = new HashMap<>();

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

                // Add custom matter items from configuration
                for (DeferredHolder<Item, Item> customItem : CUSTOM_MATTER_ITEMS.values()) {
                    output.accept(customItem.get());
                }
            })
            .build());

    /**
     * Register custom matter items from bridge matter declaration files.
     * This method loads definitions from JSON files in the kubejs/startup_scripts directory.
     *
     * Phase 1: Pre-register items (called during mod construction)
     * Phase 2: Bind items to matter types (called during common setup)
     */
    public static void registerCustomMatterItems() {
        LOGGER.info("=== registerCustomMatterItems() CALLED ===");

        CUSTOM_MATTER_ITEMS.clear(); // Clear any previous registrations

        try {
            // Scan the default external scripts directory for replication bridge definitions
            ReplicationBridgeLoader.scanExternalScriptsDirectory();

            // Get all matter definitions from the loader
            var matterDefinitions = ReplicationBridgeLoader.getMatterDefinitions();

            if (matterDefinitions.isEmpty()) {
                LOGGER.info("No custom matter definitions found in external scripts");
                LOGGER.info("=== Custom matter item registration completed. Registered {} items ===", CUSTOM_MATTER_ITEMS.size());
                return;
            }

            LOGGER.info("Found {} custom matter definitions in external scripts", matterDefinitions.size());

            // Register items for each matter definition
            for (var entry : matterDefinitions.entrySet()) {
                String registryName = entry.getKey(); // e.g., "kjs_crystal"
                String matterId = entry.getValue();    // e.g., "kubejs:crystal"

                try {
                    // Parse the matter ID to get the path (e.g., "kubejs:crystal" -> "crystal")
                    ResourceLocation rl = ResourceLocation.parse(matterId);
                    String itemName = registryName; // Use the registry name directly (already processed)

                    LOGGER.info("Attempting to register item '{}' for matter '{}'", itemName, matterId);

                    // Register the item (without matter binding for now)
                    DeferredHolder<Item, Item> holder = ITEMS.register(itemName,
                        () -> new CustomMatterItem(new Item.Properties(), itemName));

                    CUSTOM_MATTER_ITEMS.put(itemName, holder); // Key by item name, not matter ID
                    EXPECTED_MATTER_BINDINGS.put(itemName, matterId); // Store expected binding

                    // Log the registration
                    String displayName = generateDisplayName(rl.getPath());
                    LOGGER.info("SUCCESS: Pre-registered custom matter item '{}' (will bind to '{}' later). Add to lang file: \"item.rep_ae2_bridge.{}\": \"{}\"",
                        itemName, matterId, itemName, displayName);

                } catch (Exception e) {
                    // Log error but continue with other items
                    LOGGER.error("FAILED: Could not register custom matter item for: {} -> {}", registryName, matterId, e);
                }
            }

            LOGGER.info("=== Custom matter item registration completed. Registered {} items ===", CUSTOM_MATTER_ITEMS.size());
            LOGGER.info("Available custom matter items: {}", CUSTOM_MATTER_ITEMS.keySet());

        } catch (Exception e) {
            LOGGER.error("Error during custom matter item registration", e);
        }
    }

    /**
     * Generate a human-readable display name from a matter type path.
     * @param path The path part of the matter ID (e.g., "crystal")
     * @return A capitalized display name (e.g., "Crystal Matter")
     */
    private static String generateDisplayName(String path) {
        // Replace underscores with spaces and capitalize words
        String formatted = path.replace("_", " ");
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                if (result.length() > 0) result.append(" ");
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString() + " Matter";
    }

    /**
     * Register all items and creative tabs
     * @param eventBus The mod event bus
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
