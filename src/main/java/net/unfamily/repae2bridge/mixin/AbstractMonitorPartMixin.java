package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import appeng.api.stacks.AEKey;
import appeng.parts.reporting.AbstractMonitorPart;
import appeng.util.InteractionUtil;

import net.unfamily.repae2bridge.ae2.MatterKeyConversion;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;
import net.unfamily.repae2bridge.ae2.matter.MatterTankItemHelper;

/**
 * Matter-tank / virtual-matter-item click rules on storage monitors.
 */
@Mixin(value = AbstractMonitorPart.class, remap = false)
public abstract class AbstractMonitorPartMixin {

    @Shadow
    protected abstract void configureWatchers();

    @Inject(method = "onUseItemOn", at = @At("HEAD"), cancellable = true)
    private void repAe2Bridge$matterTankConfigure(
            ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos, CallbackInfoReturnable<Boolean> cir) {
        AbstractMonitorPart self = (AbstractMonitorPart) (Object) this;
        if (self.isLocked() || InteractionUtil.isInAlternateUseMode(player)) {
            return;
        }

        // Virtual matter item (JEI proxy) → configure real matter key
        var matterFromItem = MatterKeyConversion.toMatterKey(heldItem.getItem());
        if (matterFromItem != null) {
            if (self.isClientSide()) {
                cir.setReturnValue(true);
                return;
            }
            if (!self.getMainNode().isActive()) {
                cir.setReturnValue(false);
                return;
            }
            self.setConfiguredItem(matterFromItem);
            configureWatchers();
            self.getHost().markForSave();
            cir.setReturnValue(true);
            return;
        }

        if (!MatterTankItemHelper.isMatterTankItem(heldItem)) {
            return;
        }
        // isClientSide() lives on AEBasePart (public final), not on AbstractMonitorPart
        if (self.isClientSide()) {
            cir.setReturnValue(true);
            return;
        }
        if (!self.getMainNode().isActive()) {
            cir.setReturnValue(false);
            return;
        }

        var contained = MatterTankItemHelper.getContainedMatter(heldItem);
        boolean tankFilled = contained != null && !contained.isEmpty();
        AEKey displayed = self.getDisplayed();

        AEKey next;
        if (displayed == null) {
            // Nothing configured: filled tank → its matter; empty tank → AE2 vanilla (tank item)
            if (!tankFilled) {
                return;
            }
            next = ReplicationMatterKey.of(contained.getMatterType());
        } else if (tankFilled) {
            next = ReplicationMatterKey.of(contained.getMatterType());
        } else if (displayed instanceof ReplicationMatterKey current) {
            var nextType = MatterTankItemHelper.nextMatterType(current.resolveMatterType());
            next = nextType == null ? null : ReplicationMatterKey.of(nextType);
        } else {
            // e.g. tank item already configured → jump into matter cycle
            var first = MatterTankItemHelper.firstMatterType();
            next = first == null ? null : ReplicationMatterKey.of(first);
        }

        if (next == null) {
            cir.setReturnValue(false);
            return;
        }

        self.setConfiguredItem(next);
        configureWatchers();
        self.getHost().markForSave();
        cir.setReturnValue(true);
    }
}
