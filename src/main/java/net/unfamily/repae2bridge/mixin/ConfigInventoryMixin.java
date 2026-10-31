package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import appeng.api.stacks.GenericStack;
import appeng.util.ConfigInventory;

import net.unfamily.repae2bridge.ae2.MatterKeyConversion;

/**
 * Ensures config/filter slots never keep virtual matter {@code AEItemKey} proxies.
 */
@Mixin(value = ConfigInventory.class, remap = false)
public abstract class ConfigInventoryMixin {

    @ModifyVariable(method = "setStack", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private GenericStack repAe2Bridge$convertMatterStack(GenericStack stack) {
        return MatterKeyConversion.convert(stack);
    }
}
