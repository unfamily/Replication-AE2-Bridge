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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for managing dynamic matter types from the Replication registry.
 * Provides information about matter types and their corresponding dynamic items.
 */
public class MatterTypeUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Primary cache keyed by registry id string (unique across mods). */
    private static final Map<String, MatterTypeInfo> MATTER_BY_ID = new HashMap<>();
    /** Name lookup (last wins on collision); kept for component / legacy callers. */
    private static final Map<String, MatterTypeInfo> MATTER_BY_NAME = new HashMap<>();
    private static final Map<IMatterType, MatterTypeInfo> MATTER_BY_TYPE = new HashMap<>();

    /**
     * Loads all matter types from the Replication registry.
     * This should be called after dynamic items are registered.
     */
    public static void loadAllMatters() {
        MATTER_BY_ID.clear();
        MATTER_BY_NAME.clear();
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
                MATTER_BY_ID.put(id.toString(), info);
                if (name != null) {
                    MATTER_BY_NAME.put(name, info);
                    MATTER_BY_NAME.put(name.toLowerCase(), info);
                }
                MATTER_BY_TYPE.put(matterType, info);

                if (!id.getNamespace().equals("replication")) {
                    LOGGER.info("RepAE2Bridge: Loaded custom matter '{}' ({}) texture='{}' item='{}'",
                            name, id, texture, DynamicMatterRegistry.getMatterItem(matterType));
                }
            }

            LOGGER.info("RepAE2Bridge: Loaded {} matter types ({} custom by non-replication namespace).",
                    MATTER_BY_ID.size(),
                    MATTER_BY_ID.values().stream().filter(i -> i.registryId() != null
                            && !i.registryId().getNamespace().equals("replication")).count());

            if (MATTER_BY_ID.isEmpty()) {
                LOGGER.warn("RepAE2Bridge: Matter type registry loaded empty — custom textures/tint will not apply.");
            }
        } catch (Exception e) {
            LOGGER.error("RepAE2Bridge: Failed to load matter types", e);
        }
    }

    /**
     * Atlas sprite id for a matter type.
     * Builtins: {@code replication:gui/mattertypes/{name}}.
     * Custom: {@code replication:gui/mattertypes/{namespace}/{path}} (unique); file lookup also tries flat {@code {name}.png}.
     */
    private static ResourceLocation resolveTexture(ResourceLocation id, String name) {
        if (id.getNamespace().equals("replication")) {
            String pathName = (name == null ? id.getPath() : name).toLowerCase();
            return ResourceLocation.fromNamespaceAndPath("replication", "gui/mattertypes/" + pathName);
        }
        return ResourceLocation.fromNamespaceAndPath(
                "replication",
                "gui/mattertypes/" + id.getNamespace() + "/" + id.getPath()
        );
    }

    /**
     * Flat GUI icon path used by Replication / KubeJS packs: {@code gui/mattertypes/{name}}.
     */
    public static ResourceLocation flatGuiTexture(String name) {
        String pathName = name == null ? "empty" : name.toLowerCase();
        return ResourceLocation.fromNamespaceAndPath("replication", "gui/mattertypes/" + pathName);
    }

    /**
     * All loaded matter infos (one per registry id).
     */
    public static Collection<MatterTypeInfo> getAllMatterInfos() {
        if (MATTER_BY_ID.isEmpty()) {
            loadAllMatters();
        }
        return Collections.unmodifiableCollection(MATTER_BY_ID.values());
    }

    /**
     * Gets all loaded matter types keyed by name (legacy). Prefer {@link #getAllMatterInfos()}.
     */
    public static Map<String, MatterTypeInfo> getAllMatters() {
        if (MATTER_BY_ID.isEmpty()) {
            loadAllMatters();
        }
        return new HashMap<>(MATTER_BY_NAME);
    }

    /**
     * Gets matter info by name.
     */
    public static MatterTypeInfo getMatterInfo(String matterTypeName) {
        if (MATTER_BY_ID.isEmpty()) {
            loadAllMatters();
        }
        if (matterTypeName == null) {
            return null;
        }
        MatterTypeInfo byName = MATTER_BY_NAME.get(matterTypeName);
        if (byName != null) {
            return byName;
        }
        return MATTER_BY_NAME.get(matterTypeName.toLowerCase());
    }

    /**
     * Gets matter info by registry id.
     */
    public static MatterTypeInfo getMatterInfoById(ResourceLocation registryId) {
        if (MATTER_BY_ID.isEmpty()) {
            loadAllMatters();
        }
        if (registryId == null) {
            return null;
        }
        return MATTER_BY_ID.get(registryId.toString());
    }

    /**
     * Gets matter info by IMatterType.
     */
    public static MatterTypeInfo getMatterInfo(IMatterType matterType) {
        if (MATTER_BY_ID.isEmpty()) {
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
     */
    public static IMatterType getMatterTypeFromItemStack(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        IMatterType fromItem = getMatterTypeFromItem(stack.getItem());
        if (fromItem != null) return fromItem;

        if (MATTER_BY_ID.isEmpty()) {
            loadAllMatters();
        }

        return DynamicMatterRegistry.getMatterType(stack);
    }

    /**
     * Gets the matter type from a MatterComponent.
     */
    public static IMatterType getMatterTypeFromComponent(net.unfamily.repae2bridge.component.MatterComponent component) {
        if (component == null) return null;

        if (MATTER_BY_ID.isEmpty()) {
            loadAllMatters();
        }

        MatterTypeInfo info = getMatterInfo(component.matterTypeName());
        return info != null ? info.matterType() : null;
    }

    /**
     * Returns the item to use for displaying this matter type (same logic as bridge getItemForMatterType).
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

    public static boolean isMatterCacheEmpty() {
        return MATTER_BY_ID.isEmpty();
    }
}
