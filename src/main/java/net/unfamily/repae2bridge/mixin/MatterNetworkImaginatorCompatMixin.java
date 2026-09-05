package net.unfamily.repae2bridge.mixin;

import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Soft compatibility for ReplicateMekanism Imaginator.
 * <p>
 * Upstream RM ships {@code MatterNetworkMixin} in the jar but does not list it in
 * {@code replicatemekanism.mixins.json} (verified on 1.4.1), so Imaginators never
 * enter {@link MatterNetwork#getReplicators()}. Vanilla Replication only adds
 * {@code ReplicatorBlockEntity}. This mixin restores that registration when RM is loaded.
 */
@Mixin(value = MatterNetwork.class, remap = false)
public class MatterNetworkImaginatorCompatMixin {

    @Unique
    private static final String IMAGINATOR_BE = "com.github.mochi7054.imaginator.ImaginatorBlockEntity";

    @Shadow
    private List<NetworkElement> queueNetworkElements;

    @Shadow
    private List<NetworkElement> replicators;

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"))
    private void rep_ae2_bridge$registerImaginatorsAsReplicators(Level level, CallbackInfo ci) {
        if (!ModList.get().isLoaded("replicatemekanism")) {
            return;
        }
        if (this.queueNetworkElements == null || this.replicators == null) {
            return;
        }

        for (NetworkElement element : this.queueNetworkElements) {
            if (element.getLevel() == null || !element.getLevel().isLoaded(element.getPos())) {
                continue;
            }
            BlockEntity be = element.getLevel().getBlockEntity(element.getPos());
            if (be != null && isImaginator(be) && !this.replicators.contains(element)) {
                this.replicators.add(element);
            }
        }
    }

    @Unique
    private static boolean isImaginator(BlockEntity be) {
        // Soft check: no compile dependency on ReplicateMekanism
        Class<?> clazz = be.getClass();
        while (clazz != null) {
            if (IMAGINATOR_BE.equals(clazz.getName())) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}
