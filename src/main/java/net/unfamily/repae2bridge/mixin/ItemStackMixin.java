package net.unfamily.repae2bridge.mixin;

import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin per aumentare il limite massimo assoluto dello stacksize
 * per permettere stack più grandi delle matter virtuali.
 * Questo mixin modifica il codec che serializza il count degli ItemStack.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements DataComponentHolder, net.neoforged.neoforge.common.extensions.IItemStackExtension, net.neoforged.neoforge.common.MutableDataComponentHolder {
    
    /**
     * Costante per il massimo stacksize assoluto.
     * Impostiamo a Integer.MAX_VALUE / 2 per evitare overflow,
     * come fa Stackcraft.
     */
    private static final int ABSOLUTE_MAXIMUM_STACK_SIZE = Integer.MAX_VALUE / 2;
    
    /**
     * Modifica il secondo parametro della chiamata a ExtraCodecs.intRange
     * nel lambda che definisce il codec per il count.
     * Questo aumenta il limite massimo per la serializzazione.
     */
    @ModifyArg(
            method = "lambda$static$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/ExtraCodecs;intRange(II)Lcom/mojang/serialization/Codec;"
            ),
            index = 1,
            order = 1100
    )
    private static int repae2bridge$increaseMaxStackSize(int value) {
        return ABSOLUTE_MAXIMUM_STACK_SIZE;
    }
}

