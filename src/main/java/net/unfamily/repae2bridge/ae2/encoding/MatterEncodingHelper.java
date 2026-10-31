package net.unfamily.repae2bridge.ae2.encoding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.api.MatterType;
import com.buuz135.replication.calculation.MatterCompound;
import com.buuz135.replication.calculation.MatterValue;
import com.buuz135.replication.calculation.ReplicationCalculation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;
import net.unfamily.repae2bridge.ae2.matter.MatterTankItemHelper;

/**
 * Builds item→matter processing patterns from {@link ReplicationCalculation}.
 */
public final class MatterEncodingHelper {

    private MatterEncodingHelper() {
    }

    /**
     * Computes matter outputs for {@code count} items, sorted by registry id by default.
     * When {@code preferredOrder} is set, matching keys keep that order (amounts recalculated);
     * any new keys are appended in registry-id order.
     */
    @Nullable
    public static List<GenericStack> computeMatterOutputs(ItemStack stack, long count,
            @Nullable List<ResourceLocation> preferredOrder) {
        if (stack == null || stack.isEmpty() || count <= 0) {
            return null;
        }
        MatterCompound compound = ReplicationCalculation.getMatterCompound(stack);
        if (compound == null || compound.getValues().isEmpty()) {
            return null;
        }

        Map<ResourceLocation, GenericStack> byId = new HashMap<>();
        for (MatterValue value : compound.getValues().values()) {
            if (value == null || value.getMatter() == null || value.getMatter().equals(MatterType.EMPTY)) {
                continue;
            }
            if (!MatterTankItemHelper.isSelectableMatter(value.getMatter())) {
                continue;
            }
            long amount = (long) Math.ceil(value.getAmount() * count);
            if (amount <= 0) {
                continue;
            }
            ReplicationMatterKey key = ReplicationMatterKey.of(value.getMatter());
            if (key == null) {
                continue;
            }
            byId.put(key.getRegistryId(), new GenericStack(key, amount));
        }
        if (byId.isEmpty()) {
            return null;
        }

        List<GenericStack> outputs = new ArrayList<>();
        Set<ResourceLocation> used = new HashSet<>();
        if (preferredOrder != null) {
            for (ResourceLocation id : preferredOrder) {
                GenericStack stackOut = byId.get(id);
                if (stackOut != null) {
                    outputs.add(stackOut);
                    used.add(id);
                }
            }
        }
        byId.entrySet().stream()
                .filter(e -> !used.contains(e.getKey()))
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .forEach(e -> outputs.add(e.getValue()));
        return outputs;
    }

    @Nullable
    public static List<GenericStack> computeMatterOutputs(ItemStack stack, long count) {
        return computeMatterOutputs(stack, count, null);
    }

    /**
     * Rotates output order so the former primary becomes last (AE2 cycle semantics).
     */
    public static void cyclePreferredOrder(List<ResourceLocation> order) {
        if (order == null || order.size() < 2) {
            return;
        }
        ResourceLocation first = order.remove(0);
        order.add(first);
    }

    public static List<ResourceLocation> idsOf(List<GenericStack> outputs) {
        List<ResourceLocation> ids = new ArrayList<>(outputs.size());
        for (GenericStack stack : outputs) {
            if (stack.what() instanceof ReplicationMatterKey matterKey) {
                ids.add(matterKey.getRegistryId());
            }
        }
        return ids;
    }

    @Nullable
    public static ItemStack encodeProcessingPattern(@Nullable AEKey inputKey, long count,
            @Nullable List<ResourceLocation> preferredOrder) {
        if (!(inputKey instanceof AEItemKey itemKey) || count <= 0) {
            return null;
        }
        ItemStack stack = itemKey.toStack(1);
        List<GenericStack> outputs = computeMatterOutputs(stack, count, preferredOrder);
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }
        List<GenericStack> inputs = Collections.singletonList(new GenericStack(itemKey, count));
        return PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
    }

    @Nullable
    public static ItemStack encodeProcessingPattern(@Nullable AEKey inputKey, long count) {
        return encodeProcessingPattern(inputKey, count, null);
    }
}
