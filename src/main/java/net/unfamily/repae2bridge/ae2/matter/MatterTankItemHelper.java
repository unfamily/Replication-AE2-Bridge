package net.unfamily.repae2bridge.ae2.matter;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.ReplicationAttachments;
import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;
import com.buuz135.replication.api.matter_fluid.MatterStack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import appeng.api.stacks.GenericStack;

import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;

/**
 * Reads matter content from Replication matter-tank block items (TILE / tank NBT).
 */
public final class MatterTankItemHelper {

    private MatterTankItemHelper() {
    }

    public static boolean isMatterTankItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "replication".equals(id.getNamespace())
                && (id.getPath().equals("matter_tank") || id.getPath().equals("creative_matter_tank"));
    }

    public static boolean isMatterTankItem(ItemLike item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.asItem());
        return "replication".equals(id.getNamespace())
                && (id.getPath().equals("matter_tank") || id.getPath().equals("creative_matter_tank"));
    }

    @Nullable
    public static MatterStack getContainedMatter(ItemStack stack) {
        if (!isMatterTankItem(stack)) {
            return null;
        }
        CompoundTag root = null;
        if (stack.has(ReplicationAttachments.TILE)) {
            root = stack.get(ReplicationAttachments.TILE);
        } else if (stack.has(ReplicationAttachments.TANK_STORAGE)) {
            root = stack.get(ReplicationAttachments.TANK_STORAGE);
        }
        if (root == null) {
            return null;
        }
        CompoundTag tankTag = findMatterTag(root);
        if (tankTag == null || !tankTag.contains("MatterName")) {
            return null;
        }
        String name = tankTag.contains("MatterName") ? tankTag.getString("MatterName") : "";
        if (name.isEmpty()) {
            return null;
        }
        double amount = tankTag.contains("Amount") ? tankTag.getDouble("Amount") : 0.0;
        if (amount <= 0) {
            return null;
        }
        ResourceLocation matterId = ResourceLocation.tryParse(name);
        if (matterId == null || ReplicationRegistry.MATTER_TYPES_REGISTRY == null) {
            return null;
        }
        IMatterType type = ReplicationRegistry.MATTER_TYPES_REGISTRY.get(matterId);
        if (type == null) {
            return null;
        }
        return new MatterStack(type, amount);
    }

    @Nullable
    public static GenericStack getContainedGenericStack(ItemStack stack) {
        MatterStack matter = getContainedMatter(stack);
        if (matter == null || matter.isEmpty()) {
            return null;
        }
        ReplicationMatterKey key = ReplicationMatterKey.of(matter.getMatterType());
        if (key == null) {
            return null;
        }
        long amount = Math.max(0L, (long) Math.floor(matter.getAmount()));
        return amount <= 0 ? null : new GenericStack(key, amount);
    }

    /**
     * True for real matter types (skips {@link MatterType#EMPTY} / empty-named entries).
     */
    public static boolean isSelectableMatter(@Nullable IMatterType type) {
        if (type == null || type.equals(MatterType.EMPTY)) {
            return false;
        }
        String name = type.getName();
        return name != null && !name.isEmpty() && !"empty".equalsIgnoreCase(name);
    }

    @Nullable
    public static IMatterType firstMatterType() {
        var registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
        if (registry == null) {
            return null;
        }
        for (IMatterType type : registry) {
            if (isSelectableMatter(type)) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    public static IMatterType nextMatterType(@Nullable IMatterType current) {
        var registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
        if (registry == null) {
            return null;
        }
        boolean takeNext = current == null || !isSelectableMatter(current);
        IMatterType first = null;
        for (IMatterType type : registry) {
            if (!isSelectableMatter(type)) {
                continue;
            }
            if (first == null) {
                first = type;
            }
            if (takeNext) {
                return type;
            }
            if (type.equals(current)) {
                takeNext = true;
            }
        }
        return first;
    }

    @Nullable
    private static CompoundTag findMatterTag(CompoundTag root) {
        if (root.contains("MatterName")) {
            return root;
        }
        // Titanium tile NBT nests the tank component under "tank"
        if (root.contains("tank") && root.get("tank") instanceof CompoundTag tank) {
            if (tank.contains("MatterName")) {
                return tank;
            }
            // Nested further in some Titanium versions
            for (String key : tank.getAllKeys()) {
                Tag child = tank.get(key);
                if (child instanceof CompoundTag nested && nested.contains("MatterName")) {
                    return nested;
                }
            }
        }
        for (String key : root.getAllKeys()) {
            Tag child = root.get(key);
            if (child instanceof CompoundTag nested) {
                CompoundTag found = findMatterTag(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
