package net.unfamily.repae2bridge.parts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.me.helpers.MachineSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.api.task.ReplicationTask;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.calculation.MatterValue;
import com.buuz135.replication.calculation.ReplicationCalculation;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.hrznstudio.titanium.block_network.element.NetworkElement;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.ae2.Ae2MatterHost;
import net.unfamily.repae2bridge.ae2.Ae2MatterTank;
import net.unfamily.repae2bridge.ae2.IRepAe2BridgeHost;
import net.unfamily.repae2bridge.ae2.ReplicationMatterKey;
import net.unfamily.repae2bridge.ae2.encoding.ReplicationBridgePatternDetails;
import net.unfamily.repae2bridge.block.entity.BridgeMatterFederation;
import net.unfamily.repae2bridge.block.entity.RepAE2BridgeBlockEntity;
import net.unfamily.repae2bridge.menu.BridgeMenu;

/**
 * Flat cable-mounted form of the AE2-Replication bridge (same GUI/lang as the block).
 * Exposes local-network matter to AE2 (storage + crafting patterns).
 */
public class CableRepAe2BridgePart extends AEBasePart
        implements IRepAe2BridgeHost, IStorageProvider, ICraftingProvider, IGridTickable, Ae2MatterHost {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Active cable bridges — used to force Titanium disconnect during world unload. */
    private static final Set<CableRepAe2BridgePart> activeCableBridges = new HashSet<>();

    private boolean feedReplicationEnergy = true;
    private int priority;
    private final MatterItemsStorage matterItemsStorage = new MatterItemsStorage();
    private List<Ae2MatterTank> ae2MatterTanks;

    public CableRepAe2BridgePart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0)
                .addService(IStorageProvider.class, this)
                .addService(ICraftingProvider.class, this)
                .addService(IGridTickable.class, this);
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getMainNode().isReady()) {
            IStorageProvider.requestUpdate(getMainNode());
            ICraftingProvider.requestUpdate(getMainNode());
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(4, 4, 12, 12, 12, 14);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 2;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide()) {
            MenuOpener.open(BridgeMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        if (RepAE2BridgeBlockEntity.isWorldUnloading()) {
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: addToWorld skipped network join — world unloading",
                        getLocationInfo());
            }
            return;
        }
        if (!isClientSide()) {
            activeCableBridges.add(this);
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: Registered in active cable bridges (total: {})",
                        getLocationInfo(), activeCableBridges.size());
            }
        }
        registerMatterElement();
        notifyAdjacentPipes();
        if (getMainNode().isReady()) {
            IStorageProvider.requestUpdate(getMainNode());
            ICraftingProvider.requestUpdate(getMainNode());
        }
    }

    @Override
    public void removeFromWorld() {
        boolean unloading = RepAE2BridgeBlockEntity.isWorldUnloading();
        if (!isClientSide()) {
            activeCableBridges.remove(this);
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: Removed from active cable bridges (remaining: {})",
                        getLocationInfo(), activeCableBridges.size());
            }
        }
        if (unloading) {
            // Force remove + skip pipe neighbor updates — neighborChanged during save hangs Titanium
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: removeFromWorld during unload — force disconnect, skip pipe notify",
                        getLocationInfo());
            }
            disconnectFromMatterNetwork();
        } else {
            unregisterMatterElementIfOrphaned();
            notifyAdjacentPipes();
        }
        super.removeFromWorld();
    }

    private String getLocationInfo() {
        Level level = getLevel();
        BlockPos pos = getBlockPos();
        Direction side = getSide();
        String dim = level != null ? level.dimension().identifier().toString() : "unknown";
        String face = side != null ? side.getSerializedName() : "unknown";
        return " [Dimension: " + dim + "] [Position: " + pos.getX() + ", " + pos.getY() + ", "
                + pos.getZ() + "] [Side: " + face + "]";
    }

    /**
     * Force-remove this cable's matter network element (no orphan check).
     * Used during world unload so Titanium NetworkManager does not hang.
     */
    public void disconnectFromMatterNetwork() {
        Level level = getLevel();
        BlockPos pos = getBlockPos();
        if (level == null || level.isClientSide() || pos.equals(BlockPos.ZERO)) {
            return;
        }
        try {
            NetworkManager manager = NetworkManager.get(level);
            if (manager != null) {
                NetworkElement element = manager.getElement(pos);
                if (element != null) {
                    LOGGER.info("CableBridge{}: Removing from Titanium/Replication NetworkManager",
                            getLocationInfo());
                    manager.removeElement(pos);
                    LOGGER.info("CableBridge{}: Titanium/Replication network disconnected",
                            getLocationInfo());
                } else if (Config.enableDebugLogging) {
                    LOGGER.info("CableBridge{}: No matter network element to remove", getLocationInfo());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("CableBridge{}: Error disconnecting from Titanium/Replication network: {}",
                    getLocationInfo(), e.getMessage());
        }
    }

    /**
     * Disconnect all active cable bridges from Titanium/Replication during world unloading.
     */
    public static void disconnectAllCableBridgesFromNetworks() {
        List<CableRepAe2BridgePart> parts = new ArrayList<>(activeCableBridges);
        LOGGER.info("CableRepAe2Bridge: Starting matter network disconnection for {} cable bridges",
                parts.size());

        int disconnected = 0;
        int failed = 0;
        Set<String> clearedPositions = new HashSet<>();

        for (CableRepAe2BridgePart part : parts) {
            try {
                Level level = part.getLevel();
                BlockPos pos = part.getBlockPos();
                if (level == null || pos.equals(BlockPos.ZERO)) {
                    if (Config.enableDebugLogging) {
                        LOGGER.info("CableRepAe2Bridge: Skipping cable bridge with invalid level/pos{}",
                                part.getLocationInfo());
                    }
                    continue;
                }
                // One NetworkManager entry per cable-bus position — remove once
                String posKey = level.dimension().identifier() + "|" + pos.asLong();
                if (!clearedPositions.add(posKey)) {
                    if (Config.enableDebugLogging) {
                        LOGGER.info("CableRepAe2Bridge: Skipping duplicate pos for cable bridge at {}",
                                part.getLocationInfo());
                    }
                    continue;
                }
                LOGGER.info("CableRepAe2Bridge: Disconnecting cable bridge at {}",
                        part.getLocationInfo());
                part.disconnectFromMatterNetwork();
                disconnected++;
            } catch (Exception e) {
                LOGGER.warn("CableRepAe2Bridge: Failed to disconnect cable bridge: {}", e.getMessage());
                failed++;
            }
        }

        activeCableBridges.clear();
        LOGGER.info("CableRepAe2Bridge: Cable matter network disconnection completed - {} successful, {} failed",
                disconnected, failed);
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        data.putBoolean("feedReplicationEnergy", feedReplicationEnergy);
        data.putInt("priority", priority);
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        feedReplicationEnergy = data.getBooleanOr("feedReplicationEnergy", true);
        priority = data.getIntOr("priority", 0);
    }

    @Override
    public boolean isFeedReplicationEnergy() {
        return feedReplicationEnergy;
    }

    @Override
    public void setFeedReplicationEnergy(boolean enabled) {
        this.feedReplicationEnergy = enabled;
        getHost().markForSave();
    }


    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        getHost().markForSave();
        if (getMainNode().isReady()) {
            ICraftingProvider.requestUpdate(getMainNode());
            IStorageProvider.requestUpdate(getMainNode());
        }
    }

    /** Virtual AE2 cell tanks exposed to Replication via CableBus mixin. */
    public List<? extends Ae2MatterTank> getAe2MatterTanks() {
        return getOrCreateAe2MatterTanks();
    }

    private List<Ae2MatterTank> getOrCreateAe2MatterTanks() {
        int expected = 0;
        for (IMatterType type : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
            if (type != null && !type.equals(MatterType.EMPTY)) {
                expected++;
            }
        }
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

    @Override
    public long getAe2MatterAmount(IMatterType type) {
        ReplicationMatterKey key = ReplicationMatterKey.of(type);
        if (key == null || !getMainNode().isReady()) {
            return 0;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return 0;
        }
        var stacks = storage.getInventory().getAvailableStacks();
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
        long externalLocal = BridgeMatterFederation.sumPhysicalMatter(
                BridgeMatterFederation.localHolders(getMatterNetwork(), getBlockPos()), type);
        return Math.max(0, amount - externalLocal);
    }

    @Override
    public long getAe2MatterFreeSpace(IMatterType type) {
        ReplicationMatterKey key = ReplicationMatterKey.of(type);
        if (key == null || !getMainNode().isReady()) {
            return 0;
        }
        IGrid grid = getMainNode().getGrid();
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

    @Override
    public long insertAe2Matter(IMatterType type, long amount, boolean simulate) {
        ReplicationMatterKey key = ReplicationMatterKey.of(type);
        if (key == null || amount <= 0 || !getMainNode().isReady()) {
            return 0;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return 0;
        }
        return storage.getInventory().insert(
                key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, new MachineSource(this));
    }

    @Override
    public long extractAe2Matter(IMatterType type, long amount, boolean simulate) {
        ReplicationMatterKey key = ReplicationMatterKey.of(type);
        if (key == null || amount <= 0 || !getMainNode().isReady()) {
            return 0;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        IStorageService storage = grid.getStorageService();
        if (storage == null) {
            return 0;
        }
        return storage.getInventory().extract(
                key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, new MachineSource(this));
    }

    @Override
    public void notifyAe2MatterChanged(IMatterType type) {
        MatterNetwork network = getMatterNetwork();
        if (network != null && type != null && !type.equals(MatterType.EMPTY)) {
            network.onTankValueChanged(type);
        }
        if (getMainNode().isReady()) {
            IStorageProvider.requestUpdate(getMainNode());
        }
        if (getHost() != null) {
            getHost().markForSave();
        }
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getPartItem());
    }

    @Override
    public void returnToMainMenu(Player player, appeng.menu.ISubMenu subMenu) {
        MenuOpener.open(BridgeMenu.TYPE, player, MenuLocators.forPart(this));
    }

    @Override
    public IGridNode getActionableNode() {
        return getGridNode();
    }

    @Override
    public Level getLevel() {
        return getHost() != null && getHost().getBlockEntity() != null
                ? getHost().getBlockEntity().getLevel()
                : null;
    }

    @Override
    public BlockPos getBlockPos() {
        return getHost() != null && getHost().getBlockEntity() != null
                ? getHost().getBlockEntity().getBlockPos()
                : BlockPos.ZERO;
    }

    @Nullable
    public MatterNetwork getMatterNetwork() {
        Level level = getLevel();
        BlockPos pos = getBlockPos();
        if (level == null || pos.equals(BlockPos.ZERO)) {
            return null;
        }
        NetworkManager manager = NetworkManager.get(level);
        if (manager == null) {
            return null;
        }
        NetworkElement element = manager.getElement(pos);
        if (element != null && element.getNetwork() instanceof MatterNetwork matter) {
            return matter;
        }
        return null;
    }

    public boolean isPartActive() {
        return getMainNode().isOnline();
    }

    public static boolean hasBridgePartFacing(Level level, BlockPos cablePos, Direction side) {
        if (level == null || cablePos == null || side == null) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(cablePos);
        if (!(be instanceof IPartHost host)) {
            return false;
        }
        IPart part = host.getPart(side);
        return part instanceof CableRepAe2BridgePart;
    }

    // --- IStorageProvider ---

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        storageMounts.mount(matterItemsStorage, 100);
    }

    private final class MatterItemsStorage implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!isPartActive() || amount <= 0 || !(what instanceof ReplicationMatterKey matterKey)) {
                return 0;
            }
            IMatterType type = matterKey.resolveMatterType();
            if (type == null || type.equals(MatterType.EMPTY)) {
                return 0;
            }
            long drained = BridgeMatterFederation.drainPhysicalMatter(
                    BridgeMatterFederation.localHolders(getMatterNetwork(), getBlockPos()),
                    type,
                    amount,
                    mode == Actionable.SIMULATE);
            if (!mode.isSimulate() && drained > 0) {
                MatterNetwork network = getMatterNetwork();
                if (network != null) {
                    network.onTankValueChanged(type);
                }
                getHost().markForSave();
            }
            return drained;
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("Replication Matter Storage");
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (!isPartActive()) {
                return;
            }
            MatterNetwork local = getMatterNetwork();
            if (local == null) {
                return;
            }
            for (IMatterType matterType : ReplicationRegistry.MATTER_TYPES_REGISTRY) {
                if (matterType == null || matterType.equals(MatterType.EMPTY)) {
                    continue;
                }
                long amount = BridgeMatterFederation.sumPhysicalMatter(
                        BridgeMatterFederation.localHolders(local, getBlockPos()), matterType);
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

    // --- ICraftingProvider ---

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        List<IPatternDetails> patterns = new ArrayList<>();
        MatterNetwork network = getMatterNetwork();
        Level level = getLevel();
        if (network == null || level == null || !isPartActive()) {
            return patterns;
        }
        for (NetworkElement chipSupplier : network.getChipSuppliers()) {
            var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
            if (tile instanceof ChipStorageBlockEntity chipStorage) {
                for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                    if (!pattern.getStack().isEmpty() && pattern.getCompletion() == 1) {
                        if (ReplicationCalculation.getMatterCompound(pattern.getStack()) == null) {
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
        return patterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        MatterNetwork network = getMatterNetwork();
        Level level = getLevel();
        if (network == null || level == null || !isPartActive() || patternDetails == null) {
            return false;
        }
        if (patternDetails.getOutputs().size() != 1) {
            return false;
        }
        var output = patternDetails.getOutputs().iterator().next();
        if (!(output.what() instanceof AEItemKey itemKey)) {
            return false;
        }

        ItemStack patternStack = ItemStack.EMPTY;
        for (NetworkElement chipSupplier : network.getChipSuppliers()) {
            var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
            if (!(tile instanceof ChipStorageBlockEntity chipStorage)) {
                continue;
            }
            for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                if (pattern.getStack().getItem().equals(itemKey.getItem()) && pattern.getCompletion() == 1) {
                    patternStack = pattern.getStack();
                    break;
                }
            }
            if (!patternStack.isEmpty()) {
                break;
            }
        }
        if (patternStack.isEmpty()) {
            return false;
        }

        if (!restoreCraftMatterToNetwork(inputHolder, patternStack)) {
            return false;
        }

        ReplicationTask task = new ReplicationTask(
                patternStack,
                1,
                IReplicationTask.Mode.MULTIPLE,
                getBlockPos(),
                false);
        String taskId = task.getUuid().toString();
        network.getTaskManager().getPendingTasks().put(taskId, task);
        if (level instanceof ServerLevel serverLevel) {
            network.onTaskValueChanged(task, serverLevel);
        }
        getHost().markForSave();
        return true;
    }

    private boolean restoreCraftMatterToNetwork(KeyCounter[] inputHolder, ItemStack patternStack) {
        var matterCompound = ReplicationCalculation.getMatterCompound(patternStack);
        if (matterCompound == null) {
            return true;
        }

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
            if (delivered.getOrDefault(type, 0L) < needed) {
                return false;
            }
        }

        var holders = BridgeMatterFederation.localHolders(getMatterNetwork(), getBlockPos());
        for (MatterValue matterValue : matterCompound.getValues().values()) {
            IMatterType type = matterValue.getMatter();
            long needed = (long) Math.ceil(matterValue.getAmount());
            if (needed <= 0) {
                continue;
            }
            long filled = BridgeMatterFederation.fillPhysicalMatter(holders, type, needed, false);
            if (filled < needed) {
                return false;
            }
            MatterNetwork network = getMatterNetwork();
            if (network != null) {
                network.onTankValueChanged(type);
            }
        }
        return true;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public int getPatternPriority() {
        return priority;
    }

    // --- IGridTickable ---

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(10, 40, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (isClientSide()) {
            return TickRateModulation.SLEEP;
        }
        if (RepAE2BridgeBlockEntity.isWorldUnloading()) {
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: Tick skipped — world unloading", getLocationInfo());
            }
            return TickRateModulation.SLEEP;
        }
        if (getMatterNetwork() == null) {
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: Matter network missing — attempting register",
                        getLocationInfo());
            }
            registerMatterElement();
        }
        feedEnergyToMatterNetwork();
        Level level = getLevel();
        if (level != null && level.getGameTime() % 20 == 0 && getMainNode().isReady()) {
            IStorageProvider.requestUpdate(getMainNode());
            ICraftingProvider.requestUpdate(getMainNode());
        }
        return TickRateModulation.SAME;
    }

    private void feedEnergyToMatterNetwork() {
        if (!feedReplicationEnergy || !isPartActive()) {
            return;
        }
        MatterNetwork network = getMatterNetwork();
        IGrid grid = getMainNode().getGrid();
        if (network == null || grid == null) {
            return;
        }

        var energyStorage = network.getEnergyStorage();
        int freeFe = energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored();
        if (freeFe <= 0) {
            return;
        }

        var energyService = grid.getEnergyService();
        double stored = energyService.getStoredPower();
        double idle = energyService.getIdlePowerUsage();
        if (stored <= idle) {
            return;
        }

        double aeWanted = appeng.api.config.PowerUnit.FE.convertTo(appeng.api.config.PowerUnit.AE, freeFe);
        if (aeWanted <= 0) {
            return;
        }
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

    /** Public so device-federation can force Titanium registration if AE2 came up first. */
    public void ensureMatterNetworkRegistered() {
        registerMatterElement();
    }

    private void registerMatterElement() {
        if (RepAE2BridgeBlockEntity.isWorldUnloading()) {
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: Skipping matter register — world unloading",
                        getLocationInfo());
            }
            return;
        }
        Level level = getLevel();
        BlockPos pos = getBlockPos();
        if (level == null || level.isClientSide() || pos.equals(BlockPos.ZERO)) {
            return;
        }
        NetworkManager manager = NetworkManager.get(level);
        if (manager == null) {
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: NetworkManager unavailable — defer register",
                        getLocationInfo());
            }
            return;
        }
        NetworkElement existing = manager.getElement(pos);
        if (existing != null) {
            // Re-queue if this cable was classified before Ae2MatterTank exposure existed.
            if (existing.getNetwork() instanceof MatterNetwork matter) {
                var access = (net.unfamily.repae2bridge.mixin.MatterNetworkAccessor) (Object) matter;
                List<NetworkElement> holders = access.rep_ae2_bridge$getMatterStacksHoldersRaw();
                List<NetworkElement> queue = access.rep_ae2_bridge$getQueueNetworkElementsRaw();
                if (holders != null && queue != null && !holders.contains(existing) && !queue.contains(existing)) {
                    queue.add(existing);
                    if (Config.enableDebugLogging) {
                        LOGGER.info("CableBridge{}: Re-queued existing matter element for tank exposure",
                                getLocationInfo());
                    }
                }
            }
            return;
        }
        try {
            manager.addElement(new DefaultMatterNetworkElement(level, pos) {
                @Override
                public boolean canConnectFrom(Direction direction) {
                    if (level != null && !level.isLoaded(pos.relative(direction))) {
                        return false;
                    }
                    if (level != null
                            && level.getBlockEntity(pos.relative(direction)) instanceof RepAE2BridgeBlockEntity) {
                        return false;
                    }
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof IPartHost host) {
                        for (Direction face : Direction.values()) {
                            if (host.getPart(face) instanceof CableRepAe2BridgePart && face == direction) {
                                return true;
                            }
                        }
                        return false;
                    }
                    return false;
                }
            });
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: Registered matter network element", getLocationInfo());
            }
        } catch (RuntimeException e) {
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: Matter register deferred ({}): {}",
                        getLocationInfo(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private void unregisterMatterElementIfOrphaned() {
        Level level = getLevel();
        BlockPos pos = getBlockPos();
        if (level == null || level.isClientSide() || pos.equals(BlockPos.ZERO)) {
            return;
        }
        if (RepAE2BridgeBlockEntity.isWorldUnloading()) {
            disconnectFromMatterNetwork();
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IPartHost host) {
            for (Direction face : Direction.values()) {
                IPart part = host.getPart(face);
                if (part instanceof CableRepAe2BridgePart && part != this) {
                    if (Config.enableDebugLogging) {
                        LOGGER.info("CableBridge{}: Orphan unregister skipped — sibling on {} remains",
                                getLocationInfo(), face.getSerializedName());
                    }
                    return;
                }
            }
        }
        NetworkManager manager = NetworkManager.get(level);
        if (manager != null && manager.getElement(pos) != null) {
            try {
                manager.removeElement(pos);
                if (Config.enableDebugLogging) {
                    LOGGER.info("CableBridge{}: Unregistered orphaned matter network element",
                            getLocationInfo());
                }
            } catch (RuntimeException e) {
                if (Config.enableDebugLogging) {
                    LOGGER.info("CableBridge{}: Orphan unregister failed ({}): {}",
                            getLocationInfo(), e.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
    }

    private void notifyAdjacentPipes() {
        if (RepAE2BridgeBlockEntity.isWorldUnloading()) {
            if (Config.enableDebugLogging) {
                LOGGER.info("CableBridge{}: Skipping pipe neighbor notify — world unloading",
                        getLocationInfo());
            }
            return;
        }
        Level level = getLevel();
        BlockPos pos = getBlockPos();
        Direction side = getSide();
        if (level == null || level.isClientSide() || pos.equals(BlockPos.ZERO) || side == null) {
            return;
        }
        BlockPos pipePos = pos.relative(side);
        if (Config.enableDebugLogging) {
            LOGGER.info("CableBridge{}: Notifying adjacent pipe at {}", getLocationInfo(), pipePos);
        }
        level.neighborChanged(pipePos, level.getBlockState(pos).getBlock(), null);
    }
}
