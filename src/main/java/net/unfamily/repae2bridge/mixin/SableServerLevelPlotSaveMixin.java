package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;

/**
 * Breadcrumbs around Sable {@code ServerLevelPlot.save()} for silent kills
 * after the last BE NBT write (ticks / light / attachments / return).
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot", remap = false)
public abstract class SableServerLevelPlotSaveMixin {

    @Inject(method = "save", at = @At("HEAD"))
    private void repAe2Bridge$plotSaveBegin(CallbackInfoReturnable<CompoundTag> cir) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("PLOT_SAVE_BEGIN");
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void repAe2Bridge$plotSaveEnd(CallbackInfoReturnable<CompoundTag> cir) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("PLOT_SAVE_END");
    }
}
