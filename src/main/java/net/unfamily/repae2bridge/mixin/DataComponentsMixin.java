package net.unfamily.repae2bridge.mixin;

import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin per aumentare il limite massimo del componente MAX_STACK_SIZE.
 * Questo mixin modifica il codec che serializza il componente MAX_STACK_SIZE.
 */
@Mixin(DataComponents.class)
public abstract class DataComponentsMixin {
    
    /**
     * Costante per il massimo stacksize assoluto.
     * Impostiamo a Integer.MAX_VALUE / 2 per evitare overflow.
     */
    private static final int ABSOLUTE_MAXIMUM_STACK_SIZE = Integer.MAX_VALUE / 2;
    
    /**
     * Modifica il secondo parametro della chiamata a ExtraCodecs.intRange
     * nel lambda che definisce il codec per MAX_STACK_SIZE.
     * Questo aumenta il limite massimo per il componente.
     */
    @ModifyArg(
            method = "lambda$static$1",
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

