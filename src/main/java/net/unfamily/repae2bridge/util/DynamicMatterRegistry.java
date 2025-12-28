package net.unfamily.repae2bridge.util;

import com.buuz135.replication.api.IMatterType;
import net.unfamily.repae2bridge.component.MatterComponent;
import net.unfamily.repae2bridge.component.ModDataComponents;
import net.unfamily.repae2bridge.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for managing dynamic matter items and their associations with matter types.
 * Provides methods to create and retrieve matter items based on matter types.
 */
public class DynamicMatterRegistry {
    private static final Map<IMatterType, DeferredHolder<Item, Item>> MATTER_TO_ITEM = new HashMap<>();
    private static final Map<String, IMatterType> ITEM_TO_MATTER = new HashMap<>();

    /**
     * Registers a matter type with its corresponding item.
     * @param matterType The matter type
     * @param itemHolder The deferred holder for the item
     */
    public static void registerMatterItem(IMatterType matterType, DeferredHolder<Item, Item> itemHolder) {
        MATTER_TO_ITEM.put(matterType, itemHolder);
        ITEM_TO_MATTER.put(itemHolder.getId().toString(), matterType);
    }

    /**
     * Gets the item associated with a matter type.
     * @param matterType The matter type
     * @return The item, or null if not registered
     */
    public static Item getMatterItem(IMatterType matterType) {
        DeferredHolder<Item, Item> holder = MATTER_TO_ITEM.get(matterType);
        return holder != null ? holder.get() : null;
    }

    /**
     * Gets the matter type associated with an item stack.
     * @param stack The item stack
     * @return The matter type, or null if not a matter item
     */
    public static IMatterType getMatterType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        // First check if it's an old-style matter item
        String itemId = stack.getItem().getDescriptionId();
        IMatterType matterType = ITEM_TO_MATTER.get(itemId);
        if (matterType != null) {
            return matterType;
        }

        // Check if it's a universal matter item with component
        MatterComponent component = stack.get(ModDataComponents.MATTER.get());
        if (component != null) {
            return MatterTypeUtil.getMatterTypeFromComponent(component);
        }

        return null;
    }

    /**
     * Creates a matter item stack for the given matter type.
     * @param matterType The matter type
     * @param count The stack size
     * @return The item stack, or empty if matter type not registered
     */
    public static ItemStack createMatterStack(IMatterType matterType, int count) {
        Item item = getMatterItem(matterType);
        if (item != null) {
            return new ItemStack(item, count);
        }

        // Fallback to universal matter if available
        if (ModItems.UNIVERSAL_MATTER != null) {
            return new ItemStack(ModItems.UNIVERSAL_MATTER.get(), count);
        }

        return ItemStack.EMPTY;
    }
}
