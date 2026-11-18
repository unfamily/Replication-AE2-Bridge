package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.chunk.LevelChunk;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;

/**
 * Breadcrumbs around tick serialization — next step after BE NBT in Sable plot save.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkTicksSerializationMixin {

    @Inject(method = "getTicksForSerialization", at = @At("HEAD"))
    private void repAe2Bridge$ticksBegin(long gameTime, CallbackInfoReturnable<?> cir) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("PLOT_TICKS_BEGIN");
    }

    @Inject(method = "getTicksForSerialization", at = @At("RETURN"))
    private void repAe2Bridge$ticksEnd(long gameTime, CallbackInfoReturnable<?> cir) {
        if (!RepAE2BridgeBlockEntity.isServerStopping()) {
            return;
        }
        SableCompat.breadcrumb("PLOT_TICKS_END");
    }
}
