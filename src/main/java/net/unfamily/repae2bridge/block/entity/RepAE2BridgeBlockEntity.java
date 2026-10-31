package net.unfamily.repae2bridge.block.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.unfamily.repae2bridge.ae2.Ae2MatterTank;
import net.unfamily.repae2bridge.ae2.CraftMatterTank;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;
import net.unfamily.repae2bridge.ae2.encoding.ReplicationBridgePatternDetails;
import net.minecraft.resources.Identifier;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.crafting.inv.ICraftingInventory;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.MEStorage;

import com.buuz135.replication.block.tile.ReplicationMachine;
import com.buuz135.replication.calculation.ReplicationCalculation;
import com.buuz135.replication.network.MatterNetwork;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.network.IMatterTanksConsumer;
import com.buuz135.replication.api.network.IMatterTanksSupplier;
import com.buuz135.replication.calculation.MatterValue;
import com.hrznstudio.titanium.block.BasicTileBlock;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.hrznstudio.titanium.annotation.Save;
import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.unfamily.repae2bridge.block.ModBlocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.api.task.ReplicationTask;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.unfamily.repae2bridge.item.ModItems;
import com.buuz135.replication.ReplicationRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import com.buuz135.replication.util.TransferUtil;
import appeng.me.helpers.MachineSource;
import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.events.ServerLifecycleEventHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import appeng.helpers.IPriorityHost;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.lang.StringBuilder;
import java.util.Objects;

/**
 * BlockEntity for the RepAE2Bridge that connects the AE2 network with the Replication matter network
 */
