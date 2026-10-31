package net.unfamily.repae2bridge.block.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.slf4j.Logger;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPriorityHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.block.custom.MatterAssemblerBlock;
import net.unfamily.repae2bridge.menu.MatterAssemblerMenu;

/**
 * AE2-only fast craft worker. Jobs are assigned by {@link RepAE2BridgeBlockEntity}
 * (chip patterns / Replication tasks require the Bridge).
 */
public class MatterAssemblerBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, IUpgradeableObject, IPriorityHost {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int UPGRADE_SLOTS = 5;

    private final IManagedGridNode mainNode = GridHelper
            .createManagedNode(this, new IGridNodeListener<MatterAssemblerBlockEntity>() {
                @Override
                public void onSaveChanges(MatterAssemblerBlockEntity nodeOwner, IGridNode node) {
                    nodeOwner.setChanged();
                }
            })
            .setVisualRepresentation(ModBlocks.MATTER_ASSEMBLER.get())
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(1.0)
            .setInWorldNode(true)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setTagName("matter_assembler");

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            ModBlocks.MATTER_ASSEMBLER, UPGRADE_SLOTS, this::saveChanges);

    private ItemStack craftingStack = ItemStack.EMPTY;
    private int remainingCount;
    private int progress;
    private boolean powered;
    private int priority;
    @Nullable
    private BlockPos bridgePos;
    /** When set, each finished unit finalizes this Replication network task. */
    @Nullable
    private UUID replicationTaskId;

    public MatterAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATTER_ASSEMBLER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MatterAssemblerBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide()) {
            return;
        }

        boolean online = mainNode.isOnline();
        if (powered != online) {
            powered = online;
            BlockState current = getBlockState();
            if (current.hasProperty(MatterAssemblerBlock.POWERED)
                    && current.getValue(MatterAssemblerBlock.POWERED) != powered) {
                level.setBlock(worldPosition, current.setValue(MatterAssemblerBlock.POWERED, powered), 3);
            }
            setChanged();
            syncToClient();
        }

        if (!online || craftingStack.isEmpty() || remainingCount <= 0) {
            return;
        }
        // Replication cancelTask only notifies vanilla Replicators — drop orphaned assembler jobs.
        if (replicationTaskId != null && !isBoundReplicationTaskAlive()) {
            abortJob();
            return;
        }

        var grid = mainNode.getGrid();
        if (grid == null) {
            return;
        }

        double energyCost = Config.matterAssemblerEnergyPerTick * getSpeedEnergyMultiplier();
        if (energyCost > 0) {
            IEnergyService energy = grid.getEnergyService();
            double extracted = energy.extractAEPower(energyCost, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (extracted < energyCost * 0.9) {
                return;
            }
        }

        progress++;
        int maxProgress = getEffectiveMaxProgress();
        if (progress >= maxProgress) {
            progress = 0;
            finishOneUnit();
            // Sync only on craft-unit completion — per-tick TE packets fill the send queue and RAM.
            setChanged();
            syncToClient();
        } else {
            setChanged();
        }
    }

    private void finishOneUnit() {
        if (level == null || craftingStack.isEmpty()) {
            return;
        }

        ItemStack output = craftingStack.copyWithCount(1);
        boolean delivered = false;

        if (bridgePos != null) {
            BlockEntity be = level.getBlockEntity(bridgePos);
            if (be instanceof RepAE2BridgeBlockEntity bridge) {
                if (replicationTaskId != null) {
                    // Replication UI / network task: deliver to task source (terminal), never AE2.
                    delivered = bridge.deliverMatterAssemblerReplicationUnit(
                            output, replicationTaskId, worldPosition);
                } else {
                    if (!bridge.drainOneMatterForAe2Internal(craftingStack)) {
                        return;
                    }
                    delivered = bridge.receiveItemFromReplicator(output);
                }
            }
        }

        if (!delivered) {
            // Wait until the Bridge can accept the item.
            return;
        }

        remainingCount--;
        if (remainingCount <= 0) {
            clearJob();
        }
    }

    /**
     * @return true if this assembler accepted the job
     */
    public boolean tryStartJob(ItemStack stack, int count, BlockPos bridgeSource) {
        return tryStartJob(stack, count, bridgeSource, null);
    }

    /**
     * @param replicationTaskId optional Replication task UUID (matter already reserved/drained)
     * @return true if this assembler accepted the job
     */
    public boolean tryStartJob(ItemStack stack, int count, BlockPos bridgeSource, @Nullable UUID replicationTaskId) {
        if (!isIdle() || !mainNode.isOnline() || stack.isEmpty() || count <= 0 || bridgeSource == null) {
            return false;
        }
        this.craftingStack = stack.copyWithCount(1);
        this.remainingCount = count;
        this.progress = 0;
        this.bridgePos = bridgeSource.immutable();
        this.replicationTaskId = replicationTaskId;
        setChanged();
        syncToClient();
        return true;
    }

    /** Grow an AE2-internal job so the assembler stays busy between pushPattern bursts. */
    public boolean extendAe2InternalJob(ItemStack stack, int extra, BlockPos bridgeSource) {
        if (extra <= 0 || stack.isEmpty() || bridgeSource == null || replicationTaskId != null) {
            return false;
        }
        if (isIdle() || !mainNode.isOnline()) {
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

    public boolean isWorking() {
        // On the client, the AE2 node is not guaranteed; use synced craft state + powered.
        if (level != null && level.isClientSide()) {
            return powered && !craftingStack.isEmpty() && remainingCount > 0;
        }
        return !isIdle() && mainNode.isOnline();
    }

    public ItemStack getCraftingStack() {
        return craftingStack;
    }

    @Nullable
    public UUID getReplicationTaskId() {
        return replicationTaskId;
    }

    public int getProgress() {
        return progress;
    }

    public float getAnimationSpeed() {
        if (!isWorking()) {
            return 0;
        }
        int max = getEffectiveMaxProgress();
        return Math.max(1f, 20f / max);
    }

    /**
     * Matches AE2 Molecular Assembler speed-card ratios against configured base progress.
     */
    public int getEffectiveMaxProgress() {
        int base = Math.max(1, Config.matterAssemblerMaxProgress);
        double mult = getSpeedMultiplier();
        return Math.max(1, (int) Math.round(base / mult));
    }

    private double getSpeedMultiplier() {
        return switch (upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) {
            case 1 -> 1.3;
            case 2 -> 1.7;
            case 3 -> 2.0;
            case 4 -> 2.5;
            case 5 -> 5.0;
            default -> 1.0;
        };
    }

    private double getSpeedEnergyMultiplier() {
        return getSpeedMultiplier();
    }

    /** Cancels the current job without delivering output (e.g. matter reservation failed). */
    public void abortJob() {
        clearJob();
    }

    private boolean isBoundReplicationTaskAlive() {
        if (replicationTaskId == null || level == null || bridgePos == null) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(bridgePos);
        return be instanceof RepAE2BridgeBlockEntity bridge
                && bridge.hasPendingReplicationTask(replicationTaskId);
    }

    @Nullable
    public BlockPos getBridgePos() {
        return bridgePos;
    }

    private void clearJob() {
        craftingStack = ItemStack.EMPTY;
        remainingCount = 0;
        progress = 0;
        bridgePos = null;
        replicationTaskId = null;
        setChanged();
        syncToClient();
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void saveChanges() {
        setChanged();
    }

    public void openGui(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            MenuOpener.open(MatterAssemblerMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(this));
        }
    }

    /**
     * Called before the BE is removed from the level (vanilla 26+).
     * {@code affectNeighborsAfterRemoval} is too late — the BE is already gone.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            List<ItemStack> drops = new ArrayList<>();
            addDrops(drops);
            for (ItemStack drop : drops) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drop);
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    public void addDrops(List<ItemStack> drops) {
        for (ItemStack stack : upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        upgrades.clear();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        setChanged();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ModBlocks.MATTER_ASSEMBLER.get().asItem().getDefaultInstance();
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player instanceof ServerPlayer serverPlayer) {
            MenuOpener.open(MatterAssemblerMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(this), true);
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, MatterAssemblerBlockEntity::onReady);
    }

    private void onReady() {
        if (level != null && !level.isClientSide()) {
            mainNode.create(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        upgrades.writeToNBT(output, "upgrades");
        output.store("craft", ItemStack.OPTIONAL_CODEC, craftingStack);
        output.putInt("remaining", remainingCount);
        output.putInt("progress", progress);
        output.putBoolean("powered", powered);
        output.putInt("priority", priority);
        if (bridgePos != null) {
            output.store("bridge", BlockPos.CODEC, bridgePos);
        }
        if (replicationTaskId != null) {
            output.store("replicationTaskId", UUIDUtil.CODEC, replicationTaskId);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        upgrades.readFromNBT(input, "upgrades");
        craftingStack = input.read("craft", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        remainingCount = input.getIntOr("remaining", 0);
        progress = input.getIntOr("progress", 0);
        powered = input.getBooleanOr("powered", false);
        priority = input.getIntOr("priority", 0);
        bridgePos = input.read("bridge", BlockPos.CODEC).orElse(null);
        replicationTaskId = input.read("replicationTaskId", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            output.store("craft", ItemStack.OPTIONAL_CODEC, craftingStack);
            output.putInt("remaining", remainingCount);
            output.putBoolean("powered", powered);
            return output.buildResult();
        }
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        craftingStack = input.read("craft", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        remainingCount = input.getIntOr("remaining", 0);
        powered = input.getBooleanOr("powered", false);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
