package net.unfamily.repae2bridge.block.entity;

import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.matter_fluid.MatterStack;
import com.buuz135.replication.api.network.IMatterTanksSupplier;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

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
import net.unfamily.repae2bridge.block.custom.MatterFoundryBlock;
import net.unfamily.repae2bridge.menu.MatterFoundryMenu;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipeInput;

/**
 * Matter Foundry block entity.
 * <ul>
 *   <li>5 input slots + 1 output slot</li>
 *   <li>Joins Replication network for matter draining</li>
 *   <li>AE2 grid node for energy (AE → FE fallback)</li>
 *   <li>5 SPEED upgrade cards (same multiplier table as MatterAssembler)</li>
 *   <li>Matches MatterFoundryRecipe, consumes energy + matter, produces output</li>
 * </ul>
 */
public class MatterFoundryBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, IUpgradeableObject, IPriorityHost {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int UPGRADE_SLOTS = 5;
    private static final int INPUT_SLOTS = 5;
    private static final int OUTPUT_SLOTS = 1;

    private final IManagedGridNode mainNode = GridHelper
            .createManagedNode(this, new IGridNodeListener<MatterFoundryBlockEntity>() {
                @Override
                public void onSaveChanges(MatterFoundryBlockEntity nodeOwner, IGridNode node) {
                    nodeOwner.setChanged();
                }
            })
            .setVisualRepresentation(ModBlocks.MATTER_FOUNDRY.get())
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(2.0)
            .setInWorldNode(true)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setTagName("matter_foundry");

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            ModBlocks.MATTER_FOUNDRY, UPGRADE_SLOTS, this::saveChanges);

    /**
     * Item handler for input slots; extends ItemStacksResourceHandler (NeoForge 26 transfer API)
     * and provides convenience methods for direct stack manipulation.
     */
    private final FoundryItemHandler inputSlots = new FoundryItemHandler(INPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            MatterFoundryBlockEntity.this.cachedRecipe = null;
            MatterFoundryBlockEntity.this.recipeChecked = false;
            MatterFoundryBlockEntity.this.setChanged();
        }
    };

    /**
     * Item handler for the output slot.
     */
    private final FoundryItemHandler outputSlot = new FoundryItemHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            MatterFoundryBlockEntity.this.setChanged();
        }
    };

    private final FoundrySlotInventory inputInventory = new FoundrySlotInventory(inputSlots);
    private final FoundrySlotInventory outputInventory = new FoundrySlotInventory(outputSlot);

    private final SimpleEnergyHandler feBuffer;

    private int progress;
    private int maxProgress;
    private int totalEnergyCost;
    private int energySpent;
    private boolean active;
    private int priority;

    @Nullable
    private MatterFoundryRecipe cachedRecipe;
    private boolean recipeChecked;

    public MatterFoundryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATTER_FOUNDRY_BE.get(), pos, state);
        this.feBuffer = new SimpleEnergyHandler(
                Config.matterFoundryFeCapacity,
                Config.matterFoundryFeCapacity,
                0);
    }

    // ── Tick ────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, MatterFoundryBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Keep / repair Replication network membership (pipes may appear after first tick).
        if ((level.getGameTime() % 40) == 0) {
            joinReplicationNetwork();
        }

        boolean working = isWorking();
        if (active != working) {
            active = working;
            BlockState current = getBlockState();
            if (current.hasProperty(MatterFoundryBlock.ACTIVE)
                    && current.getValue(MatterFoundryBlock.ACTIVE) != active) {
                level.setBlock(worldPosition, current.setValue(MatterFoundryBlock.ACTIVE, active), 3);
            }
        }

        // Dual power: work with FE buffer and/or AE grid — AE online is not mandatory.
        // Try to match recipe if none cached
        if (cachedRecipe == null && !recipeChecked) {
            cachedRecipe = findMatchingRecipe();
            recipeChecked = true;
        }

        if (cachedRecipe == null) {
            if (progress > 0) {
                resetProgress();
                syncToClient();
            }
            return;
        }

        // Initialize crafting if we just found a recipe
        if (maxProgress <= 0) {
            totalEnergyCost = cachedRecipe.getEnergy();
            maxProgress = calculateMaxProgress(totalEnergyCost);
            energySpent = 0;
            progress = 0;
        }

        // Check output has space
        ItemStack result = cachedRecipe.getResultItem();
        ItemStack existing = outputSlot.getStack(0);
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(existing, result)
                    || existing.getCount() + result.getCount() > existing.getMaxStackSize()) {
                return;
            }
        }

        // Consume energy per tick
        int feNeeded = Math.max(1, (int) (Config.matterFoundryEnergyPerTick * getSpeedEnergyMultiplier()));
        int feConsumed = consumeEnergy(feNeeded);
        if (feConsumed < feNeeded * 0.9) {
            return;
        }
        energySpent += feConsumed;

        progress++;
        if (progress >= maxProgress) {
            // Drain matter from network
            if (!drainMatterFromNetwork(cachedRecipe)) {
                // Cannot drain matter yet; wait
                progress = maxProgress - 1;
                return;
            }

            // Consume input items
            consumeInputs(cachedRecipe);

            // Insert output
            if (existing.isEmpty()) {
                outputSlot.setStack(0, result.copy());
            } else {
                existing.grow(result.getCount());
            }

            if (Config.matterFoundryLogging) {
                LOGGER.info("Matter Foundry at {} crafted {}", worldPosition, result.getDisplayName().getString());
            }

            resetProgress();
            cachedRecipe = null;
            recipeChecked = false;
            setChanged();
            syncToClient();
        } else {
            setChanged();
        }
    }

    // ── Recipe matching ────────────────────────────────────────────────────

    @Nullable
    private MatterFoundryRecipe findMatchingRecipe() {
        if (level == null) return null;

        ItemStack[] stacks = new ItemStack[INPUT_SLOTS];
        boolean hasAny = false;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            stacks[i] = inputSlots.getStack(i);
            if (!stacks[i].isEmpty()) hasAny = true;
        }
        if (!hasAny) return null;

        MatterFoundryRecipeInput input = new MatterFoundryRecipeInput(stacks);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            var access = serverLevel.recipeAccess();
            if (access instanceof net.minecraft.world.item.crafting.RecipeManager manager) {
                for (var holder : manager.getRecipes()) {
                    if (holder.value() instanceof MatterFoundryRecipe recipe && recipe.matches(input, level)) {
                        return recipe;
                    }
                }
            }
        }
        return null;
    }

    // ── Energy consumption (FE buffer, fallback to AE grid) ────────────────

    private int consumeEnergy(int feAmount) {
        // Extract from FE buffer directly (no transaction needed for internal use)
        int currentEnergy = (int) Math.min(Integer.MAX_VALUE, feBuffer.getAmountAsLong());
        int fromBuffer = Math.min(currentEnergy, feAmount);
        if (fromBuffer > 0) {
            feBuffer.set(currentEnergy - fromBuffer);
        }

        int remaining = feAmount - fromBuffer;
        if (remaining > 0 && mainNode.isOnline()) {
            var grid = mainNode.getGrid();
            if (grid != null) {
                IEnergyService energy = grid.getEnergyService();
                double aeNeeded = remaining / Config.matterFoundryFePerAe;
                double aeExtracted = energy.extractAEPower(aeNeeded, Actionable.MODULATE, PowerMultiplier.CONFIG);
                fromBuffer += (int) (aeExtracted * Config.matterFoundryFePerAe);
            }
        }
        return Math.min(fromBuffer, feAmount);
    }

    // ── Matter draining ────────────────────────────────────────────────────

    private boolean drainMatterFromNetwork(MatterFoundryRecipe recipe) {
        if (recipe.getMatter().isEmpty()) {
            return true;
        }

        MatterNetwork network = getReplicationNetwork();
        if (network == null) {
            return false;
        }

        // First simulate all drains to verify availability
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            IMatterType type = cost.resolve();
            if (type == null) {
                return false;
            }
            double available = calculateAvailableMatter(network, type);
            if (available < cost.amount()) {
                return false;
            }
        }

        // Actually drain
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            IMatterType type = cost.resolve();
            if (type == null) return false;
            double toDrain = cost.amount();
            for (NetworkElement holder : network.getMatterStacksHolders()) {
                if (toDrain <= 0) break;
                if (!holder.getLevel().isLoaded(holder.getPos())) continue;
                BlockEntity be = holder.getLevel().getBlockEntity(holder.getPos());
                if (be instanceof IMatterTanksSupplier supplier) {
                    for (IMatterTank tank : supplier.getTanks()) {
                        if (toDrain <= 0) break;
                        if (tank.getMatter().getMatterType().equals(type)) {
                            MatterStack drained = tank.drain(
                                    new MatterStack(type, toDrain), IFluidHandler.FluidAction.EXECUTE);
                            if (!drained.isEmpty()) {
                                toDrain -= drained.getAmount();
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private double calculateAvailableMatter(MatterNetwork network, IMatterType type) {
        double total = 0;
        for (NetworkElement holder : network.getMatterStacksHolders()) {
            if (!holder.getLevel().isLoaded(holder.getPos())) continue;
            BlockEntity be = holder.getLevel().getBlockEntity(holder.getPos());
            if (be instanceof IMatterTanksSupplier supplier) {
                for (IMatterTank tank : supplier.getTanks()) {
                    if (tank.getMatter().getMatterType().equals(type)) {
                        total += tank.getMatterAmount();
                    }
                }
            }
        }
        return total;
    }

    @Nullable
    private MatterNetwork getReplicationNetwork() {
        if (level == null || level.isClientSide()) return null;
        try {
            NetworkManager mgr = NetworkManager.get(level);
            NetworkElement el = mgr.getElement(worldPosition);
            if (el != null && el.getNetwork() instanceof MatterNetwork mn) {
                return mn;
            }
        } catch (Exception e) {
            if (Config.matterFoundryLogging) {
                LOGGER.warn("Matter Foundry at {} failed to get MatterNetwork", worldPosition, e);
            }
        }
        return null;
    }

    // ── Input consumption ──────────────────────────────────────────────────

    private void consumeInputs(MatterFoundryRecipe recipe) {
        for (MatterFoundryRecipe.CountedIngredient required : recipe.getInputs()) {
            int needed = required.count();
            for (int i = 0; i < INPUT_SLOTS && needed > 0; i++) {
                ItemStack slot = inputSlots.getStack(i);
                if (!slot.isEmpty() && required.ingredient().test(slot)) {
                    int take = Math.min(needed, slot.getCount());
                    inputSlots.shrinkStack(i, take);
                    needed -= take;
                }
            }
        }
    }

    // ── Speed cards ────────────────────────────────────────────────────────

    private int calculateMaxProgress(int totalEnergy) {
        int fePerTick = Math.max(1, Config.matterFoundryEnergyPerTick);
        int baseTicks = Math.max(1, totalEnergy / fePerTick);
        double mult = getSpeedMultiplier();
        return Math.max(1, (int) Math.round(baseTicks / mult));
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

    // ── State ──────────────────────────────────────────────────────────────

    public boolean isWorking() {
        return progress > 0 && maxProgress > 0;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    /**
     * Returns the input slots handler (NeoForge 26 transfer API).
     * Compatible with {@code Capabilities.Item.BLOCK}.
     */
    public ResourceHandler<ItemResource> getInputSlots() {
        return inputSlots;
    }

    /**
     * Returns the output slot handler (NeoForge 26 transfer API).
     * Compatible with {@code Capabilities.Item.BLOCK}.
     */
    public ResourceHandler<ItemResource> getOutputSlot() {
        return outputSlot;
    }

    /**
     * Returns the internal input handler for direct stack access (menu, rendering).
     */
    public FoundryItemHandler getInputHandler() {
        return inputSlots;
    }

    /**
     * Returns the internal output handler for direct stack access (menu, rendering).
     */
    public FoundryItemHandler getOutputHandler() {
        return outputSlot;
    }

    public FoundrySlotInventory getInputInventory() {
        return inputInventory;
    }

    public FoundrySlotInventory getOutputInventory() {
        return outputInventory;
    }

    /** FE amount currently buffered (for GUI energy bar). */
    public int getFeStored() {
        return (int) Math.min(Integer.MAX_VALUE, feBuffer.getAmountAsLong());
    }

    public int getFeCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, feBuffer.getCapacityAsLong());
    }

    /**
     * Returns the FE energy buffer (NeoForge 26 transfer API).
     * Compatible with {@code Capabilities.Energy.BLOCK}.
     */
    public EnergyHandler getFeBuffer() {
        return feBuffer;
    }

    private void resetProgress() {
        progress = 0;
        maxProgress = 0;
        totalEnergyCost = 0;
        energySpent = 0;
    }

    // ── Network element (Replication) ──────────────────────────────────────

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, MatterFoundryBlockEntity::onReady);
    }

    private void onReady() {
        if (level != null && !level.isClientSide()) {
            mainNode.create(level, worldPosition);
            joinReplicationNetwork();
        }
    }

    /** Called when adjacent pipes change so the Foundry (re)joins / merges networks. */
    public void requestNetworkRescan() {
        joinReplicationNetwork();
    }

    private void joinReplicationNetwork() {
        if (level == null || level.isClientSide()) return;
        try {
            NetworkManager mgr = NetworkManager.get(level);
            if (mgr.getElement(worldPosition) == null) {
                mgr.addElement(new DefaultMatterNetworkElement(level, worldPosition) {
                    @Override
                    public boolean canConnectFrom(Direction direction) {
                        if (level != null && !level.isLoaded(worldPosition.relative(direction))) {
                            return false;
                        }
                        return super.canConnectFrom(direction);
                    }
                });
                if (Config.matterFoundryLogging) {
                    LOGGER.info("Matter Foundry at {} joined Replication network", worldPosition);
                }
            }
        } catch (Exception e) {
            if (Config.matterFoundryLogging) {
                LOGGER.warn("Matter Foundry at {} failed to join Replication network", worldPosition, e);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
        leaveReplicationNetwork();
    }

    private boolean unloaded;

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unloaded = true;
    }

    private void leaveReplicationNetwork() {
        if (level == null || level.isClientSide() || unloaded) return;
        try {
            NetworkManager mgr = NetworkManager.get(level);
            NetworkElement el = mgr.getElement(worldPosition);
            if (el != null) {
                mgr.removeElement(worldPosition);
                if (el.getNetwork() instanceof MatterNetwork mn) {
                    mn.removeElement(el);
                }
            }
        } catch (Exception e) {
            // Ignore during world unload
        }
    }

    // ── GUI ────────────────────────────────────────────────────────────────

    public void openGui(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            MenuOpener.open(MatterFoundryMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(this));
        }
    }

    public void addDrops(List<ItemStack> drops) {
        for (int i = 0; i < inputSlots.size(); i++) {
            ItemStack s = inputSlots.getStack(i);
            if (!s.isEmpty()) drops.add(s.copy());
        }
        for (int i = 0; i < outputSlot.size(); i++) {
            ItemStack s = outputSlot.getStack(i);
            if (!s.isEmpty()) drops.add(s.copy());
        }
        for (ItemStack stack : upgrades) {
            if (!stack.isEmpty()) drops.add(stack.copy());
        }
        for (int i = 0; i < inputSlots.size(); i++) inputSlots.setStack(i, ItemStack.EMPTY);
        for (int i = 0; i < outputSlot.size(); i++) outputSlot.setStack(i, ItemStack.EMPTY);
        upgrades.clear();
    }

    public void saveChanges() {
        setChanged();
    }

    // ── AE2 interfaces ────────────────────────────────────────────────────

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
        return ModBlocks.MATTER_FOUNDRY.get().asItem().getDefaultInstance();
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player instanceof ServerPlayer serverPlayer) {
            MenuOpener.open(MatterFoundryMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(this), true);
        }
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

    // ── Sync ───────────────────────────────────────────────────────────────

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ── Serialization ──────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        upgrades.writeToNBT(output, "upgrades");
        output.putInt("progress", progress);
        output.putInt("maxProgress", maxProgress);
        output.putInt("totalEnergyCost", totalEnergyCost);
        output.putInt("energySpent", energySpent);
        output.putBoolean("active", active);
        output.putInt("priority", priority);

        inputSlots.serialize(output.child("inputSlots"));
        outputSlot.serialize(output.child("outputSlot"));
        feBuffer.serialize(output.child("feBuffer"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        upgrades.readFromNBT(input, "upgrades");
        progress = input.getIntOr("progress", 0);
        maxProgress = input.getIntOr("maxProgress", 0);
        totalEnergyCost = input.getIntOr("totalEnergyCost", 0);
        energySpent = input.getIntOr("energySpent", 0);
        active = input.getBooleanOr("active", false);
        priority = input.getIntOr("priority", 0);

        inputSlots.deserialize(input.childOrEmpty("inputSlots"));
        outputSlot.deserialize(input.childOrEmpty("outputSlot"));
        feBuffer.deserialize(input.childOrEmpty("feBuffer"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            output.putInt("progress", progress);
            output.putInt("maxProgress", maxProgress);
            output.putBoolean("active", active);
            return output.buildResult();
        }
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        progress = input.getIntOr("progress", 0);
        maxProgress = input.getIntOr("maxProgress", 0);
        active = input.getBooleanOr("active", false);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ── Helper: item handler with direct stack access ──────────────────────

    /**
     * Extension of {@link ItemStacksResourceHandler} that provides convenience methods
     * for direct stack manipulation without requiring transactions (for internal BE logic).
     */
    public static class FoundryItemHandler extends ItemStacksResourceHandler {
        public FoundryItemHandler(int size) {
            super(size);
        }

        /** Returns the stack at the given index without copying. */
        public ItemStack getStack(int index) {
            return stacks.get(index);
        }

        /** Directly sets the stack at the given index. */
        public void setStack(int index, ItemStack stack) {
            ItemStack old = stacks.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            onContentsChanged(index, old);
        }

        /** Shrinks the stack at the given index by the specified count. */
        public void shrinkStack(int index, int count) {
            ItemStack current = stacks.get(index);
            if (!current.isEmpty()) {
                int newCount = current.getCount() - count;
                if (newCount <= 0) {
                    setStack(index, ItemStack.EMPTY);
                } else {
                    current.setCount(newCount);
                    onContentsChanged(index, current);
                }
            }
        }
    }
}
