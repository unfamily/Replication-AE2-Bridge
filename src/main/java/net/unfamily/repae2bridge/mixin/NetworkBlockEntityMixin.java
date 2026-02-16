package net.unfamily.repae2bridge.mixin;

import com.buuz135.replication.block.tile.NetworkBlockEntity;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import net.unfamily.repae2bridge.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin for Replication's NetworkBlockEntity to prevent crashes during onLoad()
 * when NetworkManager.addElement() throws RuntimeExceptions due to network
 * state issues (null networks, duplicate elements, etc.).
 *
 * This acts as a comprehensive safety net: the NetworkManagerMixin fixes the
 * specific "Element network is null" crash in mergeNetworksIntoOne, while this
 * mixin catches ANY RuntimeException from the entire addElement call chain.
 * This protects ALL Replication block entities (pipes, replicators, etc.),
 * not just the AE2 bridge.
 *
 * When addElement fails, the element is not added to the network immediately.
 * On subsequent world ticks, when Network.update() runs and rescans the graph,
 * or when adjacent blocks trigger a rescan, the element will be picked up
 * and properly integrated into the network.
 */
@Mixin(value = NetworkBlockEntity.class, remap = false)
public abstract class NetworkBlockEntityMixin {

    private static final Logger REPAE2BRIDGE_LOGGER = LogManager.getLogger("RepAE2Bridge/NetworkEntityFix");

    /**
     * Wraps the NetworkManager.addElement() call in onLoad() with a try-catch
     * to prevent any RuntimeException from crashing the server during world loading.
     *
     * Common exceptions caught:
     * - "Element network is null!" (from mergeNetworksIntoOne - also fixed by NetworkManagerMixin)
     * - "Network element at X already exists" (from addElement, race condition during loading)
     * - "Cannot merge networks: no candidates" (from mergeNetworksIntoOne, edge case)
     * - Any other unexpected RuntimeException in the network graph operations
     */
    @Redirect(
            method = "onLoad",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hrznstudio/titanium/block_network/NetworkManager;addElement(Lcom/hrznstudio/titanium/block_network/element/NetworkElement;)V"
            )
    )
    private void repae2bridge$safeAddElement(NetworkManager networkManager, NetworkElement element) {
        // Config check: if disabled, call the original method directly (no try-catch)
        if (!Config.enableNetworkBlockEntityFix) {
            networkManager.addElement(element);
            return;
        }

        try {
            networkManager.addElement(element);
        } catch (RuntimeException e) {
            REPAE2BRIDGE_LOGGER.error(
                    "Failed to add network element at {} during onLoad: {}. " +
                    "The element will be integrated when the network rescans.",
                    element != null ? element.getPos() : "unknown",
                    e.getMessage()
            );
        }
    }
}
