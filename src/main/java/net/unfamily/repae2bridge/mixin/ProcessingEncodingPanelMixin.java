package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.me.items.ProcessingEncodingPanel;
import appeng.client.gui.widgets.ActionButton;

/**
 * AE2 sets {@code cycleOutputBtn} visibility from {@code canCycleProcessingOutputs()} only,
 * ignoring panel visibility — so the button stays clickable when the processing panel is hidden
 * (e.g. Matter encoding tab). Force it off when the panel is hidden.
 */
@Mixin(value = ProcessingEncodingPanel.class, remap = false)
public abstract class ProcessingEncodingPanelMixin {

    @Shadow
    @Final
    private ActionButton cycleOutputBtn;

    @Inject(method = "setVisible", at = @At("RETURN"))
    private void repAe2Bridge$hideCycleWhenPanelHidden(boolean visible, CallbackInfo ci) {
        if (!visible) {
            cycleOutputBtn.setVisibility(false);
        }
    }
}
