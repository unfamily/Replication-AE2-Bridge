package net.unfamily.repae2bridge.ae2.matter;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;

import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;

/**
 * Lets AE2 read matter stored inside Replication matter-tank items (monitor / menus).
 * Insert/extract into the held tank is not supported — configure-only.
 */
public final class MatterContainerItemStrategy
        implements ContainerItemStrategy<ReplicationMatterKey, ItemStack> {

    @Override
    @Nullable
    public GenericStack getContainedStack(ItemStack stack) {
        return MatterTankItemHelper.getContainedGenericStack(stack);
    }

    @Override
    @Nullable
    public ItemStack findCarriedContext(Player player, AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        return MatterTankItemHelper.isMatterTankItem(carried) ? carried : null;
    }

    @Override
    public long extract(ItemStack context, ReplicationMatterKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public long insert(ItemStack context, ReplicationMatterKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public void playFillSound(Player player, ReplicationMatterKey what) {
    }

    @Override
    public void playEmptySound(Player player, ReplicationMatterKey what) {
    }

    @Override
    @Nullable
    public GenericStack getExtractableContent(ItemStack context) {
        return getContainedStack(context);
    }
}
