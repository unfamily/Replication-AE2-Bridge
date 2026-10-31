package net.unfamily.repae2bridge.block.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AECableType;
import appeng.blockentity.crafting.CraftingCubeModelData;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.block.chamber.ChamberAe2Display;
import net.unfamily.repae2bridge.block.chamber.ChamberStructure;
import net.unfamily.repae2bridge.block.custom.ReplicationUnitBlock;

/**
 * Interior unit of a Matter Assembling Chamber.
 * <p>
 * Leader unit: AE2 channel, bridge replication jobs, power drain, network-tool identity.
 * All units: connected crafting-cube look + chamber-wide visual mode.
 */
public class ReplicationUnitBlockEntity extends BlockEntity implements IInWorldGridNodeHost {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum VisualMode {
        IDLE,
        RUNNING,
        ANOMALY
    }

    private IManagedGridNode mainNode = createMainNode();

    private boolean formed;
    private VisualMode mode = VisualMode.IDLE;
    private int checkCooldown;
    private int anomalyTicks;
    /** Keep green briefly after a batch ends so brief idle gaps do not flash purple. */
    private static final int RUNNING_HOLD_TICKS = 12;
    private int runningHoldTicks;
    @Nullable
    private ChamberStructure.FormedBox lastBox;
    private boolean linkedToFrame;
    private boolean lastSyncedOnline;

    private ItemStack craftingStack = ItemStack.EMPTY;
    private int remainingCount;
    private int progress;
    @Nullable
    private BlockPos bridgePos;
    @Nullable
    private UUID replicationTaskId;

