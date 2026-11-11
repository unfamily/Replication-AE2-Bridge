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
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.MEStorage;

import com.buuz135.replication.block.tile.ReplicationMachine;
import com.buuz135.replication.calculation.ReplicationCalculation;
import com.buuz135.replication.network.MatterNetwork;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.network.IMatterTanksConsumer;
import com.buuz135.replication.api.network.IMatterTanksSupplier;
import com.buuz135.replication.api.MatterType;
import com.buuz135.replication.calculation.MatterValue;
import com.hrznstudio.titanium.block.BasicTileBlock;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.hrznstudio.titanium.annotation.Save;
import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.repae2bridge.block.ModBlocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.api.task.ReplicationTask;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import appeng.core.definitions.AEItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.unfamily.repae2bridge.item.ModItems;
import com.buuz135.replication.ReplicationRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import appeng.me.helpers.MachineSource;
import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.ae2.Ae2MatterTank;
import net.unfamily.repae2bridge.ae2.CraftMatterTank;
import net.unfamily.repae2bridge.ae2.IRepAe2BridgeHost;
import net.unfamily.repae2bridge.ae2.encoding.ReplicationBridgePatternDetails;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;
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
        IPriorityHost, IMatterTanksSupplier, IMatterTanksConsumer, IRepAe2BridgeHost,
        net.unfamily.repae2bridge.ae2.Ae2MatterHost, net.unfamily.repae2bridge.ae2.IAe2BridgeCraftHost {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Constant for the number of ticks before processing accumulated requests
    private static final int REQUEST_ACCUMULATION_TICKS = 100;

    // Constant for initialization delay in ticks (3 seconds = 60 ticks)
    private static final int INITIALIZATION_DELAY = 60;

    // Variable to track if the networks have been initialized
    private byte initialized = 0;

    // Counter for initialization ticks
    private int initializationTicks = 0;

    // Priority for AE2 crafting operations.
    @Save
    private int priority = 0;

    /** Matter absorbed from AE2 craft pushPattern — drained before AE2 cell mirrors. */
    @Nullable
    private List<CraftMatterTank> craftMatterTanks;

    /** Virtual tanks exposing AE2 matter cells to the Replication network. */
    @Nullable
    private List<Ae2MatterTank> ae2MatterTanks;

    private boolean ae2CraftParkPending;
    private int ae2InternalPruneCooldown;
    private static final int REQUEST_ACCUMULATION_TICKS_MIN = 5;
    private static final int REQUEST_ACCUMULATION_COUNT_FLUSH = 32;

    /** AE2-internal craft queue (never becomes a ReplicationTask). */
    private final Map<ItemWithSourceId, Integer> ae2InternalPending = new HashMap<>();
    private boolean ae2StorageUpdateDirty;
    private List<ReplicationUnitBlockEntity> cachedChamberLeaders = List.of();
    private List<MatterAssemblerBlockEntity> cachedOnlineAssemblers = List.of();
    private long ae2WorkerCacheTick = Long.MIN_VALUE;
    private static final int AE2_WORKER_CACHE_TTL = 40;
    /** One AE2 inventory scan + localHolders walk per game tick (Ae2MatterTank hits this per type). */
    private long ae2MatterViewTick = Long.MIN_VALUE;
    @Nullable
    private KeyCounter ae2StacksCache;
    @Nullable
    private List<NetworkElement> ae2LocalHoldersCache;
    private int ae2FeedCursor;
    private final Set<IMatterType> dirtyReplicationMatterTypes = new HashSet<>();
    private int replicationTankSyncTicks;
    private static final int REPLICATION_TANK_SYNC_INTERVAL = 20;

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

    // Whether the bridge feeds energy from AE2 to the Replication network
    private boolean feedReplicationEnergy = true;

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
    // Buffer for matter changes to be inserted into output inventory
    private final Map<IMatterType, Long> pendingMatterChanges = new HashMap<>();
    // MAX_MATTER_BUFFER: Now that stack sizes can be huge (Integer.MAX_VALUE / 2),
    // we set a much larger buffer. 
    // 9 slots * 2 rows * high stacksize = huge capacity for the buffer
    private static final long MAX_MATTER_BUFFER = 9L * 2L * (Integer.MAX_VALUE / 2L); // ~19 billion per slot
    // Temporary counters for crafting requests
    private final Map<UUID, Map<ItemWithSourceId, Integer>> requestCounters = new HashMap<>();
    private int requestCounterTicks = 0;

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
            // Raise the flag FIRST so ticks / neighbor updates cannot re-register during disconnect
            worldUnloading = true;

            // First time it's set to true - always log
            String dimensionInfo = level != null ? 
                " [Dimension: " + level.dimension().location() + "]" : "";
            String positionInfo = pos != null ? 
                " [Position: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]" : "";
            
            if (Config.enableDebugLogging) {
            LOGGER.info("RepAE2Bridge: World unloading detected - All bridges will now shutdown{}{}", 
                        dimensionInfo, positionInfo);
            }
            if (Config.enableDebugLogging) {
            LOGGER.info("RepAE2Bridge: Cancelling all pending operations{}{}", 
                        dimensionInfo, positionInfo);
            }
            
            // Cancel all pending operations
            cancelAllPendingOperations();
            
            // Disconnect ALL bridges from networks IMMEDIATELY
            // This prevents the NetworkManager from trying to save bridges during world unload
            if (Config.enableDebugLogging) {
            LOGGER.info("RepAE2Bridge: Disconnecting {} active bridges from AE2 and Titanium/Replication networks", activeBridges.size());
            }
            disconnectAllBridgesFromNetworks();
            // Cable bridges are not BlockEntities — disconnect them from Titanium/Replication too
            net.unfamily.repae2bridge.parts.CableRepAe2BridgePart.disconnectAllCableBridgesFromNetworks();
            
        } else if (!unloading && worldUnloading) {
            // State changed from true to false - always log
            String dimensionInfo = level != null ? 
                " [Dimension: " + level.dimension().location() + "]" : "";
            String positionInfo = pos != null ? 
                " [Position: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]" : "";
            
            if (Config.enableDebugLogging) {
            LOGGER.info("GLOBAL WORLD UNLOADING FLAG SET TO: false - Normal operation resumed{}{}", 
                       dimensionInfo, positionInfo);
            }
            worldUnloadingWarningsHidden = 0; // Reset counter
            worldUnloading = false;
        } else {
            worldUnloading = unloading;
        }
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
        return " [Dimension: " + level.dimension().location() + "] [Position: " + worldPosition.getX() + ", " + worldPosition.getY() + ", " + worldPosition.getZ() + "]";
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
                .setSlotLimit(Integer.MAX_VALUE / 2) // Set gigantic stack size for matter items
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
                if (Config.enableDebugLogging) {
                LOGGER.debug("Bridge: AE2 node already exists in onLoad, skipping creation");
                }
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
                                    // Notify the adjacent block (to trigger its connections)
                                    // Wrapped in try-catch to prevent crashes from external mods
                                    try {
                                        level.neighborChanged(neighborPos, getBlockState().getBlock(), worldPosition);
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
     * Handle when a neighboring block changes
     * Called from the RepAE2BridgeBl block
     * @param fromPos Position of the changed neighbor
     */
    public void handleNeighborChanged(BlockPos fromPos) {
        if (level != null && !level.isClientSide()) {
            // Safety check: verify both our chunk and the neighbor's chunk are loaded
            if (!level.isLoaded(worldPosition) || !level.isLoaded(fromPos)) {
                if (Config.enableDebugLogging) {
                    LOGGER.debug("Bridge{}: Skipping handleNeighborChanged - chunk not loaded", getLocationInfo());
                }
                return;
            }
            
            // If the changed block is another bridge, ignore the update
            Direction directionToNeighbor = null;
            for (Direction dir : Direction.values()) {
                if (worldPosition.relative(dir).equals(fromPos)) {
                    directionToNeighbor = dir;
                    break;
                }
            }

            if (directionToNeighbor != null && level.getBlockEntity(fromPos) instanceof RepAE2BridgeBlockEntity) {
                // Debug log disabled for production
                // LOGGER.debug("Bridge: Ignored update from another bridge at {}", fromPos);
                return;
            }

            // Check if there is an AE2 controller in the network
            boolean hasAE2Connection = hasAE2NetworkConnection();

            // If there is an AE2 connection and the node is not created, initialize the node
            if (hasAE2Connection && !nodeCreated) {
                // Check if the node has already been initialized via AE2 APIs
                boolean nodeAlreadyExists = mainNode.getNode() != null;
                
                if (!nodeAlreadyExists) {
                    try {
                        // Debug log disabled for production
                        // LOGGER.debug("Bridge: Initializing AE2 node from handleNeighborChanged");
                        mainNode.create(level, worldPosition);
                        nodeCreated = true;

                        // Notify adjacent blocks
                        forceNeighborUpdates();

                        // Update the connection state visually
                        updateConnectedState();

                        // Force a pattern update
                        // Debug log disabled for production
                        // LOGGER.debug("Bridge: Requesting AE2 pattern update");
                        ICraftingProvider.requestUpdate(mainNode);
                    }catch (Exception e) {
                        LOGGER.error("Failed to initialize AE2 node: {}", e.getMessage());
                        shouldReconnect = true;
                    }
                } else {
                    // The node already exists, only update local state
                    nodeCreated = true;
                    forceNeighborUpdates();
                    updateConnectedState();
                    ICraftingProvider.requestUpdate(mainNode);
                    if (Config.enableDebugLogging) {
                    LOGGER.debug("Bridge: AE2 node already exists in handleNeighborChanged, skipping creation");
                    }
                }
            }
            // If the node exists, update only adjacent blocks
            else if (mainNode.getNode() != null) {
                forceNeighborUpdates();
            }

            // Update the visual state
            updateConnectedState();
        }
    }

    /**
     * Explicitly disconnect this block from both networks
     * Called when the block is removed
     */
    public void disconnectFromNetworks() {
        if (Config.enableDebugLogging) {
        LOGGER.info("Bridge{}: Disconnecting from AE2 and Titanium/Replication networks", getLocationInfo());
        }
        
        // Disconnect from the AE2 network
        if (level != null && !level.isClientSide() && mainNode != null) {
            try {
                mainNode.destroy();
                nodeCreated = false;
                if (Config.enableDebugLogging) {
                LOGGER.info("Bridge{}: AE2 network disconnected", getLocationInfo());
                }
            } catch (Exception e) {
                if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge{}: Error disconnecting from AE2 network: {}", getLocationInfo(), e.getMessage());
                }
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
                        if (Config.enableDebugLogging) {
                        LOGGER.info("Bridge{}: Removing from Titanium/Replication NetworkManager", getLocationInfo());
                        }
                        networkManager.removeElement(worldPosition);
                        if (Config.enableDebugLogging) {
                        LOGGER.info("Bridge{}: Titanium/Replication network disconnected", getLocationInfo());
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (Config.enableDebugLogging) {
            LOGGER.warn("Bridge{}: Error disconnecting from Titanium/Replication network: {}", getLocationInfo(), e.getMessage());
            }
        }
        
        if (Config.enableDebugLogging) {
        LOGGER.info("Bridge{}: Network disconnection completed", getLocationInfo());
        }
    }
    
    /**
     * Disconnection of all active bridges from networks during world unloading
     * Called during world unloading to prevent hanging
     */
    private static void disconnectAllBridgesFromNetworks() {
        List<RepAE2BridgeBlockEntity> bridgesToDisconnect = new ArrayList<>(activeBridges);
        if (Config.enableDebugLogging) {
        LOGGER.info("RepAE2Bridge: Starting network disconnection for {} bridges", bridgesToDisconnect.size());
        }
        
        int disconnected = 0;
        int failed = 0;
        
        for (RepAE2BridgeBlockEntity bridge : bridgesToDisconnect) {
            try {
                if (Config.enableDebugLogging) {
                LOGGER.info("RepAE2Bridge: Disconnecting bridge at {}", bridge.getLocationInfo());
                }
                bridge.disconnectFromNetworks();
                disconnected++;
            } catch (Exception e) {
                if (Config.enableDebugLogging) {
                LOGGER.warn("RepAE2Bridge: Failed to disconnect bridge: {}", e.getMessage());
                }
                failed++;
            }
        }
        
        // Clear the registry
        activeBridges.clear();
        
        if (Config.enableDebugLogging) {
        LOGGER.info("RepAE2Bridge: Network disconnection completed - {} successful, {} failed", disconnected, failed);
        }
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
                    if (Config.enableDebugLogging) {
                    LOGGER.debug("Bridge: Destroyed existing AE2 node before recreation");
                    }
                }
                
                // Check if we're on a dedicated server
                boolean isDedicatedServer = level.getServer() != null && level.getServer().isDedicatedServer();
                
                if (isDedicatedServer) {
                    if (Config.enableDebugLogging) {
                    LOGGER.info("Bridge: Dedicated server detected, using direct node creation");
                    }
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
                        
                        if (Config.enableDebugLogging) {
                        LOGGER.info("Bridge: AE2 node successfully created and registered with the grid (direct method)");
                        }
                    } catch (Exception e) {
                        LOGGER.error("Bridge: Failed to create AE2 node with direct method: {}", e.getMessage());
                    }
                } else {
                    // Use task method only in singleplayer/integrated server
                    level.getServer().tell(new net.minecraft.server.TickTask(0, () -> {
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
                            
                            if (Config.enableDebugLogging) {
                            LOGGER.info("Bridge: AE2 node successfully created and registered with the grid");
                            }
                        } catch (Exception e) {
                            LOGGER.error("Bridge: Failed to create AE2 node during scheduled task: {}", e.getMessage());
                        }
                    }));
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
                    if (Config.enableDebugLogging) {
                    LOGGER.info("Bridge: Found active AE2 grid connection at {}", neighborPos);
                    }
                    break;
                }
            }
        }
        
        // If we found a grid but our node is not connected, force the connection
        if (connectedGrid != null && (mainNode.getNode() == null || mainNode.getNode().getGrid() == null)) {
            if (Config.enableDebugLogging) {
            LOGGER.info("Bridge: Found AE2 grid but node not connected, forcing connection");
            }
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
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
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
            super.saveAdditional(tag, registries);
            if (Config.enableDebugLogging) {
                LOGGER.error("Bridge{}: super.saveAdditional() completed", getLocationInfo());
            }
        } catch (Exception e) {
            LOGGER.error("Bridge{}: EXCEPTION in super.saveAdditional(): {}", getLocationInfo(), e.getMessage(), e);
        }
        
        // Save the state of the AE2 node
        try {
            if (Config.enableDebugLogging) {
                LOGGER.error("Bridge{}: Attempting to save AE2 node to NBT", getLocationInfo());
            }
            mainNode.saveToNBT(tag);
            if (Config.enableDebugLogging) {
                LOGGER.error("Bridge{}: AE2 node saved to NBT", getLocationInfo());
            }
        } catch (Exception e) {
            LOGGER.error("Bridge{}: EXCEPTION saving AE2 node to NBT: {}", getLocationInfo(), e.getMessage(), e);
        }
        
        // Also save the node creation flag
        tag.putBoolean("nodeCreated", nodeCreated);
        tag.putBoolean("feedReplicationEnergy", feedReplicationEnergy);
        // Save the reconnection flag
        tag.putBoolean("shouldReconnect", shouldReconnect);
        // Save the block's unique identifier
        if (blockId != null) {
            tag.putUUID("blockId", blockId);
        }
        saveCraftMatterBuffer(tag);

        if (Config.enableDebugLogging) {
            LOGGER.error("Bridge{}: saveAdditional() completed", getLocationInfo());
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Load the state of the AE2 node
        mainNode.loadFromNBT(tag);
        // Load the node creation flag
        if (tag.contains("nodeCreated")) {
            nodeCreated = tag.getBoolean("nodeCreated");
        }
        // Load the reconnection flag
        if (tag.contains("shouldReconnect")) {
            shouldReconnect = tag.getBoolean("shouldReconnect");
        }
        if (tag.contains("feedReplicationEnergy")) {
            feedReplicationEnergy = tag.getBoolean("feedReplicationEnergy");
        }
        // Load the block's unique identifier
        if (tag.contains("blockId")) {
            blockId = tag.getUUID("blockId");
        } else {
            // If no ID exists, generate a new one
            blockId = UUID.randomUUID();
        }
        loadCraftMatterBuffer(tag);
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
                if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge{}: EXITING serverTick early - world is unloading (+ {} similar warnings hidden in last 15s)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                }
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
            // Replication-terminal task assign: not every tick when idle (TPS).
            if (level.getGameTime() % 5 == 0) {
                assignReplicationTasksToAssemblers();
            }
            // Cancel detection — throttle CPU scans.
            if (--ae2InternalPruneCooldown <= 0) {
                ae2InternalPruneCooldown = 20;
                pruneOrphanedAe2InternalWork();
            }
            if (ae2CraftParkPending) {
                ae2CraftParkPending = false;
                parkCraftBufferPreferAe2KeepRest();
            }
            feedAe2InternalJobs();
            boolean craftQuiet = ae2InternalPending.isEmpty() && requestCounters.isEmpty();
            // Leftover craft-buffer matter (e.g. old Import Bus sink) → AE2 then tanks when idle.
            if (craftQuiet && activeTasks.isEmpty() && level.getGameTime() % 20 == 0 && hasCraftBufferMatter()) {
                flushCraftBufferToStorage(true);
            }
            flushReplicationTankSync(craftQuiet);
            flushAe2StorageUpdate(craftQuiet);
        }

        // POST-SUPER CHECK: Verify if the world has started unloading during super.serverTick()
        if (worldUnloading) {
            if (worldUnloadingWarningsHidden == WARNING_THROTTLE_INTERVAL) {
                if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge{}: World unloading detected after super.serverTick() - exiting immediately (+ {} similar warnings hidden)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                }
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
                if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge{}: WORLD UNLOADING DETECTED - entering cleanup mode (+ {} similar warnings hidden)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                }
                worldUnloadingWarningsHidden = 0;
            } else {
                worldUnloadingWarningsHidden++;
            }
            return;
        }

        // Handle reinitialization after world reload
        if (shouldReconnect && initialized == 1 && !nodeCreated) {
            if (Config.enableDebugLogging) {
            LOGGER.warn("Bridge at {}: Reconnecting after world reload", worldPosition);
            }
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
                    if (Config.enableDebugLogging) {
                    LOGGER.warn("Bridge at {}: Reconnection completed successfully", worldPosition);
                    }
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
                if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge at {}: Initialization completed after {} ticks", worldPosition, initializationTicks);
                }
                initialized = 1;

                // Force update of patterns and connections after initialization
                if (!level.isClientSide()) {
                    try {
                        forceNeighborUpdates();
                        updateConnectedState();
                        ICraftingProvider.requestUpdate(mainNode);
                        IStorageProvider.requestUpdate(mainNode);
                        if (Config.enableDebugLogging) {
                        LOGGER.warn("Bridge at {}: Post-initialization updates completed", worldPosition);
                        }
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

        // Periodically check if there are virtual matter items in the AE2 network
        // that shouldn't be there and remove them
        if (level.getGameTime() % 40 == 0 && isActive() && mainNode.getNode() != null) {
            try {
                IGrid grid = mainNode.getNode().getGrid();
                if (grid != null) {
                    IStorageService storageService = grid.getStorageService();
                    if (storageService != null) {
                        // Get all items in the network
                        KeyCounter items = storageService.getInventory().getAvailableStacks();

                        // Check if there are virtual matter items
                        items.forEach(entry -> {
                            AEKey key = entry.getKey();
                            if (key instanceof AEItemKey itemKey && isVirtualMatterItem(itemKey.getItem())) {
                                long amount = entry.getLongValue();
                                if (amount > 0) {
                                    try {
                                        // Extract all virtual matter to remove it
                                        MachineSource machineSource = new MachineSource(this);
                                        storageService.getInventory().extract(itemKey, amount, Actionable.MODULATE, machineSource);
                                       //LOGGER.warn("Bridge at {}: Removed {} virtual matter items {} from AE2 network", worldPosition, amount, itemKey.getItem().getDescriptionId());
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

        // Temporary counter management
        if (requestCounterTicks >= REQUEST_ACCUMULATION_TICKS) {
            try {
                // SAFETY CHECK: Verify again if the world is unloading
                if (worldUnloading) {
                    if (initialized == 1) {
                        onWorldUnload();
                    }
                    
                    if (worldUnloadingWarningsHidden == WARNING_THROTTLE_INTERVAL) {
                        if (Config.enableDebugLogging) {
                        LOGGER.warn("Bridge{}: World unloading detected during request processing - aborting (+ {} similar warnings hidden)", 
                                   getLocationInfo(), worldUnloadingWarningsHidden);
                        }
                        worldUnloadingWarningsHidden = 0;
                    } else {
                        worldUnloadingWarningsHidden++;
                    }
                    return;
                }

                // Before resetting, create tasks for all items with pending requests
                MatterNetwork network = getNetwork();
                if (network != null && !requestCounters.isEmpty()) {
                    // LOGGER.info("Bridge: Creating task for {} items with pending requests", requestCounters.size());

                    // Calculate the total number of tasks that will be created
                    int totalItems = 0;
                    for (UUID sourceId : requestCounters.keySet()) {
                        Map<ItemWithSourceId, Integer> sourceCounters = requestCounters.get(sourceId);
                        for (int count : sourceCounters.values()) {
                            totalItems += count;
                        }
                    }

                    // For each source block
                    for (UUID sourceId : requestCounters.keySet()) {
                        // CONTROLLO SICUREZZA: Controlla di nuovo se il mondo si sta scaricando
                        if (worldUnloading) {
                            // Already throttled by the parent check above, just count and exit
                            worldUnloadingWarningsHidden++;
                            if (initialized == 1) {
                                onWorldUnload();
                            }
                            return;
                        }
                        
                        Map<ItemWithSourceId, Integer> sourceCounters = requestCounters.get(sourceId);

                        // For each item with pending requests from this source
                        for (Map.Entry<ItemWithSourceId, Integer> entry : sourceCounters.entrySet()) {
                            // CONTROLLO SICUREZZA: Controlla di nuovo se il mondo si sta scaricando
                            if (worldUnloading) {
                                // Already throttled by the parent check above, just count and exit
                                worldUnloadingWarningsHidden++;
                                if (initialized == 1) {
                                    onWorldUnload();
                                }
                                return;
                            }
                            
                            ItemWithSourceId key = entry.getKey();
                            ItemStack itemStack = key.getItemStack();
                            int count = entry.getValue();

                            if (count > 0) {
                                // Search for the corresponding pattern in Replication
                                for (NetworkElement chipSupplier : BridgeDeviceFederation.localChipSuppliers(network)) {
                                    // CONTROLLO SICUREZZA: Controlla di nuovo se il mondo si sta scaricando
                                    if (worldUnloading) {
                                        // Already throttled by the parent check above, just count and exit
                                        worldUnloadingWarningsHidden++;
                                        if (initialized == 1) {
                                            onWorldUnload();
                                        }
                                        return;
                                    }
                                    
                                    var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                                    if (tile instanceof ChipStorageBlockEntity chipStorage) {
                                        for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                                            // CONTROLLO SICUREZZA: Controlla di nuovo se il mondo si sta scaricando
                                            if (worldUnloading) {
                                                // Already throttled by the parent check above, just count and exit
                                                worldUnloadingWarningsHidden++;
                                                if (initialized == 1) {
                                                    onWorldUnload();
                                                }
                                                return;
                                            }
                                            
                                            if (pattern.getStack().getItem().equals(itemStack.getItem())) {
                                                // LOGGER.info("Bridge: Creating task for {} item of {} (requests accumulated in {} ticks)",
                                                //    count, itemStack.getItem().getDescriptionId(), REQUEST_ACCUMULATION_TICKS);

                                                // Create a replication task with the total quantity
                                                ReplicationTask task = new ReplicationTask(
                                                        pattern.getStack(),
                                                        count, // Use the total number of accumulated requests
                                                        IReplicationTask.Mode.MULTIPLE,
                                                        this.worldPosition,
                                                        false // Not infinite mode
                                                );

                                                // Add the task to the network
                                                String taskId = task.getUuid().toString();
                                                network.getTaskManager().getPendingTasks().put(taskId, task);

                                                // Add to the active tasks map with source information
                                                TaskSourceInfo info = new TaskSourceInfo(itemStack, sourceId);

                                                // Initialize the map for this source if needed
                                                Map<String, TaskSourceInfo> sourceTasks = activeTasks.getOrDefault(sourceId, new HashMap<>());
                                                sourceTasks.put(taskId, info);
                                                activeTasks.put(sourceId, sourceTasks);

                                                // Update the request counter for the pattern by source
                                                Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.getOrDefault(sourceId, new HashMap<>());
                                                int currentPatternRequests = sourceRequests.getOrDefault(itemStack, 0);
                                                sourceRequests.put(itemStack, currentPatternRequests + count);
                                                patternRequestsBySource.put(sourceId, sourceRequests);

                                                // Also update the global counter for backward compatibility
                                                Map<ItemStack, Integer> globalRequests = patternRequests.getOrDefault(sourceId, new HashMap<>());
                                                int currentGlobalRequests = globalRequests.getOrDefault(itemStack, 0);
                                                globalRequests.put(itemStack, currentGlobalRequests + count);
                                                patternRequests.put(sourceId, globalRequests);

                                                //LOGGER.info("Bridge: Task created with ID {}, total requests for this pattern: {}",
                                                //    taskId, currentPatternRequests + count);

                                                // Extract the necessary matter
                                                var matterCompound = ReplicationCalculation.getMatterCompound(pattern.getStack());
                                                if (matterCompound != null) {
                                                    // Extract each type of matter from the network
                                                    for (MatterValue matterValue : matterCompound.getValues().values()) {
                                                        var matterType = matterValue.getMatter();
                                                        var matterAmount = (long)Math.ceil(matterValue.getAmount()) * count;
                                                        Item matterItem = getItemForMatterType(matterType);
                                                        if (matterItem != null) {
                                                            AEItemKey matterKey = AEItemKey.of(matterItem);
                                                            extract(matterKey, matterAmount, Actionable.MODULATE);
                                                        }
                                                    }
                                                }
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Now we can reset the counters
                requestCounters.clear();
                requestCounterTicks = 0;
                // LOGGER.info("Bridge: Reset request counters after {} ticks", REQUEST_ACCUMULATION_TICKS);
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
                if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge{}: getNetwork() called during world unloading - returning null (+ {} similar warnings hidden)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                }
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
                if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge{}: isActive() returning false due to world unloading (+ {} similar warnings hidden)", 
                           getLocationInfo(), worldUnloadingWarningsHidden);
                }
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
                    if (Config.enableDebugLogging) {
                    LOGGER.debug("Bridge at {}: isActive() = false (node inactive or null)", worldPosition);
                    }
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
                        if (Config.enableDebugLogging) {
                        LOGGER.debug("Bridge: AE2 node already exists, skipping creation");
                        }
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
    public ItemInteractionResult onActivated(Player playerIn, InteractionHand hand, Direction facing, double hitX, double hitY, double hitZ) {
        // Enable GUI for priority settings
        if (!level.isClientSide() && playerIn instanceof ServerPlayer serverPlayer) {
            // Simply open the priority GUI without forcing pattern updates
            openGui(playerIn);
        }
        return ItemInteractionResult.SUCCESS;
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

        // Debug log disabled for production
        // LOGGER.debug("Bridge: Received item from replicator: " + stack.getDisplayName().getString());

        // First try to insert directly into the AE2 network if connected
        boolean insertedIntoAE2 = false;
        long remainingCount = stack.getCount();

        if (mainNode.isActive() && mainNode.getNode() != null) {
            IGrid grid = mainNode.getNode().getGrid();
            if (grid != null) {
                IStorageService storageService = grid.getStorageService();
                if (storageService != null) {
                    // Debug log disabled for production
                    // LOGGER.debug("Bridge: Attempting to insert into AE2 storage first");

                    // Try to insert the item into the AE2 storage with high priority
                    AEItemKey key = AEItemKey.of(stack);
                    long inserted = storageService.getInventory().insert(key, stack.getCount(), Actionable.MODULATE, new MachineSource(this));

                    if (inserted > 0) {
                        // Debug log disabled for production
                        // LOGGER.debug("Bridge: Successfully inserted " + inserted + " items into AE2 storage");

                        // Remove the items from the output inventory since they were inserted into AE2
                        for (int i = 0; i < output.getSlots(); i++) {
                            ItemStack slotStack = output.getStackInSlot(i);
                            if (ItemStack.isSameItem(slotStack, stack)) {
                                int toRemove = (int) Math.min(inserted, slotStack.getCount());
                                slotStack.shrink(toRemove);
                                inserted -= toRemove;
                                if (inserted <= 0) break;
                            }
                        }

                        remainingCount -= inserted;
                        insertedIntoAE2 = true;

                        // If all items were inserted into AE2, return success
                        if (remainingCount <= 0) {
                            return true;
                        }
                    }
                }
            }
        }

        // If there are remaining items or AE2 insertion failed, try to insert into the output inventory
        if (remainingCount > 0) {
            ItemStack remainingStack = stack.copy();
            remainingStack.setCount((int)remainingCount);

            ItemStack notInserted = ItemHandlerHelper.insertItem(this.output, remainingStack, false);

            // If insertion was completely successful
            if (notInserted.isEmpty()) {
                // Notify the change
                this.setChanged();
                return true;
            }

            // If we inserted some items but not all
            if (notInserted.getCount() < remainingCount) {
                this.setChanged();
                return insertedIntoAE2 || (notInserted.getCount() < stack.getCount());
            }

            // If we couldn't insert any items and didn't insert into AE2 either
            return insertedIntoAE2;
        }

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
                for (NetworkElement chipSupplier : BridgeDeviceFederation.localChipSuppliers(network)) {
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
        if (what instanceof AEItemKey) {
            AEItemKey itemKey = (AEItemKey) what;
            Item item = itemKey.getItem();
            if (isVirtualMatterItem(item)) {
                MachineSource machineSource = new MachineSource(this);
                return matterItemsStorage.extract(what, amount, mode, machineSource);
            }
        }
        MatterNetwork network = getNetwork();
        if (network != null && what instanceof AEItemKey) {
            AEItemKey itemKey = (AEItemKey) what;
            for (NetworkElement chipSupplier : BridgeDeviceFederation.localChipSuppliers(network)) {
                var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                if (tile instanceof ChipStorageBlockEntity chipStorage) {
                    for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                        if (pattern.getStack().getItem().equals(itemKey.getItem())) {
                            // The item can be extracted
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
                for (NetworkElement chipSupplier : BridgeDeviceFederation.localChipSuppliers(network)) {
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
        if (initialized != 1) {
            return List.of();
        }

        List<IPatternDetails> patterns = new ArrayList<>();
        try {
            MatterNetwork network = getNetwork();
            if (network != null) {
                Set<Item> seenItems = new HashSet<>();
                for (NetworkElement chipSupplier : BridgeDeviceFederation.localChipSuppliers(network)) {
                    var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                    if (tile instanceof ChipStorageBlockEntity chipStorage) {
                        for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                            if (!pattern.getStack().isEmpty() && pattern.getCompletion() == 1) {
                                Item item = pattern.getStack().getItem();
                                if (!seenItems.add(item)) {
                                    continue;
                                }
                                var matterCompound = ReplicationCalculation.getMatterCompound(pattern.getStack());
                                if (matterCompound == null) {
                                    continue;
                                }
                                var details = ReplicationBridgePatternDetails.of(pattern.getStack());
                                if (details != null) {
                                    patterns.add(details);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge{}: getAvailablePatterns failed: {}", getLocationInfo(), e.getMessage());
            }
            return List.of();
        }
        return patterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // Don't accept patterns if not initialized
        if (initialized != 1) {
            return false;
        }

        MatterNetwork network = getNetwork();

        if (network != null && isActive() && patternDetails != null) {
            if (patternDetails.getOutputs().size() == 1) {
                var output = patternDetails.getOutputs().iterator().next();
                if (output.what() instanceof AEItemKey itemKey) {
                    for (NetworkElement chipSupplier : BridgeDeviceFederation.localChipSuppliers(network)) {
                        var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
                        if (tile instanceof ChipStorageBlockEntity chipStorage) {
                            for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                                if (pattern.getStack().getItem().equals(itemKey.getItem())) {
                                    ItemStack itemStack = pattern.getStack();
                                    if (!absorbCraftMatter(inputHolder, itemStack)) {
                                        if (Config.enableDebugLogging) {
                                            LOGGER.warn(
                                                    "Bridge{}: pushPattern refused — inputHolder missing required matter for {}",
                                                    getLocationInfo(), itemKey.getItem().getDescriptionId());
                                        }
                                        return false;
                                    }
                                    // AE2-internal bypass only if we assign now or already running this item.
                                    if (hasAe2GridWorker()) {
                                        ae2CraftParkPending = true;
                                        ItemWithSourceId key = new ItemWithSourceId(itemStack, this.blockId);
                                        int before = ae2InternalPending.getOrDefault(key, 0);
                                        ae2InternalPending.merge(key, 1, Integer::sum);
                                        lightFeedAe2InternalOnPush(itemStack);
                                        int after = ae2InternalPending.getOrDefault(key, 0);
                                        // Pending dropped → a chamber/MA took (or extended) this unit.
                                        if (after < before + 1) {
                                            return true;
                                        }
                                        // Could not feed: roll back and fall through to ReplicationTask path.
                                        if (after <= 1) {
                                            ae2InternalPending.remove(key);
                                        } else {
                                            ae2InternalPending.put(key, after - 1);
                                        }
                                    }
                                    // Prefer AE2 cells so chamber/MA drainMatterForAe2Internal can see the matter.
                                    flushCraftBufferToStorage(true);
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
                for (NetworkElement chipSupplier : BridgeDeviceFederation.localChipSuppliers(network)) {
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

    // Map IMatterType to virtual item (supports both built-in and custom matter types)
    private Item getItemForMatterType(IMatterType type) {
        // Check built-in matter types first
        String name = type.getName();
        if (name.equalsIgnoreCase("earth")) return ModItems.EARTH_MATTER.get();
        if (name.equalsIgnoreCase("nether")) return ModItems.NETHER_MATTER.get();
        if (name.equalsIgnoreCase("organic")) return ModItems.ORGANIC_MATTER.get();
        if (name.equalsIgnoreCase("ender")) return ModItems.ENDER_MATTER.get();
        if (name.equalsIgnoreCase("metallic")) return ModItems.METALLIC_MATTER.get();
        if (name.equalsIgnoreCase("precious")) return ModItems.PRECIOUS_MATTER.get();
        if (name.equalsIgnoreCase("living")) return ModItems.LIVING_MATTER.get();
        if (name.equalsIgnoreCase("quantum")) return ModItems.QUANTUM_MATTER.get();

        
        if(ServerLifecycleEventHandler.getCustomMatterToItemMap().get(type) != null) {
            return ServerLifecycleEventHandler.getCustomMatterToItemMap().get(type);
        }

        return null;
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

    // Method to show virtual items in the AE2 terminal
    public void getAvailableItems(KeyCounter items) {
        // Don't show items if not initialized
        if (initialized != 1) {
            return;
        }

        //LOGGER.info("Bridge: Called getAvailableItems");
        MatterNetwork network = getNetwork();
        if (network != null) {
            // Recupera tutti i tipi di matter registrati, inclusi quelli custom da KubeJS
            List<IMatterType> matterTypes = ReplicationRegistry.MATTER_TYPES_REGISTRY.stream().toList();

            for (IMatterType matterType : matterTypes) {
                long amount = network.calculateMatterAmount(matterType);
                //LOGGER.info("Bridge: " + matterType.getName() + " -> " + amount);
                if (amount > 0) {
                    Item item = getItemForMatterType(matterType);
                    if (item != null) {
                        // For universal matter, create a key with the correct matter component
                        items.add(AEItemKey.of(item), amount);
                        //LOGGER.info("Bridge: Adding virtual item " + item + " quantity " + amount);
                    } else {
                        //LOGGER.info("Bridge: No item associated with " + matterType.getName());
                    }
                }
            }
        } else {
            //LOGGER.info("Bridge: No Replication network found");
        }
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        // Don't mount storage if not initialized
        if (initialized != 1) {
            return;
        }

        // Low mount priority: visibility/extract of Replication tanks + craft refund only.
        // Matter cells must win Import Bus / network inserts (do not use high-priority sink).
        storageMounts.mount(matterItemsStorage, Integer.MIN_VALUE / 2);
    }

    /**
     * Method called when the entity is saved or loaded from/to disk
     */
    public class MatterItemsStorage implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            // Craft buffer is pushPattern-only; refuse network inserts (Import Bus / ME insert).
            // Without cells this must not become an unbounded sink — Import Bus must use cells (or fail).
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
            boolean simulate = mode == Actionable.SIMULATE;
            long remaining = amount;
            long drained = 0;
            // Craft buffer first, then physical tanks (AE2 cells are separate mounts).
            CraftMatterTank craft = findCraftMatterTank(type);
            if (craft != null && remaining > 0) {
                var stack = craft.drain(
                        (double) remaining,
                        simulate
                                ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                long got = (long) Math.floor(stack.getAmount());
                drained += got;
                remaining -= got;
            }
            if (remaining > 0) {
                drained += drainExternalMatter(type, remaining, simulate);
            }
            return drained;
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
            // One holder walk for all types (avoids N× localHolders — TPS).
            var holders = BridgeMatterFederation.localHolders(getNetwork(), getBlockPos());
            for (IMatterType matterType : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
                if (matterType == null || matterType.equals(MatterType.EMPTY)) {
                    continue;
                }
                long amount = BridgeMatterFederation.sumPhysicalMatter(holders, matterType);
                CraftMatterTank craft = findCraftMatterTank(matterType);
                if (craft != null) {
                    amount += (long) Math.floor(craft.getMatterAmount());
                }
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
                if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge{}: AE2 node was null during onWorldUnload", getLocationInfo());
                }
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
                            if (Config.enableDebugLogging) {
                            LOGGER.debug("Bridge{}: Element not found in NetworkManager (already removed?)", getLocationInfo());
                            }
                        }
                    } else {
                        if (Config.enableDebugLogging) {
                        LOGGER.warn("Bridge{}: NetworkManager not available during onWorldUnload", getLocationInfo());
                        }
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
            long inserted = storageService.getInventory().insert(key, stack.getCount(), Actionable.MODULATE, new MachineSource(this));

            if (inserted > 0) {
                // Debug log disabled for production
                // LOGGER.debug("Bridge: Transferred {} items from local inventory to AE2", inserted);

                // Remove the inserted items from the output inventory
                stack.shrink((int)inserted);
                itemsMoved = true;
            }
        }

        // If we moved any items, mark the block as changed
        if (itemsMoved) {
            this.setChanged();
        }
    }

    // IPriorityHost implementation
    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.setChanged();
    }

    // ISubMenuHost implementation
    @Override
    public ItemStack getMainMenuIcon() {
        return ModBlocks.REPAE2BRIDGE.get().asItem().getDefaultInstance();
    }

    @Override
    public void returnToMainMenu(Player player, appeng.menu.ISubMenu subMenu) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            appeng.menu.MenuOpener.open(net.unfamily.repae2bridge.menu.BridgeMenu.TYPE, serverPlayer,
                    appeng.menu.locator.MenuLocators.forBlockEntity(this));
        }
    }

    /**
     * Opens the GUI for this block entity
     * @param player The player opening the GUI
     */
    public void openGui(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            appeng.menu.MenuOpener.open(net.unfamily.repae2bridge.menu.BridgeMenu.TYPE, serverPlayer,
                appeng.menu.locator.MenuLocators.forBlockEntity(this));
        }
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
                if (Config.enableDebugLogging) {
                LOGGER.debug("Bridge at {}: Matter tracking initialized with current network amounts", worldPosition);
                }
            }
            return;
        }

        // Check all matter types for amount changes
        for (IMatterType matterType : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
            long currentAmount = network.calculateMatterAmount(matterType);
            long previousAmount = previousMatterAmounts.getOrDefault(matterType, 0L);

            if (currentAmount > previousAmount) {
                long createdAmount = currentAmount - previousAmount;

                // Accumulate the created matter in the pending buffer
                long currentPending = pendingMatterChanges.getOrDefault(matterType, 0L);
                pendingMatterChanges.put(matterType, currentPending + createdAmount);

                onMatterCreated(matterType, createdAmount);

                if (globalShouldLogDebug) {
                    if (Config.enableDebugLogging) {
                    LOGGER.debug("Bridge at {}: Detected matter creation - {}: {} units created (total: {}), pending buffer: {}",
                        worldPosition, matterType.getName(), createdAmount, currentAmount, currentPending + createdAmount);
                    }
                }
            }

            // Update the previous amount
            previousMatterAmounts.put(matterType, currentAmount);
        }
    }

    /**
     * Called when matter is detected as created in the network
     * @param matterType The type of matter that was created
     * @param amount The amount of matter that was created
     */
    private void onMatterCreated(IMatterType matterType, long amount) {
        // Here we can add logic to handle matter creation events
        // For example, we could send notifications, update statistics, or trigger other systems

        // Log the event only if debug logging is enabled
        if (Config.enableDebugLogging) {
            LOGGER.info("Bridge at {}: Matter created from disintegrator - {}: {} units",
                worldPosition, matterType.getName(), amount);
        }
    }

    // ── Feed-Replication-Energy toggle ────────────────────────────────

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

    public IManagedGridNode getMainNode() {
        return mainNode;
    }


    // ── Matter Assembler delivery ─────────────────────────────────────

    /**
     * Delivers a crafted item produced by a Matter Assembler back into the
     * Replication network (source block's item handler).
     *
     * @return true if the item was fully inserted and the task unit was finalized
     */
    public boolean deliverMatterAssemblerReplicationUnit(ItemStack stack, UUID taskId, BlockPos assemblerPos) {
        if (stack.isEmpty() || taskId == null || level == null || level.isClientSide()) {
            return false;
        }

        MatterNetwork network = getNetwork();
        if (network == null) {
            return false;
        }

        com.buuz135.replication.api.task.IReplicationTask task =
                network.getTaskManager().getPendingTasks().get(taskId.toString());
        if (task == null) {
            return false;
        }

        BlockPos source = task.getSource();
        var capability = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                source, net.minecraft.core.Direction.UP);
        if (capability == null) {
            return false;
        }

        ItemStack singleCopy = stack.copyWithCount(1);
        ItemStack leftover = ItemHandlerHelper.insertItem(capability, singleCopy, false);
        if (!leftover.isEmpty()) {
            return false;
        }

        onMatterAssemblerCompletedUnit(taskId, assemblerPos);
        return true;
    }

    public void onMatterAssemblerCompletedUnit(UUID taskId, BlockPos assemblerPos) {
        if (level == null || level.isClientSide() || taskId == null) {
            return;
        }
        MatterNetwork network = getNetwork();
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

    /** True while the Replication UI task is still in the pending map. */
    public boolean hasPendingReplicationTask(UUID taskId) {
        if (taskId == null) {
            return false;
        }
        MatterNetwork network = getNetwork();
        return network != null
                && network.getTaskManager().getPendingTasks().containsKey(taskId.toString());
    }

    /**
     * Ensures matter for one craft unit is reserved on the task for {@code workerPos}.
     */
    public boolean ensureMatterReservedForWorker(UUID taskId, BlockPos workerPos) {
        if (taskId == null || workerPos == null || level == null || level.isClientSide()) {
            return false;
        }
        MatterNetwork network = getNetwork();
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

    public long getAe2MatterAmount(IMatterType type) {
        ReplicationMatterKey key = ReplicationMatterKey.of(type);
        if (key == null || !mainNode.isReady()) {
            return 0;
        }
        KeyCounter stacks = getCachedAe2Stacks();
        if (stacks == null) {
            return 0;
        }
        long amount = stacks.get(key);
        if (amount <= 0) {
            long sum = 0;
            for (var entry : stacks) {
                if (key.equals(entry.getKey())) {
                    sum += entry.getLongValue();
                }
            }
            amount = sum;
        }
        // Ae2MatterTank must not re-report physical tanks already counted by MatterNetwork.
        long externalLocal = BridgeMatterFederation.sumPhysicalMatter(getCachedLocalHolders(), type);
        return Math.max(0, amount - externalLocal);
    }

    @Nullable
    private KeyCounter getCachedAe2Stacks() {
        refreshAe2MatterViewCache();
        return ae2StacksCache;
    }

    private List<NetworkElement> getCachedLocalHolders() {
        refreshAe2MatterViewCache();
        return ae2LocalHoldersCache != null ? ae2LocalHoldersCache : List.of();
    }

    private void refreshAe2MatterViewCache() {
        long tick = level != null ? level.getGameTime() : Long.MIN_VALUE;
        if (ae2MatterViewTick == tick && ae2StacksCache != null && ae2LocalHoldersCache != null) {
            return;
        }
        ae2MatterViewTick = tick;
        ae2StacksCache = null;
        ae2LocalHoldersCache = BridgeMatterFederation.localHolders(getNetwork(), getBlockPos());
        if (!mainNode.isReady()) {
            return;
        }
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return;
        }
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return;
        }
        ae2StacksCache = storage.getInventory().getAvailableStacks();
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

    private void scheduleReplicationTankSync(@Nullable IMatterType type) {
        if (type == null || type.equals(MatterType.EMPTY)) {
            return;
        }
        dirtyReplicationMatterTypes.add(type);
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
     * AE2-internal matter drain for {@code count} crafts: AE2 cells, craft buffer, then physical tanks.
     * Physical tanks are required after {@link #flushCraftBufferToStorage} parks leftovers there.
     */
    public boolean drainMatterForAe2Internal(ItemStack patternStack, int count) {
        if (count <= 0 || patternStack == null || patternStack.isEmpty()) {
            return false;
        }
        var matterCompound = ReplicationCalculation.getMatterCompound(patternStack);
        if (matterCompound == null) {
            return true;
        }
        MatterNetwork network = getNetworkSafe();
        for (MatterValue matterValue : matterCompound.getValues().values()) {
            long needed = (long) Math.ceil(matterValue.getAmount()) * count;
            if (needed <= 0) {
                continue;
            }
            IMatterType type = matterValue.getMatter();
            long have = extractAe2Matter(type, needed, true);
            if (have < needed) {
                CraftMatterTank craft = findCraftMatterTank(type);
                if (craft != null) {
                    have += (long) Math.floor(craft.getMatterAmount());
                }
            }
            if (have < needed && network != null) {
                double still = drainMatterTypeFromHolders(network, type, needed - have, false);
                if (still <= 0.01) {
                    have = needed;
                }
            }
            if (have < needed) {
                return false;
            }
        }
        for (MatterValue matterValue : matterCompound.getValues().values()) {
            long needed = (long) Math.ceil(matterValue.getAmount()) * count;
            if (needed <= 0) {
                continue;
            }
            IMatterType type = matterValue.getMatter();
            long got = extractAe2Matter(type, needed, false);
            long left = needed - got;
            if (left > 0) {
                CraftMatterTank craft = findCraftMatterTank(type);
                if (craft != null) {
                    var drained = craft.drain(
                            (double) left,
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    left -= (long) Math.floor(drained.getAmount());
                }
            }
            if (left > 0 && network != null) {
                left = (long) Math.ceil(drainMatterTypeFromHolders(network, type, left, true));
            }
            if (left > 0) {
                return false;
            }
            scheduleReplicationTankSync(type);
        }
        markAe2StorageDirty();
        return true;
    }

    public int insertCraftedToAe2(ItemStack stack, int count) {
        if (stack.isEmpty() || count <= 0 || !mainNode.isActive() || mainNode.getNode() == null) {
            return 0;
        }
        IGrid grid = mainNode.getNode().getGrid();
        if (grid == null) {
            return 0;
        }
        IStorageService storageService = grid.getStorageService();
        if (storageService == null) {
            return 0;
        }
        AEItemKey key = AEItemKey.of(stack);
        long inserted = storageService.getInventory().insert(
                key, count, Actionable.MODULATE, new MachineSource(this));
        if (inserted > 0) {
            markAe2StorageDirty();
        }
        return (int) Math.min(Integer.MAX_VALUE, inserted);
    }

    private void markAe2StorageDirty() {
        ae2StorageUpdateDirty = true;
    }

    private void invalidateAe2WorkerCache() {
        ae2WorkerCacheTick = Long.MIN_VALUE;
    }

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
     * Cancels Replication tasks / assembler jobs sourced by this bridge and returns craft-buffer matter to AE2.
     */


    // --- Matter v3 routing (missing defs) ---

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


    /** Online chamber leader or Matter Assembler on this AE2 grid (idle or busy). */
    private boolean hasAe2GridWorker() {
        refreshAe2WorkerCache();
        return !cachedChamberLeaders.isEmpty() || !cachedOnlineAssemblers.isEmpty();
    }

    /** True if a chamber/MA owned by this bridge is already running AE2-internal work for {@code item}. */
    private boolean hasActiveAe2InternalJobForItem(Item item) {
        if (item == null || mainNode.getGrid() == null) {
            return false;
        }
        refreshAe2WorkerCache();
        for (ReplicationUnitBlockEntity unit : cachedChamberLeaders) {
            if (unit != null
                    && !unit.isIdle()
                    && unit.getReplicationTaskId() == null
                    && worldPosition.equals(unit.getBridgePos())
                    && unit.getCraftingStack().getItem().equals(item)) {
                return true;
            }
        }
        for (MatterAssemblerBlockEntity assembler : cachedOnlineAssemblers) {
            if (assembler != null
                    && !assembler.isIdle()
                    && assembler.getReplicationTaskId() == null
                    && worldPosition.equals(assembler.getBridgePos())
                    && assembler.getCraftingStack().getItem().equals(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * O(workers) feed on pushPattern using the worker cache — keeps remainingCount topped up
     * at AE2 push rate without scanning the grid every call.
     */
    private void lightFeedAe2InternalOnPush(ItemStack patternStack) {
        if (patternStack.isEmpty() || worldUnloading) {
            return;
        }
        ItemWithSourceId key = new ItemWithSourceId(patternStack, this.blockId);
        Integer boxed = ae2InternalPending.get(key);
        if (boxed == null || boxed <= 0) {
            return;
        }
        int pending = feedAe2InternalToEligibleWorkers(patternStack, boxed);
        if (pending <= 0) {
            ae2InternalPending.remove(key);
        } else {
            ae2InternalPending.put(key, pending);
        }
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
        markAe2StorageDirty();
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
            invalidateAe2WorkerCache();
            recoverCraftBufferIntoAe2();
        }
    }


    /**
     * Feeds {@link #ae2InternalPending} to chambers/MAs.
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
            int pending = feedAe2InternalToEligibleWorkers(key.getItemStack(), boxed);
            if (pending <= 0) {
                ae2InternalPending.remove(key);
            } else {
                ae2InternalPending.put(key, pending);
            }
        }
    }


    private void flushAe2StorageUpdate(boolean force) {
        if (!ae2StorageUpdateDirty) {
            return;
        }
        if (!force && level != null && level.getGameTime() % 10 != 0) {
            return;
        }
        ae2StorageUpdateDirty = false;
        if (mainNode != null && mainNode.isReady()) {
            IStorageProvider.requestUpdate(mainNode);
        }
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


    /**
     * Local-network physical tanks only (AE2 mount).
     */
    private long calculateLocalExternalMatterAmount(IMatterType matterType) {
        return BridgeMatterFederation.sumPhysicalMatter(
                BridgeMatterFederation.localHolders(getNetwork(), getBlockPos()), matterType);
    }

    /**
     * Federated physical tanks (AE2 extract / craft drain).
     * Skips {@link Ae2MatterTank}; craft buffer is Replication-only via {@link #getTanks()}.
     */
    private long calculateExternalMatterAmount(IMatterType matterType) {
        return BridgeMatterFederation.sumPhysicalMatter(
                BridgeMatterFederation.localHolders(getNetwork(), getBlockPos()), matterType);
    }


    /**
     * Drains external Replication tanks for AE2 extract. Never drains the craft buffer or
     * {@link Ae2MatterTank} (buffer is for Replication; cells are extracted by cell mounts).
     */
    private long drainExternalMatter(IMatterType matterType, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        long remaining = amount;
        long drainedTotal = 0;

        for (NetworkElement element : BridgeMatterFederation.localHolders(getNetwork(), getBlockPos())) {
            if (remaining <= 0) {
                break;
            }
            BlockEntity tile = element.getLevel().getBlockEntity(element.getPos());
            if (!(tile instanceof IMatterTanksSupplier tanksSupplier)) {
                continue;
            }
            for (IMatterTank tank : tanksSupplier.getTanks()) {
                if (remaining <= 0) {
                    break;
                }
                if (tank instanceof Ae2MatterTank) {
                    continue;
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


    /**
     * Lets idle Matter Assemblers claim pending Replication terminal tasks (like Replicators).
     * <p>
     * Replication's {@code findTaskForReplicator} budgets parallel slots from
     * {@code network.getReplicators()} only — Matter Assemblers are invisible there, so a
     * network with assemblers but no replicators would only allow one worker per MULTIPLE task.
     * We mirror that formula with assembler count included.
     */
    private void assignReplicationTasksToAssemblers() {
        net.unfamily.repae2bridge.ae2.BridgeReplicationTaskAssign.assign(
                getNetworkSafe(), mainNode.getGrid(), level, worldPosition);
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


    // --- Matter v3 routing (ported from 26 SoT) ---

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


    /** Moves craft-buffer matter into physical tanks first, then AE2 cells. */
    private boolean hasCraftBufferMatter() {
        for (CraftMatterTank craftTank : getOrCreateCraftMatterTanks()) {
            if (craftTank.getMatterAmount() >= 1.0) {
                return true;
            }
        }
        return false;
    }

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
        for (NetworkElement element : BridgeMatterFederation.localHolders(getNetwork(), getBlockPos())) {
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


    private void refreshAe2WorkerCache() {
        if (level == null || !mainNode.isReady()) {
            cachedChamberLeaders = List.of();
            cachedOnlineAssemblers = List.of();
            return;
        }
        long now = level.getGameTime();
        if (ae2WorkerCacheTick != Long.MIN_VALUE && now - ae2WorkerCacheTick < AE2_WORKER_CACHE_TTL) {
            return;
        }
        ae2WorkerCacheTick = now;
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            cachedChamberLeaders = List.of();
            cachedOnlineAssemblers = List.of();
            return;
        }
        java.util.ArrayList<ReplicationUnitBlockEntity> chambers = new java.util.ArrayList<>();
        for (ReplicationUnitBlockEntity unit : grid.getMachines(ReplicationUnitBlockEntity.class)) {
            if (unit != null && unit.isFormed() && unit.isChamberLeader() && unit.getMainNode().isOnline()) {
                chambers.add(unit);
            }
        }
        java.util.ArrayList<MatterAssemblerBlockEntity> assemblers = new java.util.ArrayList<>();
        for (MatterAssemblerBlockEntity assembler : grid.getMachines(MatterAssemblerBlockEntity.class)) {
            if (assembler != null && assembler.getMainNode().isOnline()) {
                assemblers.add(assembler);
            }
        }
        cachedChamberLeaders = chambers;
        cachedOnlineAssemblers = assemblers;
    }


    /**
     * Split {@code count} across idle + same-item running chambers/MAs (cached workers only).
     * When {@code count < N}, round-robin one unit at a time so multi-chamber setups share +1 pushes.
     * @return units still pending
     */
    private int feedAe2InternalToEligibleWorkers(ItemStack patternStack, int count) {
        if (count <= 0 || patternStack.isEmpty()) {
            return count;
        }
        refreshAe2WorkerCache();
        Item item = patternStack.getItem();

        java.util.ArrayList<ReplicationUnitBlockEntity> chambers = new java.util.ArrayList<>();
        for (ReplicationUnitBlockEntity unit : cachedChamberLeaders) {
            if (unit == null) {
                continue;
            }
            if (unit.isChamberWorkerReady()) {
                chambers.add(unit);
            } else if (!unit.isIdle()
                    && unit.getReplicationTaskId() == null
                    && worldPosition.equals(unit.getBridgePos())
                    && unit.getCraftingStack().getItem().equals(item)) {
                chambers.add(unit);
            }
        }
        java.util.ArrayList<MatterAssemblerBlockEntity> assemblers = new java.util.ArrayList<>();
        for (MatterAssemblerBlockEntity assembler : cachedOnlineAssemblers) {
            if (assembler == null) {
                continue;
            }
            if (assembler.isIdle()) {
                assemblers.add(assembler);
            } else if (assembler.getReplicationTaskId() == null
                    && worldPosition.equals(assembler.getBridgePos())
                    && assembler.getCraftingStack().getItem().equals(item)) {
                assemblers.add(assembler);
            }
        }

        int n = chambers.size() + assemblers.size();
        if (n == 0) {
            return count;
        }

        int remaining = count;
        if (remaining < n) {
            // Round-robin single units — prevents the first idle chamber from monopolizing +1 pushes.
            while (remaining > 0) {
                int idx = Math.floorMod(ae2FeedCursor++, n);
                boolean ok;
                if (idx < chambers.size()) {
                    ok = assignAe2InternalToChamber(chambers.get(idx), patternStack, 1);
                } else {
                    ok = assignAe2InternalToAssembler(assemblers.get(idx - chambers.size()), patternStack, 1);
                }
                if (ok) {
                    remaining--;
                } else {
                    // Skip a dead slot once; stop if nobody accepts (avoid infinite loop).
                    boolean any = false;
                    for (ReplicationUnitBlockEntity unit : chambers) {
                        if (assignAe2InternalToChamber(unit, patternStack, 1)) {
                            remaining--;
                            any = true;
                            break;
                        }
                    }
                    if (!any) {
                        for (MatterAssemblerBlockEntity assembler : assemblers) {
                            if (assignAe2InternalToAssembler(assembler, patternStack, 1)) {
                                remaining--;
                                any = true;
                                break;
                            }
                        }
                    }
                    if (!any) {
                        break;
                    }
                }
            }
            return remaining;
        }

        // Ceil-split across every eligible worker (idle start or running extend).
        int i = 0;
        for (ReplicationUnitBlockEntity unit : chambers) {
            int workersLeft = n - i;
            int batch = (remaining + workersLeft - 1) / workersLeft;
            if (batch > 0 && assignAe2InternalToChamber(unit, patternStack, batch)) {
                remaining -= batch;
            }
            i++;
            if (remaining <= 0) {
                return 0;
            }
        }
        for (MatterAssemblerBlockEntity assembler : assemblers) {
            int workersLeft = n - i;
            int batch = (remaining + workersLeft - 1) / workersLeft;
            if (batch > 0 && assignAe2InternalToAssembler(assembler, patternStack, batch)) {
                remaining -= batch;
            }
            i++;
            if (remaining <= 0) {
                return 0;
            }
        }
        return remaining;
    }


    private boolean assignAe2InternalToChamber(ReplicationUnitBlockEntity unit, ItemStack patternStack, int batch) {
        if (unit.isChamberWorkerReady()) {
            return unit.tryStartJob(patternStack, batch, worldPosition, null);
        }
        int before = unit.getRemainingCount();
        if (!unit.extendAe2InternalJob(patternStack, batch, worldPosition)) {
            return false;
        }
        // extend may clamp at Integer.MAX_VALUE — treat any growth as success for this batch.
        return unit.getRemainingCount() >= before;
    }


    private boolean assignAe2InternalToAssembler(MatterAssemblerBlockEntity assembler, ItemStack patternStack, int batch) {
        if (assembler.isIdle()) {
            return assembler.tryStartJob(patternStack, batch, worldPosition, null);
        }
        int before = assembler.getRemainingCount();
        if (!assembler.extendAe2InternalJob(patternStack, batch, worldPosition)) {
            return false;
        }
        return assembler.getRemainingCount() >= before;
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


    /** @see #drainMatterForAe2Internal(ItemStack, int) */
    public boolean drainOneMatterForAe2Internal(ItemStack patternStack) {
        return drainMatterForAe2Internal(patternStack, 1);
    }


    private MatterNetwork getNetworkSafe() {
        try {
            return getNetwork();
        } catch (Exception e) {
            return null;
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
            IMatterType type = matterValue.getMatter();
            CraftMatterTank craft = findCraftMatterTank(type);
            if (craft != null && left > 0.01) {
                var drained = craft.drain(
                        left,
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                left -= drained.getAmount();
            }
            left = drainMatterTypeFromHolders(network, type, left, true);
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
        CraftMatterTank craft = findCraftMatterTank(type);
        if (craft != null) {
            total += (long) Math.floor(craft.getMatterAmount());
        }
        for (NetworkElement holderEl : BridgeMatterFederation.localHolders(getNetwork(), getBlockPos())) {
            var tile = holderEl.getLevel().getBlockEntity(holderEl.getPos());
            if (!(tile instanceof IMatterTanksSupplier supplier)) {
                continue;
            }
            for (var tank : supplier.getTanks()) {
                if (tank instanceof Ae2MatterTank || tank instanceof CraftMatterTank) {
                    continue;
                }
                var content = tank.getMatter();
                if (!content.isEmpty() && Objects.equals(content.getMatterType(), type)) {
                    total += (long) Math.floor(content.getAmount());
                }
            }
        }
        total += extractAe2Matter(type, Long.MAX_VALUE / 4, true);
        return total;
    }


    private double drainMatterTypeFromHolders(
            MatterNetwork network, IMatterType type, double amount, boolean execute) {
        double left = amount;
        var action = execute
                ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE
                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        for (NetworkElement holderEl : BridgeMatterFederation.localHolders(getNetwork(), getBlockPos())) {
            if (left <= 0) {
                break;
            }
            var tile = holderEl.getLevel().getBlockEntity(holderEl.getPos());
            if (!(tile instanceof IMatterTanksSupplier supplier)) {
                continue;
            }
            for (var tank : supplier.getTanks()) {
                if (left <= 0) {
                    break;
                }
                // Craft buffer and AE2 mirrors are drained by dedicated callers.
                if (tank instanceof Ae2MatterTank || tank instanceof CraftMatterTank) {
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
        if (left > 0.01) {
            long want = (long) Math.floor(left);
            long got = extractAe2Matter(type, want, !execute);
            if (got > 0) {
                left -= got;
                if (execute) {
                    scheduleReplicationTankSync(type);
                }
            }
        }
        return left;
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

        // Prefer AE2-internal workers when present; anything they cannot take now becomes a
        // ReplicationTask so CPUs are never parked forever in ae2InternalPending alone.
        if (hasAe2GridWorker()) {
            ItemWithSourceId key = new ItemWithSourceId(patternStack, this.blockId);
            ae2InternalPending.merge(key, remaining, Integer::sum);
            lightFeedAe2InternalOnPush(patternStack);
            int left = ae2InternalPending.getOrDefault(key, 0);
            if (left <= 0) {
                return;
            }
            ae2InternalPending.remove(key);
            remaining = left;
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


    private void saveCraftMatterBuffer(CompoundTag tag) {
        var list = new net.minecraft.nbt.ListTag();
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
            CompoundTag child = new CompoundTag();
            child.putString("type", key.location().toString());
            child.putDouble("amount", tank.getMatterAmount());
            list.add(child);
        }
        if (!list.isEmpty()) {
            tag.put("craftMatterBuffer", list);
        }
    }

    private void loadCraftMatterBuffer(CompoundTag tag) {
        getOrCreateCraftMatterTanks();
        if (!tag.contains("craftMatterBuffer", CompoundTag.TAG_LIST)) {
            return;
        }
        var list = tag.getList("craftMatterBuffer", CompoundTag.TAG_COMPOUND);
        var registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
        if (registry == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag child = list.getCompound(i);
            String idStr = child.getString("type");
            if (idStr == null || idStr.isEmpty()) {
                continue;
            }
            double amount = child.getDouble("amount");
            if (amount <= 0) {
                continue;
            }
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(idStr);
            if (id == null) {
                continue;
            }
            IMatterType type = registry.get(id);
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
            if (Config.enableDebugLogging) {
            LOGGER.debug("Bridge: AE2 node already exists in handleNeighborChanged, skipping creation");
            }
        }
    }


    /**
     * Neighbor update without a known source (MC 26 Orientation often null).
     * Refreshes local AE2 init / visuals but never re-fans neighbor updates — that is what
     * caused TPS storms with two adjacent bridges in the broken 26.1 port.
     */
    public void handleNeighborChangedUnknownSource() {
        handleNeighborChanged(worldPosition);
    }


    public void cancelAe2LinkedReplicationWork() {
        MatterNetwork network = getNetwork();
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

        recoverCraftBufferIntoAe2();

        activeTasks.remove(this.blockId);
        patternRequests.remove(this.blockId);
        patternRequestsBySource.remove(this.blockId);
        requestCounters.remove(this.blockId);
        ae2InternalPending.clear();
        invalidateAe2WorkerCache();
    }
}
