package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;

/**
 * Breadcrumbs around Sable {@code SubLevelHoldingChunkMap.saveAll()}.
 * On RETURN during server stop, raise deferred softTeardown + worldUnloading
 * so plot serialization matched pause (both flags false mid-save).
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap", remap = false)
public abstract class SableSubLevelHoldingChunkMapMixin {

    @Inject(method = "saveAll", at = @At("HEAD"))
    private void repAe2Bridge$saveAllBegin(CallbackInfo ci) {
        SableCompat.breadcrumb("SABLE_SAVEALL_BEGIN");
    }

    @Inject(method = "saveAll", at = @At("RETURN"))
    private void repAe2Bridge$saveAllEnd(CallbackInfo ci) {
        SableCompat.breadcrumb("SABLE_SAVEALL_END");
        // After plot save during stop: raise soft + unload together (pause-identical until now)
        if (RepAE2BridgeBlockEntity.isServerStopping() && !RepAE2BridgeBlockEntity.isWorldUnloading()) {
            RepAE2BridgeBlockEntity.setSoftTeardown(true);
            RepAE2BridgeBlockEntity.setWorldUnloading(true);
        }
    }
}
