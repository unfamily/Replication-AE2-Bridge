package net.unfamily.repae2bridge.item;

import net.minecraft.world.item.ItemStack;

import appeng.items.storage.BasicStorageCell;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.ae2.MatterKeyType;

/**
 * Matter storage cell with an exact byte capacity so per-type capacity matches the designed tiers.
 */
public class MatterStorageCell extends BasicStorageCell {

    private final int exactBytes;

    public MatterStorageCell(Properties properties, double idleDrain, int exactBytes, int bytesPerType) {
        // kilobytes arg is unused for capacity; getBytes() returns exactBytes
        super(properties, idleDrain, 1, bytesPerType, 6, MatterKeyType.INSTANCE);
        this.exactBytes = exactBytes;
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return exactBytes;
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        int types = Config.matterCellMaxTypes;
        if (types < 1) {
            return 1;
        }
        // AE2 BasicCellInventory clamps to 63
        return Math.min(types, 63);
    }

    /**
     * Item color for layered models: LED is always {@code layer3}.
     * RGB only — callers must apply {@link net.minecraft.util.FastColor.ARGB32#opaque}
     * (same as AE2 {@code InitItemColors.makeOpaque}).
     */
    public static int getLedColor(ItemStack stack, int tintIndex) {
        if (tintIndex == 3) {
            return BasicStorageCell.getColor(stack, 1);
        }
        // Untinted layers (must stay opaque white after ARGB32.opaque).
        return 0xFFFFFF;
    }
}

