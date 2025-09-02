package net.unfamily.repae2bridge.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event handler for server lifecycle events
 * This ensures proper cleanup when the server stops and proper initialization when it starts
 */
 @EventBusSubscriber
public class ServerLifecycleEventHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLifecycleEventHandler.class);
    
    // Sistema di throttling per ridurre lo spam di log - intervallo di 15 secondi (300 tick)
    private static final long THROTTLE_INTERVAL_TICKS = 300; // 15 secondi = 300 tick
    
    // Contatori per messaggi nascosti - logga ogni 300 eventi
    private static int serverStoppingLogsHidden = 0;
    private static int serverStartingLogsHidden = 0;

    /**
     * Backup handler per lo shutdown del server
     * Questo è già gestito nel RepAE2Bridge.java, ma lo manteniamo come sicurezza
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            // Assicuriamoci che il flag sia impostato
            RepAE2BridgeBlockEntity.setWorldUnloading(true);
            
            // Cancella tutte le operazioni pendenti
            RepAE2BridgeBlockEntity.cancelAllPendingOperations();
            
            if (serverStoppingLogsHidden == THROTTLE_INTERVAL_TICKS) {
                LOGGER.warn("RepAE2Bridge - Server Stopping: Server stopping detected - emergency cleanup (+ {} similar events hidden in last 15s)", serverStoppingLogsHidden);
                serverStoppingLogsHidden = 0;
            } else {
                serverStoppingLogsHidden++;
            }
        } catch (Exception e) {
            // Gli errori vengono sempre loggati indipendentemente dal throttling
            LOGGER.error("RepAE2Bridge - Server Stopping: Exception during server stop cleanup", e);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        try {
            // Reset del flag quando il server viene avviato
            RepAE2BridgeBlockEntity.setWorldUnloading(false);
            
            if (serverStartingLogsHidden == THROTTLE_INTERVAL_TICKS) {
                LOGGER.info("RepAE2Bridge - Server Starting: Server starting detected - resetting unload flag (+ {} similar events hidden in last 15s)", serverStartingLogsHidden);
                serverStartingLogsHidden = 0;
            } else {
                serverStartingLogsHidden++;
            }
        } catch (Exception e) {
            // Gli errori vengono sempre loggati indipendentemente dal throttling
            LOGGER.error("RepAE2Bridge - Server Starting: Exception during server starting handling", e);
        }
    }
}
