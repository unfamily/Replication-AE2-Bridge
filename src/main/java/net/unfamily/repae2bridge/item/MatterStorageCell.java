package net.unfamily.repae2bridge.item;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.localization.PlayerMessages;
import appeng.hooks.AEToolItem;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import appeng.util.ConfigInventory;
import appeng.util.InteractionUtil;
import appeng.util.Platform;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.ae2.cells.MatterCellHandler;

/**
 * Matter storage cell with configurable type limit. Uses a custom cell handler so the AE2 63-type
 * clamp does not apply. {@code 0} in config means all registered matter types (excluding EMPTY).
 */
public class MatterStorageCell extends AEBaseItem implements ICellWorkbenchItem, AEToolItem {

    private final double idleDrain;
    private final int exactBytes;
    private final int bytesPerType;

    public MatterStorageCell(Properties properties, double idleDrain, int exactBytes, int bytesPerType) {
        super(properties);
        this.idleDrain = idleDrain;
        this.exactBytes = exactBytes;
        this.bytesPerType = bytesPerType;
    }

    public int getBytes(ItemStack cellItem) {
        return exactBytes;
    }

    public int getBytesPerType(ItemStack cellItem) {
        return bytesPerType;
    }

    public double getIdleDrain() {
        return idleDrain;
    }

    public int getTotalTypes(ItemStack cellItem) {
        int types = Config.matterCellMaxTypes;
        if (types == 0) {
            return countRegisteredMatterTypes();
        }
        if (types < 0) {
            return 1;
        }
        return types;
    }

    private static int countRegisteredMatterTypes() {
        var registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
        if (registry == null) {
            return 1;
        }
        int count = 0;
        for (IMatterType matterType : registry) {
            if (matterType != null && !matterType.equals(MatterType.EMPTY)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, 3);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(Set.of(MatterKeyType.INSTANCE), is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return is.getOrDefault(AEComponents.STORAGE_CELL_FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        is.set(AEComponents.STORAGE_CELL_FUZZY_MODE, fzMode);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
            TooltipFlag advancedTooltips) {
        if (Platform.isClient()) {
            MatterCellHandler.INSTANCE.addCellInformationToTooltip(stack, lines);
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return MatterCellHandler.INSTANCE.getTooltipImage(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        this.disassembleDrive(player.getItemInHand(hand), level, player);
        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()),
                player.getItemInHand(hand));
    }

    private boolean disassembleDrive(ItemStack stack, Level level, Player player) {
        if (!InteractionUtil.isInAlternateUseMode(player)) {
            return false;
        }

        var disassembledStacks = StorageCellDisassemblyRecipe.getDisassemblyResult(level, stack.getItem());
        if (disassembledStacks.isEmpty()) {
            return false;
        }

        var playerInventory = player.getInventory();
        if (playerInventory.getSelected() != stack) {
            return false;
        }

        var inv = StorageCells.getCellInventory(stack, null);
        if (inv != null && !inv.getAvailableStacks().isEmpty()) {
            player.displayClientMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
            return false;
        }

        playerInventory.setItem(playerInventory.selected, ItemStack.EMPTY);

        for (var disassembledStack : disassembledStacks) {
            playerInventory.placeItemBackInInventory(disassembledStack.copy());
        }

        getUpgrades(stack).forEach(playerInventory::placeItemBackInInventory);
        return true;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return this.disassembleDrive(stack, context.getLevel(), context.getPlayer())
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide())
                : InteractionResult.PASS;
    }

    /**
     * Item color for layered models: LED is always {@code layer3}.
     * RGB only — callers must apply {@link net.minecraft.util.FastColor.ARGB32#opaque}
     * (same as AE2 {@code InitItemColors.makeOpaque}).
     */
    public static int getLedColor(ItemStack stack, int tintIndex) {
        if (tintIndex == 3) {
            var cellInv = StorageCells.getCellInventory(stack, null);
            var cellStatus = cellInv != null ? cellInv.getStatus() : CellState.EMPTY;
            return cellStatus.getStateColor();
        }
        return 0xFFFFFF;
    }
}
