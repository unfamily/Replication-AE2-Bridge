package net.unfamily.repae2bridge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Breadcrumbs around BE NBT save so silent kills after our MA/bridge saves
 * show the next BlockEntity type/pos in the chunk serialization order.
 * Active only while {@code serverStopping} (exit diagnosis; pause has flag false).
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkBlockEntitySaveMixin {

    @Inject(method = "getBlockEntityNbtForSaving", at = @At("HEAD"))
    private void repAe2Bridge$beSaveBegin(BlockPos pos, HolderLookup.Provider registries,
                                          CallbackInfoReturnable<CompoundTag> cir) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        LevelChunk self = (LevelChunk) (Object) this;
        BlockEntity be = self.getBlockEntities().get(pos);
        String type = "absent";
        if (be != null) {
            ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            type = key != null ? key.toString() : be.getClass().getSimpleName();
        }
        SableCompat.breadcrumb("BE_SAVE_BEGIN type=" + type + " pos=" + pos);
    }

    @Inject(method = "getBlockEntityNbtForSaving", at = @At("RETURN"))
    private void repAe2Bridge$beSaveEnd(BlockPos pos, HolderLookup.Provider registries,
                                        CallbackInfoReturnable<CompoundTag> cir) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("BE_SAVE_END pos=" + pos + " nbtNull=" + (cir.getReturnValue() == null));
    }
}
