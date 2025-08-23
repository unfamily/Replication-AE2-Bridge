package net.unfamily.repae2bridge.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.unfamily.repae2bridge.RepAE2Bridge;
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

    /**
     * Gestisce l'evento di unload del mondo
     * Questo viene chiamato quando un mondo viene scaricato (es. cambio dimensione, uscita dal mondo)
     */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        LOGGER.warn("WorldEventHandler: World unload detected - notifying all bridges");
        
        try {
            // Imposta il flag globale per indicare che il mondo si sta scaricando
            RepAE2BridgeBlockEntity.setWorldUnloading(true);
            
            // Cancella tutte le operazioni pendenti per evitare hang
            RepAE2BridgeBlockEntity.cancelAllPendingOperations();
            
            LOGGER.warn("WorldEventHandler: All bridges notified of world unload");
        } catch (Exception e) {
            LOGGER.error("WorldEventHandler: Exception during world unload handling", e);
        }
    }

    /**
     * Gestisce l'evento di load del mondo
     * Resetta il flag di unloading quando un nuovo mondo viene caricato
     */
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        LOGGER.info("WorldEventHandler: World load detected - resetting unload flag");
        
        try {
            // Reset del flag quando un mondo viene caricato
            RepAE2BridgeBlockEntity.setWorldUnloading(false);
            
            LOGGER.info("WorldEventHandler: World load handling completed");
        } catch (Exception e) {
            LOGGER.error("WorldEventHandler: Exception during world load handling", e);
        }
    }

    /**
     * Backup handler per lo shutdown del server
     * Questo è già gestito nel RepAE2Bridge.java, ma lo manteniamo come sicurezza
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.warn("WorldEventHandler: Server stopping detected - emergency cleanup");
        
        try {
            // Assicuriamoci che il flag sia impostato
            RepAE2BridgeBlockEntity.setWorldUnloading(true);
            
            // Cancella tutte le operazioni pendenti
            RepAE2BridgeBlockEntity.cancelAllPendingOperations();
            
            LOGGER.warn("WorldEventHandler: Server stop cleanup completed");
        } catch (Exception e) {
            LOGGER.error("WorldEventHandler: Exception during server stop cleanup", e);
        }
    }
}
