package net.unfamily.repae2bridge.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.data.ReplicationBridgeLoader;
import net.unfamily.repae2bridge.Config;
import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import net.minecraft.world.item.Item;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Event handler for server lifecycle events
 * This ensures proper cleanup when the server stops and proper initialization when it starts
 */
 @EventBusSubscriber
public class ServerLifecycleEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLifecycleEventHandler.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // Sistema di throttling per ridurre lo spam di log - intervallo di 15 secondi (300 tick)
    private static final long THROTTLE_INTERVAL_TICKS = 300; // 15 secondi = 300 tick

    // Contatori per messaggi nascosti - logga ogni 300 eventi
    private static int serverStoppingLogsHidden = 0;
    private static int serverStartingLogsHidden = 0;

    // Static registry of custom matter type associations (loaded once at startup)
    private static final Map<Item, IMatterType> customItemToMatterMap = new HashMap<>();
    private static final Map<IMatterType, Item> customMatterToItemMap = new HashMap<>();
    private static final java.util.Set<Item> customVirtualMatterItems = new java.util.HashSet<>();
    private static boolean customAssociationsLoaded = false;

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

            // Create example config file if it doesn't exist
            createExampleConfigFileIfNeeded();

            // Initialize custom matter associations when server starts
            initializeCustomMatterAssociations();

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

    /**
     * Initialize custom matter type associations from JSON files.
     * This method is called once at server startup to populate the static maps.
     */
    public static void initializeCustomMatterAssociations() {
        if (customAssociationsLoaded) {
            return; // Already loaded
        }

        if (Config.enableDebugLogging) {
            LOGGER.info("Initializing custom matter type associations...");
        }

        try {
            // Clear any existing associations
            customItemToMatterMap.clear();
            customMatterToItemMap.clear();
            customVirtualMatterItems.clear();

            // Load custom matter definitions from config file
            ReplicationBridgeLoader.loadConfigFile();
            var matterDefinitions = ReplicationBridgeLoader.getMatterDefinitions();

            if (matterDefinitions.isEmpty()) {
                if (Config.enableDebugLogging) {
                    LOGGER.info("No custom matter definitions found");
                }
                customAssociationsLoaded = true;
                return;
            }

            if (Config.enableDebugLogging) {
                LOGGER.info("Found {} custom matter definitions, building associations...", matterDefinitions.size());
            }

            // Build associations
            for (var entry : matterDefinitions.entrySet()) {
                String itemRegistryName = entry.getKey();
                String matterId = entry.getValue();

                try {
                    // Find the item by registry name
                    Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("rep_ae2_bridge", itemRegistryName));

                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        // Try to get the matter type from the registry
                        var matterResourceLocation = net.minecraft.resources.ResourceLocation.parse(matterId);
                        var matterType = ReplicationRegistry.MATTER_TYPES_REGISTRY.get(matterResourceLocation);

                        if (matterType != null) {
                            // Create bidirectional associations
                            customItemToMatterMap.put(item, matterType);
                            customMatterToItemMap.put(matterType, item);
                            customVirtualMatterItems.add(item);

                            if (Config.enableDebugLogging) {
                                LOGGER.debug("Associated custom item '{}' with matter type '{}' ({})",
                                    itemRegistryName, matterId, matterType.getName());
                            }
                        } else {
                            if (Config.enableDebugLogging) {
                                LOGGER.warn("Matter type '{}' not found for custom item '{}'", matterId, itemRegistryName);
                            }
                        }
                    } else {
                        if (Config.enableDebugLogging) {
                            LOGGER.warn("Custom item '{}' not found in registry", itemRegistryName);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error processing custom matter association: {} -> {}", itemRegistryName, matterId, e);
                }
            }

            if (Config.enableDebugLogging) {
                LOGGER.info("Custom matter associations initialized: {} bidirectional mappings created",
                    customItemToMatterMap.size());
            }

        } catch (Exception e) {
            LOGGER.error("Error initializing custom matter associations", e);
        } finally {
            customAssociationsLoaded = true;
        }
    }

    // Getter methods for the custom associations
    public static Map<Item, IMatterType> getCustomItemToMatterMap() {
        return customItemToMatterMap;
    }

    public static Map<IMatterType, Item> getCustomMatterToItemMap() {
        return customMatterToItemMap;
    }

    public static java.util.Set<Item> getCustomVirtualMatterItems() {
        return customVirtualMatterItems;
    }

    /**
     * Creates or updates the config file based on overwritable setting
     */
    private static void createExampleConfigFileIfNeeded() {
        try {
            // Use the NeoForge config directory
            Path configDir = FMLPaths.CONFIGDIR.get();
            String configFileName = "rep_ae2_bridge_matters.json";

            // Create path to the config file
            Path configFilePath = configDir.resolve(configFileName);

            boolean shouldCreateFile = false;

            // Check if the config file exists
            if (!Files.exists(configFilePath)) {
                // File doesn't exist, create it
                shouldCreateFile = true;
                if (Config.enableDebugLogging) {
                    LOGGER.info("Config file not found, creating example config file at: {}", configFilePath.toAbsolutePath());
                }
            } else {
                // File exists, check if it's overwritable
                try (var reader = Files.newBufferedReader(configFilePath)) {
                    JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
                    if (jsonObject != null && jsonObject.has("overwritable") && jsonObject.get("overwritable").getAsBoolean()) {
                        // File is marked as overwritable, recreate it
                        shouldCreateFile = true;
                        if (Config.enableDebugLogging) {
                            LOGGER.info("Config file exists and is overwritable, updating with latest matter associations: {}", configFilePath.toAbsolutePath());
                        }
                    } else {
                        // File exists and is not overwritable, skip
                        if (Config.enableDebugLogging) {
                            LOGGER.debug("Config file exists and is not overwritable, skipping update: {}", configFilePath.toAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    // Error reading existing file, recreate it
                    shouldCreateFile = true;
                    if (Config.enableDebugLogging) {
                        LOGGER.warn("Error reading existing config file, recreating: {}", configFilePath.toAbsolutePath(), e);
                    }
                }
            }

            if (shouldCreateFile) {
                ReplicationBridgeLoader.createExampleConfigFile(configFilePath);
            }
        } catch (Exception e) {
            LOGGER.error("Error checking/creating example config file", e);
        }
    }
}
