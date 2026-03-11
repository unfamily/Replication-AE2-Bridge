package net.unfamily.repae2bridge.util;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import net.unfamily.repae2bridge.events.ServerLifecycleEventHandler;
import net.unfamily.repae2bridge.item.ModItems;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for managing dynamic matter types from the Replication registry.
 * Provides information about matter types and their corresponding dynamic items.
 */
public class MatterTypeUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, MatterTypeInfo> MATTER_CACHE = new HashMap<>();
    private static final Map<IMatterType, MatterTypeInfo> MATTER_BY_TYPE = new HashMap<>();

    /**
     * Loads all matter types from the Replication registry.
     * This should be called after dynamic items are registered.
     */
    public static void loadAllMatters() {
        MATTER_CACHE.clear();
        MATTER_BY_TYPE.clear();

        try {
            Registry<IMatterType> registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
            if (registry == null) {
                LOGGER.warn("RepAE2Bridge: Matter type registry not ready, skipping matter info loading for now.");
                return;
            }

            for (var entry : registry.entrySet()) {
                ResourceLocation id = entry.getKey().location();
                IMatterType matterType = entry.getValue();

                String name = matterType.getName();
                float[] color = matterType.getColor().get();
                ResourceLocation texture = resolveTexture(id, name);

                MatterTypeInfo info = new MatterTypeInfo(name, texture, color, id, matterType);
                MATTER_CACHE.put(name, info);
                MATTER_BY_TYPE.put(matterType, info);

                if (!id.getNamespace().equals("replication")) {
                    LOGGER.info("RepAE2Bridge: Loaded custom matter '{}' from mod '{}' with texture '{}' and item '{}'",
                            name, id.getNamespace(), texture, DynamicMatterRegistry.getMatterItem(matterType));
                }
            }

            LOGGER.info("RepAE2Bridge: Loaded {} matter types with dynamic items.", MATTER_CACHE.size());
        } catch (Exception e) {
            LOGGER.error("RepAE2Bridge: Failed to load matter types", e);
        }
    }

    /**
     * Resolves the texture location for a matter type.
     * @param id The registry ID of the matter type
     * @param name The name of the matter type
     * @return The texture resource location
     */
    private static ResourceLocation resolveTexture(ResourceLocation id, String name) {
        return ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(),
                "gui/mattertypes/" + name.toLowerCase()
        );
    }

    /**
     * Gets all loaded matter types.
     * @return Map of matter name to MatterTypeInfo
     */
    public static Map<String, MatterTypeInfo> getAllMatters() {
        if (MATTER_CACHE.isEmpty()) {
            loadAllMatters();
        }
        return new HashMap<>(MATTER_CACHE);
    }

    /**
     * Gets matter info by name.
     * @param matterTypeName The name of the matter type
     * @return MatterTypeInfo or null if not found
     */
    public static MatterTypeInfo getMatterInfo(String matterTypeName) {
        if (MATTER_CACHE.isEmpty()) {
            loadAllMatters();
        }
        return MATTER_CACHE.get(matterTypeName);
    }

    /**
     * Gets matter info by IMatterType.
     * @param matterType The matter type instance
     * @return MatterTypeInfo or null if not found
     */
    public static MatterTypeInfo getMatterInfo(IMatterType matterType) {
        if (MATTER_CACHE.isEmpty()) {
            loadAllMatters();
        }
        return MATTER_BY_TYPE.get(matterType);
    }

    /**
     * Returns true if the item is a matter item (built-in or custom).
     * Used by the AE2 terminal mixin to show matter items in the separate "Matter" tab.
     */
    public static boolean isMatterItem(Item item) {
        return getMatterTypeFromItem(item) != null;
    }

    /**
     * Gets the matter type from an item (built-in matter items and custom map).
     * Use this when you only have the Item; for Universal Matter use getMatterTypeFromItemStack.
     */
    @org.jetbrains.annotations.Nullable
    public static IMatterType getMatterTypeFromItem(Item item) {
        if (item == null) return null;
        if (item == ModItems.EARTH_MATTER.get()) return ReplicationRegistry.Matter.EARTH.get();
        if (item == ModItems.NETHER_MATTER.get()) return ReplicationRegistry.Matter.NETHER.get();
        if (item == ModItems.ORGANIC_MATTER.get()) return ReplicationRegistry.Matter.ORGANIC.get();
        if (item == ModItems.ENDER_MATTER.get()) return ReplicationRegistry.Matter.ENDER.get();
        if (item == ModItems.METALLIC_MATTER.get()) return ReplicationRegistry.Matter.METALLIC.get();
        if (item == ModItems.PRECIOUS_MATTER.get()) return ReplicationRegistry.Matter.PRECIOUS.get();
        if (item == ModItems.LIVING_MATTER.get()) return ReplicationRegistry.Matter.LIVING.get();
        if (item == ModItems.QUANTUM_MATTER.get()) return ReplicationRegistry.Matter.QUANTUM.get();
        return ServerLifecycleEventHandler.getCustomItemToMatterMap().get(item);
    }

    /**
     * Gets the matter type from a dynamic matter item stack.
     * @param stack The item stack
     * @return IMatterType or null if not a matter item
     */
    public static IMatterType getMatterTypeFromItemStack(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        IMatterType fromItem = getMatterTypeFromItem(stack.getItem());
        if (fromItem != null) return fromItem;

        if (MATTER_CACHE.isEmpty()) {
            loadAllMatters();
        }

        return DynamicMatterRegistry.getMatterType(stack);
    }

    /**
     * Gets the matter type from a MatterComponent.
     * @param component The matter component
     * @return IMatterType or null if not found
     */
    public static IMatterType getMatterTypeFromComponent(net.unfamily.repae2bridge.component.MatterComponent component) {
        if (component == null) return null;

        if (MATTER_CACHE.isEmpty()) {
            loadAllMatters();
        }

        MatterTypeInfo info = MATTER_CACHE.get(component.matterTypeName());
        return info != null ? info.matterType() : null;
    }

    /**
     * Returns the item to use for displaying this matter type (same logic as bridge getItemForMatterType).
     * Built-in types use dedicated MatterItem; custom types use custom item when bound (server) or null (client);
     * fallback is Universal Matter (caller should then use UniversalMatterItem.createMatterStack with component).
     */
    @org.jetbrains.annotations.Nullable
    public static Item getDisplayItemForMatterType(IMatterType type) {
        if (type == null) return null;
        String name = type.getName();
        if (name == null) return ModItems.UNIVERSAL_MATTER.get();
        if (name.equalsIgnoreCase("earth")) return ModItems.EARTH_MATTER.get();
        if (name.equalsIgnoreCase("nether")) return ModItems.NETHER_MATTER.get();
        if (name.equalsIgnoreCase("organic")) return ModItems.ORGANIC_MATTER.get();
        if (name.equalsIgnoreCase("ender")) return ModItems.ENDER_MATTER.get();
        if (name.equalsIgnoreCase("metallic")) return ModItems.METALLIC_MATTER.get();
        if (name.equalsIgnoreCase("precious")) return ModItems.PRECIOUS_MATTER.get();
        if (name.equalsIgnoreCase("living")) return ModItems.LIVING_MATTER.get();
        if (name.equalsIgnoreCase("quantum")) return ModItems.QUANTUM_MATTER.get();
        Item custom = ServerLifecycleEventHandler.getCustomMatterToItemMap().get(type);
        if (custom != null) return custom;
        return ModItems.UNIVERSAL_MATTER.get();
    }
}
