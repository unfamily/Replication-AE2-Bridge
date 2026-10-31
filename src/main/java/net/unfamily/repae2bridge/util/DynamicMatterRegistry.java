package net.unfamily.repae2bridge.util;

import com.buuz135.replication.api.IMatterType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for managing dynamic matter items and their associations with matter types.
 */
public class DynamicMatterRegistry {
    private static final Map<IMatterType, DeferredHolder<Item, Item>> MATTER_TO_ITEM = new HashMap<>();
    private static final Map<String, IMatterType> ITEM_TO_MATTER = new HashMap<>();

    public static void registerMatterItem(IMatterType matterType, DeferredHolder<Item, Item> itemHolder) {
        MATTER_TO_ITEM.put(matterType, itemHolder);
        ITEM_TO_MATTER.put(itemHolder.getId().toString(), matterType);
    }

    public static Item getMatterItem(IMatterType matterType) {
        DeferredHolder<Item, Item> holder = MATTER_TO_ITEM.get(matterType);
        if (holder != null) {
            return holder.get();
        }
        return net.unfamily.repae2bridge.item.CustomMatterBindings.matterToItem().get(matterType);
    }

    public static IMatterType getMatterType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        String itemId = stack.getItem().getDescriptionId();
        IMatterType matterType = ITEM_TO_MATTER.get(itemId);
        if (matterType != null) {
            return matterType;
        }

        return net.unfamily.repae2bridge.item.CustomMatterBindings.itemToMatter().get(stack.getItem());
    }

    public static ItemStack createMatterStack(IMatterType matterType, int count) {
        Item item = getMatterItem(matterType);
        if (item != null) {
            return new ItemStack(item, count);
        }
        return ItemStack.EMPTY;
    }
}
