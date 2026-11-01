package net.unfamily.repae2bridge.block.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.matter_fluid.MatterStack;
import com.buuz135.replication.api.network.IMatterTanksConsumer;
import com.buuz135.replication.api.network.IMatterTanksSupplier;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.slf4j.Logger;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.inventories.InternalInventory;
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
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
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
import net.unfamily.repae2bridge.recipe.ModRecipes;

/**
 * Matter Foundry block entity.
 * <ul>
 *   <li>5 shapeless input slots + 1 output slot</li>
 *   <li>Dual matter path: Replication auto-pull into local X/Y buffer, or AE2 Export Bus/PP fill</li>
 *   <li>AE2 grid node for energy (AE → FE fallback)</li>
 *   <li>5 SPEED upgrade cards (same multiplier table as MatterAssembler)</li>
 * </ul>
 */
public class MatterFoundryBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, IUpgradeableObject, IPriorityHost, IConfigurableObject,
        IMatterTanksSupplier, IMatterTanksConsumer {

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
                        .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(2.0)
            .setInWorldNode(true)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setTagName("matter_foundry");

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            ModBlocks.MATTER_FOUNDRY, UPGRADE_SLOTS, this::saveChanges);

    private final IConfigManager configManager = IConfigManager.builder(this::onConfigChanged)
            .registerSetting(Settings.AUTO_EXPORT, YesNo.NO)
            .build();

    /**
     * Item handler for input slots; extends ItemStacksResourceHandler (NeoForge 26 transfer API)
     * and provides convenience methods for direct stack manipulation.
     */
    private final ItemStackHandler inputSlots = new ItemStackHandler(INPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            MatterFoundryBlockEntity.this.onInputContentsChanged();
        }
    };

    /**
     * Item handler for the output slot.
     */
    private final ItemStackHandler outputSlot = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            MatterFoundryBlockEntity.this.setChanged();
        }
    };

    private final FoundrySlotInventory inputInventory = new FoundrySlotInventory(inputSlots);
    private final FoundrySlotInventory outputInventory = new FoundrySlotInventory(outputSlot);

    private final EnergyStorage feBuffer;
    private final FoundryMatterBuffer matterBuffer = new FoundryMatterBuffer();
    /** Stable capability instance so AE2 caches always hit live buffer/recipe state. */
    private final net.unfamily.repae2bridge.ae2.matter.FoundryMatterHandler matterCapability =
            new net.unfamily.repae2bridge.ae2.matter.FoundryMatterHandler(this);

    private int progress;
    private int maxProgress;
    private int totalEnergyCost;
    private int energySpent;
    private boolean active;
    private int priority;

    @Nullable
    private MatterFoundryRecipe cachedRecipe;
    private boolean recipeChecked;

    /** Avoid thrashing remove/add while MatterNetwork classifies the new element. */
    private long lastNetworkJoinGameTime = -1000L;

    /** Client-synced recipe requirements for X/Y GUI (ids → Y). */
    private final java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, Integer> clientRequiredMatter =
            new java.util.LinkedHashMap<>();

    public MatterFoundryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATTER_FOUNDRY_BE.get(), pos, state);
        this.feBuffer = new EnergyStorage(
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
        if ((level.getGameTime() % 20) == 0) {
            joinReplicationNetwork();
            syncToClient(); // refresh matter X/Y for open GUIs
        }

        // Inscriber-style auto-export: push finished items into adjacent inventories.
        pushOutResult();

        try {
            tickCraft();
        } finally {
            // After craft / early exits so linger and continuous runs update ACTIVE once.
            updateActiveState();
        }
    }

    private void tickCraft() {
        // Dual power: work with FE buffer and/or AE grid — AE online is not mandatory.
        // Re-resolve after datapack/recipe changes: a cached instance may no longer exist
        // in the RecipeManager or may no longer match the current inputs.
        if (cachedRecipe != null && !isCachedRecipeStillValid()) {
            cachedRecipe = null;
            recipeChecked = false;
            resetProgress();
            if (level != null) {
                setChanged();
            }
            syncToClient();
        }

        if (cachedRecipe == null && !recipeChecked) {
            cachedRecipe = findMatchingRecipe();
            recipeChecked = true;
            if (level != null) {
                setChanged();
            }
            syncToClient();
        }

        if (cachedRecipe == null) {
            if (progress > 0) {
                // Cancel in-progress craft but KEEP matter buffer for future crafts.
                resetProgress();
                syncToClient();
            }
            return;
        }

        // Flow A: when on Replication network, pull required matter into local buffer (cap Y).
        if (getReplicationNetwork() != null) {
            pullMatterFromReplicationNetwork(cachedRecipe);
        }

        // Do not burn FE until the X/Y buffer can satisfy the recipe.
        if (!matterBuffer.hasAllRequired(cachedRecipe)) {
            if (progress > 0) {
                resetProgress();
                syncToClient();
            }
            return;
        }

        advanceCraft(cachedRecipe);
    }

    /**
     * One progress step (or finish) for the current recipe. Used by the main tick and by
     * same-tick restart after {@link #finishCraft} so continuous runs do not flicker off.
     */
    private void advanceCraft(MatterFoundryRecipe recipe) {
        if (maxProgress <= 0) {
            totalEnergyCost = recipe.getEnergy();
            energySpent = 0;
            progress = 0;
        }
        // Recalculate every tick so adding/removing speed cards applies mid-craft
        // (same approach as Matter Assembler).
        maxProgress = calculateMaxProgress(Math.max(1, totalEnergyCost));

        ItemStack result = recipe.getResultItem();
        ItemStack existing = outputSlot.getStackInSlot(0);
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(existing, result)
                    || existing.getCount() + result.getCount() > existing.getMaxStackSize()) {
                // Paused on full output — do not keep a half-finished progress as "working".
                if (progress > 0) {
                    resetProgress();
                    syncToClient();
                }
                return;
            }
        }

        // Already past the new max after a mid-craft speed-up — finish without waiting another tick.
        if (progress >= maxProgress) {
            finishCraft(recipe, result, existing);
            return;
        }

        int feNeeded = Math.max(1, (int) (Config.matterFoundryEnergyPerTick * getSpeedEnergyMultiplier()));
        int feConsumed = consumeEnergy(feNeeded);
        if (feConsumed < feNeeded * 0.9) {
            return;
        }
        energySpent += feConsumed;

        progress++;
        if (progress >= maxProgress) {
            finishCraft(recipe, result, existing);
        } else {
            setChanged();
        }
    }

    private void finishCraft(MatterFoundryRecipe recipe, ItemStack result, ItemStack existingOutput) {
        if (!matterBuffer.consumeRequired(recipe)) {
            resetProgress();
            return;
        }

        consumeInputs(recipe);

        if (existingOutput.isEmpty()) {
            outputSlot.setStackInSlot(0, result.copy());
        } else {
            existingOutput.grow(result.getCount());
        }

        if (Config.matterFoundryLogging) {
            LOGGER.info("Matter Foundry at {} crafted {}", worldPosition, result.getDisplayName().getString());
        }

        resetProgress();
        cachedRecipe = null;
        recipeChecked = false;
        setChanged();
        syncToClient();

        // Same-tick restart: begin the next craft (one progress step only) so ACTIVE does not flicker off.
        tryStartNextCraftSameTick();
    }

    /**
     * After a completed craft, optionally start the next job in the same tick (progress = 1).
     * Does not finish another craft this tick (avoids multi-craft loops with full input stacks).
     */
    private void tryStartNextCraftSameTick() {
        cachedRecipe = findMatchingRecipe();
        recipeChecked = true;
        if (level != null) {
            setChanged();
        }
        if (cachedRecipe == null) {
            syncToClient();
            return;
        }
        if (getReplicationNetwork() != null) {
            pullMatterFromReplicationNetwork(cachedRecipe);
        }
        if (!matterBuffer.hasAllRequired(cachedRecipe)) {
            syncToClient();
            return;
        }

        ItemStack result = cachedRecipe.getResultItem();
        ItemStack existing = outputSlot.getStackInSlot(0);
        if (!existing.isEmpty()
                && (!ItemStack.isSameItemSameComponents(existing, result)
                        || existing.getCount() + result.getCount() > existing.getMaxStackSize())) {
            syncToClient();
            return;
        }

        totalEnergyCost = cachedRecipe.getEnergy();
        energySpent = 0;
        maxProgress = calculateMaxProgress(Math.max(1, totalEnergyCost));

        int feNeeded = Math.max(1, (int) (Config.matterFoundryEnergyPerTick * getSpeedEnergyMultiplier()));
        int feConsumed = consumeEnergy(feNeeded);
        if (feConsumed < feNeeded * 0.9) {
            resetProgress();
            syncToClient();
            return;
        }
        energySpent += feConsumed;
        progress = 1;
        setChanged();
        syncToClient();
    }

    /** Sync block {@code active} after craft logic (end of tick). */
    private void updateActiveState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean lit = isWorking();
        if (active == lit) {
            // Still force blockstate if BE flag and world state drifted apart.
            BlockState current = getBlockState();
            if (current.hasProperty(MatterFoundryBlock.ACTIVE)
                    && current.getValue(MatterFoundryBlock.ACTIVE) != lit) {
                level.setBlock(worldPosition, current.setValue(MatterFoundryBlock.ACTIVE, lit), 3);
            }
            return;
        }
        active = lit;
        BlockState current = getBlockState();
        if (current.hasProperty(MatterFoundryBlock.ACTIVE)
                && current.getValue(MatterFoundryBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, current.setValue(MatterFoundryBlock.ACTIVE, active), 3);
        }
    }

    // ── Recipe matching ────────────────────────────────────────────────────

    @Nullable
    private MatterFoundryRecipe findMatchingRecipe() {
        if (level == null) return null;

        ItemStack[] stacks = new ItemStack[INPUT_SLOTS];
        boolean hasAny = false;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            stacks[i] = inputSlots.getStackInSlot(i);
            if (!stacks[i].isEmpty()) hasAny = true;
        }
        if (!hasAny) return null;

        MatterFoundryRecipeInput input = new MatterFoundryRecipeInput(stacks);
        var recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.MATTER_FOUNDRY_TYPE.get());
        for (var holder : recipes) {
            if (holder.value().matches(input, level)) {
                return holder.value();
            }
        }
        return null;
    }

    /** True if the cached recipe still matches current inputs and is still registered. */
    private boolean isCachedRecipeStillValid() {
        if (cachedRecipe == null || level == null) {
            return false;
        }
        ItemStack[] stacks = new ItemStack[INPUT_SLOTS];
        for (int i = 0; i < INPUT_SLOTS; i++) {
            stacks[i] = inputSlots.getStackInSlot(i);
        }
        MatterFoundryRecipeInput input = new MatterFoundryRecipeInput(stacks);
        if (!cachedRecipe.matches(input, level)) {
            return false;
        }
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.MATTER_FOUNDRY_TYPE.get()).stream()
                .anyMatch(h -> h.value() == cachedRecipe);
    }

    // ── Energy consumption (FE buffer, fallback to AE grid) ────────────────

    private int consumeEnergy(int feAmount) {
        // Extract from FE buffer directly (no transaction needed for internal use)
        int currentEnergy = (int) Math.min(Integer.MAX_VALUE, feBuffer.getEnergyStored());
        int fromBuffer = Math.min(currentEnergy, feAmount);
        if (fromBuffer > 0) {
            feBuffer.extractEnergy(fromBuffer, false);
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

    // ── Matter buffer (Replication pull + AE2 Export Bus/PP insert) ────────

    /**
     * Pulls from the Replication network into the local X/Y buffer (never beyond Y).
     */
    private void pullMatterFromReplicationNetwork(MatterFoundryRecipe recipe) {
        MatterNetwork network = getReplicationNetwork();
        if (network == null || recipe.getMatter().isEmpty()) {
            return;
        }
        boolean changed = false;
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            IMatterType type = cost.resolve();
            if (type == null) {
                continue;
            }
            double room = matterBuffer.roomFor(recipe, type);
            if (room <= 0) {
                continue;
            }
            double got = extractFromNetworkTanks(network, type, room);
            if (got > 0) {
                matterBuffer.insert(type, got, recipe, false);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            syncToClient();
        }
    }

    private double extractFromNetworkTanks(MatterNetwork network, IMatterType type, double max) {
        double left = max;
        for (NetworkElement holder : network.getMatterStacksHolders()) {
            if (left <= 0) {
                break;
            }
            if (!holder.getLevel().isLoaded(holder.getPos()) || holder.getPos().equals(worldPosition)) {
                continue;
            }
            BlockEntity be = holder.getLevel().getBlockEntity(holder.getPos());
            if (!(be instanceof IMatterTanksSupplier supplier)) {
                continue;
            }
            left -= drainTanksForType(supplier.getTanks(), type, left);
        }
        return max - left;
    }

    private static double drainTanksForType(List<? extends IMatterTank> tanks, IMatterType type, double max) {
        double left = max;
        for (IMatterTank tank : tanks) {
            if (left <= 0) {
                break;
            }
            MatterStack in = tank.getMatter();
            // Replication uses reference equality for matter types.
            if (in == null || in.isEmpty() || in.getMatterType() != type) {
                continue;
            }
            MatterStack drained = tank.drain(new MatterStack(type, left), IFluidHandler.FluidAction.EXECUTE);
            if (drained != null && !drained.isEmpty()) {
                left -= drained.getAmount();
            }
        }
        return max - left;
    }

    public FoundryMatterBuffer getMatterBuffer() {
        return matterBuffer;
    }

    /** Void all buffered matter (GUI dump button). Does not return it to the network. */
    public void dumpMatterBuffer() {
        if (matterBuffer.isEmpty()) {
            return;
        }
        matterBuffer.clearAll();
        if (level != null) {
            setChanged();
        }
        setChanged();
        syncToClient();
    }

    @Nullable
    public MatterFoundryRecipe getCachedRecipe() {
        return cachedRecipe;
    }

    /** Matter handler for AE2 Export Bus / Pattern Provider. Never null; live buffer. */
    public com.buuz135.replication.api.matter_fluid.IMatterHandler getMatterHandler() {
        return matterCapability;
    }

    /** Called after an external matter fill (AE2 / capability). */
    public void onExternalMatterInserted() {
        setChanged();
        syncToClient();
    }

    @Override
    public List<? extends IMatterTank> getTanks() {
        return matterBuffer.buildTanks(cachedRecipe);
    }

    public List<FoundryMatterBuffer.DisplaySlot> getMatterDisplaySlots() {
        // Client never resolves cachedRecipe; Y amounts come only from synced matterRequired.
        if (level != null && level.isClientSide()) {
            return matterBuffer.buildDisplayFromRequired(clientRequiredMatter);
        }
        return matterBuffer.buildDisplay(cachedRecipe);
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

    private void shrinkInputSlot(int index, int count) {
        ItemStack current = inputSlots.getStackInSlot(index);
        if (current.isEmpty()) {
            return;
        }
        current.shrink(count);
        if (current.isEmpty()) {
            inputSlots.setStackInSlot(index, ItemStack.EMPTY);
        }
    }

    private void consumeInputs(MatterFoundryRecipe recipe) {
        for (MatterFoundryRecipe.CountedIngredient required : recipe.getInputs()) {
            int needed = required.count();
            for (int i = 0; i < INPUT_SLOTS && needed > 0; i++) {
                ItemStack slot = inputSlots.getStackInSlot(i);
                if (!slot.isEmpty() && required.ingredient().test(slot)) {
                    int take = Math.min(needed, slot.getCount());
                    shrinkInputSlot(i, take);
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
        /**
     * Returns the output slot handler (NeoForge 26 transfer API).
     * Compatible with {@code Capabilities.Item.BLOCK}.
     */
        /**
     * Sided automation view for hoppers / pipes / AE2 buses.
     * <ul>
     *   <li>{@code DOWN}: extract-only output</li>
     *   <li>other sides / {@code null}: insert-only inputs (null also exposes output extract)</li>
     * </ul>
     */
    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (side == Direction.DOWN) {
            return outputSlot;
        }
        if (side == null) {
            return new CombinedInvWrapper(inputSlots, outputSlot);
        }
        return inputSlots;
    }

    private void onInputContentsChanged() {
        cachedRecipe = null;
        recipeChecked = false;
        setChanged();
        // Clear client-side Y requirements immediately when inputs no longer match a recipe.
        syncToClient();
    }

    /**
     * Returns the internal input handler for direct stack access (menu, rendering).
     */
    public ItemStackHandler getInputHandler() {
        return inputSlots;
    }

    /**
     * Returns the internal output handler for direct stack access (menu, rendering).
     */
    public ItemStackHandler getOutputHandler() {
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
        return (int) Math.min(Integer.MAX_VALUE, feBuffer.getEnergyStored());
    }

    public int getFeCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, feBuffer.getMaxEnergyStored());
    }

    /**
     * Returns the FE energy buffer (NeoForge 26 transfer API).
     * Compatible with {@code Capabilities.Energy.BLOCK}.
     */
    public EnergyStorage getFeBuffer() {
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
            NetworkElement existing = mgr.getElement(worldPosition);
            boolean canRemerge = (level.getGameTime() - lastNetworkJoinGameTime) >= 40;
            if (existing != null && canRemerge && needsNetworkRemerge(mgr, existing)) {
                mgr.removeElement(worldPosition);
                existing = null;
            }
            if (existing == null) {
                mgr.addElement(new DefaultMatterNetworkElement(level, worldPosition) {
                    @Override
                    public boolean canConnectFrom(Direction direction) {
                        if (level != null && !level.isLoaded(worldPosition.relative(direction))) {
                            return false;
                        }
                        return super.canConnectFrom(direction);
                    }
                });
                lastNetworkJoinGameTime = level.getGameTime();
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

    /**
     * Re-add when orphaned, not classified as a matter holder (supplier+consumer),
     * or an adjacent network element is on a different network (pipe connect after place).
     */
    private boolean needsNetworkRemerge(NetworkManager mgr, NetworkElement existing) {
        if (existing.getNetwork() == null) {
            return true;
        }
        if (!(existing.getNetwork() instanceof MatterNetwork mn)) {
            return true;
        }
        // Must be listed as holder so auto-transfer + our pull share the same graph.
        if (!mn.getMatterStacksHolders().contains(existing)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = worldPosition.relative(direction);
            if (!level.isLoaded(neighbor)) {
                continue;
            }
            NetworkElement other = mgr.getElement(neighbor);
            if (other == null || other.getNetwork() == null) {
                continue;
            }
            if (other.getNetwork() != existing.getNetwork()) {
                return true;
            }
        }
        return false;
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
        for (int i = 0; i < inputSlots.getSlots(); i++) {
            ItemStack s = inputSlots.getStackInSlot(i);
            if (!s.isEmpty()) drops.add(s.copy());
        }
        for (int i = 0; i < outputSlot.getSlots(); i++) {
            ItemStack s = outputSlot.getStackInSlot(i);
            if (!s.isEmpty()) drops.add(s.copy());
        }
        for (ItemStack stack : upgrades) {
            if (!stack.isEmpty()) drops.add(stack.copy());
        }
        for (int i = 0; i < inputSlots.getSlots(); i++) inputSlots.setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < outputSlot.getSlots(); i++) outputSlot.setStackInSlot(i, ItemStack.EMPTY);
        upgrades.clear();
    }

    public void saveChanges() {
        setChanged();
    }

    private void onConfigChanged(IConfigManager manager, Setting<?> setting) {
        saveChanges();
    }

    private boolean hasAutoExportWork() {
        return !outputInventory.getStackInSlot(0).isEmpty()
                && configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    /**
     * Push output into adjacent inventories (same approach as AE2 Inscriber).
     *
     * @return true if any items moved
     */
    private boolean pushOutResult() {
        if (!hasAutoExportWork() || level == null) {
            return false;
        }

        for (Direction dir : Direction.values()) {
            var target = InternalInventory.wrapExternal(level, worldPosition.relative(dir), dir.getOpposite());
            if (target == null) {
                continue;
            }
            int startItems = outputInventory.getStackInSlot(0).getCount();
            if (startItems <= 0) {
                return false;
            }
            outputInventory.insertItem(0,
                    target.addItems(outputInventory.extractItem(0, 64, false)),
                    false);
            if (outputInventory.getStackInSlot(0).getCount() != startItems) {
                setChanged();
                return true;
            }
        }
        return false;
    }

    // ── AE2 interfaces ────────────────────────────────────────────────────

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        upgrades.writeToNBT(tag, "upgrades", registries);
        configManager.writeToNBT(tag, registries);
        tag.putInt("progress", progress);
        tag.putInt("maxProgress", maxProgress);
        tag.putInt("totalEnergyCost", totalEnergyCost);
        tag.putInt("energySpent", energySpent);
        tag.putBoolean("active", active);
        tag.putInt("priority", priority);
        tag.put("inputSlots", inputSlots.serializeNBT(registries));
        tag.put("outputSlot", outputSlot.serializeNBT(registries));
        tag.putInt("feEnergy", feBuffer.getEnergyStored());
        matterBuffer.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        upgrades.readFromNBT(tag, "upgrades", registries);
        configManager.readFromNBT(tag, registries);
        progress = tag.getInt("progress");
        maxProgress = tag.getInt("maxProgress");
        totalEnergyCost = tag.getInt("totalEnergyCost");
        energySpent = tag.getInt("energySpent");
        active = tag.getBoolean("active");
        priority = tag.getInt("priority");
        if (tag.contains("inputSlots")) {
            inputSlots.deserializeNBT(registries, tag.getCompound("inputSlots"));
        }
        if (tag.contains("outputSlot")) {
            outputSlot.deserializeNBT(registries, tag.getCompound("outputSlot"));
        }
        if (tag.contains("feEnergy")) {
            feBuffer.receiveEnergy(tag.getInt("feEnergy"), false);
        }
        matterBuffer.load(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("progress", progress);
        tag.putInt("maxProgress", maxProgress);
        tag.putBoolean("active", active);
        matterBuffer.save(tag, registries);
        // Always write matterRequired (empty when no recipe) so the client clears stale Y values.
        ListTag req = new ListTag();
        if (cachedRecipe != null) {
            for (MatterFoundryRecipe.MatterCost cost : cachedRecipe.getMatter()) {
                CompoundTag child = new CompoundTag();
                child.putString("id", cost.matterId().toString());
                child.putInt("amount", cost.amount());
                req.add(child);
            }
        }
        tag.put("matterRequired", req);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        progress = tag.getInt("progress");
        maxProgress = tag.getInt("maxProgress");
        active = tag.getBoolean("active");
        matterBuffer.load(tag, lookupProvider);
        clientRequiredMatter.clear();
        if (tag.contains("matterRequired", Tag.TAG_LIST)) {
            ListTag children = tag.getList("matterRequired", Tag.TAG_COMPOUND);
            for (int i = 0; i < children.size(); i++) {
                CompoundTag child = children.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(child.getString("id"));
                int amount = child.getInt("amount");
                if (id != null && amount > 0) {
                    clientRequiredMatter.put(id, amount);
                }
            }
        }
    }

    public java.util.Map<net.minecraft.resources.ResourceLocation, Integer> getClientRequiredMatter() {
        return clientRequiredMatter;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

}
