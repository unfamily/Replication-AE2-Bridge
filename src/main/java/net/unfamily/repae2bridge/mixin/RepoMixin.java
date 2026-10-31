package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;

/**
 * Ensures {@link ReplicationMatterKey} entries appear under the Matter tab in AE2 terminals.
 * Redundant when {@link MatterKeyType} is already returned by the key, but keeps tab filtering
 * stable if AE2's Repo groups by a different type path.
 */
@Mixin(targets = "appeng.client.gui.me.common.Repo")
public abstract class RepoMixin {

    @Redirect(
            method = "addEntriesToView",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;getType()Lappeng/api/stacks/AEKeyType;")
    )
    private AEKeyType repae2bridge$matterKeyAsMatterType(AEKey key) {
        if (key instanceof ReplicationMatterKey) {
            return MatterKeyType.INSTANCE;
        }
        return key.getType();
    }
}