public class RepAE2BridgeBlockEntity extends ReplicationMachine<RepAE2BridgeBlockEntity>
        implements IInWorldGridNodeHost, ICraftingInventory, ICraftingProvider, IStorageProvider, IActionHost,
        IPriorityHost, IMatterTanksSupplier, IMatterTanksConsumer {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Batch pushPattern units into fewer/larger ReplicationTasks (too many tiny tasks stall).
    // Base window with 0 co-processors; scales down with max CPU accelerators on the grid.
    private static final int REQUEST_ACCUMULATION_TICKS = 100;
    /** Floor for mega-CPUs (AdvancedAE / many co-processors). */
    private static final int REQUEST_ACCUMULATION_TICKS_MIN = 5;
    /** Flush early once a single pattern has this many units waiting (batch already healthy). */
    private static final int REQUEST_ACCUMULATION_COUNT_FLUSH = 32;

    /**
     * Replication {@code onTankValueChanged} syncs every open terminal (calculateMatterAmount + packet).
     * Coalesce AE2 park/drain storms so opening the terminal mid-craft does not freeze the game.
     */
    private static final int REPLICATION_TANK_SYNC_INTERVAL = 20;

    // Constant for initialization delay in ticks (3 seconds = 60 ticks)
    private static final int INITIALIZATION_DELAY = 60;

    // Variable to track if the networks have been initialized
    private byte initialized = 0;

    // Counter for initialization ticks
    private int initializationTicks = 0;

    // Priority for AE2 crafting / Replication tank holder ordering
    @Save
    private int priority = 0;

    /** Virtual tanks exposing AE2 matter cells to the Replication network. */
    @Nullable
    private List<Ae2MatterTank> ae2MatterTanks;

    /**
     * Matter absorbed from AE2 craft {@code pushPattern} inputHolder — visible to Replication
     * via {@link #getTanks()} and drained before AE2 cell mirrors.
     */
    @Nullable
    private List<CraftMatterTank> craftMatterTanks;

    // Queue of pending patterns - NO LONGER USED, capacity is managed by the Replication network
    // private final Queue<IPatternDetails> pendingPatterns = new LinkedList<>();
    // private final Map<IPatternDetails, KeyCounter[]> pendingInputs = new HashMap<>();

    // AE2 node for network connection
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, new IGridNodeListener<RepAE2BridgeBlockEntity>() {
                @Override
                public void onSaveChanges(RepAE2BridgeBlockEntity nodeOwner, IGridNode node) {
                    // Schedule update instead of calling setChanged immediately
                    nodeOwner.pendingBlockUpdate = true;
                }

                @Override
                public void onStateChanged(RepAE2BridgeBlockEntity nodeOwner, IGridNode node, IGridNodeListener.State state) {
                    // Schedule update instead of calling immediately
                    if (nodeOwner.level != null) {
                        nodeOwner.pendingBlockUpdate = true;

                        // Also update the CONNECTED property state in the block
                        updateConnectedState();

                        // If the node is active, update patterns
                        if (state == IGridNodeListener.State.POWER && node.isActive()) {
                            // LOGGER.info("Bridge: AE2 node active, requesting pattern update");
                            ICraftingProvider.requestUpdate(mainNode);

                            // Also request a storage update to show matter items
                            IStorageProvider.requestUpdate(mainNode);
                        }
                    }
                }

                @Override
                public void onGridChanged(RepAE2BridgeBlockEntity nodeOwner, IGridNode node) {
                    // Schedule update instead of calling immediately
                    if (nodeOwner.level != null) {
                        nodeOwner.pendingBlockUpdate = true;

                        // Also update the CONNECTED property state in the block
                        updateConnectedState();

                        // Force a pattern update when the grid changes
                        ICraftingProvider.requestUpdate(mainNode);

                        // Also request a storage update to show matter items
                        IStorageProvider.requestUpdate(mainNode);
                    }
                }
            })
            .setVisualRepresentation(ModBlocks.REPAE2BRIDGE.get())
            .setInWorldNode(true)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(Config.bridgeEnergyConsumption)  // Use the energy consumption from config
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .addService(ICraftingProvider.class, this)
            .addService(IStorageProvider.class, this)
            .setTagName("main");

    // Flag to track if the node has been created
    private boolean nodeCreated = false;

    // Flag to indicate if we should try to reconnect to networks
    private boolean shouldReconnect = false;
    
    // Flag to indicate the Replication network element needs to be retried
    // Set when onLoad() fails due to "Element network is null!" and cleared on successful network access
    private boolean needsNetworkRetry = false;
    
    /**
     * Returns true if this bridge needs a network retry (onLoad failed for Replication network).
     * Used by RepAE2BridgeBl.tick() to determine if scheduled retries should call onLoad().
     */
    public boolean needsNetworkRetry() {
        return needsNetworkRetry;
    }
    
    // Flag to schedule block update on next tick instead of immediately
    private boolean pendingBlockUpdate = false;

    // Terminal components
    @Save
    private InventoryComponent<RepAE2BridgeBlockEntity> output;
    @Save
    private int sortingTypeValue;
    @Save
    private int sortingDirection;
    @Save
    private int matterOpediaSortingTypeValue;
    @Save
    private int matterOpediaSortingDirection;
    private TerminalPlayerTracker terminalPlayerTracker;

    // Unique identifier for this block
    @Save
    private UUID blockId;

    // Map to track requests for patterns
    private final Map<UUID, Map<ItemStack, Integer>> patternRequests = new HashMap<>();
    // Map to track active tasks with source information
    private final Map<UUID, Map<String, TaskSourceInfo>> activeTasks = new HashMap<>();
    // Map to track requests by source block
    private final Map<UUID, Map<ItemStack, Integer>> patternRequestsBySource = new HashMap<>();

    // Matter creation tracking
    private final Map<IMatterType, Long> previousMatterAmounts = new HashMap<>();
    private int matterTrackingTickCounter = 0;
    private boolean matterTrackingInitialized = false;

    // Temporary counters for crafting requests (ReplicationTask / terminal path)
    private final Map<UUID, Map<ItemWithSourceId, Integer>> requestCounters = new HashMap<>();
    private int requestCounterTicks = 0;

    /**
     * AE2-internal craft queue: pushPattern units that stay on AE2 (matter cells + chamber/MA).
     * Never becomes a {@link ReplicationTask} / terminal entry.
     */
    private final Map<ItemWithSourceId, Integer> ae2InternalPending = new HashMap<>();

    /** Matter types waiting for a coalesced {@link MatterNetwork#onTankValueChanged} flush. */
    private final Set<IMatterType> dirtyReplicationMatterTypes = new HashSet<>();
    private int replicationTankSyncTicks = 0;

    /** When true, each tick feeds AE power into the Replication matter network as FE. Default ON. */
    @Save
    private boolean feedReplicationEnergy = true;

    // Timer for periodic pattern updates
    private int patternUpdateTicks = 0;
    private static final int PATTERN_UPDATE_INTERVAL = 100; // Update every 5 seconds (100 ticks)

    // Cache of matter insufficient warnings to avoid repetitions
    private Map<String, Long> lastMatterWarnings = new HashMap<>();
    // Minimum time between consecutive warnings for the same item (in ticks)
    private static final int WARNING_COOLDOWN = 600; // 30 seconds

    // Debug logging counters to reduce spam
    private int debugTickCounter = 0;
    private int hiddenDebugMessages = 0;
    private static final int DEBUG_LOG_INTERVAL = 300; // Show debug summary every 15 seconds (300 ticks)
    private static boolean globalShouldLogDebug = false; // Static flag to control debug logging across all instances
    private static int globalHiddenDebugMessages = 0; // Global counter for hidden debug messages

    // Throttling for spam-prone warning/error logs
    // System implemented to reduce log spam during world unloading and global operations
    // Logs are shown every 300 events with a counter for hidden messages
    private static final long WARNING_THROTTLE_INTERVAL = 300; // 15 seconds = 300 events
    private static int worldUnloadingWarningsHidden = 0;
    private static int globalOperationLogsHidden = 0;

    // Static flag to track world unloading state
    private static boolean worldUnloading = false;

    // Static registry of all active bridges for emergency cleanup during world unloading
    private static final Set<RepAE2BridgeBlockEntity> activeBridges = new HashSet<>();


    /**
     * Sets the world unloading state
     * Called from the mod main class when the server is stopping
     */
    public static void setWorldUnloading(boolean unloading) {
        setWorldUnloading(unloading, null, null);
    }

    /**
     * Sets the world unloading state with optional context information
     * @param unloading whether the world is unloading
     * @param level the level/dimension (can be null)
     * @param pos the position where this was triggered (can be null)
     */
    public static void setWorldUnloading(boolean unloading, @Nullable net.minecraft.world.level.Level level, @Nullable BlockPos pos) {
        // Only log once when set to true or if more than 15 seconds have passed
        if (unloading && !worldUnloading) {
            // First time it's set to true - always log
            String dimensionInfo = level != null ? 
                " [Dimension: " + level.dimension().identifier() + "]" : "";
            String positionInfo = pos != null ? 
                " [Position: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]" : "";
            
            LOGGER.info("RepAE2Bridge: World unloading detected - All bridges will now shutdown{}{}", 
                        dimensionInfo, positionInfo);
            LOGGER.info("RepAE2Bridge: Cancelling all pending operations{}{}", 
                        dimensionInfo, positionInfo);
            
            // Cancel all pending operations
            cancelAllPendingOperations();
            
            // Disconnect ALL bridges from networks IMMEDIATELY
            // This prevents the NetworkManager from trying to save bridges during world unload
            LOGGER.info("RepAE2Bridge: Disconnecting {} active bridges from AE2 and Titanium/Replication networks", activeBridges.size());
            disconnectAllBridgesFromNetworks();
            
        } else if (!unloading && worldUnloading) {
            // State changed from true to false - always log
            String dimensionInfo = level != null ? 
                " [Dimension: " + level.dimension().identifier() + "]" : "";
            String positionInfo = pos != null ? 
                " [Position: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]" : "";
            
            LOGGER.info("GLOBAL WORLD UNLOADING FLAG SET TO: false - Normal operation resumed{}{}", 
                       dimensionInfo, positionInfo);
            worldUnloadingWarningsHidden = 0; // Reset counter
        }
        worldUnloading = unloading;
    }

    /**
     * Checks if the world is currently unloading
     */
    public static boolean isWorldUnloading() {
        return worldUnloading;
    }

    /**
     * Helper method to create position and dimension info string
     * @return formatted string with position and dimension information
     */
    private String getLocationInfo() {
        if (level == null) {
            return " [Dimension: unknown] [Position: " + worldPosition.getX() + ", " + worldPosition.getY() + ", " + worldPosition.getZ() + "]";
        }
        return " [Dimension: " + level.dimension().identifier() + "] [Position: " + worldPosition.getX() + ", " + worldPosition.getY() + ", " + worldPosition.getZ() + "]";
    }

    /**
     * Logs detailed network state for debugging during world unloading
     */
    private void logNetworkState(String context) {
        // Skip logging if debug logging is not enabled
        if (!Config.enableDebugLogging) {
            return;
        }
        
        LOGGER.error("Bridge{}: ========== NETWORK STATE DUMP [{}] ==========", getLocationInfo(), context);
        
        // Log AE2 Node state
        LOGGER.error("Bridge{}: AE2 Node State:", getLocationInfo());
        if (mainNode == null) {
            LOGGER.error("Bridge{}: - mainNode is NULL", getLocationInfo());
        } else {
            LOGGER.error("Bridge{}: - mainNode exists: {}", getLocationInfo(), mainNode.getClass().getName());
            IGridNode node = mainNode.getNode();
            if (node == null) {
                LOGGER.error("Bridge{}: - mainNode.getNode() is NULL", getLocationInfo());
            } else {
                LOGGER.error("Bridge{}: - IGridNode exists: {}", getLocationInfo(), node.getClass().getName());
                try {
                    LOGGER.error("Bridge{}: - Node active: {}", getLocationInfo(), node.isActive());
                    IGrid grid = node.getGrid();
                    if (grid == null) {
                        LOGGER.error("Bridge{}: - Grid is NULL", getLocationInfo());
                    } else {
                        LOGGER.error("Bridge{}: - Grid exists: {}", getLocationInfo(), grid.getClass().getName());
                        LOGGER.error("Bridge{}: - Grid node count: {}", getLocationInfo(), grid.getMachines(IGridNode.class).size());
                    }
                } catch (Exception e) {
                    LOGGER.error("Bridge{}: - Exception accessing node properties: {}", getLocationInfo(), e.getMessage(), e);
                }
            }
            LOGGER.error("Bridge{}: - mainNode.isActive(): {}", getLocationInfo(), mainNode.isActive());
        }
        LOGGER.error("Bridge{}: - nodeCreated flag: {}", getLocationInfo(), nodeCreated);
        LOGGER.error("Bridge{}: - shouldReconnect flag: {}", getLocationInfo(), shouldReconnect);
        
        // Log Replication Network state
        LOGGER.error("Bridge{}: Replication Network State:", getLocationInfo());
        try {
            MatterNetwork network = getNetwork();
            if (network == null) {
                LOGGER.error("Bridge{}: - Replication network is NULL", getLocationInfo());
            } else {
                LOGGER.error("Bridge{}: - Replication network exists: {}", getLocationInfo(), network.getClass().getName());
                try {
                    LOGGER.error("Bridge{}: - Network ID: {}", getLocationInfo(), network.getId());
                    LOGGER.error("Bridge{}: - Network has task manager: {}", getLocationInfo(), network.getTaskManager() != null);
                    if (network.getTaskManager() != null) {
                        LOGGER.error("Bridge{}: - Pending tasks count: {}", getLocationInfo(), 
                            network.getTaskManager().getPendingTasks() != null ? network.getTaskManager().getPendingTasks().size() : "NULL");
                    }
                } catch (Exception e) {
                    LOGGER.error("Bridge{}: - Exception accessing network properties: {}", getLocationInfo(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Bridge{}: - Exception getting Replication network: {}", getLocationInfo(), e.getMessage(), e);
        }
        
        // Log NetworkManager state
        LOGGER.error("Bridge{}: NetworkManager State:", getLocationInfo());
        if (level == null) {
            LOGGER.error("Bridge{}: - Level is NULL, cannot check NetworkManager", getLocationInfo());
        } else {
            try {
                NetworkManager networkManager = NetworkManager.get(level);
                if (networkManager == null) {
                    LOGGER.error("Bridge{}: - NetworkManager is NULL", getLocationInfo());
                } else {
                    LOGGER.error("Bridge{}: - NetworkManager exists: {}", getLocationInfo(), networkManager.getClass().getName());
                    NetworkElement element = networkManager.getElement(worldPosition);
                    if (element == null) {
                        LOGGER.error("Bridge{}: - NetworkElement at position is NULL", getLocationInfo());
                    } else {
                        LOGGER.error("Bridge{}: - NetworkElement exists: {}", getLocationInfo(), element.getClass().getName());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Bridge{}: - Exception accessing NetworkManager: {}", getLocationInfo(), e.getMessage(), e);
            }
        }
        
        // Log internal state
        LOGGER.error("Bridge{}: Internal State:", getLocationInfo());
        LOGGER.error("Bridge{}: - initialized: {}", getLocationInfo(), initialized);
        LOGGER.error("Bridge{}: - worldUnloading (static): {}", getLocationInfo(), worldUnloading);
        LOGGER.error("Bridge{}: - level null: {}", getLocationInfo(), level == null);
        LOGGER.error("Bridge{}: - level client side: {}", getLocationInfo(), level != null && level.isClientSide());
        LOGGER.error("Bridge{}: - blockId: {}", getLocationInfo(), blockId);
        
        LOGGER.error("Bridge{}: ========== END NETWORK STATE DUMP ==========", getLocationInfo());
    }

    public RepAE2BridgeBlockEntity(BlockPos pos, BlockState blockState) {
        super((BasicTileBlock<RepAE2BridgeBlockEntity>) ModBlocks.REPAE2BRIDGE.get(),
                ModBlockEntities.REPAE2BRIDGE_BE.get(),
                pos,
                blockState);

        // Generate a unique identifier for this block
        this.blockId = UUID.randomUUID();

        // Initialize terminal component
        this.terminalPlayerTracker = new TerminalPlayerTracker();
        this.sortingTypeValue = 0;
        this.sortingDirection = 1;
        this.matterOpediaSortingTypeValue = 0;
        this.matterOpediaSortingDirection = 1;

        // Initialize the output inventory component with 18 slots (9x2)
        this.output = new InventoryComponent<RepAE2BridgeBlockEntity>("output", 11, 131, 36)
                .setRange(9, 4)
                .setComponentHarness(this)
                // Crafted item buffer only — matter is mirrored via ReplicationMatterKey, not item stacks.
                .setSlotLimit(64)
                .setInputFilter((stack, slot) -> true); // Allows insertion of any item
        this.addInventory(this.output);

        // Note: Registration to activeBridges is done in onLoad() to avoid client-side instances
    }

    @NotNull
    @Override
    public RepAE2BridgeBlockEntity getSelf() {
        return this;
    }

    /**
     * Check if there is another bridge in the specified direction
     * @param direction The direction to check
     * @return true if there is another bridge in the specified direction
     */
    private boolean hasBridgeInDirection(Direction direction) {
        if (level != null) {
            BlockPos neighborPos = worldPosition.relative(direction);
            // Safety check: only query if the chunk is loaded
            if (!level.isLoaded(neighborPos)) {
                return false;
            }
            return level.getBlockEntity(neighborPos) instanceof RepAE2BridgeBlockEntity;
        }
        return false;
    }

    /**
     * Creates the network element for this bridge.
     * 
     * CRITICAL: This method MUST NOT return null because NetworkBlockEntity.onLoad() passes
     * the result directly to NetworkManager.addElement() without null-checking:
     *   networkManager.addElement(createElement(level, worldPosition));
     * Returning null would cause a NullPointerException inside Titanium's NetworkManager.
     */
    @Override
    protected NetworkElement createElement(Level level, BlockPos pos) {
        try {
            return new DefaultMatterNetworkElement(level, pos) {
                @Override
                public boolean canConnectFrom(Direction direction) {
                    // Safety check: only query if the chunk is loaded
                    if (level != null && !level.isLoaded(pos.relative(direction))) {
                        return false;
                    }
                    // Prevent two adjacent bridges from connecting to each other
                    // (each bridge should connect to the pipe network, not directly to another bridge)
                    if (level != null && level.getBlockEntity(pos.relative(direction)) instanceof RepAE2BridgeBlockEntity) {
                        return false;
                    }
                    return super.canConnectFrom(direction);
                }
            };
        } catch (Exception e) {
            LOGGER.error("Bridge at {}: Failed to create custom network element, using default: {}", pos, e.getMessage(), e);
            // Fallback: return a basic element to prevent NPE in NetworkBlockEntity.onLoad()
            return new DefaultMatterNetworkElement(level, pos);
        }
    }

    /**
     * Called when the BlockEntity is loaded or after placement.
     * 
     * SAFE APPROACH: Let super.onLoad() handle network element creation/addition normally.
     * We only wrap it in try-catch to handle "Element network is null!" gracefully.
     * 
     * CRITICAL: We NEVER manipulate network elements belonging to other blocks.
     * Removing or modifying other blocks' network elements corrupts the Replication network
     * and causes cascading crashes when those blocks try to reload.
     */
    @Override
    public void onLoad() {
        // Let super.onLoad() handle the Replication network element lifecycle.
        // NetworkBlockEntity.onLoad() will create and add the element via createElement().
        // We wrap it in try-catch to handle cases where the network isn't ready yet.
        try {
            super.onLoad();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Element network is null") || e.getMessage().contains("network is null"))) {
                // Network not ready yet - this is normal during world loading.
                // Schedule a retry via the block's scheduled tick. Do NOT manipulate other blocks' elements.
                if (level != null && !level.isClientSide()) {
                    if (Config.enableDebugLogging) {
                        LOGGER.warn("Bridge{}: Replication network not ready during onLoad, scheduling retry. Error: {}", getLocationInfo(), e.getMessage());
                    }
                    needsNetworkRetry = true;
                    level.scheduleTick(worldPosition, getBlockState().getBlock(), 40);
                }
                // Still proceed with AE2 initialization and bridge registration below
            } else {
                // Unexpected error - log and schedule retry
                LOGGER.error("Bridge{}: Unexpected error during onLoad(): {}", getLocationInfo(), e.getMessage(), e);
                if (level != null && !level.isClientSide()) {
                    needsNetworkRetry = true;
                    level.scheduleTick(worldPosition, getBlockState().getBlock(), 100);
                }
                return;
            }
        }

        // Register this bridge in the global registry for emergency cleanup (server-side only)
        // IMPORTANT: This must be done ALWAYS, not just when !nodeCreated
        // because nodeCreated can be true when reloading from NBT
        if (level != null && !level.isClientSide()) {
            activeBridges.add(this);
            if (Config.enableDebugLogging) {
                LOGGER.info("Bridge{}: Registered in active bridges registry (total: {})", getLocationInfo(), activeBridges.size());
            }
        }

        // Initialize the AE2 node if it hasn't been done
        if (!nodeCreated && level != null && !level.isClientSide()) {
            // Check if the node has already been initialized via AE2 APIs
            boolean nodeAlreadyExists = mainNode.getNode() != null;
            
            if (!nodeAlreadyExists) {
                try {
                    mainNode.create(level, worldPosition);
                    nodeCreated = true;
                    forceNeighborUpdates();
                    updateConnectedState();
                    ICraftingProvider.requestUpdate(mainNode);
                } catch (Exception e) {
                    LOGGER.error("Failed to initialize AE2 node: {}", e.getMessage());
                    shouldReconnect = true;
                    // Schedule another retry
                    if (level != null) {
                        level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
                    }
                }
            } else {
                // The node already exists, only update local state
                nodeCreated = true;
                forceNeighborUpdates();
                updateConnectedState();
                ICraftingProvider.requestUpdate(mainNode);
                LOGGER.debug("Bridge: AE2 node already exists in onLoad, skipping creation");
            }
        }
        // Reset the flag if it was loaded but the node no longer exists
        else if (nodeCreated && mainNode.getNode() == null) {
            nodeCreated = false;
            shouldReconnect = true;
            // Schedule a retry
            if (level != null && !level.isClientSide()) {
                level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
            }
        }
    }

    /**
     * Update the visual connection state of the block
     */
    private void updateConnectedState() {
        if (level != null && !level.isClientSide()) {
            // Safety check: verify the chunk is loaded before attempting state updates
            // This prevents deadlocks when chunks are being unloaded (e.g. in compact machines)
            if (!level.isLoaded(worldPosition)) {
                if (Config.enableDebugLogging) {
                    LOGGER.debug("Bridge{}: Skipping updateConnectedState - chunk not loaded", getLocationInfo());
                }
                return;
            }
            
            BlockState currentState = level.getBlockState(worldPosition);
            if (currentState.getBlock() == ModBlocks.REPAE2BRIDGE.get()) {
                boolean isConnected = isActive() && getNetwork() != null;
                if (currentState.getValue(net.unfamily.repae2bridge.block.custom.RepAE2BridgeBl.CONNECTED) != isConnected) {
                    level.setBlock(worldPosition, currentState.setValue(
                            net.unfamily.repae2bridge.block.custom.RepAE2BridgeBl.CONNECTED, isConnected), 3);
                }
            }
        }
    }

    /**
     * Check if there is an AE2 controller in the network by checking if there are active AE2 cables adjacent
     * @return true if an active AE2 cable is found, which is probably connected to a controller
     */
    private boolean hasAE2NetworkConnection() {
        if (level != null && !level.isClientSide()) {
            // Check all adjacent blocks to see if there are active AE2 cables
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = worldPosition.relative(direction);

                // Safety check: only query if the chunk is loaded
                if (!level.isLoaded(neighborPos)) {
                    continue;
                }

                // If the block has a block entity and implements IInWorldGridNodeHost
                if (level.getBlockEntity(neighborPos) instanceof IInWorldGridNodeHost host) {
                    IGridNode node = host.getGridNode(direction.getOpposite());
                    if (node != null && node.isActive()) {
                        // If the node is active, it is probably connected to a controller
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Force updates to adjacent blocks
     * Schedules updates with a delay to prevent ClassCastException during block initialization
     */
    private void forceNeighborUpdates() {
        if (level != null && !level.isClientSide()) {
            // Safety check: verify the chunk is loaded before attempting updates
            if (!level.isLoaded(worldPosition)) {
                if (Config.enableDebugLogging) {
                    LOGGER.debug("Bridge{}: Skipping forceNeighborUpdates - chunk not loaded", getLocationInfo());
                }
                return;
            }
            
            // Schedule block update instead of doing it immediately
            pendingBlockUpdate = true;

            // Schedule neighbor updates with a 1-tick delay to ensure full initialization
            // This prevents ClassCastException when MatterPipeBlock checks for INetworkDirectionalConnection
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
            
            // Use a scheduled task to safely notify neighbors after initialization
            if (level.getServer() != null) {
                level.getServer().execute(() -> {
                    if (level != null && !level.isClientSide() && !isRemoved()) {
                        try {
                            // Then force updates to adjacent blocks
                            for (Direction direction : Direction.values()) {
                                BlockPos neighborPos = worldPosition.relative(direction);
                                // Safety check: only update if the neighbor chunk is loaded
                                if (!level.isLoaded(neighborPos)) {
                                    continue;
                                }
                                BlockState neighborState = level.getBlockState(neighborPos);
                                if (!neighborState.isAir()) {
                                    // Notify the adjacent block (to trigger its connections).
                                    // Adjacent bridges must get an explicit fromPos so they ignore us
                                    // (MC 26 Level.neighborChanged no longer carries fromPos → TPS storms).
                                    try {
                                        if (level.getBlockEntity(neighborPos) instanceof RepAE2BridgeBlockEntity otherBridge) {
                                            otherBridge.handleNeighborChanged(worldPosition);
                                        } else {
                                            level.neighborChanged(neighborPos, getBlockState().getBlock(), null);
                                        }
                                    } catch (ClassCastException e) {
                                        // Log but don't crash - this can happen with incompatible neighbor blocks
                                        if (Config.enableDebugLogging) {
                                            LOGGER.warn("Failed to notify neighbor at {} due to ClassCastException. This is usually safe to ignore.", neighborPos, e);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Unexpected error while forcing neighbor updates", e);
                        }
                    }
                });
            }
        }
    }

    /**
     * Handle when a neighboring block changes.
     * Called from the RepAE2BridgeBl block or directly from another bridge with an explicit fromPos.
     *
     * @param fromPos Position of the changed neighbor
     */
    public void handleNeighborChanged(BlockPos fromPos) {
        handleNeighborChanged(fromPos, true);
    }

    /**
     * Neighbor update without a known source (MC 26 Orientation often null).
     * Refreshes local AE2 init / visuals but never re-fans neighbor updates — that is what
     * caused TPS storms with two adjacent bridges in the broken 26.1 port.
     */
    public void handleNeighborChangedUnknownSource() {
        handleNeighborChanged(worldPosition, false);
    }

    private void handleNeighborChanged(BlockPos fromPos, boolean allowForceNeighborUpdates) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!level.isLoaded(worldPosition) || !level.isLoaded(fromPos)) {
            if (Config.enableDebugLogging) {
                LOGGER.debug("Bridge{}: Skipping handleNeighborChanged - chunk not loaded", getLocationInfo());
            }
            return;
        }

        // Self-notify must never cascade (was the broken 26.1 fan-out path).
        if (fromPos.equals(worldPosition)) {
            tryInitAe2NodeIfNeeded(false);
            updateConnectedState();
            return;
        }

        // If the changed block is another bridge, ignore the update (1.21.1 anti-cascade).
        Direction directionToNeighbor = null;
        for (Direction dir : Direction.values()) {
            if (worldPosition.relative(dir).equals(fromPos)) {
                directionToNeighbor = dir;
                break;
            }
        }
        if (directionToNeighbor != null && level.getBlockEntity(fromPos) instanceof RepAE2BridgeBlockEntity) {
            return;
        }

        boolean alreadyHadNode = nodeCreated && mainNode.getNode() != null;
        tryInitAe2NodeIfNeeded(allowForceNeighborUpdates);
        // 1.21.1: once the AE2 node exists, known-source updates still re-sync neighbors
        // (but never for adjacent bridges / unknown sources — those return earlier or pass false).
        if (allowForceNeighborUpdates && alreadyHadNode) {
            forceNeighborUpdates();
        }
        updateConnectedState();
    }

    private void tryInitAe2NodeIfNeeded(boolean allowForceNeighborUpdates) {
        boolean hasAE2Connection = hasAE2NetworkConnection();
        if (!hasAE2Connection || nodeCreated) {
            return;
        }
        boolean nodeAlreadyExists = mainNode.getNode() != null;
        if (!nodeAlreadyExists) {
            try {
                mainNode.create(level, worldPosition);
                nodeCreated = true;
                if (allowForceNeighborUpdates) {
                    forceNeighborUpdates();
                }
                updateConnectedState();
                ICraftingProvider.requestUpdate(mainNode);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize AE2 node: {}", e.getMessage());
                shouldReconnect = true;
            }
        } else {
            nodeCreated = true;
            if (allowForceNeighborUpdates) {
                forceNeighborUpdates();
            }
            updateConnectedState();
            ICraftingProvider.requestUpdate(mainNode);
            LOGGER.debug("Bridge: AE2 node already exists in handleNeighborChanged, skipping creation");
        }
    }

    /**
     * Explicitly disconnect this block from both networks
     * Called when the block is removed
     */
    public void disconnectFromNetworks() {
        LOGGER.info("Bridge{}: Disconnecting from AE2 and Titanium/Replication networks", getLocationInfo());
        
        // Disconnect from the AE2 network
        if (level != null && !level.isClientSide() && mainNode != null) {
            try {
                mainNode.destroy();
                nodeCreated = false;
                LOGGER.info("Bridge{}: AE2 network disconnected", getLocationInfo());
            } catch (Exception e) {
                LOGGER.warn("Bridge{}: Error disconnecting from AE2 network: {}", getLocationInfo(), e.getMessage());
            }
        }

        // Disconnect from the Replication network by calling parent's removal
        // This is normally handled by super.setRemoved() but we do it explicitly during world unloading
        try {
            if (level != null && !level.isClientSide()) {
                NetworkManager networkManager = NetworkManager.get(level);
                if (networkManager != null) {
                    NetworkElement element = networkManager.getElement(worldPosition);
                    if (element != null) {
                        LOGGER.info("Bridge{}: Removing from Titanium/Replication NetworkManager", getLocationInfo());
                        networkManager.removeElement(worldPosition);
                        LOGGER.info("Bridge{}: Titanium/Replication network disconnected", getLocationInfo());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Bridge{}: Error disconnecting from Titanium/Replication network: {}", getLocationInfo(), e.getMessage());
        }
        
        LOGGER.info("Bridge{}: Network disconnection completed", getLocationInfo());
    }
    
    /**
     * Disconnection of all active bridges from networks during world unloading
     * Called during world unloading to prevent hanging
     */
    private static void disconnectAllBridgesFromNetworks() {
        List<RepAE2BridgeBlockEntity> bridgesToDisconnect = new ArrayList<>(activeBridges);
        LOGGER.info("RepAE2Bridge: Starting network disconnection for {} bridges", bridgesToDisconnect.size());
        
        int disconnected = 0;
        int failed = 0;
        
        for (RepAE2BridgeBlockEntity bridge : bridgesToDisconnect) {
            try {
                LOGGER.info("RepAE2Bridge: Disconnecting bridge at {}", bridge.getLocationInfo());
                bridge.disconnectFromNetworks();
                disconnected++;
            } catch (Exception e) {
                LOGGER.warn("RepAE2Bridge: Failed to disconnect bridge: {}", e.getMessage());
                failed++;
            }
        }
        
        // Clear the registry
        activeBridges.clear();
        
        LOGGER.info("RepAE2Bridge: Network disconnection completed - {} successful, {} failed", disconnected, failed);
    }

    /**
     * Initialize the AE2 node with improved mechanisms for dedicated servers
     */
    public void initializeAE2Node() {
        if (level != null && !level.isClientSide() && mainNode != null) {
            try {
                // Force destruction of existing node before recreating it
                if (mainNode.getNode() != null) {
                    mainNode.destroy();
                    LOGGER.debug("Bridge: Destroyed existing AE2 node before recreation");
                }
                
                // Check if we're on a dedicated server
                boolean isDedicatedServer = level.getServer() != null && level.getServer().isDedicatedServer();
                
                if (isDedicatedServer) {
                    LOGGER.info("Bridge: Dedicated server detected, using direct node creation");
                    // Use direct method on dedicated servers to avoid scheduling issues
                    try {
                        // Create the node directly
                        mainNode.create(level, worldPosition);
                        nodeCreated = true;
                        
                        // Update all services immediately
                        ICraftingProvider.requestUpdate(mainNode);
                        IStorageProvider.requestUpdate(mainNode);
                        
                        // Force update of adjacent blocks
                        forceNeighborUpdates();
                        updateConnectedState();
                        
                        LOGGER.info("Bridge: AE2 node successfully created and registered with the grid (direct method)");
                    } catch (Exception e) {
                        LOGGER.error("Bridge: Failed to create AE2 node with direct method: {}", e.getMessage());
                    }
                } else {
                    // Use task method only in singleplayer/integrated server
                    level.getServer().execute(() -> {
                        try {
                            // Create the node with priority
                            mainNode.create(level, worldPosition);
                            nodeCreated = true;

                            // Update all services
                            ICraftingProvider.requestUpdate(mainNode);
                            IStorageProvider.requestUpdate(mainNode);

                            // Force update of adjacent blocks
                            forceNeighborUpdates();
                            updateConnectedState();

                            LOGGER.info("Bridge: AE2 node successfully created and registered with the grid");
                        } catch (Exception e) {
                            LOGGER.error("Bridge: Failed to create AE2 node during scheduled task: {}", e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                LOGGER.error("Bridge: Failed to initialize AE2 node: {}", e.getMessage());
            }
        }
    }

    /**
     * Check if it's possible to connect to an AE2 network and force it if necessary
     */
    private boolean forceAE2GridConnection() {
        if (level == null || level.isClientSide()) return false;
        
        // Safety check: verify the chunk is loaded
        if (!level.isLoaded(worldPosition)) {
            return false;
        }
        
        // Search for AE2 controller or cables nearby
        boolean foundAE2Component = false;
        IGrid connectedGrid = null;
        
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            // Safety check: only query if the neighbor chunk is loaded
            if (!level.isLoaded(neighborPos)) {
                continue;
            }
            BlockEntity neighborEntity = level.getBlockEntity(neighborPos);
            
            if (neighborEntity instanceof IInWorldGridNodeHost) {
                foundAE2Component = true;
                IGridNode neighborNode = ((IInWorldGridNodeHost) neighborEntity).getGridNode(direction.getOpposite());
                
                if (neighborNode != null && neighborNode.isActive()) {
                    connectedGrid = neighborNode.getGrid();
                    LOGGER.info("Bridge: Found active AE2 grid connection at {}", neighborPos);
                    break;
                }
            }
        }
        
        // If we found a grid but our node is not connected, force the connection
        if (connectedGrid != null && (mainNode.getNode() == null || mainNode.getNode().getGrid() == null)) {
            LOGGER.info("Bridge: Found AE2 grid but node not connected, forcing connection");
            initializeAE2Node();
            return true;
        }
        
        return foundAE2Component;
    }

    @Override
    public void setRemoved() {
        // Reduce logging spam for uninitialized bridges
        boolean shouldLog = Config.enableDebugLogging && initialized == 1;

        if (shouldLog) {
            LOGGER.error("Bridge{}: setRemoved() called - beginning cleanup", getLocationInfo());
        }

        // Remove from active bridges registry
        activeBridges.remove(this);
        if (shouldLog) {
            LOGGER.error("Bridge{}: Removed from active bridges registry (remaining: {})", getLocationInfo(), activeBridges.size());
        }

        // Log network state BEFORE any cleanup only for initialized bridges
        if (shouldLog) {
            logNetworkState("BEFORE setRemoved cleanup");
        }

        try {
            if (shouldLog) {
                LOGGER.error("Bridge{}: Attempting to destroy AE2 node", getLocationInfo());
            }
            // Destroy the AE2 node first
            if (mainNode != null) {
                mainNode.destroy();
                if (shouldLog) {
                    LOGGER.error("Bridge{}: AE2 node destruction completed", getLocationInfo());
                }
            } else {
                if (shouldLog) {
                    LOGGER.warn("Bridge{}: AE2 node was null during setRemoved", getLocationInfo());
                }
            }
        } catch (Exception e) {
            if (shouldLog) {
                LOGGER.error("Bridge{}: EXCEPTION destroying AE2 node: {}", getLocationInfo(), e.getMessage(), e);
            }
            // Continue with cleanup even if node destruction fails
        }

        // Log network state AFTER AE2 node destruction only for initialized bridges
        if (shouldLog) {
            logNetworkState("AFTER AE2 node destruction");
        }

        try {
            if (shouldLog) {
                LOGGER.error("Bridge{}: Calling super.setRemoved()", getLocationInfo());
            }
            super.setRemoved();
            if (shouldLog) {
                LOGGER.error("Bridge{}: super.setRemoved() completed", getLocationInfo());
            }
        } catch (Exception e) {
            if (shouldLog) {
                LOGGER.error("Bridge{}: EXCEPTION in super.setRemoved(): {}", getLocationInfo(), e.getMessage(), e);
            }
        }

        // Log network state AFTER super.setRemoved() only for initialized bridges
        if (shouldLog) {
            logNetworkState("AFTER super.setRemoved()");
        }

        // Force reset flags even if cleanup partially fails
        nodeCreated = false;
        shouldReconnect = false;
        if (shouldLog) {
            LOGGER.error("Bridge{}: setRemoved() cleanup completed", getLocationInfo());
        }
    }

    @Override
    public void onChunkUnloaded() {
        // Reduce logging spam for uninitialized bridges
        boolean shouldLog = Config.enableDebugLogging && initialized == 1;

        if (shouldLog) {
            LOGGER.error("Bridge{}: onChunkUnloaded() called - beginning cleanup", getLocationInfo());
        }

        // Log network state BEFORE any cleanup only for initialized bridges
        if (shouldLog) {
            logNetworkState("BEFORE onChunkUnloaded cleanup");
        }

        try {
            if (shouldLog) {
                LOGGER.error("Bridge{}: Attempting to destroy AE2 node in onChunkUnloaded", getLocationInfo());
            }
            // Clean up the AE2 node when the chunk is unloaded
            if (mainNode != null) {
                mainNode.destroy();
                if (shouldLog) {
                    LOGGER.error("Bridge{}: AE2 node destruction in onChunkUnloaded completed", getLocationInfo());
                }
            } else {
                if (shouldLog) {
                    LOGGER.warn("Bridge{}: AE2 node was null during onChunkUnloaded", getLocationInfo());
                }
            }
        } catch (Exception e) {
            if (shouldLog) {
                LOGGER.error("Bridge{}: EXCEPTION destroying AE2 node in onChunkUnloaded: {}", getLocationInfo(), e.getMessage(), e);
            }
            // Continue with cleanup even if node destruction fails
        }

        // Log network state AFTER AE2 node destruction only for initialized bridges
        if (shouldLog) {
            logNetworkState("AFTER AE2 node destruction in onChunkUnloaded");
        }

        try {
            if (shouldLog) {
                LOGGER.error("Bridge{}: Calling super.onChunkUnloaded()", getLocationInfo());
            }
            super.onChunkUnloaded();
            if (shouldLog) {
                LOGGER.error("Bridge{}: super.onChunkUnloaded() completed", getLocationInfo());
            }
        } catch (Exception e) {
            if (shouldLog) {
                LOGGER.error("Bridge{}: EXCEPTION in super.onChunkUnloaded(): {}", getLocationInfo(), e.getMessage(), e);
            }
        }

        // Log network state AFTER super.onChunkUnloaded() only for initialized bridges
        if (shouldLog) {
            logNetworkState("AFTER super.onChunkUnloaded()");
        }

        // Force reset flags even if cleanup partially fails
        nodeCreated = false;
        shouldReconnect = false;
        if (shouldLog) {
            LOGGER.error("Bridge{}: onChunkUnloaded() cleanup completed", getLocationInfo());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        // Skip heavy logging when not initialized to avoid performance issues
        if (Config.enableDebugLogging && initialized == 1) {
            LOGGER.error("Bridge{}: saveAdditional() called", getLocationInfo());
            // Log network state at save time only when initialized
            logNetworkState("DURING saveAdditional");
        }

        try {
            if (Config.enableDebugLogging) {
                LOGGER.error("Bridge{}: Calling super.saveAdditional()", getLocationInfo());
            }
            super.saveAdditional(output);
            if (Config.enableDebugLogging) {
                LOGGER.error("Bridge{}: super.saveAdditional() completed", getLocationInfo());
            }
        } catch (Exception e) {
            LOGGER.error("Bridge{}: EXCEPTION in super.saveAdditional(): {}", getLocationInfo(), e.getMessage(), e);
        }

        // Save the state of the AE2 node
        try {
            if (Config.enableDebugLogging) {
                LOGGER.error("Bridge{}: Attempting to serialize AE2 node", getLocationInfo());
            }
            mainNode.serialize(output);
            if (Config.enableDebugLogging) {
                LOGGER.error("Bridge{}: AE2 node serialized", getLocationInfo());
            }
        } catch (Exception e) {
            LOGGER.error("Bridge{}: EXCEPTION serializing AE2 node: {}", getLocationInfo(), e.getMessage(), e);
        }

        output.putBoolean("nodeCreated", nodeCreated);
        output.putBoolean("shouldReconnect", shouldReconnect);
        output.putBoolean("feedReplicationEnergy", feedReplicationEnergy);
        if (blockId != null) {
            output.store("blockId", UUIDUtil.CODEC, blockId);
        }
        saveCraftMatterBuffer(output);

        if (Config.enableDebugLogging) {
            LOGGER.error("Bridge{}: saveAdditional() completed", getLocationInfo());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        nodeCreated = input.getBooleanOr("nodeCreated", false);
        shouldReconnect = input.getBooleanOr("shouldReconnect", false);
        feedReplicationEnergy = input.getBooleanOr("feedReplicationEnergy", true);
        blockId = input.read("blockId", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        loadCraftMatterBuffer(input);
    }

    private void saveCraftMatterBuffer(ValueOutput output) {
        var list = output.childrenList("craftMatterBuffer");
        for (CraftMatterTank tank : getOrCreateCraftMatterTanks()) {
            if (tank.getMatterAmount() <= 0) {
                continue;
            }
            var registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
            if (registry == null) {
                continue;
            }
            var key = registry.getResourceKey(tank.getMatterType()).orElse(null);
            if (key == null) {
                continue;
            }
            ValueOutput child = list.addChild();
            child.store("type", Identifier.CODEC, key.identifier());
            child.putDouble("amount", tank.getMatterAmount());
        }
    }

    private void loadCraftMatterBuffer(ValueInput input) {
        getOrCreateCraftMatterTanks();
        input.childrenList("craftMatterBuffer").ifPresent(children -> {
            for (ValueInput child : children) {
                Identifier id = child.read("type", Identifier.CODEC).orElse(null);
                if (id == null) {
                    continue;
                }
                double amount = child.getDoubleOr("amount", 0);
                if (amount <= 0) {
                    continue;
                }
                var registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
                if (registry == null) {
                    continue;
                }
                IMatterType type = registry.getValue(id);
                if (type == null || type.equals(MatterType.EMPTY)) {
                    continue;
                }
                for (CraftMatterTank tank : craftMatterTanks) {
                    if (tank.getMatterType().equals(type)) {
                        tank.setAmount(amount);
                        break;
                    }
                }
            }
        });
    }

    // =================== Implement IInWorldGridNodeHost ===================

    @Override
    @Nullable
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART; // Use SMART for compatibility with most AE2 cables
    }

    /**
     * Implementation of IActionHost interface
     * @return The actionable grid node for this block entity
     */
    @Override
    @Nullable
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    /**
     * Handles server tick for synchronizing the two networks
     */
    @Override
    public void serverTick(Level level, BlockPos pos, BlockState state, RepAE2BridgeBlockEntity blockEntity) {
        // PRIORITY CHECK: Exit immediately if the world is unloading
        if (worldUnloading) {
            if (worldUnloadingWarningsHidden == WARNING_THROTTLE_INTERVAL) {
                LOGGER.warn("Bridge{}: EXITING serverTick early - world is unloading (+ {} similar warnings hidden in last 15s)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                worldUnloadingWarningsHidden = 0;
            } else {
                worldUnloadingWarningsHidden++;
            }
            return;
        }

        // Increment debug counter and manage debug logging
        debugTickCounter++;
        boolean shouldLogDebug = debugTickCounter % DEBUG_LOG_INTERVAL == 0 && Config.enableDebugLogging;
        globalShouldLogDebug = shouldLogDebug; // Update the global flag
        
        if (shouldLogDebug) {
            LOGGER.debug("Bridge at {}: serverTick summary - {} ticks completed, {} local + {} global debug messages hidden", 
                worldPosition, debugTickCounter, hiddenDebugMessages, globalHiddenDebugMessages);
            hiddenDebugMessages = 0; // Reset local counter
            globalHiddenDebugMessages = 0; // Reset global counter
        } else {
            hiddenDebugMessages += 3; // Count the debug messages we're hiding locally
        }

        // SINGLE CALL to super.serverTick() with safety check
        try {
            if (shouldLogDebug) {
                LOGGER.debug("Bridge at {}: Calling super.serverTick()", worldPosition);
            }
            super.serverTick(level, pos, state, blockEntity);
            if (shouldLogDebug) {
                LOGGER.debug("Bridge at {}: super.serverTick() completed successfully", worldPosition);
            }
        } catch (Exception e) {
            LOGGER.error("Bridge at {}: EXCEPTION in super.serverTick(): {}", worldPosition, e.getMessage(), e);
            // If super.serverTick() fails, don't continue with bridge-specific operations
            return;
        }

        // Periodic check of network element health (every 5 seconds = 100 ticks)
        // Only logs status - does NOT remove or manipulate elements to avoid corruption
        if (level.getGameTime() % 100 == 0 && initialized == 1) {
            try {
                NetworkManager networkManager = NetworkManager.get(level);
                if (networkManager != null) {
                    NetworkElement element = networkManager.getElement(worldPosition);
                    if (element == null || element.getNetwork() == null) {
                        // Element missing or has null network - mark for retry via onLoad()
                        if (!needsNetworkRetry) {
                            needsNetworkRetry = true;
                            if (Config.enableDebugLogging) {
                                LOGGER.warn("Bridge{}: Periodic check found missing/invalid element, flagging for retry", getLocationInfo());
                            }
                            level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
                        }
                    }
                }
            } catch (Exception e) {
                // Don't spam logs for periodic checks
                if (Config.enableDebugLogging) {
                    LOGGER.debug("Bridge{}: Error during periodic network validation: {}", getLocationInfo(), e.getMessage());
                }
            }
        }

        // Track matter creation from disintegrators only if networks are available
        if (initialized == 1 && getNetwork() != null && isActive()) {
            trackMatterCreation();
            feedEnergyToMatterNetwork();
            // Chambers finish in 1 tick at max speed — assign every tick so they never idle waiting.
            assignReplicationTasksToAssemblers();
            // Drop AE2-internal work when the crafting CPU was cancelled (AdvancedAE included).
            pruneOrphanedAe2InternalWork();
            feedAe2InternalJobs();
            // Flush matter UI sync when quiet, or every REPLICATION_TANK_SYNC_INTERVAL ticks.
            boolean craftQuiet = ae2InternalPending.isEmpty() && requestCounters.isEmpty();
            flushReplicationTankSync(craftQuiet);
        }

        // POST-SUPER CHECK: Verify if the world has started unloading during super.serverTick()
        if (worldUnloading) {
            if (worldUnloadingWarningsHidden == WARNING_THROTTLE_INTERVAL) {
                LOGGER.warn("Bridge{}: World unloading detected after super.serverTick() - exiting immediately (+ {} similar warnings hidden)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                worldUnloadingWarningsHidden = 0;
            } else {
                worldUnloadingWarningsHidden++;
            }
            return;
        }

        if (shouldLogDebug) {
            LOGGER.debug("Bridge at {}: Beginning bridge-specific operations", worldPosition);
        }

        // Check if we're on a dedicated server
        boolean isDedicatedServer = level.getServer() != null && level.getServer().isDedicatedServer();
        
        // At the beginning, check if we need to reconnect to the AE2 network
        if (isDedicatedServer && level.getGameTime() % 100 == 0) {  // Check every 5 seconds
            if (shouldReconnect || mainNode.getNode() == null || !mainNode.getNode().isActive()) {
                if (Config.enableDebugLogging) {
                    LOGGER.info("Bridge at {}: Performing reconnection check for AE2 network on dedicated server", worldPosition);
                }
                forceAE2GridConnection();
                shouldReconnect = false;
            }
        }
        
        // CONTINUOUS CHECK: If the world starts unloading during our operations
        if (worldUnloading) {
            // Still need to call cleanup even if we don't log
            if (initialized == 1) {
                onWorldUnload();
            }
            
            if (worldUnloadingWarningsHidden == WARNING_THROTTLE_INTERVAL) {
                LOGGER.warn("Bridge{}: WORLD UNLOADING DETECTED - entering cleanup mode (+ {} similar warnings hidden)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                worldUnloadingWarningsHidden = 0;
            } else {
                worldUnloadingWarningsHidden++;
            }
            return;
        }

        // Handle reinitialization after world reload
        if (shouldReconnect && initialized == 1 && !nodeCreated) {
            LOGGER.warn("Bridge at {}: Reconnecting after world reload", worldPosition);
            if (mainNode.getNode() == null) {
                try {
                    mainNode.create(level, worldPosition);
                    nodeCreated = true;

                    // Update connections
                    forceNeighborUpdates();
                    updateConnectedState();

                    // Force pattern and storage updates
                    ICraftingProvider.requestUpdate(mainNode);
                    IStorageProvider.requestUpdate(mainNode);
                    shouldReconnect = false;
                    LOGGER.warn("Bridge at {}: Reconnection completed successfully", worldPosition);
                } catch (Exception e) {
                    LOGGER.error("Bridge at {}: Failed to initialize AE2 node during reconnection: {}", worldPosition, e.getMessage(), e);
                    shouldReconnect = true;
                }
            }
        }

        // Delayed initialization handling
        if (initialized == 0) {
            initializationTicks++;
            if (initializationTicks >= INITIALIZATION_DELAY) {
                LOGGER.warn("Bridge at {}: Initialization completed after {} ticks", worldPosition, initializationTicks);
                initialized = 1;

                // Force update of patterns and connections after initialization
                if (!level.isClientSide()) {
                    try {
                        forceNeighborUpdates();
                        updateConnectedState();
                        ICraftingProvider.requestUpdate(mainNode);
                        IStorageProvider.requestUpdate(mainNode);
                        LOGGER.warn("Bridge at {}: Post-initialization updates completed", worldPosition);
                    } catch (Exception e) {
                        LOGGER.error("Bridge at {}: EXCEPTION during post-initialization updates: {}", worldPosition, e.getMessage(), e);
                    }
                }
            }
        }



        // Process pending block updates every 20 ticks (1 second) to batch updates
        if (level.getGameTime() % 20 == 0 && pendingBlockUpdate) {
            pendingBlockUpdate = false;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        // Try to transfer items from local inventory to AE2 every 20 ticks (1 second)
        if (level.getGameTime() % 20 == 0 && initialized == 1) {
            try {
                transferItemsToAE2();
            } catch (Exception e) {
                LOGGER.error("Bridge at {}: EXCEPTION in transferItemsToAE2(): {}", worldPosition, e.getMessage(), e);
            }
        }

        // Periodic pattern updates - remove check for matterUpdatesBlocked
        if (patternUpdateTicks >= PATTERN_UPDATE_INTERVAL) {
            if (isActive() && getNetwork() != null) {
                try {
                    // LOGGER.info("Bridge: Periodic pattern update");
                    ICraftingProvider.requestUpdate(mainNode);

                    // Also update storage to show new matter quantities
                    IStorageProvider.requestUpdate(mainNode);
                    if (shouldLogDebug) {
                        LOGGER.debug("Bridge at {}: Periodic pattern update completed", worldPosition);
                    } else {
                        hiddenDebugMessages++;
                    }
                } catch (Exception e) {
                    LOGGER.error("Bridge at {}: EXCEPTION during periodic pattern update: {}", worldPosition, e.getMessage(), e);
                }
            }
            patternUpdateTicks = 0;
        } else {
            patternUpdateTicks++;
        }

        // Periodically strip legacy virtual matter AEItemKeys from the AE2 network.
        // Cap per-pass extract: unbounded extract of huge amounts can allocate hard and OOM.
        if (level.getGameTime() % 40 == 0 && isActive() && mainNode.getNode() != null) {
            try {
                IGrid grid = mainNode.getNode().getGrid();
                if (grid != null) {
                    IStorageService storageService = grid.getStorageService();
                    if (storageService != null) {
                        KeyCounter items = storageService.getInventory().getAvailableStacks();
                        MachineSource machineSource = new MachineSource(this);
                        final long maxExtractPerKey = 1_000_000L;
                        items.forEach(entry -> {
                            AEKey key = entry.getKey();
                            if (key instanceof AEItemKey itemKey && isVirtualMatterItem(itemKey.getItem())) {
                                long amount = Math.min(entry.getLongValue(), maxExtractPerKey);
                                if (amount > 0) {
                                    try {
                                        storageService.getInventory().extract(itemKey, amount, Actionable.MODULATE, machineSource);
                                    } catch (Exception e) {
                                        LOGGER.error("Bridge at {}: EXCEPTION removing virtual matter items: {}", worldPosition, e.getMessage(), e);
                                    }
                                }
                            }
                        });
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Bridge at {}: EXCEPTION during virtual matter cleanup: {}", worldPosition, e.getMessage(), e);
            }
        }

        // Batch into fewer ReplicationTasks — never flush every tick (terminal chokes).
        int accumulationWindow = getAdaptiveRequestAccumulationTicks();
        boolean timeReady = requestCounterTicks >= accumulationWindow;
        boolean countReady = !requestCounters.isEmpty() && getPendingRequestMaxCount() >= REQUEST_ACCUMULATION_COUNT_FLUSH;
        if (timeReady || countReady) {
            try {
                // SAFETY CHECK: Verify again if the world is unloading
                if (worldUnloading) {
                    if (initialized == 1) {
                        onWorldUnload();
                    }
                    
                    if (worldUnloadingWarningsHidden == WARNING_THROTTLE_INTERVAL) {
                        LOGGER.warn("Bridge{}: World unloading detected during request processing - aborting (+ {} similar warnings hidden)", 
                                   getLocationInfo(), worldUnloadingWarningsHidden);
                        worldUnloadingWarningsHidden = 0;
                    } else {
                        worldUnloadingWarningsHidden++;
                    }
                    return;
                }

                flushPendingRequestCounters();
            } catch (Exception e) {
                LOGGER.error("Bridge at {}: EXCEPTION in creating tasks: {}", worldPosition, e.getMessage(), e);
            }
        } else {
            requestCounterTicks++;
        }

        // Check network connection to the Replication network
        // SAFE APPROACH: If our network is null and we need a retry, schedule it via onLoad().
        // We NEVER manipulate network elements belonging to other blocks (neighbors).
        if (getNetwork() == null && needsNetworkRetry) {
            // Only retry periodically (every 2 seconds) to avoid spam
            if (level.getGameTime() % 40 == 0) {
                if (Config.enableDebugLogging) {
                    LOGGER.debug("Bridge at {}: Network still null, scheduling onLoad retry", pos);
                }
                level.scheduleTick(pos, getBlockState().getBlock(), 20);
            }
        }

        // Pattern queue management removed - we no longer use the local queue
        // Patterns are sent directly to the Replication network which manages capacity


        // Check less frequently the state of the AE2 node
        if (level.getGameTime() % 40 == 0) { // Only every 2 seconds
            if (!isActive() && shouldReconnect) {
                try {
                    if (mainNode.getNode() == null && !nodeCreated) {
                        mainNode.create(level, worldPosition);
                        nodeCreated = true;
                        forceNeighborUpdates();
                        updateConnectedState();
                        ICraftingProvider.requestUpdate(mainNode);
                    } else {
                        forceNeighborUpdates();
                    }
                    shouldReconnect = false;
                } catch (Exception e) {
                    LOGGER.error("Failed to reconnect AE2 node: {}", e.getMessage());
                }
            }

            // Update the visual state
            updateConnectedState();
        }

        // Update the terminal player tracker
        this.terminalPlayerTracker.checkIfValid();
    }

    /**
     * Overridden to ensure the node is always powered
     * Similar to the ReplicationTerminal that is always active
     */
    public boolean isPowered() {
        // Always returns true regardless of the actual power state
        return true;
    }

    /**
     * Get the Replication network for this bridge.
     * 
     * SAFE APPROACH: Only reads the existing network element state.
     * NEVER creates, adds, or removes network elements here.
     * Element lifecycle is handled exclusively by onLoad() (via super.onLoad())
     * and setRemoved() (via super.setRemoved()).
     */
    @Override
    public MatterNetwork getNetwork() {
        if (level == null || level.isClientSide()) {
            return null;
        }
        
        // If the world is being unloaded, avoid accessing networks
        if (worldUnloading) {
            if (worldUnloadingWarningsHidden == WARNING_THROTTLE_INTERVAL) {
                LOGGER.warn("Bridge{}: getNetwork() called during world unloading - returning null (+ {} similar warnings hidden)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                worldUnloadingWarningsHidden = 0;
            } else {
                worldUnloadingWarningsHidden++;
            }
            return null;
        }
        
        try {
            NetworkManager networkManager = NetworkManager.get(level);
            if (networkManager == null) {
                return null;
            }
            NetworkElement element = networkManager.getElement(worldPosition);
            if (element == null) {
                // Element doesn't exist yet - this is normal if onLoad() hasn't run
                // or if the network retry is pending. Don't create elements here.
                return null;
            }
            
            if (element.getNetwork() == null) {
                // Element exists but has no network - this can happen during loading.
                // Don't remove or manipulate it; let the normal lifecycle handle it.
                return null;
            }
            
            if (element.getNetwork() instanceof MatterNetwork matterNetwork) {
                // Network is valid - clear any pending retry flag
                needsNetworkRetry = false;
                return matterNetwork;
            } else {
                if (Config.enableDebugLogging) {
                    LOGGER.error("Bridge at {}: Network element exists but is not a MatterNetwork!", worldPosition);
                }
            }
        } catch (Exception e) {
            if (Config.enableDebugLogging) {
                LOGGER.error("Bridge at {}: EXCEPTION accessing Replication network: {}", worldPosition, e.getMessage(), e);
            }
        }
        return null;
    }

    // =================== Utility methods ===================

    public boolean isActive() {
        // If world is unloading, consider the bridge as inactive to prevent operations
        if (worldUnloading) {
            if (worldUnloadingWarningsHidden == WARNING_THROTTLE_INTERVAL) {
                LOGGER.warn("Bridge{}: isActive() returning false due to world unloading (+ {} similar warnings hidden)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                worldUnloadingWarningsHidden = 0;
            } else {
                worldUnloadingWarningsHidden++;
            }
            return false;
        }
        
        try {
            boolean active = mainNode.isActive() && mainNode.getNode() != null;
            if (!active) {
                if (globalShouldLogDebug) {
                    LOGGER.debug("Bridge at {}: isActive() = false (node inactive or null)", worldPosition);
                } else {
                    globalHiddenDebugMessages++; // Count this hidden debug message globally
                }
            }
            return active;
        } catch (Exception e) {
            // If there's an error accessing the node, consider the bridge as inactive
            LOGGER.error("Bridge at {}: EXCEPTION checking node activity: {}", worldPosition, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Method called when the entity is saved or loaded from/to disk
     */
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && !level.isClientSide()) {
            try {
                GridHelper.onFirstTick(this, blockEntity -> {
                    // Check if the node has already been initialized via AE2 APIs
                    boolean nodeAlreadyExists = mainNode.getNode() != null;
                    
                    if ((shouldReconnect || !nodeCreated) && !nodeAlreadyExists) {
                        mainNode.create(level, worldPosition);
                        nodeCreated = true;
                        forceNeighborUpdates();
                        updateConnectedState();
                        ICraftingProvider.requestUpdate(mainNode);
                        shouldReconnect = false;
                    } else if (nodeAlreadyExists) {
                        // The node already exists, only update local state
                        nodeCreated = true;
                        shouldReconnect = false;
                        forceNeighborUpdates();
                        updateConnectedState();
                        ICraftingProvider.requestUpdate(mainNode);
                        LOGGER.debug("Bridge: AE2 node already exists, skipping creation");
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Failed to schedule AE2 node initialization: {}", e.getMessage());
            }
        }
    }

    /**
     * Utility method to get the Replication network with consistent naming
     * @return The Replication matter network
     */
    private MatterNetwork getReplicationNetwork() {
        return getNetwork();
    }

    // =================== Terminal implementation ===================

    @Override
    public InteractionResult onActivated(Player playerIn, InteractionHand hand, Direction facing, double hitX, double hitY, double hitZ) {
        // Enable GUI for priority settings
        if (!level.isClientSide() && playerIn instanceof ServerPlayer serverPlayer) {
            // Simply open the priority GUI without forcing pattern updates
            openGui(playerIn);
        }
        return InteractionResult.SUCCESS;
    }

    public TerminalPlayerTracker getTerminalPlayerTracker() {
        return terminalPlayerTracker;
    }

    public InventoryComponent<RepAE2BridgeBlockEntity> getOutput() {
        return output;
    }

    /**
     * Method to receive items from the Replicator
     * @param stack The item stack to insert
     * @return true if the insertion was successful, false otherwise
     */
    public boolean receiveItemFromReplicator(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // First try to insert directly into the AE2 network if connected.
        // Do not touch the output buffer here — this stack is inbound, not from buffer slots.
        boolean insertedIntoAE2 = false;
        long remainingCount = stack.getCount();

        if (mainNode.isActive() && mainNode.getNode() != null) {
            IGrid grid = mainNode.getNode().getGrid();
            if (grid != null) {
                IStorageService storageService = grid.getStorageService();
                if (storageService != null) {
                    AEItemKey key = AEItemKey.of(stack);
                    long insertedAmount = storageService.getInventory().insert(
                            key, stack.getCount(), Actionable.MODULATE, new MachineSource(this));

                    if (insertedAmount > 0) {
                        remainingCount -= insertedAmount;
                        insertedIntoAE2 = true;

                        if (remainingCount <= 0) {
                            return true;
                        }
                    }
                }
            }
        }

        // Remainder (or full stack if AE2 unavailable) → bridge output buffer
        if (remainingCount > 0) {
            ItemStack remainingStack = stack.copy();
            remainingStack.setCount((int) remainingCount);

            ItemStack notInserted = ItemUtil.insertItemReturnRemaining(this.output, remainingStack, false, null);

            if (notInserted.isEmpty()) {
                this.setChanged();
                return true;
            }

            if (notInserted.getCount() < remainingCount) {
                this.setChanged();
                return insertedIntoAE2 || (notInserted.getCount() < stack.getCount());
            }

            return insertedIntoAE2;
        }

        return true;
    }

    /**
     * Deliver one crafted unit for a Replication-network task to {@code task.getSource()}
     * (same path as vanilla Replicator → terminal). No AE2 fallback.
     *
     * @return true if the item was fully inserted and the task unit was finalized
     */
    /** True while the Replication UI task is still in the pending map. */
    public boolean hasPendingReplicationTask(UUID taskId) {
        if (taskId == null) {
            return false;
        }
        MatterNetwork network = getNetworkSafe();
        return network != null
                && network.getTaskManager().getPendingTasks().containsKey(taskId.toString());
    }

    /**
     * Ensures matter for one craft unit is reserved on the task for {@code workerPos}.
     * Needed between chamber multi-batch units (finalizeReplication clears the prior reserve).
     */
    public boolean ensureMatterReservedForWorker(UUID taskId, BlockPos workerPos) {
        if (taskId == null || workerPos == null || level == null || level.isClientSide()) {
            return false;
        }
        MatterNetwork network = getNetworkSafe();
        if (network == null) {
            return false;
        }
        IReplicationTask task = network.getTaskManager().getPendingTasks().get(taskId.toString());
        if (task == null) {
            return false;
        }
        if (task.getStoredMatterStack().containsKey(workerPos.asLong())) {
            return true;
        }
        task.storeMatterStacksFor(level, workerPos, network);
        return task.getStoredMatterStack().containsKey(workerPos.asLong());
    }

    public boolean deliverMatterAssemblerReplicationUnit(ItemStack stack, UUID taskId, BlockPos assemblerPos) {
        if (stack.isEmpty() || taskId == null || level == null || level.isClientSide()) {
            return false;
        }

        MatterNetwork network = getNetworkSafe();
        if (network == null) {
            return false;
        }

        IReplicationTask task = network.getTaskManager().getPendingTasks().get(taskId.toString());
        if (task == null) {
            return false;
        }

        BlockPos source = task.getSource();
        var capability = level.getCapability(Capabilities.Item.BLOCK, source, Direction.UP);
        if (capability == null) {
            return false;
        }

        ItemStack leftover = TransferUtil.insertItem(capability, stack.copyWithCount(1), false);
        if (!leftover.isEmpty()) {
            // Terminal / source full — Matter Assembler has no local spill; wait.
            return false;
        }

        onMatterAssemblerCompletedUnit(taskId, assemblerPos);
        return true;
    }

    /**
     * Handles a completed task from the Replication network
     * @param task The completed task
     * @param stack The produced item stack
     */
    public void handleCompletedTask(IReplicationTask task, ItemStack stack) {
        // Try to insert the item into the AE2 network or local inventory
        boolean inserted = receiveItemFromReplicator(stack);

        if (!inserted) {
            // Debug log disabled for production
            // LOGGER.debug("Bridge: Failed to insert item from completed task: {}", stack.getDisplayName().getString());
        }

        // Update request counters
        UUID sourceId = this.blockId;
        if (task.getSource().equals(this.worldPosition)) {
            // Decrement the counter for this pattern in source-specific requests
            Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.getOrDefault(sourceId, new HashMap<>());
            int currentCount = sourceRequests.getOrDefault(stack, 0);
            if (currentCount > 0) {
                sourceRequests.put(stack, currentCount - 1);
                patternRequestsBySource.put(sourceId, sourceRequests);
            }

            // Also update the global counter for backward compatibility
            Map<ItemStack, Integer> globalRequests = patternRequests.getOrDefault(sourceId, new HashMap<>());
            int currentGlobalCount = globalRequests.getOrDefault(stack, 0);
            if (currentGlobalCount > 0) {
                globalRequests.put(stack, currentGlobalCount - 1);
                patternRequests.put(sourceId, globalRequests);
                //LOGGER.info("Bridge: Task completed for {}, remaining {} active requests",
                //    pattern.getItem().getDescriptionId(), currentGlobalCount - 1);
            }
        }
    }

    // =================== Utility methods ===================

    public static class TerminalPlayerTracker {
        private List<ServerPlayer> players;
        private List<UUID> uuidsToRemove;
        private List<ServerPlayer> playersToAdd;

        public TerminalPlayerTracker() {
            this.players = new ArrayList<>();
            this.uuidsToRemove = new ArrayList<>();
            this.playersToAdd = new ArrayList<>();
        }

        public void checkIfValid() {
            var output = new ArrayList<>(playersToAdd);
            var input = new ArrayList<>(players);
            for (ServerPlayer serverPlayer : input) {
                if (!this.uuidsToRemove.contains(serverPlayer.getUUID())) {
                    output.add(serverPlayer);
                }
            }
            this.players = output;
            this.uuidsToRemove = new ArrayList<>();
            this.playersToAdd = new ArrayList<>();
        }

        public void removePlayer(ServerPlayer serverPlayer) {
            this.uuidsToRemove.add(serverPlayer.getUUID());
        }

        public void addPlayer(ServerPlayer serverPlayer) {
            this.playersToAdd.add(serverPlayer);
        }

        public List<ServerPlayer> getPlayers() {
            return players;
        }
    }

    // Implementazione di ICraftingInventory
    @Override
    public void insert(AEKey what, long amount, Actionable mode) {
        if (mode == Actionable.MODULATE && what instanceof AEItemKey itemKey) {
            // When an item is inserted for crafting, check if it can be crafted with Replication
            MatterNetwork network = getNetwork();
            if (network != null) {
                // Check if the item can be crafted
                for (NetworkElement chipSupplier : network.getChipSuppliers()) {
                    var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                    if (tile instanceof ChipStorageBlockEntity chipStorage) {
                        for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                            if (pattern.getStack().getItem().equals(itemKey.getItem())) {
                                // The item can be crafted, we can proceed
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode) {
        if (what instanceof ReplicationMatterKey) {
            MachineSource machineSource = new MachineSource(this);
            return matterItemsStorage.extract(what, amount, mode, machineSource);
        }
        // Legacy: old AEItemKey matter patterns
        if (what instanceof AEItemKey itemKey && isVirtualMatterItem(itemKey.getItem())) {
            MachineSource machineSource = new MachineSource(this);
            return matterItemsStorage.extract(what, amount, mode, machineSource);
        }
        MatterNetwork network = getNetwork();
        if (network != null && what instanceof AEItemKey) {
            AEItemKey itemKey = (AEItemKey) what;
            for (NetworkElement chipSupplier : network.getChipSuppliers()) {
                var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                if (tile instanceof ChipStorageBlockEntity chipStorage) {
                    for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                        if (pattern.getStack().getItem().equals(itemKey.getItem())) {
                            return amount;
                        }
                    }
                }
            }
        }
        return 0;
    }

    @Override
    public Iterable<AEKey> findFuzzyTemplates(AEKey input) {
        if (input instanceof AEItemKey itemKey) {
            MatterNetwork network = getNetwork();
            if (network != null) {
                List<AEKey> templates = new ArrayList<>();
                // Search for all items that can be crafted with Replication
                for (NetworkElement chipSupplier : network.getChipSuppliers()) {
                    var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                    if (tile instanceof ChipStorageBlockEntity chipStorage) {
                        for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                            templates.add(AEItemKey.of(pattern.getStack().getItem()));
                        }
                    }
                }
                return templates;
            }
        }
        return List.of();
    }

    // Implementazione di ICraftingProvider
    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        // Don't show patterns if not initialized
        //§ fix?
        if (initialized != 1) {
            return List.of();
        }

        List<IPatternDetails> patterns = new ArrayList<>();
        MatterNetwork network = getNetwork();
        if (network != null) {
            // For each chip storage in the network
            for (NetworkElement chipSupplier : network.getChipSuppliers()) {
                var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                if (tile instanceof ChipStorageBlockEntity chipStorage) {
                    // For each pattern in the chip storage
                    for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                        if (!pattern.getStack().isEmpty() && pattern.getCompletion() == 1) {
                            var matterCompound = ReplicationCalculation.getMatterCompound(pattern.getStack());
                            if (matterCompound == null) {
                                continue;
                            }
                            // Matter inputs so AE2 reserves cell stock; pushPattern absorbs into craft buffer.
                            var details = ReplicationBridgePatternDetails.of(pattern.getStack());
                            if (details != null) {
                                patterns.add(details);
                            }
                        }
                    }
                }
            }
        }
        //LOGGER.info("Bridge: Total available patterns: {}", patterns.size());
        return patterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // Don't accept patterns if not initialized
        //§ fix?
        if (initialized != 1) {
            return false;
        }

        MatterNetwork network = getNetwork();

        if (network != null && isActive() && patternDetails != null) {
            // Check if the pattern produces an item that can be replicated
            if (patternDetails.getOutputs().size() == 1) {
                var output = patternDetails.getOutputs().iterator().next();
                if (output.what() instanceof AEItemKey itemKey) {
                    //LOGGER.info("Bridge: Request to pushPattern for {}", itemKey.getItem().getDescriptionId());

                    // We no longer put patterns in queue - the Replication network manages capacity
                    // Se ci sono replicatori liberi, prenderanno il task
                    // Se sono tutti occupati, il task resta in coda nella rete Replication

                    // Search for the pattern in all chip storage in the network
                    for (NetworkElement chipSupplier : network.getChipSuppliers()) {
                        var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                        if (tile instanceof ChipStorageBlockEntity chipStorage) {
                            for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                                if (pattern.getStack().getItem().equals(itemKey.getItem())) {
                                    // 1) Sink CPU inputs into craft buffer
                                    ItemStack itemStack = pattern.getStack();
                                    if (!absorbCraftMatter(inputHolder, itemStack)) {
                                        if (Config.enableDebugLogging) {
                                            LOGGER.warn(
                                                    "Bridge{}: pushPattern refused — inputHolder missing required matter for {}",
                                                    getLocationInfo(), itemKey.getItem().getDescriptionId());
                                        }
                                        return false;
                                    }
                                    // 2) AE2-internal bypass when chamber/MA is on this grid.
                                    // Must NOT fall through to ReplicationTask — a failed cell park used to
                                    // flood the terminal and freeze it on open.
                                    if (hasAe2GridWorker()) {
                                        parkCraftBufferPreferAe2KeepRest();
                                        // Only count real pushPattern units — never prefill from CPU job size.
                                        // Prefill crafted before waitingFor was registered, so AE2 showed
                                        // "missing" outputs that were already sitting in the network.
                                        ItemWithSourceId key = new ItemWithSourceId(itemStack, this.blockId);
                                        ae2InternalPending.merge(key, 1, Integer::sum);
                                        feedAe2InternalJobs();
                                        return true;
                                    }
                                    // 3) No AE2 workers: Replication accumulation → possible ReplicationTask.
                                    flushCraftBufferToStorage(false);
                                    ItemWithSourceId key = new ItemWithSourceId(itemStack, this.blockId);
                                    Map<ItemWithSourceId, Integer> sourceCounters =
                                            requestCounters.getOrDefault(this.blockId, new HashMap<>());
                                    sourceCounters.put(key, sourceCounters.getOrDefault(key, 0) + 1);
                                    requestCounters.put(this.blockId, sourceCounters);
                                    return true;
                                }
                            }
                        }
                    }
                    //LOGGER.warn("Bridge: Pattern not found in the Replication network");
                } else {
                    //LOGGER.warn("Bridge: No Replication network found");
                }
            }
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        // Il bridge NON deve bloccarsi in base ai task pendenti nella rete Replication
        // Must only track completed tasks to update counters
        // Capacity management is delegated to the Replication network itself
        
        MatterNetwork network = getNetwork();
        if (network != null) {
            // Get all task IDs from the network
            Set<String> networkTaskIds = new HashSet<>(network.getTaskManager().getPendingTasks().keySet());

            // Check all sources per tracciare i task completati
            for (UUID sourceId : activeTasks.keySet()) {
                Map<String, TaskSourceInfo> sourceTasks = activeTasks.get(sourceId);

                // Create a copy of the keys to avoid concurrent modification
                Set<String> taskIds = new HashSet<>(sourceTasks.keySet());

                for (String taskId : taskIds) {
                    // If the task is no longer in the network, it's completed
                    if (!networkTaskIds.contains(taskId)) {
                        // The task has been completed, remove it
                        TaskSourceInfo info = sourceTasks.remove(taskId);
                        if (info != null) {
                            ItemStack pattern = info.getItemStack();

                            // Decrement the counter for this pattern by source
                            Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.getOrDefault(sourceId, new HashMap<>());
                            int currentCount = sourceRequests.getOrDefault(pattern, 0);
                            if (currentCount > 0) {
                                sourceRequests.put(pattern, currentCount - 1);
                                patternRequestsBySource.put(sourceId, sourceRequests);
                            }

                            // Also update the global counter for backward compatibility
                            Map<ItemStack, Integer> globalRequests = patternRequests.getOrDefault(sourceId, new HashMap<>());
                            int currentGlobalCount = globalRequests.getOrDefault(pattern, 0);
                            if (currentGlobalCount > 0) {
                                globalRequests.put(pattern, currentGlobalCount - 1);
                                patternRequests.put(sourceId, globalRequests);
                            }
                        }
                    }
                }

                // If this source has no more tasks, remove it from the map
                if (sourceTasks.isEmpty()) {
                    activeTasks.remove(sourceId);
                } else {
                    activeTasks.put(sourceId, sourceTasks);
                }
            }

            // Always update storage to show new quantities
            if (requestCounters.isEmpty() && level != null && level.getGameTime() % 20 == 0) {
                IStorageProvider.requestUpdate(mainNode);
            }
        }
        
        // The bridge is never "busy" - let the Replication network manage capacity
        return false;
    }

    @Override
    public int getPatternPriority() {
        return priority;
    }

    public Future<ICraftingPlan> beginCraftingCalculation(Level level,
                                                          ICraftingSimulationRequester simRequester,
                                                          AEKey what,
                                                          long amount,
                                                          CalculationStrategy strategy) {

        if (what instanceof AEItemKey itemKey) {
            //LOGGER.info("Bridge: Crafting calculation for {} x{}", itemKey.getItem().getDescriptionId(), amount);
            MatterNetwork network = getNetwork();
            if (network != null) {
                // Search for the pattern in the Replication network
                for (NetworkElement chipSupplier : network.getChipSuppliers()) {
                    var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                    if (tile instanceof ChipStorageBlockEntity chipStorage) {
                        for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                            if (pattern.getStack().getItem().equals(itemKey.getItem())) {
                                // Check if there is enough matter for this quantity
                                var matterCompound = ReplicationCalculation.getMatterCompound(pattern.getStack());
                                if (matterCompound != null) {
                                    boolean hasEnoughMatter = true;
                                    Map<IMatterType, Long> missingMatter = new HashMap<>();

                                    // Calculate the necessary and available matter
                                    for (MatterValue matterValue : matterCompound.getValues().values()) {
                                        var matterType = matterValue.getMatter();
                                        var matterPerItem = matterValue.getAmount();
                                        // Match pattern encoding: ceil per item, then * count
                                        long totalMatterNeeded = (long) Math.ceil(matterPerItem) * amount;
                                        long available = network.calculateMatterAmount(matterType);

                                        //LOGGER.info("Bridge: Matter needed for {}: {} {} ({} per item)",
                                        //    itemKey.getItem().getDescriptionId(),
                                        //    totalMatterNeeded,
                                        //    matterType.toString(),
                                        //    matterPerItem);

                                        //LOGGER.info("Bridge: Matter available: {} {}",
                                        //    available,
                                        //    matterType.toString());

                                        if (available < totalMatterNeeded) {
                                            hasEnoughMatter = false;
                                            missingMatter.put(matterType, totalMatterNeeded - available);

                                            // Create a unique key for this warning
                                            String warningKey = itemKey.getItem().getDescriptionId() + ":" + matterType.getName();
                                            long currentTime = level.getGameTime();

                                            // Check if we have already shown this warning recently
                                            if (!lastMatterWarnings.containsKey(warningKey) ||
                                                    currentTime - lastMatterWarnings.get(warningKey) > WARNING_COOLDOWN) {
                                                //LOGGER.warn("Bridge: Matter {} insufficient for {}. Needed: {}, Available: {}",
                                                //    matterType.getName(),
                                                //    itemKey.getItem().getDescriptionId(),
                                                //    totalMatterNeeded,
                                                //    available);
                                                lastMatterWarnings.put(warningKey, currentTime);
                                            }
                                        }
                                    }

                                    if (!hasEnoughMatter) {
                                        StringBuilder errorMsg = new StringBuilder();
                                        errorMsg.append("Not enough matter available. Missing:\n");
                                        for (Map.Entry<IMatterType, Long> entry : missingMatter.entrySet()) {
                                            errorMsg.append("- ")
                                                    .append(entry.getValue())
                                                    .append(" ")
                                                    .append(entry.getKey().toString())
                                                    .append("\n");
                                        }

                                        //LOGGER.warn("Bridge: Request of {} x{} rejected due to lack of matter",
                                        //    itemKey.getItem().getDescriptionId(), amount);
                                        //LOGGER.warn("Bridge: Missing matter:\n{}", errorMsg);

                                        return CompletableFuture.failedFuture(
                                                new IllegalStateException(errorMsg.toString()));
                                    }

                                    //LOGGER.info("Bridge: Matter sufficient for crafting {} x{}",
                                    //    itemKey.getItem().getDescriptionId(), amount);

                                    // If there is enough matter, create a crafting plan
                                    return CompletableFuture.completedFuture(new ICraftingPlan() {
                                        @Override
                                        public GenericStack finalOutput() {
                                            return new GenericStack(AEItemKey.of(itemKey.getItem()), amount);
                                        }

                                        @Override
                                        public long bytes() {
                                            return 0; // We don't use bytes
                                        }

                                        @Override
                                        public boolean simulation() {
                                            return false; // It's not a simulation
                                        }

                                        @Override
                                        public boolean multiplePaths() {
                                            return false; // There are no multiple paths
                                        }

                                        @Override
                                        public KeyCounter usedItems() {
                                            return new KeyCounter(); // We don't use items
                                        }

                                        @Override
                                        public KeyCounter emittedItems() {
                                            return new KeyCounter(); // We don't emit items
                                        }

                                        @Override
                                        public KeyCounter missingItems() {
                                            return new KeyCounter(); // There are no missing items
                                        }

                                        @Override
                                        public Map<IPatternDetails, Long> patternTimes() {
                                            return Map.of(); // We don't use patterns
                                        }
                                    });
                                } else {
                                    //LOGGER.error("Bridge: Impossible to calculate the required matter for {}",
                                    //    itemKey.getItem().getDescriptionId());
                                    return CompletableFuture.failedFuture(
                                            new IllegalStateException("Cannot calculate required matter"));
                                }
                            }
                        }
                    }
                }
                //LOGGER.warn("Bridge: No pattern found for {}", itemKey.getItem().getDescriptionId());
                return CompletableFuture.failedFuture(
                        new IllegalStateException("No pattern found for this item"));
            } else {
                //LOGGER.error("Bridge: No Replication network found");
                return CompletableFuture.failedFuture(
                        new IllegalStateException("No Replication network found"));
            }
        }

        // If we get here, we can't craft this item
        //LOGGER.error("Bridge: Attempt to craft an invalid item");
        return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot craft this item"));
    }

    // Utility to recognize virtual matter items (supports both built-in and custom matter types)
    private boolean isVirtualMatterItem(Item item) {
        // Check built-in matter items
        boolean isBuiltIn = item == ModItems.EARTH_MATTER.get()
                || item == ModItems.NETHER_MATTER.get()
                || item == ModItems.ORGANIC_MATTER.get()
                || item == ModItems.ENDER_MATTER.get()
                || item == ModItems.METALLIC_MATTER.get()
                || item == ModItems.PRECIOUS_MATTER.get()
                || item == ModItems.LIVING_MATTER.get()
                || item == ModItems.QUANTUM_MATTER.get();

        // Check custom matter items
        boolean isCustom = ServerLifecycleEventHandler.getCustomVirtualMatterItems().contains(item);

        return isBuiltIn || isCustom;
    }

    // Method to show matter keys in the AE2 terminal
    public void getAvailableItems(KeyCounter items) {
        if (initialized != 1) {
            return;
        }
        MatterNetwork network = getNetwork();
        if (network == null) {
            return;
        }
        for (IMatterType matterType : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
            long amount = network.calculateMatterAmount(matterType);
            if (amount <= 0) {
                continue;
            }
            ReplicationMatterKey key = ReplicationMatterKey.of(matterType);
            if (key != null) {
                items.add(key, amount);
            }
        }
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        // Don't mount storage if not initialized
        if (initialized != 1) {
            return;
        }

        // Mirror external Replication tanks into AE2; cell matter is listed by cell mounts.
        storageMounts.mount(matterItemsStorage, 100);
    }

    /**
     * Method called when the entity is saved or loaded from/to disk
     */
    public class MatterItemsStorage implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            // Craft-cancel refunds must return to AE2 cells, not be voided here.
            // Real inserts (disintegrator, buses) also go through cell mounts, not this facade.
            return 0;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (initialized != 1 || amount <= 0 || !(what instanceof ReplicationMatterKey matterKey)) {
                return 0;
            }
            IMatterType type = matterKey.resolveMatterType();
            if (type == null || type.equals(MatterType.EMPTY)) {
                return 0;
            }
            // Drain external Replication tanks / craft buffer so craft simulation and
            // CPU extract match getAvailableStacks (no phantom stock).
            return drainExternalMatter(type, amount, mode == Actionable.SIMULATE);
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("Replication Matter Storage");
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (initialized != 1 || !isActive()) {
                return;
            }
            for (IMatterType matterType : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
                if (matterType == null || matterType.equals(MatterType.EMPTY)) {
                    continue;
                }
                long amount = calculateExternalMatterAmount(matterType);
                if (amount <= 0) {
                    continue;
                }
                ReplicationMatterKey key = ReplicationMatterKey.of(matterType);
                if (key != null) {
                    out.add(key, amount);
                }
            }
        }
    }

    // Instance of the virtual matter item storage
    private final MatterItemsStorage matterItemsStorage = new MatterItemsStorage();

    // Method to react to Replication network events
    public void handleReplicationNetworkEvent() {
        if (isActive() && level != null && !level.isClientSide()) {
            // Force a pattern update
            //LOGGER.info("Bridge: Pattern update in response to Replication event");
            ICraftingProvider.requestUpdate(mainNode);
        }
    }

    // Map virtual item -> IMatterType (supports both built-in and custom matter types)
    private IMatterType getMatterTypeForItem(Item item) {
        // Check built-in matter types first
        if (item == ModItems.EARTH_MATTER.get()) return ReplicationRegistry.Matter.EARTH.get();
        if (item == ModItems.NETHER_MATTER.get()) return ReplicationRegistry.Matter.NETHER.get();
        if (item == ModItems.ORGANIC_MATTER.get()) return ReplicationRegistry.Matter.ORGANIC.get();
        if (item == ModItems.ENDER_MATTER.get()) return ReplicationRegistry.Matter.ENDER.get();
        if (item == ModItems.METALLIC_MATTER.get()) return ReplicationRegistry.Matter.METALLIC.get();
        if (item == ModItems.PRECIOUS_MATTER.get()) return ReplicationRegistry.Matter.PRECIOUS.get();
        if (item == ModItems.LIVING_MATTER.get()) return ReplicationRegistry.Matter.LIVING.get();
        if (item == ModItems.QUANTUM_MATTER.get()) return ReplicationRegistry.Matter.QUANTUM.get();

        // Check custom matter types
        return ServerLifecycleEventHandler.getCustomItemToMatterMap().get(item);
    }

    /**
     * Metodo statico per cancellare tutte le operazioni pendenti su tutti i bridge
     * durante la chiusura del server
     */
    public static void cancelAllPendingOperations() {
        try {
            // This method is called when the world is unloading to cancel all pending operations
            // to prevent hanging during shutdown
            
            // Clear any active tasks or pending operations here
            // For now, we just set the flag to indicate operations should stop
            
            if (globalOperationLogsHidden == WARNING_THROTTLE_INTERVAL) {
                LOGGER.error("GLOBAL OPERATION CANCELLATION - Starting cleanup (+ {} hidden identical messages in last 15s)", globalOperationLogsHidden);
                LOGGER.error("GLOBAL OPERATION CANCELLATION - All pending operations marked for cancellation");
                globalOperationLogsHidden = 0;
            } else {
                globalOperationLogsHidden++;
            }
        } catch (Exception e) {
            // Gli errori vengono sempre loggati indipendentemente dal throttling
            LOGGER.error("GLOBAL OPERATION CANCELLATION - EXCEPTION during cleanup: {}", e.getMessage(), e);
        }
    }

    // Method to handle world unload event
    public void onWorldUnload() {
        LOGGER.error("Bridge{}: onWorldUnload() called - CRITICAL CLEANUP STARTING", getLocationInfo());
        
        try {
            LOGGER.error("Bridge{}: Setting world unloading flag locally", getLocationInfo());
            // Set the flag to indicate that the world is unloading
            worldUnloading = true;

            LOGGER.error("Bridge{}: Starting AE2 node destruction", getLocationInfo());
            // Clean up AE2 connections with timeout protection
            if (mainNode != null) {
                try {
                    // Clean up AE2 node without timing - just ensure it gets destroyed
                    mainNode.destroy();
                    LOGGER.error("Bridge{}: AE2 node destruction successful", getLocationInfo());
                } catch (Exception e) {
                    LOGGER.error("Bridge{}: EXCEPTION destroying AE2 node in onWorldUnload: {}", getLocationInfo(), e.getMessage(), e);
                }
            } else {
                LOGGER.warn("Bridge{}: AE2 node was null during onWorldUnload", getLocationInfo());
            }

            LOGGER.error("Bridge{}: Starting Replication network element removal", getLocationInfo());
            // CRITICAL: Remove element from Replication NetworkManager to prevent corruption
            // This ensures the element doesn't remain with network == null after world reload
            try {
                if (level != null && !level.isClientSide()) {
                    NetworkManager networkManager = NetworkManager.get(level);
                    if (networkManager != null) {
                        NetworkElement element = networkManager.getElement(worldPosition);
                        if (element != null) {
                            LOGGER.error("Bridge{}: Removing element from Replication NetworkManager", getLocationInfo());
                            networkManager.removeElement(worldPosition);
                            LOGGER.error("Bridge{}: Replication network element removed successfully", getLocationInfo());
                        } else {
                            LOGGER.debug("Bridge{}: Element not found in NetworkManager (already removed?)", getLocationInfo());
                        }
                    } else {
                        LOGGER.warn("Bridge{}: NetworkManager not available during onWorldUnload", getLocationInfo());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Bridge{}: EXCEPTION removing Replication network element in onWorldUnload: {}", getLocationInfo(), e.getMessage(), e);
                // Continue with cleanup even if removal fails
            }

            LOGGER.error("Bridge{}: Clearing data structures", getLocationInfo());
            // Clear any pending operations and data structures
            try {
                // pendingPatterns and pendingInputs removed - no longer used
                patternRequests.clear();
                activeTasks.clear();
                patternRequestsBySource.clear();
                requestCounters.clear();
                ae2InternalPending.clear();
                
                // Clear terminal tracker and other data
                if (terminalPlayerTracker != null) {
                    terminalPlayerTracker = null;
                }
                
                // Clear matter warnings and other caches
                lastMatterWarnings.clear();
                
                // Reset initialization state
                initializationTicks = 0;
                
                LOGGER.error("Bridge{}: Data structures cleared successfully", getLocationInfo());
            } catch (Exception e) {
                LOGGER.error("Bridge{}: EXCEPTION clearing data structures: {}", getLocationInfo(), e.getMessage(), e);
            }

            LOGGER.error("Bridge{}: Resetting connection flags", getLocationInfo());
            // Reset connection flags
            nodeCreated = false;
            shouldReconnect = false;
            initialized = 0;

            LOGGER.error("Bridge{}: Parent class cleanup not needed (no super.onWorldUnload)", getLocationInfo());

            LOGGER.error("Bridge{}: onWorldUnload() CRITICAL CLEANUP COMPLETED", getLocationInfo());
        } catch (Exception e) {
            LOGGER.error("Bridge{}: FATAL EXCEPTION in onWorldUnload(): {}", getLocationInfo(), e.getMessage(), e);
        }
    }

    /**
     * Get the unique identifier for this block
     * @return the UUID of this block
     */
    public UUID getBlockId() {
        return blockId;
    }

    /**
     * Get the number of active requests for this specific block
     * @return the total number of active requests for this block
     */
    public int getActiveRequestsForThisBlock() {
        Map<ItemStack, Integer> requests = patternRequestsBySource.getOrDefault(this.blockId, new HashMap<>());
        return requests.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Get the number of active requests for a specific item from this block
     * @param item the item to check
     * @return the number of active requests for the item from this block
     */
    public int getActiveRequestsForItem(Item item) {
        Map<ItemStack, Integer> requests = patternRequestsBySource.getOrDefault(this.blockId, new HashMap<>());
        return requests.entrySet().stream()
                .filter(entry -> entry.getKey().getItem() == item)
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    /**
     * Get the total number of active requests across all blocks
     * @return the total number of active requests
     */
    public int getTotalActiveRequests() {
        int total = 0;
        for (Map<ItemStack, Integer> sourceRequests : patternRequestsBySource.values()) {
            total += sourceRequests.values().stream().mapToInt(Integer::intValue).sum();
        }
        return total;
    }

    /**
     * Helper class to track an item with its source block ID
     */
    public static class ItemWithSourceId {
        private final ItemStack itemStack;
        private final UUID sourceId;

        public ItemWithSourceId(ItemStack itemStack, UUID sourceId) {
            this.itemStack = itemStack.copy(); // Create a copy to avoid reference issues
            this.sourceId = sourceId;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public UUID getSourceId() {
            return sourceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemWithSourceId that = (ItemWithSourceId) o;
            return ItemStack.matches(itemStack, that.itemStack) &&
                    Objects.equals(sourceId, that.sourceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemStack.getItem(), sourceId);
        }
    }

    /**
     * Helper class to track task information including source block
     */
    public static class TaskSourceInfo {
        private final ItemStack itemStack;
        private final UUID sourceId;

        public TaskSourceInfo(ItemStack itemStack, UUID sourceId) {
            this.itemStack = itemStack.copy(); // Create a copy to avoid reference issues
            this.sourceId = sourceId;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public UUID getSourceId() {
            return sourceId;
        }
    }




    /**
     * Periodically tries to transfer items from the local inventory to the AE2 network
     * This ensures that items are moved to the AE2 system as soon as possible
     */
    private void transferItemsToAE2() {
        // Only proceed if we have an active AE2 connection
        if (!mainNode.isActive() || mainNode.getNode() == null) {
            return;
        }

        IGrid grid = mainNode.getNode().getGrid();
        if (grid == null) {
            return;
        }

        IStorageService storageService = grid.getStorageService();
        if (storageService == null) {
            return;
        }

        // Check each slot in the output inventory
        boolean itemsMoved = false;

        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack stack = output.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            // Try to insert the item into the AE2 storage
            AEItemKey key = AEItemKey.of(stack);
            long inserted = storageService.getInventory().insert(
                    key, stack.getCount(), Actionable.MODULATE, new MachineSource(this));

            if (inserted > 0) {
                // Titanium 26 getStackInSlot returns a copy — must extract, not shrink the copy.
                output.extractItem(i, (int) inserted, false);
                itemsMoved = true;
            }
        }

        if (itemsMoved) {
            this.setChanged();
        }
    }

    // IPriorityHost / IMatterTanksSupplier / IMatterTanksConsumer
    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.setChanged();
    }

    @Override
    public List<? extends IMatterTank> getTanks() {
        // Prefer AE2 cell mirrors. Only expose non-empty craft-buffer leftovers
        // (pushPattern flushes buffer into physical tanks / AE2 before starting tasks).
        List<IMatterTank> tanks = new ArrayList<>();
        for (CraftMatterTank tank : getOrCreateCraftMatterTanks()) {
            if (tank.getMatterAmount() > 0) {
                tanks.add(tank);
            }
        }
        tanks.addAll(getOrCreateAe2MatterTanks());
        return tanks;
    }

    private List<CraftMatterTank> getOrCreateCraftMatterTanks() {
        int expected = countNonEmptyMatterTypes();
        if (craftMatterTanks == null || craftMatterTanks.size() != expected) {
            Map<IMatterType, Double> previous = new HashMap<>();
            if (craftMatterTanks != null) {
                for (CraftMatterTank tank : craftMatterTanks) {
                    if (tank.getMatterAmount() > 0) {
                        previous.put(tank.getMatterType(), tank.getMatterAmount());
                    }
                }
            }
            List<CraftMatterTank> tanks = new ArrayList<>();
            for (IMatterType type : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
                if (type == null || type.equals(MatterType.EMPTY)) {
                    continue;
                }
                CraftMatterTank tank = new CraftMatterTank(type);
                Double prior = previous.get(type);
                if (prior != null) {
                    tank.setAmount(prior);
                }
                tanks.add(tank);
            }
            craftMatterTanks = tanks;
        }
        return craftMatterTanks;
    }

    private List<Ae2MatterTank> getOrCreateAe2MatterTanks() {
        int expected = countNonEmptyMatterTypes();
        if (ae2MatterTanks == null || ae2MatterTanks.size() != expected) {
            List<Ae2MatterTank> tanks = new ArrayList<>();
            for (IMatterType type : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
                if (type == null || type.equals(MatterType.EMPTY)) {
                    continue;
                }
                tanks.add(new Ae2MatterTank(this, type));
            }
            ae2MatterTanks = tanks;
        }
        return ae2MatterTanks;
    }

    private static int countNonEmptyMatterTypes() {
        int expected = 0;
        for (IMatterType type : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
            if (type != null && !type.equals(MatterType.EMPTY)) {
                expected++;
            }
        }
        return expected;
    }

    /**
     * Fills the craft buffer from AE2 pattern inputs. Returns false if required matter is missing.
     */
    private boolean absorbCraftMatter(KeyCounter[] inputHolder, ItemStack patternStack) {
        var matterCompound = ReplicationCalculation.getMatterCompound(patternStack);
        if (matterCompound == null) {
            return true;
        }

        // Sum matter delivered in inputHolder
        java.util.Map<IMatterType, Long> delivered = new java.util.HashMap<>();
        if (inputHolder != null) {
            for (KeyCounter counter : inputHolder) {
                if (counter == null) {
                    continue;
                }
                for (var entry : counter) {
                    if (!(entry.getKey() instanceof ReplicationMatterKey matterKey)) {
                        continue;
                    }
                    IMatterType type = matterKey.resolveMatterType();
                    if (type == null || type.equals(MatterType.EMPTY)) {
                        continue;
                    }
                    delivered.merge(type, entry.getLongValue(), Long::sum);
                }
            }
        }

        for (MatterValue matterValue : matterCompound.getValues().values()) {
            IMatterType type = matterValue.getMatter();
            long needed = (long) Math.ceil(matterValue.getAmount());
            if (needed <= 0) {
                continue;
            }
            long have = delivered.getOrDefault(type, 0L);
            if (have < needed) {
                return false;
            }
        }

        for (MatterValue matterValue : matterCompound.getValues().values()) {
            IMatterType type = matterValue.getMatter();
            long needed = (long) Math.ceil(matterValue.getAmount());
            if (needed <= 0) {
                continue;
            }
            CraftMatterTank tank = findCraftMatterTank(type);
            if (tank == null) {
                return false;
            }
            double filled = tank.fill(
                    new com.buuz135.replication.api.matter_fluid.MatterStack(type, needed),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (filled + 0.01 < needed) {
                return false;
            }
            delivered.merge(type, -needed, Long::sum);
        }

        for (MatterValue matterValue : matterCompound.getValues().values()) {
            scheduleReplicationTankSync(matterValue.getMatter());
        }
        setChanged();
        return true;
    }

    /** Moves craft-buffer matter into physical tanks first, then AE2 cells. */
    private void flushCraftBufferToStorage() {
        flushCraftBufferToStorage(false);
    }

    /**
     * Moves craft-buffer matter into storage.
     * @param preferAe2 when true (idle chamber/MA), insert into AE2 cells first so workers
     *                  can drain via {@link Ae2MatterTank} without waiting on physical tanks.
     */
    private void flushCraftBufferToStorage(boolean preferAe2) {
        MatterNetwork network = getNetworkSafe();
        for (CraftMatterTank craftTank : getOrCreateCraftMatterTanks()) {
            double amount = craftTank.getMatterAmount();
            if (amount <= 0) {
                continue;
            }
            IMatterType type = craftTank.getMatterType();

            if (preferAe2) {
                amount = flushCraftTankIntoAe2(craftTank, type, network);
            }
            if (amount > 0) {
                amount = flushCraftTankIntoPhysicalTanks(craftTank, type, network);
            }
            if (!preferAe2 && amount > 0) {
                flushCraftTankIntoAe2(craftTank, type, network);
            }
        }
        setChanged();
        if (mainNode != null && mainNode.isReady()) {
            IStorageProvider.requestUpdate(mainNode);
        }
    }

    private double flushCraftTankIntoAe2(CraftMatterTank craftTank, IMatterType type, @Nullable MatterNetwork network) {
        double amount = craftTank.getMatterAmount();
        if (amount <= 0) {
            return 0;
        }
        long moved = insertAe2Matter(type, (long) Math.floor(amount), false);
        if (moved > 0) {
            craftTank.drain(
                    (double) moved,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            scheduleReplicationTankSync(type);
        }
        return craftTank.getMatterAmount();
    }

    private double flushCraftTankIntoPhysicalTanks(
            CraftMatterTank craftTank, IMatterType type, @Nullable MatterNetwork network) {
        if (network == null) {
            return craftTank.getMatterAmount();
        }
        List<IMatterTanksConsumer> consumers = new ArrayList<>();
        for (NetworkElement element : network.getMatterStacksHolders()) {
            if (!element.getLevel().isLoaded(element.getPos()) || element.getPos().equals(worldPosition)) {
                continue;
            }
            BlockEntity tile = element.getLevel().getBlockEntity(element.getPos());
            if (tile instanceof IMatterTanksConsumer consumer) {
                consumers.add(consumer);
            }
        }
        consumers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (IMatterTanksConsumer consumer : consumers) {
            double amount = craftTank.getMatterAmount();
            if (amount <= 0) {
                break;
            }
            for (IMatterTank tank : consumer.getTanks()) {
                amount = craftTank.getMatterAmount();
                if (amount <= 0) {
                    break;
                }
                if (!tank.getMatter().isEmpty() && !tank.getMatter().getMatterType().equals(type)) {
                    continue;
                }
                var offer = new com.buuz135.replication.api.matter_fluid.MatterStack(type, amount);
                if (!tank.isMatterValid(offer) && !tank.getMatter().isEmpty()) {
                    continue;
                }
                double space = tank.getCapacity() - tank.getMatterAmount();
                if (space <= 0) {
                    continue;
                }
                double toMove = Math.min(amount, space);
                double filled = tank.fill(
                        new com.buuz135.replication.api.matter_fluid.MatterStack(type, toMove),
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0) {
                    craftTank.drain(filled, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    scheduleReplicationTankSync(type);
                }
            }
        }
        return craftTank.getMatterAmount();
    }

    /**
     * Shorter accumulation when the grid has many co-processors (fast pushPattern).
     * {@code ticks = max(5, 100 / (1 + maxCoProcessors))}.
     */
    private int getAdaptiveRequestAccumulationTicks() {
        int maxCo = 0;
        IGrid grid = mainNode.getGrid();
        if (grid != null) {
            for (ICraftingCPU cpu : grid.getCraftingService().getCpus()) {
                if (cpu != null) {
                    maxCo = Math.max(maxCo, cpu.getCoProcessors());
                }
            }
        }
        return Math.max(REQUEST_ACCUMULATION_TICKS_MIN, REQUEST_ACCUMULATION_TICKS / (1 + maxCo));
    }

    private int getPendingRequestMaxCount() {
        int max = 0;
        for (Map<ItemWithSourceId, Integer> sourceCounters : requestCounters.values()) {
            if (sourceCounters == null) {
                continue;
            }
            for (int count : sourceCounters.values()) {
                if (count > max) {
                    max = count;
                }
            }
        }
        return max;
    }

    /**
     * Flushes accumulated {@link #requestCounters} into chambers / MAs / ReplicationTasks.
     */
    private void flushPendingRequestCounters() {
        if (requestCounters.isEmpty()) {
            requestCounterTicks = 0;
            return;
        }
        if (worldUnloading) {
            return;
        }
        for (UUID sourceId : new ArrayList<>(requestCounters.keySet())) {
            if (worldUnloading) {
                return;
            }
            Map<ItemWithSourceId, Integer> sourceCounters = requestCounters.get(sourceId);
            if (sourceCounters == null) {
                continue;
            }
            for (Map.Entry<ItemWithSourceId, Integer> entry : sourceCounters.entrySet()) {
                if (worldUnloading) {
                    return;
                }
                int count = entry.getValue();
                if (count > 0) {
                    enqueueReplicationUnits(entry.getKey().getItemStack(), count);
                }
            }
        }
        requestCounters.clear();
        requestCounterTicks = 0;
    }

    /** Online chamber leader or Matter Assembler on this AE2 grid (idle or busy). */
    private boolean hasAe2GridWorker() {
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return false;
        }
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit != null && unit.isFormed() && unit.isChamberLeader() && unit.getMainNode().isOnline()) {
                return true;
            }
        }
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler != null && assembler.getMainNode().isOnline()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIdleReplicationWorker() {
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return false;
        }
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit != null && unit.isChamberWorkerReady()) {
                return true;
            }
        }
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler != null && assembler.isIdle() && assembler.getMainNode().isOnline()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Best-effort: push craft-buffer matter into AE2 cells. Leftovers stay in the craft buffer
     * so {@link #drainMatterForCraft} can still see them — never spill to physical tanks here
     * (that path was used by the Replication fallback).
     */
    private void parkCraftBufferPreferAe2KeepRest() {
        MatterNetwork network = getNetworkSafe();
        for (CraftMatterTank craftTank : getOrCreateCraftMatterTanks()) {
            if (craftTank.getMatterAmount() < 1.0) {
                continue;
            }
            flushCraftTankIntoAe2(craftTank, craftTank.getMatterType(), network);
        }
        setChanged();
        if (mainNode != null && mainNode.isReady()) {
            IStorageProvider.requestUpdate(mainNode);
        }
    }

    /**
     * After AE2 cancel (vanilla or AdvancedAE), no CPU remains busy for an item — abort chamber/MA
     * jobs and clear pending/prefill so work does not keep running.
     */
    private void pruneOrphanedAe2InternalWork() {
        if (worldUnloading || mainNode.getGrid() == null) {
            return;
        }
        IGrid grid = mainNode.getGrid();
        boolean aborted = false;

        for (ItemWithSourceId key : new ArrayList<>(ae2InternalPending.keySet())) {
            if (peekAe2CraftOutputAmount(key.getItemStack().getItem()) <= 0) {
                ae2InternalPending.remove(key);
                aborted = true;
            }
        }

        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit == null || unit.isIdle() || !worldPosition.equals(unit.getBridgePos())) {
                continue;
            }
            // Only AE2-internal jobs (no Replication task id).
            if (unit.getReplicationTaskId() != null) {
                continue;
            }
            Item item = unit.getCraftingStack().getItem();
            if (peekAe2CraftOutputAmount(item) <= 0) {
                unit.abortJob();
                aborted = true;
            }
        }
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler == null || assembler.isIdle() || !worldPosition.equals(assembler.getBridgePos())) {
                continue;
            }
            if (assembler.getReplicationTaskId() != null) {
                continue;
            }
            Item item = assembler.getCraftingStack().getItem();
            if (peekAe2CraftOutputAmount(item) <= 0) {
                assembler.abortJob();
                aborted = true;
            }
        }

        // Same as cancelAe2Linked: park leftover absorb-buffer matter back into AE2 cells.
        if (aborted) {
            recoverCraftBufferIntoAe2();
        }
    }

    /** Moves any leftover craft-buffer matter into AE2 cells (cancel / orphan prune). */
    private void recoverCraftBufferIntoAe2() {
        for (CraftMatterTank tank : getOrCreateCraftMatterTanks()) {
            double amount = tank.getMatterAmount();
            if (amount <= 0) {
                continue;
            }
            long moved = insertAe2Matter(tank.getMatterType(), (long) Math.floor(amount), false);
            if (moved > 0) {
                tank.drain(
                        (double) moved,
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            }
        }
        setChanged();
        if (mainNode != null && mainNode.isReady()) {
            IStorageProvider.requestUpdate(mainNode);
        }
    }

    /**
     * Feeds {@link #ae2InternalPending} to chambers/MAs: top-up running jobs first (no idle flash),
     * then start idle workers with the full pending pile.
     * Only real {@code pushPattern} units — each output must match an AE2 {@code waitingFor} slot.
     * Matter is drained per crafted unit. Never creates {@link ReplicationTask} entries.
     */
    private void feedAe2InternalJobs() {
        if (ae2InternalPending.isEmpty() || worldUnloading) {
            return;
        }
        if (mainNode.getGrid() == null || getNetworkSafe() == null) {
            return;
        }

        for (ItemWithSourceId key : new ArrayList<>(ae2InternalPending.keySet())) {
            if (worldUnloading) {
                break;
            }
            Integer boxed = ae2InternalPending.get(key);
            if (boxed == null || boxed <= 0) {
                ae2InternalPending.remove(key);
                continue;
            }
            ItemStack patternStack = key.getItemStack();
            int pending = boxed;

            // 1) Idle workers first — otherwise the first RUNNING chamber eats the whole backlog
            // and the second chamber stays purple / the first flashes between batches.
            if (hasIdleReplicationWorker()) {
                int rem = dispatchAe2InternalDeferred(patternStack, pending);
                pending = rem;
            }
            // 2) Split any remainder evenly across already-RUNNING same-item workers.
            if (pending > 0) {
                pending -= topUpRunningAe2InternalWorkers(patternStack, pending);
            }
            if (pending <= 0) {
                ae2InternalPending.remove(key);
            } else {
                ae2InternalPending.put(key, pending);
            }
        }
    }

    /**
     * Append {@code count} split across all same-item AE2-internal chamber/MA jobs.
     * Returns how many units were applied.
     */
    private int topUpRunningAe2InternalWorkers(ItemStack patternStack, int count) {
        if (count <= 0) {
            return 0;
        }
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return 0;
        }
        java.util.List<ReplicationUnitBlockEntity> chambers = new java.util.ArrayList<>();
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit != null && !unit.isIdle() && unit.isChamberLeader()
                    && worldPosition.equals(unit.getBridgePos())
                    && unit.getCraftingStack().getItem().equals(patternStack.getItem())
                    && unit.getReplicationTaskId() == null) {
                chambers.add(unit);
            }
        }
        java.util.List<MatterAssemblerBlockEntity> assemblers = new java.util.ArrayList<>();
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler != null && !assembler.isIdle()
                    && worldPosition.equals(assembler.getBridgePos())
                    && assembler.getCraftingStack().getItem().equals(patternStack.getItem())
                    && assembler.getReplicationTaskId() == null) {
                assemblers.add(assembler);
            }
        }

        int left = count;
        for (int i = 0; i < chambers.size() && left > 0; i++) {
            int workersLeft = chambers.size() - i;
            int batch = (left + workersLeft - 1) / workersLeft;
            ReplicationUnitBlockEntity chamber = chambers.get(i);
            int before = chamber.getRemainingCount();
            if (chamber.extendAe2InternalJob(patternStack, batch, worldPosition)) {
                left -= chamber.getRemainingCount() - before;
            }
        }
        for (int i = 0; i < assemblers.size() && left > 0; i++) {
            int workersLeft = assemblers.size() - i;
            int batch = (left + workersLeft - 1) / workersLeft;
            MatterAssemblerBlockEntity assembler = assemblers.get(i);
            int before = assembler.getRemainingCount();
            if (assembler.extendAe2InternalJob(patternStack, batch, worldPosition)) {
                left -= assembler.getRemainingCount() - before;
            }
        }
        return count - left;
    }

    /**
     * Largest AE2 crafting-CPU requested output amount for {@code item} (0 if none busy with it).
     */
    private long peekAe2CraftOutputAmount(Item item) {
        IGrid grid = mainNode.getGrid();
        if (grid == null || item == null) {
            return 0;
        }
        long best = 0;
        for (ICraftingCPU cpu : grid.getCraftingService().getCpus()) {
            if (cpu == null || !cpu.isBusy()) {
                continue;
            }
            var status = cpu.getJobStatus();
            if (status == null || status.crafting() == null) {
                continue;
            }
            if (status.crafting().what() instanceof AEItemKey key && key.getItem().equals(item)) {
                best = Math.max(best, status.crafting().amount());
            }
        }
        return best;
    }

    /**
     * Assign AE2-internal work without draining matter upfront (drained per unit while crafting).
     */
    private int dispatchAe2InternalDeferred(ItemStack patternStack, int count) {
        if (count <= 0 || patternStack.isEmpty()) {
            return count;
        }
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return count;
        }
        int remaining = count;

        java.util.List<ReplicationUnitBlockEntity> idleChambers = new java.util.ArrayList<>();
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit != null && unit.isChamberWorkerReady()) {
                idleChambers.add(unit);
            }
        }
        for (int i = 0; i < idleChambers.size() && remaining > 0; i++) {
            ReplicationUnitBlockEntity chamber = idleChambers.get(i);
            int leftWorkers = idleChambers.size() - i;
            int batch = (remaining + leftWorkers - 1) / leftWorkers;
            if (batch <= 0) {
                break;
            }
            if (chamber.tryStartJob(patternStack, batch, worldPosition, null)) {
                remaining -= batch;
            } else {
                break;
            }
        }
        if (remaining <= 0) {
            return 0;
        }

        java.util.List<MatterAssemblerBlockEntity> idle = new java.util.ArrayList<>();
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler != null && assembler.isIdle() && assembler.getMainNode().isOnline()) {
                idle.add(assembler);
            }
        }
        idle.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        for (int i = 0; i < idle.size() && remaining > 0; i++) {
            MatterAssemblerBlockEntity assembler = idle.get(i);
            int leftWorkers = idle.size() - i;
            int batch = (remaining + leftWorkers - 1) / leftWorkers;
            if (batch <= 0) {
                break;
            }
            if (assembler.tryStartJob(patternStack, batch, worldPosition, null)) {
                remaining -= batch;
            } else {
                break;
            }
        }
        return remaining;
    }

    /**
     * Drains one craft's worth of matter for an AE2-internal chamber/MA unit (taskId null path).
     */
    public boolean drainOneMatterForAe2Internal(ItemStack patternStack) {
        MatterNetwork network = getNetworkSafe();
        if (network == null || patternStack == null || patternStack.isEmpty()) {
            return false;
        }
        return drainMatterForCraft(network, patternStack, 1);
    }

    /**
     * Starts Replication/Assembler work for {@code count} units. Creates a single pending
     * ReplicationTask for the remainder (merging idle same-item tasks from this bridge).
     */
    private void enqueueReplicationUnits(ItemStack patternStack, int count) {
        if (count <= 0 || patternStack.isEmpty()) {
            return;
        }
        MatterNetwork network = getNetworkSafe();
        if (network == null) {
            return;
        }

        // Fold leftover of idle same-item tasks into one larger job per resource.
        Map<String, IReplicationTask> pending = network.getTaskManager().getPendingTasks();
        int mergeLeft = 0;
        List<String> mergeIds = new ArrayList<>();
        for (Map.Entry<String, IReplicationTask> entry : pending.entrySet()) {
            IReplicationTask existing = entry.getValue();
            if (existing == null
                    || !this.worldPosition.equals(existing.getSource())
                    || existing.getMode() != IReplicationTask.Mode.MULTIPLE
                    || !existing.getReplicatingStack().getItem().equals(patternStack.getItem())
                    || !existing.getReplicatorsOnTask().isEmpty()) {
                continue;
            }
            int left = existing.getTotalAmount() - existing.getCurrentAmount();
            if (left <= 0) {
                continue;
            }
            mergeLeft += left;
            mergeIds.add(entry.getKey());
        }
        UUID sourceId = this.blockId;
        for (String id : mergeIds) {
            pending.remove(id);
            Map<String, TaskSourceInfo> sourceTasks = activeTasks.get(sourceId);
            if (sourceTasks != null) {
                sourceTasks.remove(id);
            }
        }
        count += mergeLeft;

        int remaining = dispatchToMatterAssemblers(patternStack, count);
        if (remaining <= 0) {
            return;
        }

        // Prefer AE2-internal queue over terminal tasks whenever a chamber/MA exists.
        if (hasAe2GridWorker()) {
            ae2InternalPending.merge(new ItemWithSourceId(patternStack, this.blockId), remaining, Integer::sum);
            return;
        }

        ReplicationTask task = new ReplicationTask(
                patternStack,
                remaining,
                IReplicationTask.Mode.MULTIPLE,
                this.worldPosition,
                false
        );
        String taskId = task.getUuid().toString();
        pending.put(taskId, task);

        TaskSourceInfo info = new TaskSourceInfo(patternStack, sourceId);
        Map<String, TaskSourceInfo> sourceTasks = activeTasks.getOrDefault(sourceId, new HashMap<>());
        sourceTasks.put(taskId, info);
        activeTasks.put(sourceId, sourceTasks);

        Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.getOrDefault(sourceId, new HashMap<>());
        sourceRequests.put(patternStack, sourceRequests.getOrDefault(patternStack, 0) + remaining);
        patternRequestsBySource.put(sourceId, sourceRequests);

        Map<ItemStack, Integer> globalRequests = patternRequests.getOrDefault(sourceId, new HashMap<>());
        globalRequests.put(patternStack, globalRequests.getOrDefault(patternStack, 0) + remaining);
        patternRequests.put(sourceId, globalRequests);

        if (level instanceof ServerLevel serverLevel) {
            network.onTaskValueChanged(task, serverLevel);
        }
    }

    /**
     * Cancels Replication tasks / assembler jobs sourced by this bridge and returns any
     * leftover craft-buffer matter into AE2 cells. Does not touch AE2 CPU refund (vanilla).
     */
    public void cancelAe2LinkedReplicationWork() {
        MatterNetwork network = getNetworkSafe();
        if (network != null) {
            Map<String, IReplicationTask> pending = network.getTaskManager().getPendingTasks();
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, IReplicationTask> entry : pending.entrySet()) {
                IReplicationTask task = entry.getValue();
                if (task != null && this.worldPosition.equals(task.getSource())) {
                    toRemove.add(entry.getKey());
                    for (long replicatorPos : new HashSet<>(task.getReplicatorsOnTask())) {
                        BlockPos pos = BlockPos.of(replicatorPos);
                        if (level != null) {
                            BlockEntity be = level.getBlockEntity(pos);
                            if (be instanceof MatterAssemblerBlockEntity assembler) {
                                assembler.abortJob();
                            } else if (be instanceof ReplicationUnitBlockEntity chamber) {
                                chamber.abortJob();
                            }
                        }
                    }
                }
            }
            for (String id : toRemove) {
                pending.remove(id);
            }
        }

        IGrid grid = mainNode.getGrid();
        if (grid != null) {
            for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
                if (assembler != null && worldPosition.equals(assembler.getBridgePos())) {
                    assembler.abortJob();
                }
            }
            for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
                if (unit != null && worldPosition.equals(unit.getBridgePos())) {
                    unit.abortJob();
                }
            }
        }

        // Recover our absorb buffer into AE2 storage (not an AE2 CPU refund path).
        recoverCraftBufferIntoAe2();

        activeTasks.remove(this.blockId);
        patternRequests.remove(this.blockId);
        patternRequestsBySource.remove(this.blockId);
        requestCounters.remove(this.blockId);
        ae2InternalPending.clear();
    }

    @Nullable
    private CraftMatterTank findCraftMatterTank(IMatterType type) {
        for (CraftMatterTank tank : getOrCreateCraftMatterTanks()) {
            if (tank.getMatterType().equals(type)) {
                return tank;
            }
        }
        return null;
    }

    public long getAe2MatterAmount(IMatterType type) {
        ReplicationMatterKey key = ReplicationMatterKey.of(type);
        if (key == null || !mainNode.isReady()) {
            return 0;
        }
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return 0;
        }
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return 0;
        }
        var stacks = storage.getInventory().getAvailableStacks();
        long amount = stacks.get(key);
        if (amount > 0) {
            return amount;
        }
        // Fallback if KeyCounter primary-key identity ever mismatches equals().
        long sum = 0;
        for (var entry : stacks) {
            if (key.equals(entry.getKey())) {
                sum += entry.getLongValue();
            }
        }
        return Math.max(0, sum);
    }

    public long getAe2MatterFreeSpace(IMatterType type) {
        ReplicationMatterKey key = ReplicationMatterKey.of(type);
        if (key == null || !mainNode.isReady()) {
            return 0;
        }
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return 0;
        }
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return 0;
        }
        // Probe how much AE2 would accept (capped to avoid huge creative probes).
        long probe = 1_000_000_000L;
        return Math.max(0, storage.getInventory().insert(key, probe, Actionable.SIMULATE, new MachineSource(this)));
    }

    public long insertAe2Matter(IMatterType type, long amount, boolean simulate) {
        ReplicationMatterKey key = ReplicationMatterKey.of(type);
        if (key == null || amount <= 0 || !mainNode.isReady()) {
            return 0;
        }
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return 0;
        }
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return 0;
        }
        return storage.getInventory().insert(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                new MachineSource(this));
    }

    public long extractAe2Matter(IMatterType type, long amount, boolean simulate) {
        ReplicationMatterKey key = ReplicationMatterKey.of(type);
        if (key == null || amount <= 0 || !mainNode.isReady()) {
            return 0;
        }
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return 0;
        }
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return 0;
        }
        return storage.getInventory().extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                new MachineSource(this));
    }

    public void notifyAe2MatterChanged(IMatterType type) {
        scheduleReplicationTankSync(type);
        if (mainNode != null && mainNode.isReady()) {
            IStorageProvider.requestUpdate(mainNode);
        }
    }

    /**
     * Queues a Replication terminal matter sync instead of calling {@code onTankValueChanged}
     * immediately (that path recalculates + packets every open terminal).
     */
    private void scheduleReplicationTankSync(@Nullable IMatterType type) {
        if (type == null || type.equals(MatterType.EMPTY)) {
            return;
        }
        dirtyReplicationMatterTypes.add(type);
    }

    private void flushReplicationTankSync(boolean force) {
        if (dirtyReplicationMatterTypes.isEmpty()) {
            replicationTankSyncTicks = 0;
            return;
        }
        if (!force) {
            replicationTankSyncTicks++;
            if (replicationTankSyncTicks < REPLICATION_TANK_SYNC_INTERVAL) {
                return;
            }
        }
        replicationTankSyncTicks = 0;
        MatterNetwork network = getNetworkSafe();
        if (network == null) {
            dirtyReplicationMatterTypes.clear();
            return;
        }
        for (IMatterType type : dirtyReplicationMatterTypes) {
            network.onTankValueChanged(type);
        }
        dirtyReplicationMatterTypes.clear();
    }

    @Nullable
    private MatterNetwork getNetworkSafe() {
        try {
            return getNetwork();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Matter visible to AE2 craft from external Replication tanks only.
     * The craft absorb buffer is Replication-only ({@link #getTanks()}) — listing it in AE2
     * made leftover buffer matter look like "still crafting" network stock.
     */
    private long calculateExternalMatterAmount(IMatterType matterType) {
        MatterNetwork network = getNetworkSafe();
        if (network == null) {
            return 0;
        }
        long amount = 0;
        for (NetworkElement element : network.getMatterStacksHolders()) {
            if (!element.getLevel().isLoaded(element.getPos())) {
                continue;
            }
            if (element.getPos().equals(worldPosition)) {
                continue;
            }
            BlockEntity tile = element.getLevel().getBlockEntity(element.getPos());
            if (tile instanceof IMatterTanksSupplier tanksSupplier) {
                for (IMatterTank tank : tanksSupplier.getTanks()) {
                    if (!tank.getMatter().isEmpty() && tank.getMatter().getMatterType().equals(matterType)) {
                        amount += (long) tank.getMatterAmount();
                    }
                }
            }
        }
        return amount;
    }

    /**
     * Drains external Replication tanks for AE2 extract. Never drains the craft buffer or
     * {@link Ae2MatterTank} (buffer is for Replication; cells are extracted by cell mounts).
     */
    private long drainExternalMatter(IMatterType matterType, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        MatterNetwork network = getNetworkSafe();
        if (network == null) {
            return 0;
        }
        long remaining = amount;
        long drainedTotal = 0;

        for (NetworkElement element : network.getMatterStacksHolders()) {
            if (remaining <= 0) {
                break;
            }
            if (!element.getLevel().isLoaded(element.getPos()) || element.getPos().equals(worldPosition)) {
                continue;
            }
            BlockEntity tile = element.getLevel().getBlockEntity(element.getPos());
            if (!(tile instanceof IMatterTanksSupplier tanksSupplier)) {
                continue;
            }
            for (IMatterTank tank : tanksSupplier.getTanks()) {
                if (remaining <= 0) {
                    break;
                }
                var content = tank.getMatter();
                if (content.isEmpty() || !content.getMatterType().equals(matterType)) {
                    continue;
                }
                var drained = tank.drain(
                        new com.buuz135.replication.api.matter_fluid.MatterStack(matterType, remaining),
                        simulate
                                ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                long got = (long) Math.floor(drained.getAmount());
                if (got > 0) {
                    drainedTotal += got;
                    remaining -= got;
                }
            }
        }

        if (!simulate && drainedTotal > 0) {
            setChanged();
            scheduleReplicationTankSync(matterType);
        }
        return drainedTotal;
    }

    public void onMatterAssemblerCompletedUnit(UUID taskId, BlockPos assemblerPos) {
        if (level == null || level.isClientSide() || taskId == null) {
            return;
        }
        MatterNetwork network = getNetworkSafe();
        if (network == null) {
            return;
        }
        IReplicationTask task = network.getTaskManager().getPendingTasks().get(taskId.toString());
        if (task == null) {
            return;
        }
        task.finalizeReplication(level, assemblerPos, network);
        if (level instanceof ServerLevel serverLevel) {
            network.onTaskValueChanged(task, serverLevel);
        }
    }

    /**
     * Lets idle Matter Assemblers claim pending Replication terminal tasks (like Replicators).
     * <p>
     * Replication's {@code findTaskForReplicator} budgets parallel slots from
     * {@code network.getReplicators()} only — Matter Assemblers are invisible there, so a
     * network with assemblers but no replicators would only allow one worker per MULTIPLE task.
     * We mirror that formula with assembler count included.
     */
    private void assignReplicationTasksToAssemblers() {
        MatterNetwork network = getNetworkSafe();
        IGrid grid = mainNode.getGrid();
        if (network == null || grid == null || level == null || level.isClientSide()) {
            return;
        }

        List<ReplicationUnitBlockEntity> idleChambers = new ArrayList<>();
        int onlineChamberLeaders = 0;
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit == null || !unit.isFormed() || !unit.isChamberLeader() || !unit.getMainNode().isOnline()) {
                continue;
            }
            onlineChamberLeaders++;
            if (unit.isIdle()) {
                idleChambers.add(unit);
            }
        }

        List<MatterAssemblerBlockEntity> onlineAssemblers = new ArrayList<>();
        List<MatterAssemblerBlockEntity> idle = new ArrayList<>();
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler == null || !assembler.getMainNode().isOnline()) {
                continue;
            }
            onlineAssemblers.add(assembler);
            if (assembler.isIdle()) {
                idle.add(assembler);
            }
        }
        if (idle.isEmpty() && idleChambers.isEmpty()) {
            return;
        }
        idle.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        int maxWorkersPerMultipleTask = computeAssemblerReplicatorBudget(
                network, onlineAssemblers.size() + onlineChamberLeaders);

        // Chambers first — assign large batches (not 1) so Replication UI crafts match chamber speed.
        java.util.Map<String, Integer> chamberClaimed = new java.util.HashMap<>();
        for (int ci = 0; ci < idleChambers.size(); ci++) {
            ReplicationUnitBlockEntity chamber = idleChambers.get(ci);
            IReplicationTask task = findReplicationTaskForAssembler(
                    network, chamber.getBlockPos(), maxWorkersPerMultipleTask);
            if (task == null) {
                continue;
            }
            ItemStack pattern = task.getReplicatingStack();
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            String taskKey = task.getUuid().toString();
            int left = task.getTotalAmount() - task.getCurrentAmount() - chamberClaimed.getOrDefault(taskKey, 0);
            if (left <= 0) {
                continue;
            }
            int chambersLeft = idleChambers.size() - ci;
            int batch = Math.max(1, (left + chambersLeft - 1) / chambersLeft);
            if (!chamber.tryStartJob(pattern, batch, worldPosition, task.getUuid())) {
                continue;
            }
            chamberClaimed.merge(taskKey, batch, Integer::sum);
            task.acceptReplicator(chamber.getBlockPos());
            task.storeMatterStacksFor(level, chamber.getBlockPos(), network);
            if (!task.getStoredMatterStack().containsKey(chamber.getBlockPos().asLong())) {
                task.getReplicatorsOnTask().remove(chamber.getBlockPos().asLong());
                chamberClaimed.merge(taskKey, -batch, Integer::sum);
                chamber.abortJob();
            }
        }

        for (MatterAssemblerBlockEntity assembler : idle) {
            IReplicationTask task = findReplicationTaskForAssembler(
                    network, assembler.getBlockPos(), maxWorkersPerMultipleTask);
            if (task == null) {
                continue;
            }
            ItemStack pattern = task.getReplicatingStack();
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }

            // Reserve assembler first, then drain matter into the task (Replication holder path).
            if (!assembler.tryStartJob(pattern, 1, worldPosition, task.getUuid())) {
                continue;
            }
            task.acceptReplicator(assembler.getBlockPos());
            task.storeMatterStacksFor(level, assembler.getBlockPos(), network);
            if (!task.getStoredMatterStack().containsKey(assembler.getBlockPos().asLong())) {
                // Could not reserve matter — abort job and release claim.
                task.getReplicatorsOnTask().remove(assembler.getBlockPos().asLong());
                assembler.abortJob();
            }
        }
    }

    /**
     * Same budget math as ReplicationTaskManager.findTaskForReplicator, but counting
     * online Matter Assemblers together with real Replicators.
     */
    private static int computeAssemblerReplicatorBudget(MatterNetwork network, int onlineAssemblerCount) {
        int workers = network.getReplicators().size() + Math.max(0, onlineAssemblerCount);
        long multipleTasks = 0;
        for (IReplicationTask pending : network.getTaskManager().getPendingTasks().values()) {
            if (pending.getMode() == IReplicationTask.Mode.MULTIPLE) {
                multipleTasks++;
            }
        }
        long reservedForSingle = network.getTaskManager().getPendingTasks().size() - multipleTasks;
        workers -= (int) reservedForSingle;
        if (multipleTasks == 0) {
            multipleTasks = 1;
        }
        return Math.max(1, (int) Math.ceil(workers / (double) multipleTasks));
    }

    @Nullable
    private static IReplicationTask findReplicationTaskForAssembler(
            MatterNetwork network, BlockPos assemblerPos, int maxWorkersPerMultipleTask) {
        for (IReplicationTask task : network.getTaskManager().getPendingTasks().values()) {
            if (task.canAcceptReplicator(assemblerPos, maxWorkersPerMultipleTask)) {
                return task;
            }
        }
        return null;
    }

    // ISubMenuHost implementation
    @Override
    public ItemStack getMainMenuIcon() {
        return ModBlocks.REPAE2BRIDGE.get().asItem().getDefaultInstance();
    }

    @Override
    public void returnToMainMenu(Player player, appeng.menu.ISubMenu subMenu) {
        if (player instanceof ServerPlayer serverPlayer) {
            appeng.menu.MenuOpener.open(net.unfamily.repae2bridge.menu.BridgeMenu.TYPE, serverPlayer,
                    appeng.menu.locator.MenuLocators.forBlockEntity(this), true);
        }
    }

    /**
     * Opens the bridge GUI (energy-feed toggle).
     */
    public void openGui(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            appeng.menu.MenuOpener.open(net.unfamily.repae2bridge.menu.BridgeMenu.TYPE, serverPlayer,
                    appeng.menu.locator.MenuLocators.forBlockEntity(this));
        }
    }

    public boolean isFeedReplicationEnergy() {
        return feedReplicationEnergy;
    }

    public void setFeedReplicationEnergy(boolean feedReplicationEnergy) {
        this.feedReplicationEnergy = feedReplicationEnergy;
        setChanged();
    }

    public void toggleFeedReplicationEnergy() {
        setFeedReplicationEnergy(!feedReplicationEnergy);
    }

    /**
     * Tracks matter creation by monitoring changes in matter amounts within the network
     */
    private void trackMatterCreation() {
        // Only check every 20 ticks (1 second) to avoid excessive calculations
        matterTrackingTickCounter++;
        if (matterTrackingTickCounter % 20 != 0) {
            return;
        }

        // Additional safety check - should not be called if not properly initialized
        if (initialized != 1 || !isActive()) {
            return;
        }

        MatterNetwork network = getNetwork();
        if (network == null) {
            // Reset initialization flag if we lose network connection
            matterTrackingInitialized = false;
            return;
        }

        // Initialize previous amounts on first connection to network
        if (!matterTrackingInitialized) {
            for (IMatterType matterType : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
                long currentAmount = network.calculateMatterAmount(matterType);
                previousMatterAmounts.put(matterType, currentAmount);
            }
            matterTrackingInitialized = true;

            if (globalShouldLogDebug) {
                LOGGER.debug("Bridge at {}: Matter tracking initialized with current network amounts", worldPosition);
            }
            return;
        }

        // Check all matter types for amount changes (skip EMPTY)
        for (IMatterType matterType : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
            if (matterType == null || matterType.equals(MatterType.EMPTY)) {
                continue;
            }
            long currentAmount = network.calculateMatterAmount(matterType);
            long previousAmount = previousMatterAmounts.getOrDefault(matterType, 0L);

            if (currentAmount > previousAmount) {
                long createdAmount = currentAmount - previousAmount;

                onMatterCreated(matterType, createdAmount);

                if (globalShouldLogDebug) {
                    LOGGER.debug("Bridge at {}: Detected matter creation - {}: {} units created (total: {})",
                        worldPosition, matterType.getName(), createdAmount, currentAmount);
                }
            }

            previousMatterAmounts.put(matterType, currentAmount);
        }
    }

    private void onMatterCreated(IMatterType matterType, long amount) {
        if (Config.enableDebugLogging) {
            LOGGER.info("Bridge at {}: Matter created from disintegrator - {}: {} units",
                worldPosition, matterType.getName(), amount);
        }
        if (mainNode != null && mainNode.isReady()) {
            IStorageProvider.requestUpdate(mainNode);
        }
    }

    /**
     * Dynamic AE → FE feed: demand = free space in the matter network energy buffer;
     * supply = what the AE2 grid can spare. Uses {@link appeng.api.config.PowerUnit} FE ratio.
     * Skips when the grid is energy-starved (cannot cover idle demand).
     */
    private void feedEnergyToMatterNetwork() {
        if (!feedReplicationEnergy || !isActive()) {
            return;
        }
        MatterNetwork network = getNetwork();
        IGrid grid = mainNode.getGrid();
        if (network == null || grid == null) {
            return;
        }

        var energyStorage = network.getEnergyStorage();
        int freeFe = energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored();
        if (freeFe <= 0) {
            return;
        }

        var energyService = grid.getEnergyService();
        // If the grid cannot cover its own idle load, do not siphon power into Replication.
        double stored = energyService.getStoredPower();
        double idle = energyService.getIdlePowerUsage();
        if (stored <= idle) {
            return;
        }

        // AE needed to fill free FE, using AE2's configured FE conversion ratio.
        double aeWanted = appeng.api.config.PowerUnit.FE.convertTo(appeng.api.config.PowerUnit.AE, freeFe);
        if (aeWanted <= 0) {
            return;
        }
        // Never drain below idle reserve.
        double aeBudget = Math.min(aeWanted, stored - idle);
        if (aeBudget <= 0) {
            return;
        }

        double simulated = energyService.extractAEPower(
                aeBudget, Actionable.SIMULATE, appeng.api.config.PowerMultiplier.CONFIG);
        if (simulated <= 0) {
            return;
        }

        double feSim = appeng.api.config.PowerUnit.AE.convertTo(appeng.api.config.PowerUnit.FE, simulated);
        int accepted = energyStorage.receiveEnergy((int) Math.floor(feSim), true);
        if (accepted <= 0) {
            return;
        }

        double aeToExtract = appeng.api.config.PowerUnit.FE.convertTo(appeng.api.config.PowerUnit.AE, accepted);
        double actuallyExtracted = energyService.extractAEPower(
                aeToExtract, Actionable.MODULATE, appeng.api.config.PowerMultiplier.CONFIG);
        int fed = (int) Math.floor(
                appeng.api.config.PowerUnit.AE.convertTo(appeng.api.config.PowerUnit.FE, actuallyExtracted));
        if (fed > 0) {
            energyStorage.receiveEnergy(fed, false);
        }
    }

    /**
     * Prefer online Matter Assemblers on this AE2 grid. Returns leftover count for ReplicationTask fallback.
     * Matter for assembler jobs is drained from the MatterNetwork tanks immediately.
     */
    private int dispatchToMatterAssemblers(ItemStack patternStack, int count) {
        if (count <= 0 || patternStack.isEmpty()) {
            return count;
        }
        IGrid grid = mainNode.getGrid();
        MatterNetwork network = getNetwork();
        if (grid == null || network == null) {
            return count;
        }

        int remaining = count;

        // Prefer formed chamber leaders (extremely fast).
        java.util.List<ReplicationUnitBlockEntity> idleChambers = new java.util.ArrayList<>();
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit != null && unit.isChamberWorkerReady()) {
                idleChambers.add(unit);
            }
        }
        for (int i = 0; i < idleChambers.size() && remaining > 0; i++) {
            ReplicationUnitBlockEntity chamber = idleChambers.get(i);
            int left = idleChambers.size() - i;
            int batch = (remaining + left - 1) / left;
            batch = maxCraftableFromMatter(network, patternStack, batch);
            if (batch <= 0) {
                break;
            }
            if (!drainMatterForCraft(network, patternStack, batch)) {
                break;
            }
            if (chamber.tryStartJob(patternStack, batch, worldPosition, null)) {
                remaining -= batch;
            } else {
                break;
            }
        }
        if (remaining <= 0) {
            return 0;
        }

        java.util.List<MatterAssemblerBlockEntity> idle = new java.util.ArrayList<>();
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler != null && assembler.isIdle() && assembler.getMainNode().isOnline()) {
                idle.add(assembler);
            }
        }
        if (idle.isEmpty()) {
            return remaining;
        }

        // Higher priority assemblers receive jobs first; split the batch so they run in parallel.
        idle.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (int i = 0; i < idle.size() && remaining > 0; i++) {
            MatterAssemblerBlockEntity assembler = idle.get(i);
            int assemblersLeft = idle.size() - i;
            // Ceil-split remaining work across the remaining idle assemblers.
            int batch = (remaining + assemblersLeft - 1) / assemblersLeft;
            batch = maxCraftableFromMatter(network, patternStack, batch);
            if (batch <= 0) {
                break;
            }
            if (!drainMatterForCraft(network, patternStack, batch)) {
                break;
            }
            if (assembler.tryStartJob(patternStack, batch, worldPosition)) {
                remaining -= batch;
            } else {
                // Refund is not trivial; leave matter drained (same as failed replicator edge-case)
                break;
            }
        }
        return remaining;
    }

    /** Largest craft count {@code desired} that current matter stocks can cover. */
    private int maxCraftableFromMatter(MatterNetwork network, ItemStack patternStack, int desired) {
        if (desired <= 0 || patternStack.isEmpty()) {
            return 0;
        }
        var matterCompound = ReplicationCalculation.getMatterCompound(patternStack);
        if (matterCompound == null) {
            return desired;
        }
        int max = desired;
        for (MatterValue matterValue : matterCompound.getValues().values()) {
            long per = (long) Math.ceil(matterValue.getAmount());
            if (per <= 0) {
                continue;
            }
            long avail = availableMatterForCraft(network, matterValue.getMatter());
            long can = avail / per;
            if (can <= 0) {
                return 0;
            }
            if (can < max) {
                max = can > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) can;
            }
        }
        return Math.max(0, max);
    }

    private boolean drainMatterForCraft(MatterNetwork network, ItemStack patternStack, int count) {
        var matterCompound = ReplicationCalculation.getMatterCompound(patternStack);
        if (matterCompound == null) {
            return true;
        }

        // Prefer live AE2 extract-simulate over calculateMatterAmount (available-stacks cache
        // often lags right after pushPattern insert, which forced the ReplicationTask path).
        for (MatterValue matterValue : matterCompound.getValues().values()) {
            long needed = (long) Math.ceil(matterValue.getAmount()) * count;
            if (availableMatterForCraft(network, matterValue.getMatter()) < needed) {
                return false;
            }
        }

        for (MatterValue matterValue : matterCompound.getValues().values()) {
            double needed = Math.ceil(matterValue.getAmount()) * count;
            double left = needed;
            left = drainMatterTypeFromHolders(network, matterValue.getMatter(), left, true);
            if (left > 0.01) {
                return false;
            }
        }

        if (mainNode != null && mainNode.isReady()) {
            IStorageProvider.requestUpdate(mainNode);
        }
        return true;
    }

    /**
     * Matter available for chamber/MA jobs: craft buffer + physical tanks + live AE2 extract.
     */
    private long availableMatterForCraft(MatterNetwork network, IMatterType type) {
        long total = 0;
        boolean countedAe2 = false;
        for (NetworkElement holderEl : network.getMatterStacksHolders()) {
            if (!holderEl.getLevel().isLoaded(holderEl.getPos())) {
                continue;
            }
            var tile = holderEl.getLevel().getBlockEntity(holderEl.getPos());
            if (!(tile instanceof IMatterTanksSupplier supplier)) {
                continue;
            }
            for (var tank : supplier.getTanks()) {
                if (tank instanceof Ae2MatterTank ae2) {
                    if (!countedAe2 && Objects.equals(ae2.getMatterType(), type)) {
                        total += extractAe2Matter(type, Long.MAX_VALUE / 4, true);
                        countedAe2 = true;
                    }
                    continue;
                }
                var content = tank.getMatter();
                if (!content.isEmpty() && Objects.equals(content.getMatterType(), type)) {
                    total += (long) Math.floor(content.getAmount());
                }
            }
        }
        if (!countedAe2) {
            total += extractAe2Matter(type, Long.MAX_VALUE / 4, true);
        }
        return total;
    }

    private double drainMatterTypeFromHolders(
            MatterNetwork network, IMatterType type, double amount, boolean execute) {
        double left = amount;
        var action = execute
                ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE
                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        boolean drainedAe2 = false;
        for (NetworkElement holderEl : network.getMatterStacksHolders()) {
            if (left <= 0) {
                break;
            }
            if (!holderEl.getLevel().isLoaded(holderEl.getPos())) {
                continue;
            }
            var tile = holderEl.getLevel().getBlockEntity(holderEl.getPos());
            if (!(tile instanceof IMatterTanksSupplier supplier)) {
                continue;
            }
            for (var tank : supplier.getTanks()) {
                if (left <= 0) {
                    break;
                }
                if (tank instanceof Ae2MatterTank ae2) {
                    if (drainedAe2 || !Objects.equals(ae2.getMatterType(), type)) {
                        continue;
                    }
                    // Live extract — avoids stale getAvailableStacks() after pushPattern insert.
                    long want = (long) Math.floor(left);
                    long got = extractAe2Matter(type, want, !execute);
                    if (got > 0) {
                        left -= got;
                        if (execute) {
                            scheduleReplicationTankSync(type);
                        }
                    }
                    drainedAe2 = true;
                    continue;
                }
                var content = tank.getMatter();
                if (content.isEmpty() || !Objects.equals(content.getMatterType(), type)) {
                    continue;
                }
                var drained = tank.drain(
                        new com.buuz135.replication.api.matter_fluid.MatterStack(type, left),
                        action);
                left -= drained.getAmount();
            }
        }
        if (left > 0.01 && !drainedAe2 && execute) {
            long want = (long) Math.floor(left);
            long got = extractAe2Matter(type, want, false);
            if (got > 0) {
                left -= got;
                scheduleReplicationTankSync(type);
            }
        }
        return left;
    }
}