package net.unfamily.repae2bridge.mixin;

import java.util.List;
import java.util.UUID;

import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.compat.SableCompat;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;

/**
 * Exit diagnosis + mitigation after {@code ServerLevelPlot.save()} in Sable serialize.
 * Exact Sable parameter types required — {@code Object} handlers fail mixin apply.
 */
@Mixin(value = SubLevelSerializer.class, remap = false)
public abstract class SableSubLevelSerializeMixin {

    @Inject(
            method = "serialize",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/plot/ServerLevelPlot;save()Lnet/minecraft/nbt/CompoundTag;",
                    shift = At.Shift.AFTER
            )
    )
    private static void repAe2Bridge$afterPlot(ServerSubLevel subLevel, List<UUID> passengers,
                                               CallbackInfoReturnable<CompoundTag> cir) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("SERIALIZE_AFTER_PLOT");
        }
    }

    @Inject(method = "serialize", at = @At("RETURN"))
    private static void repAe2Bridge$serializeEnd(ServerSubLevel subLevel, List<UUID> passengers,
                                                  CallbackInfoReturnable<CompoundTag> cir) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("SERIALIZE_END");
        }
    }

    @Inject(method = "toData", at = @At("RETURN"))
    private static void repAe2Bridge$toDataEnd(ServerSubLevel subLevel, List<UUID> passengers,
                                               CallbackInfoReturnable<SubLevelData> cir) {
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
    private static RigidBodyHandle repAe2Bridge$rigidBodyOf(ServerSubLevel subLevel) {
        if (RepAE2BridgeBlockEntity.isServerStopping()) {
            SableCompat.breadcrumb("SERIALIZE_BEFORE_RIGIDBODY");
            SableCompat.breadcrumb("SERIALIZE_SKIP_RIGIDBODY");
            return null;
        }
        return RigidBodyHandle.of(subLevel);
    }

    @Redirect(
            method = "serialize",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;getLinearVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            )
    )
    private static Vector3d repAe2Bridge$safeLinearVelocity(RigidBodyHandle handle, Vector3d out) {
        if (handle == null || RepAE2BridgeBlockEntity.isServerStopping()) {
            return out.set(0.0D, 0.0D, 0.0D);
        }
        return handle.getLinearVelocity(out);
    }

    @Redirect(
            method = "serialize",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;getAngularVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            )
    )
    private static Vector3d repAe2Bridge$safeAngularVelocity(RigidBodyHandle handle, Vector3d out) {
        if (handle == null || RepAE2BridgeBlockEntity.isServerStopping()) {
            return out.set(0.0D, 0.0D, 0.0D);
        }
        return handle.getAngularVelocity(out);
    }
}