    public ReplicationUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REPLICATION_UNIT_BE.get(), pos, state);
    }

    private IManagedGridNode createMainNode() {
        return GridHelper
                .createManagedNode(this, new IGridNodeListener<ReplicationUnitBlockEntity>() {
                    @Override
                    public void onSaveChanges(ReplicationUnitBlockEntity nodeOwner, IGridNode node) {
                        nodeOwner.setChanged();
                    }
                })
                .setVisualRepresentation((AEItemKey) null)
                .setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL)
                .addService(IGridMultiblock.class, this::getMultiblockNodes)
                .setIdlePowerUsage(0.0)
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.noneOf(Direction.class))
                .setTagName("replication_unit");
    }

    private Iterator<IGridNode> getMultiblockNodes() {
        List<IGridNode> nodes = new ArrayList<>();
        IGridNode self = mainNode.getNode();
        if (self != null) {
            nodes.add(self);
        }
        if (level == null || lastBox == null) {
            return nodes.iterator();
        }
        BlockPos.betweenClosedStream(lastBox.min(), lastBox.max()).forEach(pos -> {
            if (level.getBlockEntity(pos) instanceof ReplicationUnitBlockEntity unit) {
                IGridNode n = unit.mainNode.getNode();
                if (n != null && n != self) {
                    nodes.add(n);
                }
            } else if (level.getBlockEntity(pos) instanceof ChamberFrameBlockEntity frame) {
                IGridNode n = frame.getMainGridNode();
                if (n != null) {
                    nodes.add(n);
                }
            }
        });
        return nodes.iterator();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReplicationUnitBlockEntity be) {
        be.tickServer();
    }

    public void requestRecheck() {
        // Coalesce: already scheduled within the next 2 ticks — skip.
        if (checkCooldown == 1 || checkCooldown == 2) {
            return;
        }
        checkCooldown = 2;
    }

    private void tickServer() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (!Config.chamberAnomalyVisual) {
            anomalyTicks = 0;
        } else if (anomalyTicks > 0) {
            anomalyTicks--;
        }

        if (formed && isChamberLeader()) {
            tickJob();
            VisualMode before = mode;
            refreshModeFromJob();
            if (before != mode) {
                syncModeToChamber();
            }
            refreshPowerUsage();
            syncOnlineToClientIfNeeded();
        }

        if (--checkCooldown > 0) {
            return;
        }
        checkCooldown = 20;

        ChamberStructure.FormedBox box = ChamberStructure.findAround(level, worldPosition);
        boolean nowFormed = box != null;
        boolean formedChanged = nowFormed != formed;
        ChamberStructure.FormedBox previousBox = lastBox;
        formed = nowFormed;
        lastBox = box;

        if (!nowFormed) {
            anomalyTicks = 0;
            runningHoldTicks = 0;
            if (!craftingStack.isEmpty()) {
                clearJob();
            }
            mode = VisualMode.IDLE;
        } else if (isChamberLeader()) {
            // ~1/30 chance per structure recheck (~1/s) → about every 30s; lasts ~1.2s so the flash is visible.
            if (Config.chamberAnomalyVisual && anomalyTicks <= 0 && level.getRandom().nextInt(30) == 0) {
                anomalyTicks = 24;
            }
            if (!Config.chamberAnomalyVisual) {
                anomalyTicks = 0;
            }
            refreshModeFromJob();
            syncModeToChamber();
            refreshPowerUsage();
        }

        boolean wantPowered = formed && mainNode.isOnline();
        if (formedChanged || getBlockState().getValue(ReplicationUnitBlock.FORMED) != formed
                || getBlockState().getValue(ReplicationUnitBlock.POWERED) != wantPowered
                || getBlockState().getValue(ReplicationUnitBlock.MODE) != mode.ordinal()) {
            applyVisualState(wantPowered);
        }
        if (formedChanged) {
            updateAe2AndFrames(previousBox, box);
        } else if (formed && !linkedToFrame) {
            linkToNearestFrame(box);
        }
    }

    private static final int PROGRESS_PER_ITEM = 100;

    private void tickJob() {
        if (!mainNode.isOnline() || craftingStack.isEmpty() || remainingCount <= 0) {
            return;
        }
        // Replication terminal cancelTask only clears vanilla Replicators — self-heal stuck chamber jobs.
        if (replicationTaskId != null && !isBoundReplicationTaskAlive()) {
            abortJob();
            return;
        }
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return;
        }

        double energyCost = Config.chamberActivePowerAe;
        if (energyCost > 0) {
            IEnergyService energy = grid.getEnergyService();
            double extracted = energy.extractAEPower(energyCost, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (extracted < energyCost * 0.9) {
                return;
            }
        }

        progress += getProgressPointsPerTick();
        int want = Math.min(remainingCount, progress / PROGRESS_PER_ITEM);
        if (want <= 0) {
            return;
        }
        int done;
        if (replicationTaskId == null) {
            // AE2-internal: one drain + one AE2 insert for the whole tick (avoids TPS spikes).
            done = finishAe2InternalBatch(want);
        } else {
            done = 0;
            for (int i = 0; i < want; i++) {
                if (!finishOneUnit()) {
                    break;
                }
                done++;
            }
        }
        if (done > 0) {
            progress -= done * PROGRESS_PER_ITEM;
            setChanged();
        }
    }

    /**
     * Crafts up to {@code want} AE2-internal units with batched matter drain / AE2 insert.
     * @return how many units completed
     */
    private int finishAe2InternalBatch(int want) {
        if (level == null || craftingStack.isEmpty() || bridgePos == null || want <= 0) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(bridgePos);
        if (!(be instanceof RepAE2BridgeBlockEntity bridge)) {
            return 0;
        }
        int batch = want;
        while (batch > 0) {
            if (bridge.drainMatterForAe2Internal(craftingStack, batch)) {
                break;
            }
            if (batch == 1) {
                return 0;
            }
            batch = Math.max(1, batch / 2);
        }
        int inserted = bridge.insertCraftedToAe2(craftingStack, batch);
        if (inserted <= 0) {
            // Matter already drained — try one-by-one into buffer as last resort.
            if (bridge.receiveItemFromReplicator(craftingStack.copyWithCount(1))) {
                inserted = 1;
            } else {
                return 0;
            }
        }
        remainingCount -= inserted;
        if (remainingCount <= 0) {
            clearJob();
            refreshModeFromJob();
            syncModeToChamber();
        }
        return inserted;
    }

    /**
     * @return false if the output could not be delivered (job stays in place)
     */
    private boolean finishOneUnit() {
        if (level == null || craftingStack.isEmpty()) {
            return false;
        }
        ItemStack output = craftingStack.copyWithCount(1);
        boolean delivered = false;
        if (bridgePos != null) {
            BlockEntity be = level.getBlockEntity(bridgePos);
            if (be instanceof RepAE2BridgeBlockEntity bridge) {
                if (replicationTaskId != null) {
                    // Each unit needs its own matter reserve (batch jobs re-reserve after finalize).
                    if (!bridge.ensureMatterReservedForWorker(replicationTaskId, worldPosition)) {
                        return false;
                    }
                    delivered = bridge.deliverMatterAssemblerReplicationUnit(
                            output, replicationTaskId, worldPosition);
                } else {
                    if (!bridge.drainMatterForAe2Internal(craftingStack, 1)) {
                        return false;
                    }
                    delivered = bridge.receiveItemFromReplicator(output);
                }
            }
        }
        if (!delivered) {
            return false;
        }
        remainingCount--;
        if (remainingCount <= 0) {
            clearJob();
            refreshModeFromJob();
            syncModeToChamber();
        }
        return true;
    }

    /**
     * @return true if this chamber leader accepted the job
     */
    public boolean tryStartJob(ItemStack stack, int count, BlockPos bridgeSource, @Nullable UUID taskId) {
        if (!formed || !isChamberLeader() || !isIdle() || !mainNode.isOnline()
                || stack.isEmpty() || count <= 0 || bridgeSource == null) {
            return false;
        }
        this.craftingStack = stack.copyWithCount(1);
        this.remainingCount = count;
        this.progress = 0;
        this.bridgePos = bridgeSource.immutable();
        this.replicationTaskId = taskId;
        VisualMode before = mode;
        refreshModeFromJob();
        // Avoid block/model sync when hold already kept RUNNING — that caused a sprite snap.
        if (before != mode) {
            syncModeToChamber();
        }
        refreshPowerUsage();
        setChanged();
        return true;
    }

    /**
     * Grows an in-progress AE2-internal job (no Replication task id) so the chamber stays RUNNING
     * instead of flashing idle between small batches.
     */
    public boolean extendAe2InternalJob(ItemStack stack, int extra, BlockPos bridgeSource) {
        if (extra <= 0 || stack.isEmpty() || bridgeSource == null || replicationTaskId != null) {
            return false;
        }
        if (isIdle() || !formed || !isChamberLeader() || !mainNode.isOnline()) {
            return false;
        }
        if (!bridgeSource.equals(bridgePos) || !craftingStack.getItem().equals(stack.getItem())) {
            return false;
        }
        long sum = (long) remainingCount + extra;
        remainingCount = sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
        setChanged();
        return true;
    }

    public boolean isIdle() {
        return craftingStack.isEmpty() || remainingCount <= 0;
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public ItemStack getCraftingStack() {
        return craftingStack;
    }

    @Nullable
    public UUID getReplicationTaskId() {
        return replicationTaskId;
    }

    public boolean isWorking() {
        if (level != null && level.isClientSide()) {
            return formed && mode == VisualMode.RUNNING;
        }
        return formed && isChamberLeader() && !isIdle() && mainNode.isOnline();
    }

    public void abortJob() {
        clearJob();
        runningHoldTicks = 0;
        refreshModeFromJob();
        if (formed) {
            syncModeToChamber();
        }
        refreshPowerUsage();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private boolean isBoundReplicationTaskAlive() {
        if (replicationTaskId == null || level == null || bridgePos == null) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(bridgePos);
        return be instanceof RepAE2BridgeBlockEntity bridge
                && bridge.hasPendingReplicationTask(replicationTaskId);
    }

    private void clearJob() {
        craftingStack = ItemStack.EMPTY;
        remainingCount = 0;
        progress = 0;
        bridgePos = null;
        replicationTaskId = null;
        setChanged();
    }

    @Nullable
    public BlockPos getBridgePos() {
        return bridgePos;
    }

    /** Progress points added each tick (item completes at {@link #PROGRESS_PER_ITEM}). */
    public int getProgressPointsPerTick() {
        int speed = Math.max(1, Config.chamberSpeed);
        int units = lastBox != null && level != null
                ? Math.max(1, ChamberAe2Display.countUnits(level, lastBox))
                : 1;
        double mult = 1.0 + Config.chamberSpeedPerUnit * (units - 1);
        long ppt = Math.round(speed * mult);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, ppt));
    }

    /** Approximate ticks per item at current unit count (min 1). */
    public int getEffectiveMaxProgress() {
        return Math.max(1, (int) Math.ceil(PROGRESS_PER_ITEM / (double) getProgressPointsPerTick()));
    }

    public boolean isChamberLeader() {
        if (!formed || level == null || lastBox == null) {
            return false;
        }
        BlockPos leader = ChamberAe2Display.findLeaderUnit(level, lastBox);
        return leader != null && leader.equals(worldPosition);
    }

    /** True when this unit's chamber can accept bridge jobs. */
    public boolean isChamberWorkerReady() {
        return formed && isChamberLeader() && isIdle() && mainNode.isOnline();
    }

    public boolean isAe2Online() {
        if (level != null && level.isClientSide()) {
            return formed && (clientOnline
                    || (getBlockState().hasProperty(ReplicationUnitBlock.POWERED)
                    && getBlockState().getValue(ReplicationUnitBlock.POWERED)));
        }
        return formed && mainNode.isOnline();
    }

    @Nullable
    public ChamberStructure.FormedBox getLastBox() {
        return lastBox;
    }

    private void refreshModeFromJob() {
        if (!formed) {
            mode = VisualMode.IDLE;
            runningHoldTicks = 0;
            return;
        }
        if (Config.chamberAnomalyVisual && anomalyTicks > 0) {
            mode = VisualMode.ANOMALY;
            return;
        }
        if (!isIdle() && mainNode.isOnline()) {
            mode = VisualMode.RUNNING;
            runningHoldTicks = RUNNING_HOLD_TICKS;
        } else if (runningHoldTicks > 0) {
            runningHoldTicks--;
            mode = VisualMode.RUNNING;
        } else {
            mode = VisualMode.IDLE;
        }
    }

    private void syncModeToChamber() {
        if (level == null || lastBox == null) {
            return;
        }
        boolean online = mainNode.isOnline();
        for (BlockPos pos : BlockPos.betweenClosed(lastBox.min(), lastBox.max())) {
            if (!(level.getBlockEntity(pos) instanceof ReplicationUnitBlockEntity unit)) {
                continue;
            }
            unit.mode = this.mode;
            unit.anomalyTicks = this.anomalyTicks;
            unit.formed = true;
            unit.lastBox = lastBox;
            unit.clientOnline = online;
            unit.applyVisualState(online);
        }
    }

    private void refreshPowerUsage() {
        if (!isChamberLeader() || !formed) {
            mainNode.setIdlePowerUsage(0.0);
            return;
        }
        if (!mainNode.isOnline()) {
            mainNode.setIdlePowerUsage(0.0);
            return;
        }
        double passive = Config.chamberIdlePowerAe;
        // Active drain is extracted in tickJob; idle usage stays passive.
        mainNode.setIdlePowerUsage(passive);
    }

    private void applyVisualState() {
        applyVisualState(formed && mainNode.isOnline());
    }

    private void applyVisualState(boolean powered) {
        if (level == null) {
            return;
        }
        BlockState current = getBlockState();
        if (!(current.getBlock() instanceof ReplicationUnitBlock)) {
            return;
        }
        BlockState updated = current
                .setValue(ReplicationUnitBlock.FORMED, formed)
                .setValue(ReplicationUnitBlock.POWERED, powered)
                .setValue(ReplicationUnitBlock.MODE, mode.ordinal());
        if (updated == current && powered == lastSyncedOnline) {
            return;
        }
        if (updated != current) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
        }
        requestModelDataUpdate();
        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbor instanceof ReplicationUnitBlockEntity) {
                neighbor.requestModelDataUpdate();
            }
        }
        setChanged();
        lastSyncedOnline = powered;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Keep POWERED blockstate in sync when the AE2 node comes online after form. */
    private void syncOnlineToClientIfNeeded() {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean online = formed && mainNode.isOnline();
        if (online != lastSyncedOnline
                || getBlockState().getValue(ReplicationUnitBlock.POWERED) != online) {
            lastSyncedOnline = online;
            if (lastBox != null) {
                syncModeToChamber();
            } else {
                applyVisualState(online);
            }
        }
    }

    private void updateAe2AndFrames(@Nullable ChamberStructure.FormedBox previousBox,
            @Nullable ChamberStructure.FormedBox box) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (formed && box != null) {
            ensureAe2Node();
            refreshChamberNetworkVisuals(level, box);
            ChamberFrameBlockEntity.setFramesInBox(level, box, true);
            linkToNearestFrame(box);
            if (isChamberLeader()) {
                refreshPowerUsage();
            }
        } else {
            linkedToFrame = false;
            clearJob();
            mainNode.setVisualRepresentation((AEItemKey) null);
            mainNode.setIdlePowerUsage(0.0);
            destroyAe2Node();
            if (previousBox != null) {
                // Coalesced offline — no per-frame findAround; one pass per box/tick.
                ChamberFrameBlockEntity.setFramesInBox(level, previousBox, false);
            }
        }
    }

    private static void refreshChamberNetworkVisuals(Level level, ChamberStructure.FormedBox box) {
        BlockPos leader = ChamberAe2Display.findLeaderUnit(level, box);
        AEItemKey visual = ChamberAe2Display.visualKey();
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            if (!(level.getBlockEntity(pos) instanceof ReplicationUnitBlockEntity unit)) {
                continue;
            }
            if (unit.mainNode.getNode() == null) {
                unit.ensureAe2Node();
            }
            boolean isLeader = leader != null && pos.equals(leader);
            unit.mainNode.setVisualRepresentation(isLeader ? visual : null);
            if (!isLeader) {
                unit.mainNode.setIdlePowerUsage(0.0);
            }
        }
    }

    private void ensureAe2Node() {
        if (level == null || level.isClientSide() || mainNode.isReady()) {
            return;
        }
        try {
            mainNode.create(level, worldPosition);
        } catch (IllegalStateException alreadyUsed) {
            mainNode = createMainNode();
            mainNode.create(level, worldPosition);
        }
    }

    private void destroyAe2Node() {
        if (mainNode.getNode() == null) {
            return;
        }
        mainNode.destroy();
        mainNode = createMainNode();
        linkedToFrame = false;
    }

    private void linkToNearestFrame(@Nullable ChamberStructure.FormedBox box) {
        if (level == null || box == null || linkedToFrame) {
            return;
        }
        IGridNode unitNode = mainNode.getNode();
        if (unitNode == null) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            if (!(level.getBlockEntity(pos) instanceof ChamberFrameBlockEntity frame)) {
                continue;
            }
            IGridNode frameNode = frame.getMainGridNode();
            if (frameNode == null) {
                continue;
            }
            try {
                GridHelper.createConnection(unitNode, frameNode);
                linkedToFrame = true;
                return;
            } catch (RuntimeException ignored) {
            }
        }
    }

    public boolean isFormed() {
        return formed;
    }

    public VisualMode getMode() {
        return mode;
    }

    // --- Client-only mode crossfade (not saved) ---
    private int clientModeFrom;
    private int clientModeTo = -1;
    private float clientModeBlend = 1f;

    /** Advances idle/running/anomaly blend toward the current MODE. */
    public void updateClientModeBlend(float partialTicks) {
        int target = mode.ordinal();
        if (clientModeTo < 0) {
            clientModeFrom = target;
            clientModeTo = target;
            clientModeBlend = 1f;
            return;
        }
        if (target != clientModeTo) {
            if (target == clientModeFrom) {
                // Reversed mid-fade (e.g. hold green → fade purple → work again): invert, no snap.
                clientModeFrom = clientModeTo;
                clientModeTo = target;
                clientModeBlend = 1f - clientModeBlend;
            } else {
                clientModeFrom = clientModeTo;
                clientModeTo = target;
                clientModeBlend = 0f;
            }
        }
        // Slightly slower than RUNNING_HOLD so the hold + fade read as one continuous green.
        clientModeBlend = Math.min(1f, clientModeBlend + partialTicks / 12f);
    }

    public int getClientModeFrom() {
        return clientModeFrom;
    }

    public int getClientModeTo() {
        return clientModeTo < 0 ? mode.ordinal() : clientModeTo;
    }

    public float getClientModeBlend() {
        return clientModeBlend;
    }

    @Override
    public ModelData getModelData() {
        return CraftingCubeModelData.create(getConnections());
    }

    private EnumSet<Direction> getConnections() {
        if (level == null) {
            return EnumSet.noneOf(Direction.class);
        }
        EnumSet<Direction> connections = EnumSet.noneOf(Direction.class);
        for (Direction facing : Direction.values()) {
            if (isConnected(level, worldPosition, facing)) {
                connections.add(facing);
            }
        }
        return connections;
    }

    private static boolean isConnected(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockState(pos.relative(side)).getBlock() instanceof ReplicationUnitBlock;
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        requestModelDataUpdate();
    }

    public int getFillColor() {
        return switch (getMode()) {
            case RUNNING -> 0xA05EC962;
            case IDLE -> 0xA09B59B6;
            case ANOMALY -> 0xA0FF8C00;
        };
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, ReplicationUnitBlockEntity::onReady);
    }

    private void onReady() {
        if (formed) {
            ChamberStructure.FormedBox box = ChamberStructure.findAround(level, worldPosition);
            lastBox = box;
            updateAe2AndFrames(null, box);
            applyVisualState();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (mainNode.getNode() != null) {
            mainNode.destroy();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (mainNode.getNode() != null) {
            mainNode.destroy();
        }
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return null;
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.NONE;
    }

    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        output.putBoolean("formed", formed);
        output.putString("mode", mode.name());
        output.store("craftStack", ItemStack.OPTIONAL_CODEC, craftingStack);
        output.putInt("remaining", remainingCount);
        output.putInt("progress", progress);
        if (bridgePos != null) {
            output.putLong("bridgePos", bridgePos.asLong());
        }
        if (replicationTaskId != null) {
            output.putString("taskId", replicationTaskId.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        formed = input.getBooleanOr("formed", false);
        try {
            mode = parseMode(input.getStringOr("mode", VisualMode.IDLE.name()));
        } catch (IllegalArgumentException e) {
            mode = VisualMode.IDLE;
        }
        craftingStack = input.read("craftStack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        remainingCount = input.getIntOr("remaining", 0);
        progress = input.getIntOr("progress", 0);
        long bridgeLong = input.getLongOr("bridgePos", Long.MIN_VALUE);
        bridgePos = bridgeLong == Long.MIN_VALUE ? null : BlockPos.of(bridgeLong);
        String task = input.getStringOr("taskId", "");
        if (!task.isEmpty()) {
            try {
                replicationTaskId = UUID.fromString(task);
            } catch (IllegalArgumentException e) {
                replicationTaskId = null;
            }
        } else {
            replicationTaskId = null;
        }
    }

    private static VisualMode parseMode(String raw) {
        return switch (raw) {
            case "PURPLE", "IDLE" -> VisualMode.IDLE;
            case "GREEN", "RUNNING" -> VisualMode.RUNNING;
            case "ORANGE", "ANOMALY" -> VisualMode.ANOMALY;
            default -> VisualMode.valueOf(raw);
        };
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            output.putBoolean("formed", formed);
            output.putString("mode", mode.name());
            output.putBoolean("online", mainNode.isOnline());
            return output.buildResult();
        }
    }

    private boolean clientOnline;

    @Override
    public void handleUpdateTag(ValueInput input) {
        formed = input.getBooleanOr("formed", false);
        try {
            mode = parseMode(input.getStringOr("mode", VisualMode.IDLE.name()));
        } catch (IllegalArgumentException e) {
            mode = VisualMode.IDLE;
        }
        clientOnline = input.getBooleanOr("online", false);
        if (level != null && level.isClientSide()) {
            if (formed) {
                lastBox = ChamberStructure.findAround(level, worldPosition);
            } else {
                lastBox = null;
            }
            BlockState current = getBlockState();
            if (current.getBlock() instanceof ReplicationUnitBlock) {
                BlockState updated = current
                        .setValue(ReplicationUnitBlock.FORMED, formed)
                        .setValue(ReplicationUnitBlock.POWERED, formed && clientOnline)
                        .setValue(ReplicationUnitBlock.MODE, mode.ordinal());
                if (updated != current) {
                    level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public boolean isClientOnline() {
        // AE2 ManagedGridNode is rarely "online" on the pure client; trust synced flag.
        return clientOnline;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
