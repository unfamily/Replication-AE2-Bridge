package net.unfamily.repae2bridge.ae2.cells;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.IncludeExclude;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.items.storage.StorageCellTooltipComponent;

/**
 * Cell handler for matter storage cells only. Does not affect vanilla AE2 cells.
 */
public final class MatterCellHandler implements ICellHandler {

    public static final MatterCellHandler INSTANCE = new MatterCellHandler();

    private MatterCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack is) {
        return MatterCellInventory.isCell(is);
    }

    @Override
    public MatterCellInventory getCellInventory(ItemStack is, ISaveProvider container) {
        return MatterCellInventory.createInventory(is, container);
    }

    public void addCellInformationToTooltip(ItemStack is, Consumer<Component> lines) {
        var handler = getCellInventory(is, null);
        if (handler == null) {
            return;
        }

        lines.accept(Tooltips.bytesUsed(handler.getUsedBytes(), handler.getTotalBytes()));
        lines.accept(Tooltips.typesUsed(handler.getStoredItemTypes(), handler.getTotalItemTypes()));

        if (handler.isPreformatted()) {
            var list = (handler.getPartitionListMode() == IncludeExclude.WHITELIST ? GuiText.Included
                    : GuiText.Excluded).text();

            if (handler.isFuzzy()) {
                lines.accept(
                        GuiText.Partitioned.withSuffix(" - ").append(list).append(" ").append(GuiText.Fuzzy.text()));
            } else {
                lines.accept(
                        GuiText.Partitioned.withSuffix(" - ").append(list).append(" ").append(GuiText.Precise.text()));
            }
        }
    }

    public Optional<TooltipComponent> getTooltipImage(ItemStack is) {
        var handler = getCellInventory(is, null);
        if (handler == null) {
            return Optional.empty();
        }

        var upgradeStacks = new ArrayList<ItemStack>();
        if (AEConfig.instance().isTooltipShowCellUpgrades()) {
            for (var upgrade : handler.getUpgradesInventory()) {
                upgradeStacks.add(upgrade);
            }
        }

        boolean hasMoreContent;
        List<GenericStack> content;
        if (AEConfig.instance().isTooltipShowCellContent()) {
            content = new ArrayList<>();
            var maxCountShown = AEConfig.instance().getTooltipMaxCellContentShown();
            var availableStacks = handler.getAvailableStacks();
            for (var entry : availableStacks) {
                content.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }

            if (content.size() < maxCountShown && handler.getPartitionListMode() == IncludeExclude.WHITELIST) {
                var config = handler.getConfigInventory();
                for (int i = 0; i < config.size(); i++) {
                    var what = config.getKey(i);
                    if (what != null && availableStacks.get(what) <= 0) {
                        content.add(new GenericStack(what, 0));
                    }
                    if (content.size() > maxCountShown) {
                        break;
                    }
                }
            }

            content.sort(Comparator.comparingLong(GenericStack::amount).reversed());
            hasMoreContent = content.size() > maxCountShown;
            if (content.size() > maxCountShown) {
                content.subList(maxCountShown, content.size()).clear();
            }
        } else {
            hasMoreContent = false;
            content = Collections.emptyList();
        }

        return Optional.of(new StorageCellTooltipComponent(upgradeStacks, content, hasMoreContent, true));
    }
}
