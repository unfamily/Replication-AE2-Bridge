package net.unfamily.repae2bridge.block.entity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.capabilities.Capabilities;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.IPriorityHost;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.me.helpers.MachineSource;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.api.task.ReplicationTask;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.block.tile.ReplicationMachine;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.calculation.MatterValue;
import com.buuz135.replication.calculation.ReplicationCalculation;
import com.buuz135.replication.network.MatterNetwork;
import com.buuz135.replication.ReplicationRegistry;
import com.hrznstudio.titanium.annotation.Save;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import net.unfamily.repae2bridge.item.ModItems;
import com.hrznstudio.titanium.component.energy.EnergyStorageComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.block.custom.RepAE2BridgeBlock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Block Entity per il bridge AE2-Replication.
 * Gestisce la connessione alla rete di Replication e alla rete AE2.
 * Implementa l'autocrafting convertendo le richieste AE2 in task di replication.
 */
public class RepAE2BridgeBlockEntity extends ReplicationMachine<RepAE2BridgeBlockEntity> 
        implements IGridConnectedBlockEntity, IStorageProvider, ICraftingProvider, IPriorityHost {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Constants
    private static final int INITIALIZATION_DELAY = 60; // 3 seconds
    private static final int REQUEST_ACCUMULATION_TICKS = 100; // 5 seconds
    private static final int WARNING_COOLDOWN = 600; // 30 seconds

    // Network initialization state
    @Save
    private int initialized = 0; // 0=default, 1=initialized, 2=server stopped
    
    @Save
    private int initializationTicks = 0;

    // Crafting priority
    @Save
    private int priority = 0;

    // AE2 Grid Node
    private final IManagedGridNode mainNode;
    
    // Storage per esporre i matter ad AE2
    private final MatterItemsStorage matterItemsStorage = new MatterItemsStorage();

    // Output inventory (9x2 slots)
    @Save
    private InventoryComponent<RepAE2BridgeBlockEntity> output;

    // Request accumulation system
    private final Map<UUID, Map<ItemWithSourceId, Integer>> requestCounters = new HashMap<>();
    private int requestCounterTicks = 0;

    // Active tasks tracking
    private final Map<UUID, Map<String, TaskSourceInfo>> activeTasks = new HashMap<>();
    
    // Matter warnings cache
    private final Map<String, Long> lastMatterWarnings = new HashMap<>();
    
    // Unique ID for this block
    private final UUID blockId = UUID.randomUUID();

    public RepAE2BridgeBlockEntity(BlockPos pos, BlockState state) {
        super((RepAE2BridgeBlock) ModBlocks.REP_AE2_BRIDGE_BLOCK.get(), 
              ModBlocks.REP_AE2_BRIDGE_BLOCK_ENTITY.get(), pos, state);
        
        // Initialize output inventory (9x2 = 18 slots)
        this.output = new InventoryComponent<RepAE2BridgeBlockEntity>("output", 11, 131, 18)
                .setRange(9, 2)
                .setComponentHarness(this)
                .setInputFilter((stack, slot) -> true);
        this.addInventory(this.output);
        
        // Create AE2 node with crafting and storage services
        this.mainNode = GridHelper.createManagedNode(this, new BlockEntityNodeListener<>()) 
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(Config.bridgeEnergyConsumption)
                .setInWorldNode(true)
                .setTagName("ae2_grid_node")
                .addService(IStorageProvider.class, this)
                .addService(ICraftingProvider.class, this);
    }

    @Nonnull
    @Override
    public RepAE2BridgeBlockEntity getSelf() {
        return this;
    }

    // =================== IGridConnectedBlockEntity ===================
    
    @Override
    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    @Override
    public void saveChanges() {
        if (level != null && !level.isClientSide()) {
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (level != null && !level.isClientSide()) {
            setChanged();
            
            // Request pattern update when node becomes active
            if (reason == IGridNodeListener.State.GRID_BOOT) {
                ICraftingProvider.requestUpdate(mainNode);
                IStorageProvider.requestUpdate(mainNode);
            }
        }
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }
    
    // =================== Tick Logic ===================
    
    @Override
    public void serverTick(Level level, BlockPos pos, BlockState state, RepAE2BridgeBlockEntity blockEntity) {
        super.serverTick(level, pos, state, blockEntity);
        
        // Handle initialization delay
        if (initialized == 0) {
            initializationTicks++;
            
            if (initializationTicks >= INITIALIZATION_DELAY) {
                if (mainNode != null && !mainNode.isReady()) {
                    mainNode.create(level, pos);
                }
                
                initialized = 1;
                syncObject(initialized);
                syncObject(initializationTicks);
                
                if (Config.enableDebugLogging) {
                    LOGGER.info("Bridge at {}: Networks initialized successfully", pos);
                }
                
                // Request initial updates
                IStorageProvider.requestUpdate(mainNode);
                ICraftingProvider.requestUpdate(mainNode);
            }
            return;
        }
        
        // Only process if initialized
        if (initialized != 1) return;

        // Transfer items from output inventory to AE2 every 20 ticks
        if (level.getGameTime() % 20 == 0) {
            transferItemsToAE2();
        }

        // Handle request accumulation
        requestCounterTicks++;
        if (requestCounterTicks >= REQUEST_ACCUMULATION_TICKS) {
            processAccumulatedRequests();
            requestCounterTicks = 0;
        }
    }

    @Override
    public void clientTick(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, 
                          @Nonnull RepAE2BridgeBlockEntity blockEntity) {
        super.clientTick(level, pos, state, blockEntity);
    }

    // =================== Item Transfer ===================
    
    /**
     * Transfer items from the output inventory to the AE2 network
     */
    private void transferItemsToAE2() {
        if (mainNode == null || !mainNode.isActive()) return;
        
        IGridNode node = mainNode.getNode();
        if (node == null) return;
        
        IGrid grid = node.getGrid();
        if (grid == null) return;
        
        IStorageService storageService = grid.getStorageService();
        if (storageService == null) return;

        // Try to insert each item from output inventory
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack stack = output.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            AEItemKey key = AEItemKey.of(stack);
            long inserted = storageService.getInventory().insert(
                key, stack.getCount(), Actionable.MODULATE, new MachineSource(this));

            if (inserted > 0) {
                stack.shrink((int) inserted);
                output.setStackInSlot(i, stack);
            }
        }
    }

    // =================== ICraftingProvider ===================
    
    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (initialized != 1) {
            return List.of();
        }

        List<IPatternDetails> patterns = new ArrayList<>();
        MatterNetwork network = getNetwork();
        if (network == null) {
            if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge: getAvailablePatterns called but network is null");
            }
            return patterns;
        }

        // For each chip storage in the network
        for (NetworkElement chipSupplier : network.getChipSuppliers()) {
            var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
            if (!(tile instanceof ChipStorageBlockEntity chipStorage)) continue;

            // For each pattern in the chip storage  
            for (MatterPattern pattern : chipStorage.getPatterns(chipStorage)) {
                if (pattern.getStack().isEmpty() || pattern.getCompletion() != 1) continue;

                try {
                    // Get the matter compound for this pattern
                    var matterCompound = ReplicationCalculation.getMatterCompound(pattern.getStack());
                    if (matterCompound == null) continue;

                    // Create the processing pattern with matter requirements
                    List<GenericStack> inputsList = new ArrayList<>();

                    // Add each type of matter required as input
                    for (MatterValue matterValue : matterCompound.getValues().values()) {
                        var matterType = matterValue.getMatter();
                        var matterAmount = (long) Math.ceil(matterValue.getAmount());

                        Item matterItem = getItemForMatterType(matterType);
                        if (matterItem != null) {
                            inputsList.add(new GenericStack(AEItemKey.of(matterItem), matterAmount));
                        }
                    }

                    List<GenericStack> outputsList = new ArrayList<>();
                    outputsList.add(new GenericStack(AEItemKey.of(pattern.getStack().getItem()), 1));

                    // Convert to arrays
                    GenericStack[] inputs = inputsList.toArray(new GenericStack[0]);
                    GenericStack[] outputs = outputsList.toArray(new GenericStack[0]);

                    // Encode the pattern using ProcessingPatternItem
                    var processingPatternItem = (appeng.crafting.pattern.ProcessingPatternItem) AEItems.PROCESSING_PATTERN.asItem();
                    ItemStack patternStack = processingPatternItem.encode(inputs, outputs);
                    
                    AEProcessingPattern aePattern = new AEProcessingPattern(AEItemKey.of(patternStack));

                    patterns.add(aePattern);
                } catch (Exception e) {
                    // Ignore pattern conversion errors
                }
            }
        }
        return patterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (initialized != 1) return false;

        MatterNetwork network = getNetwork();
        if (network == null || !isActive() || patternDetails == null) return false;

        // Check if the pattern produces an item that can be replicated
        GenericStack primaryOutput = patternDetails.getPrimaryOutput();
        if (primaryOutput == null || !(primaryOutput.what() instanceof AEItemKey itemKey)) return false;

        // Search for the pattern in all chip storage in the network
        for (NetworkElement chipSupplier : network.getChipSuppliers()) {
            var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
            if (!(tile instanceof ChipStorageBlockEntity chipStorage)) continue;

            for (MatterPattern pattern : chipStorage.getPatterns(chipStorage)) {
                if (!pattern.getStack().getItem().equals(itemKey.getItem())) continue;

                // Check if we have enough matter
                var matterCompound = ReplicationCalculation.getMatterCompound(pattern.getStack());
                if (matterCompound == null) continue;

                boolean hasAllMatter = true;
                for (MatterValue matterValue : matterCompound.getValues().values()) {
                    var matterType = matterValue.getMatter();
                    var matterAmount = (long) Math.ceil(matterValue.getAmount());
                    long available = network.calculateMatterAmount(matterType);

                    if (available < matterAmount) {
                        hasAllMatter = false;
                        
                        // Log warning (throttled)
                        String warningKey = itemKey.getItem().getDescriptionId() + ":" + matterType.getName();
                        long currentTime = level.getGameTime();
                        if (!lastMatterWarnings.containsKey(warningKey) ||
                            currentTime - lastMatterWarnings.get(warningKey) > WARNING_COOLDOWN) {
                            lastMatterWarnings.put(warningKey, currentTime);
                        }
                        break;
                    }
                }

                if (!hasAllMatter) return false;

                // Extract virtual matter (just for accounting)
                for (MatterValue matterValue : matterCompound.getValues().values()) {
                    var matterType = matterValue.getMatter();
                    var matterAmount = (long) Math.ceil(matterValue.getAmount());
                    Item matterItem = getItemForMatterType(matterType);
                    if (matterItem != null) {
                        AEItemKey matterKey = AEItemKey.of(matterItem);
                        matterItemsStorage.extract(matterKey, matterAmount, Actionable.MODULATE, new MachineSource(this));
                    }
                }

                // Add to request counter
                ItemStack itemStack = pattern.getStack();
                ItemWithSourceId key = new ItemWithSourceId(itemStack, blockId);

                Map<ItemWithSourceId, Integer> sourceCounters = requestCounters.getOrDefault(blockId, new HashMap<>());
                int currentCount = sourceCounters.getOrDefault(key, 0);
                sourceCounters.put(key, currentCount + 1);
                requestCounters.put(blockId, sourceCounters);

                if (Config.enableDebugLogging) {
                    LOGGER.info("Bridge: Accepted pattern request for {}, total pending: {}", 
                        itemKey.getItem().getDescriptionId(), currentCount + 1);
                }

                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        MatterNetwork network = getNetwork();
        if (network == null) return false;

        // Check if there are pending tasks
        boolean busy = !network.getTaskManager().getPendingTasks().isEmpty();

        // Update storage when not busy
        if (!busy && requestCounters.isEmpty()) {
            IStorageProvider.requestUpdate(mainNode);
        }

        return busy;
    }

    @Override
    public int getPatternPriority() {
        return priority;
    }

    // =================== Request Accumulation ===================
    
    /**
     * Process accumulated requests and create replication tasks
     */
    private void processAccumulatedRequests() {
        if (requestCounters.isEmpty()) return;

        MatterNetwork network = getNetwork();
        if (network == null) {
            if (Config.enableDebugLogging) {
                LOGGER.warn("Bridge: Cannot process accumulated requests - network is null");
            }
            return;
        }

        // Process each source's requests
        for (UUID sourceId : new HashSet<>(requestCounters.keySet())) {
            Map<ItemWithSourceId, Integer> sourceCounters = requestCounters.get(sourceId);
            if (sourceCounters == null) continue;

            // Process each item
            for (Map.Entry<ItemWithSourceId, Integer> entry : new HashMap<>(sourceCounters).entrySet()) {
                ItemWithSourceId key = entry.getKey();
                int count = entry.getValue();

                // Create replication task
                try {
                    ReplicationTask task = new ReplicationTask(
                        key.itemStack.copy(),
                        count,
                        IReplicationTask.Mode.MULTIPLE,
                        this.worldPosition
                    );

                    // Add task to network
                    String taskId = task.getUuid().toString();
                    network.getTaskManager().getPendingTasks().put(taskId, task);

                    if (Config.enableDebugLogging) {
                        LOGGER.info("Bridge: Created replication task for {} x{} (ID: {})", 
                            key.itemStack.getItem().getDescriptionId(), count, taskId);
                    }

                    // Track active task
                    Map<String, TaskSourceInfo> sourceTasks = activeTasks.getOrDefault(sourceId, new HashMap<>());
                    sourceTasks.put(taskId, new TaskSourceInfo(key.itemStack, sourceId));
                    activeTasks.put(sourceId, sourceTasks);
                } catch (Exception e) {
                    if (Config.enableDebugLogging) {
                        LOGGER.error("Bridge: Failed to create replication task: {}", e.getMessage());
                    }
                }
            }
        }

        // Clear request counters
        requestCounters.clear();
    }

    // =================== IStorageProvider ===================
    
    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (initialized != 1) return;
        storageMounts.mount(matterItemsStorage, 100);
    }

    // =================== IPriorityHost ===================
    
    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
        saveChanges();
    }

    // =================== Helper Methods ===================
    
    /**
     * Check if the bridge is active and connected
     */
    public boolean isActive() {
        return mainNode != null && mainNode.isActive() && mainNode.getNode() != null;
    }
    
    /**
     * Get the output inventory (for GUI)
     */
    public InventoryComponent<RepAE2BridgeBlockEntity> getOutput() {
        return output;
    }

    /**
     * Convert IMatterType to virtual Item
     */
    private Item getItemForMatterType(IMatterType type) {
        String name = type.getName();
        if (name.equalsIgnoreCase("earth")) return ModItems.EARTH_MATTER.get();
        if (name.equalsIgnoreCase("nether")) return ModItems.NETHER_MATTER.get();
        if (name.equalsIgnoreCase("organic")) return ModItems.ORGANIC_MATTER.get();
        if (name.equalsIgnoreCase("ender")) return ModItems.ENDER_MATTER.get();
        if (name.equalsIgnoreCase("metallic")) return ModItems.METALLIC_MATTER.get();
        if (name.equalsIgnoreCase("precious")) return ModItems.PRECIOUS_MATTER.get();
        if (name.equalsIgnoreCase("living")) return ModItems.LIVING_MATTER.get();
        if (name.equalsIgnoreCase("quantum")) return ModItems.QUANTUM_MATTER.get();
        return null;
    }

    /**
     * Convert virtual Item to IMatterType
     */
    private IMatterType getMatterTypeForItem(Item item) {
        if (item == ModItems.EARTH_MATTER.get()) return ReplicationRegistry.Matter.EARTH.get();
        if (item == ModItems.NETHER_MATTER.get()) return ReplicationRegistry.Matter.NETHER.get();
        if (item == ModItems.ORGANIC_MATTER.get()) return ReplicationRegistry.Matter.ORGANIC.get();
        if (item == ModItems.ENDER_MATTER.get()) return ReplicationRegistry.Matter.ENDER.get();
        if (item == ModItems.METALLIC_MATTER.get()) return ReplicationRegistry.Matter.METALLIC.get();
        if (item == ModItems.PRECIOUS_MATTER.get()) return ReplicationRegistry.Matter.PRECIOUS.get();
        if (item == ModItems.LIVING_MATTER.get()) return ReplicationRegistry.Matter.LIVING.get();
        if (item == ModItems.QUANTUM_MATTER.get()) return ReplicationRegistry.Matter.QUANTUM.get();
        return null;
    }

    /**
     * Check if an item is a virtual matter item
     */
    private boolean isVirtualMatterItem(Item item) {
        return item == ModItems.EARTH_MATTER.get()
                || item == ModItems.NETHER_MATTER.get()
                || item == ModItems.ORGANIC_MATTER.get()
                || item == ModItems.ENDER_MATTER.get()
                || item == ModItems.METALLIC_MATTER.get()
                || item == ModItems.PRECIOUS_MATTER.get()
                || item == ModItems.LIVING_MATTER.get()
                || item == ModItems.QUANTUM_MATTER.get();
    }

    // =================== Energy ===================
    
    @Nonnull
    @Override
    protected EnergyStorageComponent<RepAE2BridgeBlockEntity> createEnergyStorage() {
        // Create a minimal energy storage (1 FE capacity)
        // The bridge doesn't use FE/RF energy, only AE2 energy
        // This is just to satisfy the ReplicationMachine parent class requirement
        return new EnergyStorageComponent<>(1, 0, 0);
    }

    @Override
    public int getTitleColor() {
        return 0x00BFFF;
    }

    @Override
    public float getTitleYPos(float titleWidth, float screenWidth, float screenHeight, 
                             float guiWidth, float guiHeight) {
        return super.getTitleYPos(titleWidth, screenWidth, screenHeight, guiWidth, guiHeight) - 16;
    }
    
    // =================== GUI ===================
    
    @Override
    public net.minecraft.world.InteractionResult onActivated(Player playerIn, net.minecraft.world.InteractionHand hand, 
                                                            Direction facing, double hitX, double hitY, double hitZ) {
        // Open priority GUI instead of Replication GUI
        if (!level.isClientSide() && playerIn instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            openGui(serverPlayer);
            if (Config.enableDebugLogging) {
                LOGGER.info("Bridge: Opening priority GUI for player {}", playerIn.getName().getString());
            }
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    
    @Override
    public void openGui(@Nonnull Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // Open the priority menu using AE2's menu system
            appeng.menu.MenuOpener.open(appeng.menu.implementations.PriorityMenu.TYPE, serverPlayer, 
                appeng.menu.locator.MenuLocators.forBlockEntity(this));
        }
    }

    // =================== NBT ===================
    
    @Override
    public void load(@Nonnull CompoundTag data) {
        super.load(data);
        if (mainNode != null) {
            mainNode.loadFromNBT(data);
        }
    }

    @Override
    public void saveAdditional(@Nonnull CompoundTag data) {
        super.saveAdditional(data);
        if (mainNode != null) {
            mainNode.saveToNBT(data);
        }
    }

    // =================== Lifecycle ===================
    
    @Override
    public void setRemoved() {
        super.setRemoved();
        
        if (mainNode != null) {
            mainNode.destroy();
        }
        
        if (RepAE2Bridge.worldUnloading) {
            initialized = 2;
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        
        if (mainNode != null) {
            mainNode.destroy();
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        initialized = 0;
        initializationTicks = 0;
    }

    // =================== Capabilities ===================
    
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == Capabilities.IN_WORLD_GRID_NODE_HOST) {
            return LazyOptional.of(() -> this).cast();
        }
        
        // Expose output inventory as IItemHandler from top
        if (cap == ForgeCapabilities.ITEM_HANDLER && side == Direction.UP) {
            return LazyOptional.of(() -> (IItemHandler) output).cast();
        }
        
        return super.getCapability(cap, side);
    }

    // =================== ISubMenuHost (from Titanium) ===================
    
    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(ModBlocks.REP_AE2_BRIDGE_ITEM.get());
    }
    
    @Override
    public void returnToMainMenu(Player player, appeng.menu.ISubMenu subMenu) {
        // Return to main menu
    }

    // =================== Inner Classes ===================
    
    /**
     * Storage for virtual matter items
     */
    private class MatterItemsStorage implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0; // Read-only
        }
        
        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (initialized != 1) return 0;

            if (what instanceof AEItemKey itemKey && isVirtualMatterItem(itemKey.getItem())) {
                var network = getNetwork();
                if (network != null) {
                    IMatterType matterType = getMatterTypeForItem(itemKey.getItem());
                    if (matterType != null) {
                        long available = network.calculateMatterAmount(matterType);
                        long toExtract = Math.min(amount, available);

                        if (mode == Actionable.SIMULATE) {
                            return toExtract;
                        }

                        return toExtract > 0 ? toExtract : 0;
                    }
                }
            }
            return 0;
        }
        
        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (initialized != 1) return;

            var network = getNetwork();
            if (network != null) {
                try {
                    addMatterToOutput(ReplicationRegistry.Matter.EARTH.get(), out, network);
                    addMatterToOutput(ReplicationRegistry.Matter.NETHER.get(), out, network);
                    addMatterToOutput(ReplicationRegistry.Matter.ORGANIC.get(), out, network);
                    addMatterToOutput(ReplicationRegistry.Matter.ENDER.get(), out, network);
                    addMatterToOutput(ReplicationRegistry.Matter.METALLIC.get(), out, network);
                    addMatterToOutput(ReplicationRegistry.Matter.PRECIOUS.get(), out, network);
                    addMatterToOutput(ReplicationRegistry.Matter.LIVING.get(), out, network);
                    addMatterToOutput(ReplicationRegistry.Matter.QUANTUM.get(), out, network);
                } catch (Exception e) {
                    // Ignore errors
                }
            }
        }
        
        private void addMatterToOutput(IMatterType matterType, KeyCounter out, MatterNetwork network) {
            long amount = network.calculateMatterAmount(matterType);
            if (amount > 0) {
                Item item = getItemForMatterType(matterType);
                if (item != null) {
                    out.add(AEItemKey.of(item), amount);
                }
            }
        }
        
        @Override
        public Component getDescription() {
            return Component.literal("Replication Matter Storage");
        }
    }

    /**
     * Helper class to track items with source ID
     */
    private static class ItemWithSourceId {
        private final ItemStack itemStack;
        private final UUID sourceId;

        public ItemWithSourceId(ItemStack itemStack, UUID sourceId) {
            this.itemStack = itemStack.copy();
            this.sourceId = sourceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemWithSourceId)) return false;
            ItemWithSourceId that = (ItemWithSourceId) o;
            return ItemStack.isSameItemSameTags(itemStack, that.itemStack) && 
                   Objects.equals(sourceId, that.sourceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemStack.getItem(), sourceId);
        }
    }

    /**
     * Helper class to track task source info
     */
    private static class TaskSourceInfo {
        private final ItemStack itemStack;
        private final UUID sourceId;

        public TaskSourceInfo(ItemStack itemStack, UUID sourceId) {
            this.itemStack = itemStack.copy();
            this.sourceId = sourceId;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public UUID getSourceId() {
            return sourceId;
        }
    }
}
