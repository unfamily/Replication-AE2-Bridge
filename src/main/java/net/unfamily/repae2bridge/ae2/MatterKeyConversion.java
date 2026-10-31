package net.unfamily.repae2bridge.ae2;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.api.IMatterType;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.unfamily.repae2bridge.util.MatterTypeUtil;

/**
 * Converts virtual matter {@link Item}/{@link AEItemKey} proxies (JEI drag) into
 * {@link ReplicationMatterKey}.
 */
public final class MatterKeyConversion {

    private MatterKeyConversion() {
    }

    @Nullable
    public static IMatterType matterTypeOf(@Nullable Item item) {
        return MatterTypeUtil.getMatterTypeFromItem(item);
    }

    @Nullable
    public static ReplicationMatterKey toMatterKey(@Nullable Item item) {
        IMatterType type = matterTypeOf(item);
        return type == null ? null : ReplicationMatterKey.of(type);
    }

    @Nullable
    public static AEKey convert(@Nullable AEKey key) {
        if (key instanceof ReplicationMatterKey) {
            return key;
        }
        if (key instanceof AEItemKey itemKey) {
            ReplicationMatterKey matter = toMatterKey(itemKey.getItem());
            return matter != null ? matter : key;
        }
        return key;
    }

    @Nullable
    public static GenericStack convert(@Nullable GenericStack stack) {
        if (stack == null) {
            return null;
        }
        AEKey converted = convert(stack.what());
        if (converted == null || converted == stack.what()) {
            return stack;
        }
        return new GenericStack(converted, stack.amount());
    }

    @Nullable
    public static GenericStack fromMatterItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ReplicationMatterKey key = toMatterKey(stack.getItem());
        if (key == null) {
            return null;
        }
        return new GenericStack(key, Math.max(1, stack.getCount()));
    }
}
