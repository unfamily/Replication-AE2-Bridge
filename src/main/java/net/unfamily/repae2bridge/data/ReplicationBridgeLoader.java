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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads replication bridge matter definitions from custom declaration JSON files
 */
public class ReplicationBridgeLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // Map to store matter definitions
    private static final Map<String, String> MATTER_DEFINITIONS = new HashMap<>();

    // Stores files with overwritable=false to prevent them from being overwritten
    private static final Map<String, Boolean> PROTECTED_DEFINITIONS = new HashMap<>();

    /**
     * Data class for replication bridge entries
     */
    public static class ReplicationBridgeEntry {
        public String itemId;
        public String matterId;
    }

    /**
     * Data class for replication bridge definitions
     */
    public static class ReplicationBridgeDefinition {
        public String type;
        public boolean overwritable = false;
        public List<ReplicationBridgeEntry> entries = new ArrayList<>();
    }

    /**
     * Scans the bridge custom matter declaration directory for replication bridge definitions
     */
    public static void scanExternalScriptsDirectory() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Scanning bridge custom matter declaration directory for replication bridge definitions...");
        }

        try {
            // Use the default kubejs startup_scripts directory
            String externalScriptsBasePath = "kubejs/startup_scripts";

            // Create directory for replication bridges if it doesn't exist
            Path configPath = Paths.get(externalScriptsBasePath);
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Created directory for external scripts: {}", configPath.toAbsolutePath());
                }
                return;
            }

            if (!Files.isDirectory(configPath)) {
                LOGGER.warn("The path for external scripts exists but is not a directory: {}", configPath);
                return;
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Scanning directory for replication bridge definitions: {}", configPath.toAbsolutePath());
            }

            // Clear previous protections and definitions
            PROTECTED_DEFINITIONS.clear();
            MATTER_DEFINITIONS.clear();

            // Scan all JSON files in the directory
            try (Stream<Path> files = Files.walk(configPath)) {
                files.filter(Files::isRegularFile)
                     .filter(path -> path.toString().endsWith(".json"))
                     .filter(path -> !path.getFileName().toString().startsWith("."))
                     .forEach(path -> {
                         try {
                             loadReplicationBridgeFile(path);
                         } catch (Exception e) {
                             LOGGER.error("Failed to load replication bridge file: {}", path, e);
                         }
                     });
            }

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Loaded {} replication bridge definitions from external scripts", MATTER_DEFINITIONS.size());
            }

        } catch (Exception e) {
            LOGGER.error("Error scanning external scripts directory for replication bridge definitions", e);
        }
    }

    /**
     * Loads a single replication bridge JSON file
     */
    private static void loadReplicationBridgeFile(Path filePath) {
        try (InputStream inputStream = Files.newInputStream(filePath);
             InputStreamReader reader = new InputStreamReader(inputStream)) {

            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            if (jsonObject == null) {
                LOGGER.warn("Empty or invalid JSON file: {}", filePath);
                return;
            }

            // Check type
            if (!jsonObject.has("type") || !jsonObject.get("type").getAsString().equals("replication_bridges")) {
                // Not a replication bridge file, skip silently
                return;
            }

            // Parse overwritable flag
            boolean overwritable = false;
            if (jsonObject.has("overwritable")) {
                overwritable = jsonObject.get("overwritable").getAsBoolean();
            }

            // Check if file is protected and already loaded
            String fileName = filePath.getFileName().toString();
            if (!overwritable && PROTECTED_DEFINITIONS.containsKey(fileName)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Skipping protected replication bridge file: {}", fileName);
                }
                return;
            }

            // Mark as protected if overwritable is false
            if (!overwritable) {
                PROTECTED_DEFINITIONS.put(fileName, true);
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

                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Loaded replication bridge entry: {} -> {}", registryName, matterId);
                            }
                        } else {
                            LOGGER.warn("Invalid entry in {}: missing itemId or matterId", fileName);
                        }
                    }
                }
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Successfully loaded replication bridge file: {}", fileName);
            }

        } catch (IOException e) {
            LOGGER.error("Failed to read replication bridge file: {}", filePath, e);
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
