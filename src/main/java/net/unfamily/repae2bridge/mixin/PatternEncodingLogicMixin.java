package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;

import net.unfamily.repae2bridge.ae2.encoding.MatterEncodingLogicAccess;

/**
 * Persists matter-disintegration as its own tab flag, and skips AE2's amount=1 clamp
 * while that tab is active (matter reuses STONECUTTING slot UI but needs stack counts).
 */
@Mixin(value = PatternEncodingLogic.class, remap = false)
public abstract class PatternEncodingLogicMixin implements MatterEncodingLogicAccess {

    @Shadow
    public abstract void saveChanges();

    @Unique
    private boolean repAe2Bridge$allowStackedInputs;

    @Unique
    private boolean repAe2Bridge$matterEncoding;

    @Override
    public void repAe2Bridge$setAllowStackedInputs(boolean allow) {
        this.repAe2Bridge$allowStackedInputs = allow;
    }

    @Override
    public boolean repAe2Bridge$allowStackedInputs() {
        return repAe2Bridge$allowStackedInputs;
    }

    @Override
    public void repAe2Bridge$setMatterEncoding(boolean matterEncoding) {
        if (this.repAe2Bridge$matterEncoding != matterEncoding) {
            this.repAe2Bridge$matterEncoding = matterEncoding;
            this.repAe2Bridge$allowStackedInputs = matterEncoding;
            saveChanges();
        } else {
            this.repAe2Bridge$allowStackedInputs = matterEncoding;
        }
    }

    @Override
    public boolean repAe2Bridge$isMatterEncoding() {
        return repAe2Bridge$matterEncoding;
    }

    @Inject(method = "fixCraftingRecipes", at = @At("HEAD"), cancellable = true)
    private void repAe2Bridge$skipClampForMatterAmounts(CallbackInfo ci) {
        if (repAe2Bridge$allowStackedInputs || repAe2Bridge$matterEncoding) {
            ci.cancel();
        }
    }

    /**
     * Matter encodes as an AE2 processing pattern. Loading/creating it must not flip the
     * terminal to the Processing tab while the Matter disintegration tab is active.
     */
    @ModifyVariable(method = "setMode", at = @At("HEAD"), argsOnly = true)
    private EncodingMode repAe2Bridge$keepMatterTabOnProcessingPattern(EncodingMode mode) {
        if (repAe2Bridge$matterEncoding && mode == EncodingMode.PROCESSING) {
            return EncodingMode.STONECUTTING;
        }
        return mode;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void repAe2Bridge$writeMatterEncoding(ValueOutput output, CallbackInfo ci) {
        output.putBoolean("repAe2BridgeMatterEncoding", this.repAe2Bridge$matterEncoding);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void repAe2Bridge$readMatterEncoding(ValueInput data, CallbackInfo ci) {
        this.repAe2Bridge$matterEncoding = data.getBooleanOr("repAe2BridgeMatterEncoding", false);
        this.repAe2Bridge$allowStackedInputs = this.repAe2Bridge$matterEncoding;
    }
}
