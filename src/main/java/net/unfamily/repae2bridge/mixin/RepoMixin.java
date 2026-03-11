package net.unfamily.repae2bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.unfamily.repae2bridge.ae2.MatterKeyType;
import net.unfamily.repae2bridge.util.MatterTypeUtil;

/**
 * When building the terminal view, matter items (AEItemKey) are treated as "Matter" type
 * so they appear only in the Matter tab, not in the Items tab. The bridge keeps matter
 * as items in storage; this mixin only changes which tab they show under.
 */
@Mixin(targets = "appeng.client.gui.me.common.Repo")
public abstract class RepoMixin {

    @Redirect(
            method = "addEntriesToView",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;getType()Lappeng/api/stacks/AEKeyType;")
    )
    private AEKeyType repae2bridge$matterItemsAsMatterType(AEKey key) {
        if (key instanceof AEItemKey itemKey && MatterTypeUtil.isMatterItem(itemKey.getItem())) {
            return MatterKeyType.INSTANCE;
        }
        return key.getType();
    }
}
