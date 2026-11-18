package net.unfamily.repae2bridge.mixin;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.ChunkPos;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;

/**
 * Exit mitigation for bridge-in-Sable-zone world unload:
 * skip {@code processUnload} (toData + removeSubLevel kill), then on
 * {@code moveAndSave} write via {@code attemptSaveSubLevel(ChunkPos, data)}.
 */
@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public abstract class SableSubLevelHoldingChunkMapMixin {

    @Inject(method = "saveAll", at = @At("HEAD"))
    private void repAe2Bridge$saveAllBegin(CallbackInfo ci) {
        SableCompat.breadcrumb("SABLE_SAVEALL_BEGIN");
    }

    @Inject(method = "saveAll", at = @At("RETURN"))
    private void repAe2Bridge$saveAllEnd(CallbackInfo ci) {
        SableCompat.breadcrumb("SABLE_SAVEALL_END");
        if (RepAE2BridgeBlockEntity.isServerStopping() && !RepAE2BridgeBlockEntity.isWorldUnloading()) {
            RepAE2BridgeBlockEntity.setSoftTeardown(true);
            RepAE2BridgeBlockEntity.setWorldUnloading(true);
        }
    }

    /**
     * On server stop, do not convert live plots into holding via processUnload
     * (silent kill after TODATA on bridge plots). Leave sublevels for saveAll
     * moveAndSave + exitDirectDisk.
     */
    @Inject(
            method = "processUnload(Lnet/minecraft/world/level/ChunkPos;Ljava/util/Collection;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void repAe2Bridge$skipProcessUnloadOnExit(ChunkPos chunkPos,
                                                      Collection<ServerSubLevel> forceLoaded,
                                                      CallbackInfo ci) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("PROCESS_UNLOAD_SKIP_EXIT");
        ci.cancel();
    }

    @Inject(
            method = "processUnload(Lnet/minecraft/world/level/ChunkPos;Ljava/util/Collection;)V",
            at = @At("HEAD")
    )
    private void repAe2Bridge$processUnloadBegin(ChunkPos chunkPos,
                                                 Collection<ServerSubLevel> forceLoaded,
                                                 CallbackInfo ci) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("PROCESS_UNLOAD_BEGIN");
        }
    }

    @Inject(
            method = "processUnload(Lnet/minecraft/world/level/ChunkPos;Ljava/util/Collection;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelSerializer;toData(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ljava/util/List;)Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;",
                    shift = At.Shift.AFTER
            )
    )
    private void repAe2Bridge$processUnloadAfterToData(ChunkPos chunkPos,
                                                       Collection<ServerSubLevel> forceLoaded,
                                                       CallbackInfo ci) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("PROCESS_UNLOAD_AFTER_TODATA");
        }
    }

    @Inject(
            method = "processUnload(Lnet/minecraft/world/level/ChunkPos;Ljava/util/Collection;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;removeSubLevel(Ldev/ryanhcode/sable/sublevel/SubLevel;Ldev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V"
            )
    )
    private void repAe2Bridge$processUnloadBeforeRemove(ChunkPos chunkPos,
                                                        Collection<ServerSubLevel> forceLoaded,
                                                        CallbackInfo ci) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("PROCESS_UNLOAD_BEFORE_REMOVE");
        }
    }

    @Inject(
            method = "moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/level/ChunkPos;Ljava/util/List;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelSerializer;toData(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ljava/util/List;)Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;",
                    shift = At.Shift.AFTER
            )
    )
    private void repAe2Bridge$voidAfterToData(ServerSubLevel subLevel, ChunkPos chunkPos, List<UUID> passengers,
                                              CallbackInfo ci) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("MOVE_SAVE_VOID_AFTER_TODATA");
        }
    }

    /**
     * On server stop, avoid getOrLoadHoldingChunk (silent kill after TODATA_END).
     * Write SubLevelData straight to storage by chunk position.
     */
    @Inject(
            method = "moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Lnet/minecraft/world/level/ChunkPos;)Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void repAe2Bridge$exitDirectDisk(
            ServerSubLevel subLevel,
            SubLevelData data,
            GlobalSavedSubLevelPointer pointer,
            ChunkPos chunkPos,
            CallbackInfoReturnable<GlobalSavedSubLevelPointer> cir) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("MOVE_SAVE_4ARG_BEGIN");
        SableCompat.breadcrumb("MOVE_SAVE_EXIT_DIRECT_DISK");
        SubLevelHoldingChunkMap self = (SubLevelHoldingChunkMap) (Object) this;
        GlobalSavedSubLevelPointer result = self.getStorage().attemptSaveSubLevel(chunkPos, data);
        cir.setReturnValue(result);
    }

    @Inject(method = "getOrLoadHoldingChunk", at = @At("HEAD"))
    private void repAe2Bridge$getOrLoadBegin(ChunkPos chunkPos, boolean create,
                                             CallbackInfoReturnable<SubLevelHoldingChunk> cir) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("GET_OR_LOAD_HOLDING_BEGIN");
        }
    }

    @Inject(method = "getOrLoadHoldingChunk", at = @At("RETURN"))
    private void repAe2Bridge$getOrLoadEnd(ChunkPos chunkPos, boolean create,
                                           CallbackInfoReturnable<SubLevelHoldingChunk> cir) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("GET_OR_LOAD_HOLDING_END");
        }
    }
}
