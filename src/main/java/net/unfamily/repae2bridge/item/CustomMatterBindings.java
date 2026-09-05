package net.unfamily.repae2bridge.item;

import com.buuz135.replication.api.IMatterType;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runtime bindings between custom matter types and their dedicated bridge items.
 * Populated during matter-type registry events (no config JSON).
 */
public final class CustomMatterBindings {
    private static final Map<Item, IMatterType> ITEM_TO_MATTER = new HashMap<>();
    private static final Map<IMatterType, Item> MATTER_TO_ITEM = new HashMap<>();
    private static final Set<Item> VIRTUAL_ITEMS = new HashSet<>();
    private static final Map<String, Item> ITEMS_BY_REGISTRY_NAME = new HashMap<>();

    private CustomMatterBindings() {}

    public static void clear() {
        ITEM_TO_MATTER.clear();
        MATTER_TO_ITEM.clear();
        VIRTUAL_ITEMS.clear();
        ITEMS_BY_REGISTRY_NAME.clear();
    }

    public static void bind(Item item, IMatterType matterType, String registryName) {
        ITEM_TO_MATTER.put(item, matterType);
        MATTER_TO_ITEM.put(matterType, item);
        VIRTUAL_ITEMS.add(item);
        ITEMS_BY_REGISTRY_NAME.put(registryName, item);
    }

    public static Map<Item, IMatterType> itemToMatter() {
        return Collections.unmodifiableMap(ITEM_TO_MATTER);
    }

    public static Map<IMatterType, Item> matterToItem() {
        return Collections.unmodifiableMap(MATTER_TO_ITEM);
    }

    public static Set<Item> virtualItems() {
        return Collections.unmodifiableSet(VIRTUAL_ITEMS);
    }

    public static Item getItem(String registryName) {
        return ITEMS_BY_REGISTRY_NAME.get(registryName);
    }

    public static boolean hasBindings() {
        return !ITEM_TO_MATTER.isEmpty();
    }
}
