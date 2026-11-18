package net.unfamily.repae2bridge.mixin;

import java.lang.reflect.Method;
import java.util.List;

import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;

/**
 * Exit diagnosis + mitigation after {@code ServerLevelPlot.save()} in Sable serialize:
 * breadcrumbs across pose/physics gap, and skip {@code RigidBodyHandle.of} while serverStopping
 * (velocity written as zero — craft is idle on quit).
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer", remap = false)
public abstract class SableSubLevelSerializeMixin {

    @Unique
    private static Method repAe2Bridge$rigidBodyOf;

    @Unique
    private static Method repAe2Bridge$getLinearVelocity;

    @Unique
    private static Method repAe2Bridge$getAngularVelocity;

    @Inject(
            method = "serialize",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/plot/ServerLevelPlot;save()Lnet/minecraft/nbt/CompoundTag;",
                    shift = At.Shift.AFTER
            )
    )
    private static void repAe2Bridge$afterPlot(Object subLevel, List<?> passengers,
                                               CallbackInfoReturnable<CompoundTag> cir) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("SERIALIZE_AFTER_PLOT");
        }
    }

    @Inject(method = "serialize", at = @At("RETURN"))
    private static void repAe2Bridge$serializeEnd(Object subLevel, List<?> passengers,
                                                  CallbackInfoReturnable<CompoundTag> cir) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("SERIALIZE_END");
        }
    }

    @Inject(method = "toData", at = @At("RETURN"))
    private static void repAe2Bridge$toDataEnd(Object subLevel, List<?> passengers,
                                               CallbackInfoReturnable<?> cir) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("TODATA_END");
        }
    }

    @Redirect(
            method = "serialize",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;of(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;"
            )
    )
    private static Object repAe2Bridge$rigidBodyOf(Object subLevel) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("SERIALIZE_BEFORE_RIGIDBODY");
            SableCompat.breadcrumb("SERIALIZE_SKIP_RIGIDBODY");
            return null;
        }
        try {
            return repAe2Bridge$invokeOf(subLevel);
        } catch (Throwable t) {
            return null;
        }
    }

    @Redirect(
            method = "serialize",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;getLinearVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            )
    )
    private static Vector3d repAe2Bridge$safeLinearVelocity(Object handle, Vector3d out) {
        if (handle == null || RepAE2BridgeBlockEntity.isServerStopping()) {
            return out.set(0.0D, 0.0D, 0.0D);
        }
        try {
            return repAe2Bridge$invokeLinear(handle, out);
        } catch (Throwable t) {
            return out.set(0.0D, 0.0D, 0.0D);
        }
    }

    @Redirect(
            method = "serialize",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;getAngularVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            )
    )
    private static Vector3d repAe2Bridge$safeAngularVelocity(Object handle, Vector3d out) {
        if (handle == null || RepAE2BridgeBlockEntity.isServerStopping()) {
            return out.set(0.0D, 0.0D, 0.0D);
        }
        try {
            return repAe2Bridge$invokeAngular(handle, out);
        } catch (Throwable t) {
            return out.set(0.0D, 0.0D, 0.0D);
        }
    }

    @Unique
    private static Object repAe2Bridge$invokeOf(Object subLevel) throws Exception {
        if (repAe2Bridge$rigidBodyOf == null) {
            Class<?> handleCl = Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle");
            Class<?> subCl = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            repAe2Bridge$rigidBodyOf = handleCl.getMethod("of", subCl);
        }
        return repAe2Bridge$rigidBodyOf.invoke(null, subLevel);
    }

    @Unique
    private static Vector3d repAe2Bridge$invokeLinear(Object handle, Vector3d out) throws Exception {
        if (repAe2Bridge$getLinearVelocity == null) {
            repAe2Bridge$getLinearVelocity = handle.getClass().getMethod("getLinearVelocity", Vector3d.class);
        }
        return (Vector3d) repAe2Bridge$getLinearVelocity.invoke(handle, out);
    }

    @Unique
    private static Vector3d repAe2Bridge$invokeAngular(Object handle, Vector3d out) throws Exception {
        if (repAe2Bridge$getAngularVelocity == null) {
            repAe2Bridge$getAngularVelocity = handle.getClass().getMethod("getAngularVelocity", Vector3d.class);
        }
        return (Vector3d) repAe2Bridge$getAngularVelocity.invoke(handle, out);
    }
}
