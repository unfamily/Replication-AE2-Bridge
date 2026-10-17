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
}
