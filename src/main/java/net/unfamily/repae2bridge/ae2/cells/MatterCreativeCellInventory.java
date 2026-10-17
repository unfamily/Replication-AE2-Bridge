package net.unfamily.repae2bridge.ae2.cells;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;

import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;

/**
 * Infinite storage for every registered Replication matter type.
 */
final class MatterCreativeCellInventory implements StorageCell {

    private final ItemStack stack;

    MatterCreativeCellInventory(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return what instanceof ReplicationMatterKey ? amount : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        // Infinite extract for debit / export. Bridge patterns do not pull matter into the CPU.
        return what instanceof ReplicationMatterKey ? amount : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
        if (registry == null) {
            return;
        }
        for (IMatterType matterType : registry) {
            if (matterType == null || matterType.equals(MatterType.EMPTY)) {
                continue;
            }
            ReplicationMatterKey key = ReplicationMatterKey.of(matterType);
            if (key != null) {
                // Match AE2 CreativeCellInventory: Integer.MAX_VALUE (not Long.MAX_VALUE —
                // huge longs blow up AE2 listing / extract loops and can OOM the heap).
                out.add(key, Integer.MAX_VALUE);
            }
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey input, IActionSource source) {
        return input instanceof ReplicationMatterKey;
    }

    @Override
    public CellState getStatus() {
        return CellState.TYPES_FULL;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public void persist() {
    }
}
