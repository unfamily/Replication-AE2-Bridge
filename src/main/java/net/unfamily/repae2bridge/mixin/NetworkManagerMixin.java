package net.unfamily.repae2bridge.mixin;

import com.hrznstudio.titanium.block_network.Network;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.hrznstudio.titanium.block_network.NetworkRegistry;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.unfamily.repae2bridge.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Mixin for Titanium's NetworkManager to prevent crashes when network elements
 * have null network references during merge operations.
 *
 * Root cause: during world loading, elements are deserialized from NBT into the
 * elements map, but their {@code network} field remains null until
 * {@code Network.update() -> scanGraph() -> joinNetwork()} populates it.
 * If another block's {@code onLoad()} triggers
 * {@code addElement() -> mergeNetworksIntoOne()} before the existing elements
 * have been assigned to networks, the original code throws
 * {@code RuntimeException("Element network is null!")}, crashing the server.
 *
 * This mixin intercepts the merge when null networks are detected, skipping
 * those elements gracefully. If ALL candidates have null networks, a new
 * network is formed for the position instead.
 */
@Mixin(value = NetworkManager.class, remap = false)
public abstract class NetworkManagerMixin {

    private static final Logger REPAE2BRIDGE_LOGGER = LogManager.getLogger("RepAE2Bridge/NetworkFix");

    @Shadow
    public abstract void addNetwork(Network network);

    @Shadow
    public abstract void removeNetwork(String id);

    /**
     * Intercepts mergeNetworksIntoOne when any candidate element has a null network.
     * In the normal case (all networks valid), the original method runs untouched.
     * When null networks are detected, this provides a safe alternative that skips
     * invalid elements instead of crashing.
     */
    @Inject(method = "mergeNetworksIntoOne", at = @At("HEAD"), cancellable = true)
    private void repae2bridge$safeMergeNetworks(Set<NetworkElement> candidates, Level level, BlockPos pos, CallbackInfo ci) {
        // Config check: if disabled, let the original (potentially crashing) method run
        if (!Config.enableNetworkManagerFix) {
            return;
        }

        // Quick scan: if all candidates have valid networks, let the original method handle it
        boolean hasNullNetwork = false;
        for (NetworkElement candidate : candidates) {
            if (candidate.getNetwork() == null) {
                hasNullNetwork = true;
                break;
            }
        }

        if (!hasNullNetwork) {
            return; // All networks valid, original method is safe to run
        }

        // Cancel original method to prevent RuntimeException("Element network is null!")
        ci.cancel();

        REPAE2BRIDGE_LOGGER.warn(
                "Detected element(s) with null network during merge at {}. " +
                "Applying safe merge to prevent crash (this is normal during world loading).", pos
        );

        if (candidates.isEmpty()) {
            REPAE2BRIDGE_LOGGER.warn("No candidates for merge at {} (unexpected state)", pos);
            return;
        }

        // Collect only candidates with valid networks
        Set<Network> networkCandidates = new HashSet<>();
        ResourceLocation networkType = null;

        for (NetworkElement candidate : candidates) {
            if (networkType == null) {
                networkType = candidate.getNetworkType();
            }
            if (candidate.getNetwork() != null) {
                networkCandidates.add(candidate.getNetwork());
            } else {
                REPAE2BRIDGE_LOGGER.debug("Skipping element at {} with null network during merge", candidate.getPos());
            }
        }

        if (networkCandidates.isEmpty()) {
            // All candidates had null networks - form a new network for this position
            REPAE2BRIDGE_LOGGER.info(
                    "All adjacent elements had null networks at {}, forming new network of type {}",
                    pos, networkType
            );
            try {
                var factory = NetworkRegistry.INSTANCE.getFactory(networkType);
                if (factory == null) {
                    REPAE2BRIDGE_LOGGER.error("No network factory found for type {} at {}", networkType, pos);
                    return;
                }
                Network network = factory.create(pos);
                addNetwork(network);
                network.scanGraph(level, pos);
            } catch (Exception e) {
                REPAE2BRIDGE_LOGGER.error("Failed to form new network at {}: {}", pos, e.getMessage());
            }
            return;
        }

        // Proceed with standard merge logic using only valid candidates
        Iterator<Network> nets = networkCandidates.iterator();
        Network mainNetwork = nets.next();
        Set<Network> mergedNetworks = new HashSet<>();

        while (nets.hasNext()) {
            Network otherNetwork = nets.next();
            if (mainNetwork.getType().equals(otherNetwork.getType())) {
                mergedNetworks.add(otherNetwork);
                try {
                    removeNetwork(otherNetwork.getId());
                } catch (RuntimeException e) {
                    REPAE2BRIDGE_LOGGER.warn("Failed to remove network {} during merge: {}", otherNetwork.getId(), e.getMessage());
                }
            }
        }

        mainNetwork.scanGraph(level, pos);
        mergedNetworks.forEach(n -> n.onMergedWith(mainNetwork));
    }
}
