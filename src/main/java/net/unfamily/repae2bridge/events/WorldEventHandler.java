package net.unfamily.repae2bridge.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event handler for world loading/unloading events
 * This ensures proper cleanup when worlds are unloaded or the server stops
 */
 @EventBusSubscriber
public class WorldEventHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldEventHandler.class);
    
    // Sistema di throttling per ridurre lo spam di log - intervallo di 15 secondi (300 tick)
    private static final long THROTTLE_INTERVAL_TICKS = 300; // 15 secondi = 300 tick
    
    // Contatori per messaggi nascosti - logga ogni 300 eventi
    private static int worldUnloadLogsHidden = 0;
    private static int worldLoadLogsHidden = 0;
    private static int serverStoppingLogsHidden = 0;
    private static int serverStartingLogsHidden = 0;

    /**
     * Gestisce l'evento di unload del mondo
     * Questo viene chiamato quando un mondo viene scaricato (es. cambio dimensione, uscita dal mondo)
     */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        try {
            // Imposta il flag globale per indicare che il mondo si sta scaricando
            // Passiamo il level context per informazioni di dimensione
            if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
                RepAE2BridgeBlockEntity.setWorldUnloading(true, level, null);
            } else {
                RepAE2BridgeBlockEntity.setWorldUnloading(true);
            }
            
            // Cancella tutte le operazioni pendenti per evitare hang
            RepAE2BridgeBlockEntity.cancelAllPendingOperations();
            
            if (worldUnloadLogsHidden == THROTTLE_INTERVAL_TICKS) {
                LOGGER.warn("WorldEventHandler: World unload detected - notifying all bridges (+ {} similar events hidden in last 15s)", worldUnloadLogsHidden);
                worldUnloadLogsHidden = 0;
            } else {
                worldUnloadLogsHidden++;
            }
        } catch (Exception e) {
            // Gli errori vengono sempre loggati indipendentemente dal throttling
            LOGGER.error("WorldEventHandler: Exception during world unload handling", e);
        }
    }

    /**
     * Gestisce l'evento di load del mondo
     * Resetta il flag di unloading quando un nuovo mondo viene caricato
     */
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        try {
            // Reset del flag quando un mondo viene caricato
            // Passiamo il level context per informazioni di dimensione
            if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
                RepAE2BridgeBlockEntity.setWorldUnloading(false, level, null);
            } else {
                RepAE2BridgeBlockEntity.setWorldUnloading(false);
            }
            
            if (worldLoadLogsHidden == THROTTLE_INTERVAL_TICKS) {
                LOGGER.info("WorldEventHandler: World load detected - resetting unload flag (+ {} similar events hidden in last 15s)", worldLoadLogsHidden);
                worldLoadLogsHidden = 0;
            } else {
                worldLoadLogsHidden++;
            }
        } catch (Exception e) {
            // Gli errori vengono sempre loggati indipendentemente dal throttling
            LOGGER.error("WorldEventHandler: Exception during world load handling", e);
        }
    }

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
                LOGGER.warn("WorldEventHandler: Server stopping detected - emergency cleanup (+ {} similar events hidden in last 15s)", serverStoppingLogsHidden);
                serverStoppingLogsHidden = 0;
            } else {
                serverStoppingLogsHidden++;
            }
        } catch (Exception e) {
            // Gli errori vengono sempre loggati indipendentemente dal throttling
            LOGGER.error("WorldEventHandler: Exception during server stop cleanup", e);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        try {
            // Reset del flag quando il server viene avviato
            RepAE2BridgeBlockEntity.setWorldUnloading(false);
            
            if (serverStartingLogsHidden == THROTTLE_INTERVAL_TICKS) {
                LOGGER.info("WorldEventHandler: Server starting detected - resetting unload flag (+ {} similar events hidden in last 15s)", serverStartingLogsHidden);
                serverStartingLogsHidden = 0;
            } else {
                serverStartingLogsHidden++;
            }
        } catch (Exception e) {
            // Gli errori vengono sempre loggati indipendentemente dal throttling
            LOGGER.error("WorldEventHandler: Exception during server starting handling", e);
        }
    }
}
