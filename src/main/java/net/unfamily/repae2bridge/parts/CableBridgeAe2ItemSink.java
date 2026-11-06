package net.unfamily.repae2bridge.parts;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Insert-only sink for vanilla Replicators delivering to a cable-bridge task source.
 * Immediately forwards stacks into AE2 (same role as the block bridge output inventory).
 */
public final class CableBridgeAe2ItemSink extends ItemStacksResourceHandler {

    private final CableRepAe2BridgePart cable;

    CableBridgeAe2ItemSink(CableRepAe2BridgePart cable) {
        super(1);
        this.cable = cable;
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        ItemStack stack = stacks.get(index);
        if (stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        if (cable.receiveItemFromReplicator(copy)) {
            set(index, ItemResource.EMPTY, 0);
        }
    }
}
