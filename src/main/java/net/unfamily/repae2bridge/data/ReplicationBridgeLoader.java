package net.unfamily.repae2bridge.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import net.unfamily.repae2bridge.Config;
import com.buuz135.replication.ReplicationRegistry;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Loads replication bridge matter definitions from config JSON file
 * Automatically creates example config file if it doesn't exist
 */
public class ReplicationBridgeLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // Map to store matter definitions
    private static final Map<String, String> MATTER_DEFINITIONS = new HashMap<>();


    /**
     * Loads the replication bridge matter definitions from the config JSON file
     */
    public static void loadConfigFile() {
        if (Config.enableDebugLogging) {
            LOGGER.info("Loading replication bridge matter definitions from config file...");
        }

        try {
            // Use the NeoForge config directory
            Path configDir = FMLPaths.CONFIGDIR.get();
            String configFileName = "rep_ae2_bridge_matters.json";

            // Create path to the config file
            Path configFilePath = configDir.resolve(configFileName);

            if (Config.enableDebugLogging) {
                LOGGER.info("Attempting to load config file: {}", configFilePath.toAbsolutePath());
                LOGGER.info("NeoForge config dir: '{}', File name: '{}'", configDir.toAbsolutePath(), configFileName);
            }

            // Clear previous definitions
            MATTER_DEFINITIONS.clear();

            // Check if the config file exists
            if (!Files.exists(configFilePath)) {
                if (Config.enableDebugLogging) {
                    LOGGER.info("Config file not found: {}. Matter definitions will be empty.", configFilePath.toAbsolutePath());
                }
                return;
            }

            if (!Files.isRegularFile(configFilePath)) {
                LOGGER.warn("Config file path exists but is not a regular file: {}", configFilePath);
                return;
            }

            // Load the config file
            loadReplicationBridgeFile(configFilePath);

            if (Config.enableDebugLogging) {
                LOGGER.info("Loaded {} replication bridge matter definitions from config file", MATTER_DEFINITIONS.size());
            }

        } catch (Exception e) {
            LOGGER.error("Error loading replication bridge config file", e);
        }
    }

    /**
     * Creates an example config file with automatic matter associations
     * Excludes built-in Replication matter types (earth, nether, organic, etc.)
     * but includes custom matter types from other mods
     */
    public static void createExampleConfigFile(Path configFilePath) {
        try {
            if (Config.enableDebugLogging) {
                LOGGER.info("Creating example config file at: {}", configFilePath.toAbsolutePath());
            }

            // Create directory if it doesn't exist
            Files.createDirectories(configFilePath.getParent());

            // Build example JSON with automatic associations
            JsonObject rootObject = new JsonObject();
            rootObject.addProperty("type", "rep_ae2_bridge");
            rootObject.addProperty("overwritable", true); // Global overwritable setting

            JsonArray entriesArray = new JsonArray();

            // Get all registered matter types and create example entries (excluding built-in replication matter)
            var matterTypes = ReplicationRegistry.MATTER_TYPES_REGISTRY.stream().toList();

            // List of built-in replication matter types to exclude from auto-generation
            var builtInMatterTypes = java.util.Set.of(
                ReplicationRegistry.Matter.EMPTY.get(),
                ReplicationRegistry.Matter.EARTH.get(),
                ReplicationRegistry.Matter.NETHER.get(),
                ReplicationRegistry.Matter.ORGANIC.get(),
                ReplicationRegistry.Matter.ENDER.get(),
                ReplicationRegistry.Matter.METALLIC.get(),
                ReplicationRegistry.Matter.PRECIOUS.get(),
                ReplicationRegistry.Matter.LIVING.get(),
                ReplicationRegistry.Matter.QUANTUM.get()
            );

            for (var matterType : matterTypes) {
                // Skip built-in replication matter types
                if (builtInMatterTypes.contains(matterType)) {
                    continue;
                }

                var matterResourceLocation = ReplicationRegistry.MATTER_TYPES_REGISTRY.getKey(matterType);
                if (matterResourceLocation != null) {
                    String matterId = matterResourceLocation.toString();

                    JsonObject entryObject = new JsonObject();

                    // Create itemId from matterId by replacing colons with dashes
                    String itemId = matterId.replace(":", "-");

                    entryObject.addProperty("itemId", itemId);
                    entryObject.addProperty("matterId", matterId);

                    entriesArray.add(entryObject);

                    if (Config.enableDebugLogging) {
                        LOGGER.debug("Created example entry: {} -> {}", itemId, matterId);
                    }
                }
            }

            rootObject.add("entries", entriesArray);

            // Write the example file
            try (var writer = Files.newBufferedWriter(configFilePath)) {
                GSON.toJson(rootObject, writer);
            }

            if (Config.enableDebugLogging) {
                LOGGER.info("Created example config file with {} automatic matter associations", entriesArray.size());
            }

        } catch (Exception e) {
            LOGGER.error("Failed to create example config file: {}", configFilePath, e);
        }
    }

    /**
     * Loads the replication bridge JSON config file
     */
    private static void loadReplicationBridgeFile(Path filePath) {
        try (InputStream inputStream = Files.newInputStream(filePath);
             InputStreamReader reader = new InputStreamReader(inputStream)) {

            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            if (jsonObject == null) {
                LOGGER.warn("Empty or invalid JSON file: {}", filePath);
                return;
            }

            // Check type - should be "rep_ae2_bridge"
            if (jsonObject.has("type") && !jsonObject.get("type").getAsString().equals("rep_ae2_bridge")) {
                if (Config.enableDebugLogging) {
                    LOGGER.warn("Config file has wrong type: expected 'rep_ae2_bridge', found '{}'", jsonObject.get("type").getAsString());
                }
                return;
            }

            // Parse entries
            if (jsonObject.has("entries")) {
                JsonArray entriesArray = jsonObject.getAsJsonArray("entries");
                for (JsonElement entryElement : entriesArray) {
                    if (entryElement.isJsonObject()) {
                        JsonObject entryObject = entryElement.getAsJsonObject();

                        if (entryObject.has("itemId") && entryObject.has("matterId")) {
                            String itemId = entryObject.get("itemId").getAsString();
                            String matterId = entryObject.get("matterId").getAsString();

                            // Convert dashes to underscores in itemId for registry name
                            String registryName = itemId.replace("-", "_");

                            MATTER_DEFINITIONS.put(registryName, matterId);

                            if (Config.enableDebugLogging) {
                                LOGGER.debug("Loaded replication bridge entry: {} -> {}", registryName, matterId);
                            }
                        } else {
                            if (Config.enableDebugLogging) {
                                LOGGER.warn("Invalid entry in config file: missing itemId or matterId");
                            }
                        }
                    }
                }
            }

            if (Config.enableDebugLogging) {
                LOGGER.debug("Successfully loaded replication bridge config file: {}", filePath.getFileName());
            }

        } catch (IOException e) {
            LOGGER.error("Failed to read replication bridge config file: {}", filePath, e);
        }
    }

    /**
     * Gets all loaded matter definitions
     * @return Map of registry name -> matter ID
     */
    public static Map<String, String> getMatterDefinitions() {
        return new HashMap<>(MATTER_DEFINITIONS);
    }

    /**
     * Gets a specific matter definition by registry name
     * @param registryName The registry name of the item
     * @return The matter ID, or null if not found
     */
    public static String getMatterId(String registryName) {
        return MATTER_DEFINITIONS.get(registryName);
    }
}
