package net.unfamily.repae2bridge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import guideme.internal.extensions.DefaultExtensions;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * GuideME builds scene tag compilers during common init (Replication guide).
 * Skip them on dedicated server — those classes reference client-only render types.
 */
@Mixin(value = DefaultExtensions.class, remap = false)
public abstract class GuideMeSceneCompilersMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "sceneElementTagCompilers", at = @At("HEAD"), cancellable = true)
    private static void repae2bridge$skipOnDedicatedServer(CallbackInfoReturnable cir) {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            cir.setReturnValue(List.of());
        }
    }
}
