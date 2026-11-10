package net.unfamily.repae2bridge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.implementations.CellWorkbenchScreen;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.CellWorkbenchMenu;

/**
 * AE2's cell workbench marks any non-config key type as "Incompatible with cell".
 * Upgrade cards are items, so matter cells (MatterKeyType-only config) falsely show that
 * warning even though the cards install fine. Skip the check for AE2 upgrade cards.
 */
@Mixin(value = CellWorkbenchScreen.class, remap = false)
public abstract class CellWorkbenchScreenMixin extends UpgradeableScreen<CellWorkbenchMenu> {

    public CellWorkbenchScreenMixin(CellWorkbenchMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("HEAD"), cancellable = true)
    private void repAe2Bridge$skipUpgradeCardIncompatibility(ItemStack stack,
            CallbackInfoReturnable<List<Component>> cir) {
        if (Upgrades.isUpgradeCardItem(stack)) {
            cir.setReturnValue(super.getTooltipFromContainerItem(stack));
        }
    }
}
