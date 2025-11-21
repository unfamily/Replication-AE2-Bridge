package net.unfamily.repae2bridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin per aumentare il limite degli slot in InventoryComponent di Titanium.
 * InventoryComponent override getSlotLimit(), quindi dobbiamo intercettare quello invece di ItemStackHandler.
 */
@Mixin(InventoryComponent.class)
public abstract class InventoryComponentMixin {
    
    /**
     * Costante per il massimo stacksize assoluto.
     */
    private static final int ABSOLUTE_MAXIMUM_STACK_SIZE = Integer.MAX_VALUE / 2;
    
    /**
     * Modifica il valore di ritorno di getSlotLimit per permettere stacksize enormi negli inventari di Titanium.
     */
    @ModifyReturnValue(
            method = "Lcom/hrznstudio/titanium/component/inventory/InventoryComponent;getSlotLimit(I)I",
            at = @At("RETURN"),
            remap = false
    )
    private int repae2bridge$maximizeStackSize(int original) {
        return ABSOLUTE_MAXIMUM_STACK_SIZE;
    }
}

