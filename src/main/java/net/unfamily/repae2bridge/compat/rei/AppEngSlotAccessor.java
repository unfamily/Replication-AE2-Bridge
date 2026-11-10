package net.unfamily.repae2bridge.compat.rei;

import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * REI slot accessor for AE2 {@code AppEngSlot}.
 * <p>
 * Must <b>not</b> extend {@code VanillaSlotAccessor}: REI's vanilla serializer would save the
 * slot index then recreate a plain Vanilla accessor on the server, losing
 * {@link #allowModification} (empty AppEng slots fail {@code mayPickup} → "is not modifiable!").
 * Pair with {@link RepAE2BridgeReiCommonPlugin} serializer registration.
 */
public final class AppEngSlotAccessor implements SlotAccessor {

    private final Slot slot;

    public AppEngSlotAccessor(Slot slot) {
        this.slot = slot;
    }

    public Slot getSlot() {
        return slot;
    }

    @Override
    public ItemStack getItemStack() {
        return slot.getItem();
    }

    @Override
    public void setItemStack(ItemStack stack) {
        slot.set(stack);
    }

    @Override
    public ItemStack takeStack(int amount) {
        return slot.remove(amount);
    }

    @Override
    public boolean allowModification(Player player) {
        return slot.isActive();
    }

    @Override
    public boolean canPlace(ItemStack stack) {
        return slot.mayPlace(stack);
    }
}
