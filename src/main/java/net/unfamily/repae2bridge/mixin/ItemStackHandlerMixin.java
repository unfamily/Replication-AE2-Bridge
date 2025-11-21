package net.unfamily.repae2bridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin per aumentare il limite degli slot in ItemStackHandler.
 * Questo copre tutti gli inventory standard di NeoForge.
 */
@Mixin(ItemStackHandler.class)
public abstract class ItemStackHandlerMixin {
    
    /**
     * Costante per il massimo stacksize assoluto.
     */
    private static final int ABSOLUTE_MAXIMUM_STACK_SIZE = Integer.MAX_VALUE / 2;
    
    /**
     * Modifica il valore di ritorno di getSlotLimit(slot) per permettere stacksize enormi.
     */
    @ModifyReturnValue(
            method = "Lnet/neoforged/neoforge/items/ItemStackHandler;getSlotLimit(I)I",
            at = @At("RETURN"),
            remap = false
    )
    private int repae2bridge$maximizeSlotLimit(int original) {
        return ABSOLUTE_MAXIMUM_STACK_SIZE;
    }
    
    /**
     * Modifica il valore di ritorno di getStackLimit(slot, stack) per permettere stacksize enormi.
     * Questo è il metodo che combina il limite dello slot con il limite dell'itemstack.
     */
    @ModifyReturnValue(
            method = "Lnet/neoforged/neoforge/items/ItemStackHandler;getStackLimit(ILnet/minecraft/world/item/ItemStack;)I",
            at = @At("RETURN"),
            remap = false
    )
    private int repae2bridge$maximizeStackLimit(int original) {
        return ABSOLUTE_MAXIMUM_STACK_SIZE;
    }
}

