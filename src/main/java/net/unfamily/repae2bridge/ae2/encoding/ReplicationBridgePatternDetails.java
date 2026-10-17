package net.unfamily.repae2bridge.ae2.encoding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.calculation.MatterValue;
import com.buuz135.replication.calculation.ReplicationCalculation;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;

/**
 * Bridge-provided replication pattern. Matter inputs are real AE2 ingredients so the
 * craft plan reserves them; {@code pushPattern} absorbs them into the bridge craft buffer
 * for Replication (AE2 then only waits for the output item).
 */
public final class ReplicationBridgePatternDetails implements IPatternDetails {

    private final AEItemKey definition;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;
    private final ItemStack replicatingStack;

    private ReplicationBridgePatternDetails(AEItemKey definition, ItemStack replicatingStack, IInput[] inputs) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.replicatingStack = replicatingStack.copy();
        this.inputs = inputs;
        this.outputs = List.of(new GenericStack(definition, 1));
    }

    /**
     * @param replicatingStack chip-storage pattern item (the item to replicate)
     * @return pattern details, or null if the stack is empty / has no matter recipe
     */
    @Nullable
    public static ReplicationBridgePatternDetails of(ItemStack replicatingStack) {
        if (replicatingStack == null || replicatingStack.isEmpty()) {
            return null;
        }
        AEItemKey outputKey = AEItemKey.of(replicatingStack.getItem());
        if (outputKey == null) {
            return null;
        }
        var matterCompound = ReplicationCalculation.getMatterCompound(replicatingStack);
        if (matterCompound == null) {
            return null;
        }

        List<IInput> inputList = new ArrayList<>();
        for (MatterValue matterValue : matterCompound.getValues().values()) {
            ReplicationMatterKey key = ReplicationMatterKey.of(matterValue.getMatter());
            if (key == null) {
                continue;
            }
            long amount = (long) Math.ceil(matterValue.getAmount());
            if (amount <= 0) {
                continue;
            }
            inputList.add(new MatterInput(key, amount));
        }
        if (inputList.isEmpty()) {
            return null;
        }

        return new ReplicationBridgePatternDetails(
                outputKey, replicatingStack, inputList.toArray(IInput[]::new));
    }

    public ItemStack getReplicatingStack() {
        return replicatingStack.copy();
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return false;
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        var tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_PRODUCES);
        tooltip.addProperty(Component.literal("Replication (AE2 Bridge)"));
        tooltip.addInputsAndOutputs(this);
        return tooltip;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ReplicationBridgePatternDetails other && definition.equals(other.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    private static final class MatterInput implements IInput {
        private final GenericStack[] template;
        private final long multiplier;

        private MatterInput(ReplicationMatterKey key, long amount) {
            this.template = new GenericStack[] { new GenericStack(key, 1) };
            this.multiplier = amount;
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return template;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input.matches(template[0]);
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
