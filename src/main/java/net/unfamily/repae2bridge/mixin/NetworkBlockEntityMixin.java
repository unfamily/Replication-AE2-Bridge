package net.unfamily.repae2bridge.mixin;

import com.buuz135.replication.block.tile.NetworkBlockEntity;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.compat.SableCompat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin for Replication's NetworkBlockEntity to prevent crashes during onLoad()
 * / setRemoved() when NetworkManager throws RuntimeExceptions due to network
 * state issues (null networks, missing elements, Sable block moves, etc.).
 *
 * This acts as a comprehensive safety net: the NetworkManagerMixin fixes the
 * specific "Element network is null" crash in mergeNetworksIntoOne, while this
 * mixin catches ANY RuntimeException from the addElement / removeElement call chain.
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

        // Sable plot coords share the host Level with Titanium NetworkManager — never register there.
        if (element != null) {
            Level level = element.getLevel();
            BlockPos pos = element.getPos();
            if (SableCompat.isInSubLevelPlot(level, pos)) {
                if (Config.enableSableLogging) {
                    REPAE2BRIDGE_LOGGER.debug(
                            "Skipping addElement at {} during onLoad — Sable sub-level plot",
                            pos
                    );
                }
                return;
            }
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

    /**
     * Replication's setRemoved always calls removeElement, then uses the previously
     * fetched element without a null check. When the element is already gone
     * (Bridge disconnect, Sable move), that becomes remove-miss + NPE on getNetwork().
     * Skip remove when missing; swallow remaining RuntimeExceptions.
     */
    @Redirect(
            method = "setRemoved",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hrznstudio/titanium/block_network/NetworkManager;removeElement(Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void repae2bridge$safeRemoveElement(NetworkManager networkManager, BlockPos pos) {
        if (!Config.enableNetworkBlockEntityFix) {
            networkManager.removeElement(pos);
            return;
        }

        NetworkElement existing = networkManager.getElement(pos);
        if (existing == null) {
            REPAE2BRIDGE_LOGGER.debug(
                    "Skipping removeElement in NetworkBlockEntity.setRemoved at {}: element already absent.",
                    pos
            );
            return;
        }

        try {
            networkManager.removeElement(pos);
        } catch (RuntimeException e) {
            REPAE2BRIDGE_LOGGER.warn(
                    "Failed to remove network element at {} during setRemoved: {}",
                    pos, e.getMessage()
            );
        }
    }

    /**
     * After removeElement, Replication calls getNetwork() on the cached element even when
     * that element was null (dead ifnull in upstream bytecode). Guard the NPE.
     */
    @Redirect(
            method = "setRemoved",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hrznstudio/titanium/block_network/element/NetworkElement;getNetwork()Lcom/hrznstudio/titanium/block_network/Network;"
            )
    )
    private com.hrznstudio.titanium.block_network.Network repae2bridge$safeGetNetwork(NetworkElement element) {
        if (!Config.enableNetworkBlockEntityFix) {
            return element.getNetwork();
        }
        if (element == null) {
            return null;
        }
        try {
            return element.getNetwork();
        } catch (RuntimeException e) {
            REPAE2BRIDGE_LOGGER.warn("Failed to read network from element during setRemoved: {}", e.getMessage());
            return null;
        }
    }
}
