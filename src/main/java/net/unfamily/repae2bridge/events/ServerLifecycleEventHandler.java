package net.unfamily.repae2bridge.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.item.CustomMatterBindings;
import com.buuz135.replication.api.IMatterType;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Server lifecycle: unload cleanup and accessors for custom matter bindings.
 * Custom matter items are created at registry time (see CustomMatterAutoRegistrar);
 * the legacy rep_ae2_bridge_matters.json config is no longer read or written.
 */
@EventBusSubscriber
public class ServerLifecycleEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLifecycleEventHandler.class);

    private static final long THROTTLE_INTERVAL_TICKS = 300;

    private static int serverStoppingLogsHidden = 0;
    private static int serverStartingLogsHidden = 0;

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            RepAE2BridgeBlockEntity.setWorldUnloading(true);
            RepAE2BridgeBlockEntity.cancelAllPendingOperations();

            if (serverStoppingLogsHidden == THROTTLE_INTERVAL_TICKS) {
                LOGGER.warn("RepAE2Bridge - Server Stopping: Server stopping detected - emergency cleanup (+ {} similar events hidden in last 15s)", serverStoppingLogsHidden);
                serverStoppingLogsHidden = 0;
            } else {
                serverStoppingLogsHidden++;
            }
        } catch (Exception e) {
            LOGGER.error("RepAE2Bridge - Server Stopping: Exception during server stop cleanup", e);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        try {
            RepAE2BridgeBlockEntity.setWorldUnloading(false);

            if (serverStartingLogsHidden == THROTTLE_INTERVAL_TICKS) {
                LOGGER.info("RepAE2Bridge - Server Starting: Server starting detected - resetting unload flag (+ {} similar events hidden in last 15s)", serverStartingLogsHidden);
                serverStartingLogsHidden = 0;
            } else {
                serverStartingLogsHidden++;
            }
        } catch (Exception e) {
            LOGGER.error("RepAE2Bridge - Server Starting: Exception during server starting handling", e);
        }
    }

    public static Map<Item, IMatterType> getCustomItemToMatterMap() {
        return CustomMatterBindings.itemToMatter();
    }

    public static Map<IMatterType, Item> getCustomMatterToItemMap() {
        return CustomMatterBindings.matterToItem();
    }

    public static java.util.Set<Item> getCustomVirtualMatterItems() {
        return CustomMatterBindings.virtualItems();
    }
}
