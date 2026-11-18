package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.ChunkPos;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;

/**
 * Breadcrumbs around Sable region I/O after serialize/toData.
 * Exact Sable parameter types required — {@code Object} handlers fail mixin apply.
 */
@Mixin(value = SubLevelStorage.class, remap = false)
public abstract class SableSubLevelStorageMixin {

    @Inject(
            method = "attemptSaveSubLevel(Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)V",
            at = @At("HEAD")
    )
    private void repAe2Bridge$attemptSavePtrBegin(GlobalSavedSubLevelPointer pointer, SubLevelData data,
                                                  CallbackInfo ci) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("ATTEMPT_SAVE_SUBLEVEL_BEGIN");
        }
    }

    @Inject(
            method = "attemptSaveSubLevel(Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)V",
            at = @At("RETURN")
    )
    private void repAe2Bridge$attemptSavePtrEnd(GlobalSavedSubLevelPointer pointer, SubLevelData data,
                                                CallbackInfo ci) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("ATTEMPT_SAVE_SUBLEVEL_END");
        }
    }

    @Inject(
            method = "attemptSaveSubLevel(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;",
            at = @At("HEAD")
    )
    private void repAe2Bridge$attemptSaveChunkBegin(ChunkPos pos, SubLevelData data,
                                                    CallbackInfoReturnable<GlobalSavedSubLevelPointer> cir) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("ATTEMPT_SAVE_SUBLEVEL_CHUNK_BEGIN");
        }
    }

    @Inject(
            method = "attemptSaveSubLevel(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;",
            at = @At("RETURN")
    )
    private void repAe2Bridge$attemptSaveChunkEnd(ChunkPos pos, SubLevelData data,
                                                  CallbackInfoReturnable<GlobalSavedSubLevelPointer> cir) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("ATTEMPT_SAVE_SUBLEVEL_CHUNK_END");
        }
    }

    @Inject(method = "flush", at = @At("HEAD"))
    private void repAe2Bridge$flushBegin(CallbackInfo ci) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("SABLE_FLUSH_BEGIN");
        }
    }

    @Inject(method = "flush", at = @At("RETURN"))
    private void repAe2Bridge$flushEnd(CallbackInfo ci) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("SABLE_FLUSH_END");
        }
    }
}
