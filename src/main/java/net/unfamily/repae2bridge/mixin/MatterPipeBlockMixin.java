package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.buuz135.replication.block.MatterPipeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.unfamily.repae2bridge.parts.CableRepAe2BridgePart;

/**
 * Allow matter pipes to connect to an AE2 cable bus when a bridge cable part
 * faces the pipe on the external side.
 */
@Mixin(value = MatterPipeBlock.class, remap = false)
public abstract class MatterPipeBlockMixin {

    @Inject(method = "getConnectionType", at = @At("HEAD"), cancellable = true)
    private void repae2bridge$cableBridgePart(
            Level world,
            BlockPos pos,
            Direction direction,
            BlockState state,
            CallbackInfoReturnable<Boolean> cir) {
        if (world == null || world.isClientSide()) {
            return;
        }
        BlockPos relative = pos.relative(direction);
        if (CableRepAe2BridgePart.hasBridgePartFacing(world, relative, direction.getOpposite())) {
            cir.setReturnValue(true);
        }
    }
}
