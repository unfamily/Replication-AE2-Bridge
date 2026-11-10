package net.unfamily.repae2bridge.ae2.cells;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.IPartitionList;

import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.item.MatterStorageCell;

/**
 * Matter-cell inventory based on AE2 {@code BasicCellInventory}, without the hard 63-type clamp.
 * Vanilla AE2 cells are untouched; only {@link MatterStorageCell} uses this path.
 */
public final class MatterCellInventory implements StorageCell {

    @Nullable
    private final ISaveProvider container;
    private final AEKeyType keyType;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionListMode;
    private final int maxItemTypes;
    private int storedItems;
    private long storedItemCount;
    private Object2LongMap<AEKey> storedAmounts;
    private final ItemStack i;
    private final MatterStorageCell cellType;
    private final long maxItemsPerType;
    private final boolean hasVoidUpgrade;
    private boolean isPersisted = true;

    private MatterCellInventory(MatterStorageCell cellType, ItemStack o, @Nullable ISaveProvider container) {
        this.i = o;
        this.cellType = cellType;
        this.maxItemTypes = Math.max(1, cellType.getTotalTypes(o));
        this.container = container;
        var storedStacks = getStoredStacks();
        this.storedItems = storedStacks.size();
        this.storedItemCount = storedStacks.stream().mapToLong(GenericStack::amount).sum();
        this.storedAmounts = null;
        this.keyType = MatterKeyType.INSTANCE;

        var builder = IPartitionList.builder();
        var upgrades = getUpgradesInventory();
        var config = getConfigInventory();

        boolean hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD);
        boolean isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD);
        if (isFuzzy) {
            builder.fuzzyMode(getFuzzyMode());
        }

        builder.addAll(config.keySet());

        partitionListMode = hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        partitionList = builder.build();

        if (upgrades.isInstalled(AEItems.EQUAL_DISTRIBUTION_CARD)) {
            long maxTypes = Integer.MAX_VALUE;
            if (!isFuzzy && partitionListMode == IncludeExclude.WHITELIST && !config.keySet().isEmpty()) {
                maxTypes = config.keySet().size();
            }
            maxTypes = Math.min(maxTypes, this.maxItemTypes);

            long totalStorage = (getTotalBytes() - getBytesPerType() * maxTypes) * keyType.getAmountPerByte();
            this.maxItemsPerType = Math.max(0, (totalStorage + maxTypes - 1) / maxTypes);
        } else {
            this.maxItemsPerType = Long.MAX_VALUE;
        }

        this.hasVoidUpgrade = upgrades.isInstalled(AEItems.VOID_CARD);
    }

    @Nullable
    public static MatterCellInventory createInventory(ItemStack o, @Nullable ISaveProvider container) {
        Objects.requireNonNull(o, "Cannot create cell inventory for null itemstack");
        if (!(o.getItem() instanceof MatterStorageCell cellType)) {
            return null;
        }
        return new MatterCellInventory(cellType, o, container);
    }

    public static boolean isCell(ItemStack input) {
        return input != null && !input.isEmpty() && input.getItem() instanceof MatterStorageCell;
    }

    private List<GenericStack> getStoredStacks() {
        return i.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of());
    }

    public IncludeExclude getPartitionListMode() {
        return partitionListMode;
    }

    public boolean isPreformatted() {
        return !partitionList.isEmpty();
    }

    public boolean isFuzzy() {
        return partitionList instanceof FuzzyPriorityList;
    }

    @Override
    public boolean canFitInsideCell() {
        return getAvailableStacks().isEmpty();
    }

    private Object2LongMap<AEKey> getCellItems() {
        if (this.storedAmounts == null) {
            this.storedAmounts = new Object2LongOpenHashMap<>();
            this.loadCellItems();
        }
        return this.storedAmounts;
    }

    @Override
    public void persist() {
        if (this.isPersisted) {
            return;
        }

        var itemCount = 0L;
        var stacks = new ArrayList<GenericStack>(storedAmounts.size());

        for (var entry : this.storedAmounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            itemCount += amount;
            if (amount > 0) {
                stacks.add(new GenericStack(entry.getKey(), amount));
            }
        }

        if (stacks.isEmpty()) {
            i.remove(AEComponents.STORAGE_CELL_INV);
        } else {
            i.set(AEComponents.STORAGE_CELL_INV, stacks);
        }

        this.storedItems = this.storedAmounts.size();
        this.storedItemCount = itemCount;
        this.isPersisted = true;
    }

    private void saveChanges() {
        this.storedItems = this.storedAmounts.size();
        this.storedItemCount = 0;
        for (var storedAmount : this.storedAmounts.values()) {
            this.storedItemCount += storedAmount;
        }

        this.isPersisted = false;
        if (this.container != null) {
            this.container.saveChanges();
        } else {
            this.persist();
        }
    }

    private void loadCellItems() {
        for (var stack : getStoredStacks()) {
            storedAmounts.put(stack.what(), stack.amount());
        }
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : Object2LongMaps.fastIterable(this.getCellItems())) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public double getIdleDrain() {
        return this.cellType.getIdleDrain();
    }

    public FuzzyMode getFuzzyMode() {
        return this.cellType.getFuzzyMode(this.i);
    }

    public ConfigInventory getConfigInventory() {
        return this.cellType.getConfigInventory(this.i);
    }

    public IUpgradeInventory getUpgradesInventory() {
        return this.cellType.getUpgrades(this.i);
    }

    public int getBytesPerType() {
        return this.cellType.getBytesPerType(this.i);
    }

    public boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType()
                || bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0)
                && this.getRemainingItemTypes() > 0;
    }

    public long getTotalBytes() {
        return this.cellType.getBytes(this.i);
    }

    public long getFreeBytes() {
        return this.getTotalBytes() - this.getUsedBytes();
    }

    public long getTotalItemTypes() {
        return this.maxItemTypes;
    }

    public long getStoredItemCount() {
        return this.storedItemCount;
    }

    public long getStoredItemTypes() {
        return this.storedItems;
    }

    public long getRemainingItemTypes() {
        var basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        var baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    public long getUsedBytes() {
        var bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / keyType.getAmountPerByte();
        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    public long getRemainingItemCount() {
        final long remaining = this.getFreeBytes() * keyType.getAmountPerByte() + this.getUnusedItemCount();
        return remaining > 0 ? remaining : 0;
    }

    public int getUnusedItemCount() {
        final int div = (int) (this.getStoredItemCount() % keyType.getAmountPerByte());
        if (div == 0) {
            return 0;
        }
        return keyType.getAmountPerByte() - div;
    }

    @Override
    public CellState getStatus() {
        if (this.getStoredItemTypes() == 0) {
            return CellState.EMPTY;
        }
        if (this.canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        if (this.getRemainingItemCount() > 0) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount == 0 || !keyType.contains(what)) {
            return 0;
        }

        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }

        long inserted = innerInsert(what, amount, mode);

        if (!isPreformatted() && hasVoidUpgrade && !canHoldNewItem()) {
            return getCellItems().containsKey(what) ? amount : inserted;
        }

        return hasVoidUpgrade ? amount : inserted;
    }

    private long innerInsert(AEKey what, long amount, Actionable mode) {
        if (what instanceof AEItemKey itemKey) {
            var stack = itemKey.toStack();
            var cellInv = StorageCells.getCellInventory(stack, null);
            if (cellInv != null && !cellInv.canFitInsideCell()) {
                return 0;
            }
        }

        var currentAmount = this.getCellItems().getLong(what);
        long remainingItemCount = this.getRemainingItemCount();

        if (currentAmount <= 0) {
            if (!canHoldNewItem()) {
                return 0;
            }

            remainingItemCount -= (long) this.getBytesPerType() * keyType.getAmountPerByte();
            if (remainingItemCount <= 0) {
                return 0;
            }
        }

        remainingItemCount = Math.max(0, Math.min(this.maxItemsPerType - currentAmount, remainingItemCount));

        if (amount > remainingItemCount) {
            amount = remainingItemCount;
        }

        if (mode == Actionable.MODULATE) {
            getCellItems().put(what, currentAmount + amount);
            this.saveChanges();
        }

        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var currentAmount = getCellItems().getLong(what);
        if (currentAmount > 0) {
            if (amount >= currentAmount) {
                if (mode == Actionable.MODULATE) {
                    getCellItems().remove(what, currentAmount);
                    this.saveChanges();
                }
                return currentAmount;
            } else {
                if (mode == Actionable.MODULATE) {
                    getCellItems().put(what, currentAmount - amount);
                    this.saveChanges();
                }
                return amount;
            }
        }
        return 0;
    }

    @Override
    public Component getDescription() {
        return i.getHoverName();
    }
}
