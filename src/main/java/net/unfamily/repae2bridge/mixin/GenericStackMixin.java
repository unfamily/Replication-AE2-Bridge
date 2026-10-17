package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;

import net.unfamily.repae2bridge.ae2.MatterKeyConversion;

/**
 * JEI / ghost-slot ItemStacks of virtual matter items become {@code ReplicationMatterKey}.
 */
@Mixin(value = GenericStack.class, remap = false)
public abstract class GenericStackMixin {

    @Inject(method = "fromItemStack", at = @At("RETURN"), cancellable = true)
    private static void repAe2Bridge$convertMatterItem(
            ItemStack stack, CallbackInfoReturnable<GenericStack> cir) {
        GenericStack current = cir.getReturnValue();
        if (current == null) {
            return;
        }
        GenericStack converted = MatterKeyConversion.convert(current);
        if (converted != null && converted != current) {
            cir.setReturnValue(converted);
        }
    }
}
