package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;

/**
 * Breadcrumbs around Sable light/attachment chunk writes after BE + ticks.
 */
@Mixin(targets = "dev.ryanhcode.sable.neoforge.platform.SablePlotPlatformImpl", remap = false)
public abstract class SablePlotPlatformWriteMixin {

    @Inject(method = "writeLightData", at = @At("HEAD"))
    private void repAe2Bridge$lightBegin(CompoundTag tag, RegistryAccess access, LevelChunk chunk, CallbackInfo ci) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("PLOT_LIGHT_BEGIN");
    }

    @Inject(method = "writeLightData", at = @At("RETURN"))
    private void repAe2Bridge$lightEnd(CompoundTag tag, RegistryAccess access, LevelChunk chunk, CallbackInfo ci) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("PLOT_LIGHT_END");
    }

    @Inject(method = "writeChunkAttachments", at = @At("HEAD"))
    private void repAe2Bridge$attachBegin(CompoundTag tag, RegistryAccess access, LevelChunk chunk, CallbackInfo ci) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("PLOT_ATTACH_BEGIN");
    }

    @Inject(method = "writeChunkAttachments", at = @At("RETURN"))
    private void repAe2Bridge$attachEnd(CompoundTag tag, RegistryAccess access, LevelChunk chunk, CallbackInfo ci) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("PLOT_ATTACH_END");
    }
}
