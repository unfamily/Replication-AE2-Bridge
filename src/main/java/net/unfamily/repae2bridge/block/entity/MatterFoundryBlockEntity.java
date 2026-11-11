package net.unfamily.repae2bridge.block.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.slf4j.Logger;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
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

import net.unfamily.iskalib.crafting.RecipeBlockBridge;
import net.unfamily.repae2bridge.Config;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.block.custom.MatterFoundryBlock;
import net.unfamily.repae2bridge.menu.MatterFoundryMenu;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;
import net.unfamily.repae2bridge.recipe.MatterFoundryRecipeInput;

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
        appeng.api.networking.security.IActionHost,
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
            .setVisualRepresentation(ModBlocks.MATTER_FOUNDRY.get())
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
    private final FoundryItemHandler inputSlots = new FoundryItemHandler(INPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            MatterFoundryBlockEntity.this.onInputContentsChanged();
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

    /** Automation: insert into inputs from any side except bottom; extract products from bottom. */
    private final ResourceHandler<ItemResource> automationInputs = new DelegatingResourceHandler<>(inputSlots) {
        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    };

    private final ResourceHandler<ItemResource> automationOutput = new DelegatingResourceHandler<>(outputSlot) {
        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    };

    private final ResourceHandler<ItemResource> automationCombined =
            new CombinedResourceHandler<>(automationInputs, automationOutput);

    private final FoundrySlotInventory inputInventory = new FoundrySlotInventory(inputSlots);
    private final FoundrySlotInventory outputInventory = new FoundrySlotInventory(outputSlot);

    private final SimpleEnergyHandler feBuffer;
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
    /** Child recipe id from RecipeManager (post-bundle split); never a bundle id. */
    @Nullable
    private Identifier cachedRecipeId;
    private boolean recipeChecked;
    /**
     * Set by {@link #findMatchingRecipe()}: true when inputs matched at least one Matter Foundry
     * recipe but every match was stage-blocked (re-scan after unlock without touching slots).
     */
    private boolean lastFindHadStageBlockedMatch;

    /**
     * First player to place or open the GUI; passed to {@link RecipeBlockBridge}.
     * Null owner is normal until claim — bridge delegates to providers (world stages still apply;
     * not a blanket fail-close).
     */
    @Nullable
    private UUID ownerPlayer;

    /** Avoid thrashing remove/add while MatterNetwork classifies the new element. */
    private long lastNetworkJoinGameTime = -1000L;

    /** Client-synced recipe requirements for X/Y GUI (ids → Y). */
    private final java.util.LinkedHashMap<net.minecraft.resources.Identifier, Integer> clientRequiredMatter =
            new java.util.LinkedHashMap<>();

    private int lastMatterDisplaySyncHash = Integer.MIN_VALUE;

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
        if ((level.getGameTime() % 20) == 0) {
            joinReplicationNetwork();
            syncToClient(); // refresh matter X/Y for open GUIs
        }

        // Inscriber-style auto-export: push finished items into adjacent inventories.
        pushOutResult();

        rechargeFeFromAe();

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
            clearCachedRecipe();
            recipeChecked = false;
            resetProgress();
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
            }
            syncToClient();
        }

        if (cachedRecipe == null && !recipeChecked) {
            cachedRecipe = findMatchingRecipe();
            // Do not latch when inputs match only stage-blocked recipes — re-scan after unlock.
            recipeChecked = cachedRecipe != null || !lastFindHadStageBlockedMatch;
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
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

        // Flow A: pull required matter into local buffer (cap Y) from Replication tanks, then AE cells.
        pullMatterFromReplicationNetwork(cachedRecipe);
        if (!matterBuffer.hasAllRequired(cachedRecipe)) {
            pullMatterFromAe(cachedRecipe);
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
        if (!ensureCachedRecipeAllowed()) {
            return;
        }
        if (maxProgress <= 0) {
            totalEnergyCost = recipe.getEnergy();
            energySpent = 0;
            progress = 0;
        }
        // Recalculate every tick so adding/removing speed cards applies mid-craft
        // (same approach as Matter Assembler).
        maxProgress = calculateMaxProgress(Math.max(1, totalEnergyCost));

        ItemStack result = recipe.getResultItem();
        ItemStack existing = outputSlot.getStack(0);
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
        if (!ensureCachedRecipeAllowed()) {
            return;
        }
        if (!matterBuffer.consumeRequired(recipe)) {
            resetProgress();
            return;
        }

        consumeInputs(recipe);

        if (existingOutput.isEmpty()) {
            outputSlot.setStack(0, result.copy());
        } else {
            existingOutput.grow(result.getCount());
        }

        if (Config.matterFoundryLogging) {
            LOGGER.info("Matter Foundry at {} crafted {}", worldPosition, result.getDisplayName().getString());
        }

        resetProgress();
        clearCachedRecipe();
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
        recipeChecked = cachedRecipe != null || !lastFindHadStageBlockedMatch;
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
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
        ItemStack existing = outputSlot.getStack(0);
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
        lastFindHadStageBlockedMatch = false;
        if (level == null) {
            clearCachedRecipe();
            return null;
        }

        ItemStack[] stacks = new ItemStack[INPUT_SLOTS];
        boolean hasAny = false;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            stacks[i] = inputSlots.getStack(i);
            if (!stacks[i].isEmpty()) hasAny = true;
        }
        if (!hasAny) {
            clearCachedRecipe();
            return null;
        }

        MatterFoundryRecipeInput input = new MatterFoundryRecipeInput(stacks);
        boolean hadStageBlockedMatch = false;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            var access = serverLevel.recipeAccess();
            if (access instanceof net.minecraft.world.item.crafting.RecipeManager manager) {
                for (var holder : manager.getRecipes()) {
                    if (!(holder.value() instanceof MatterFoundryRecipe recipe) || !recipe.matches(input, level)) {
                        continue;
                    }
                    // Child RecipeManager id (path_N after Library bundle split), never the bundle id.
                    Identifier id = holder.id().identifier();
                    if (RecipeBlockBridge.isRecipeBlocked(id, ownerPlayer, level)) {
                        hadStageBlockedMatch = true;
                        continue;
                    }
                    lastFindHadStageBlockedMatch = false;
                    cachedRecipeId = id;
                    return recipe;
                }
            }
        }
        lastFindHadStageBlockedMatch = hadStageBlockedMatch;
        clearCachedRecipe();
        return null;
    }

    /** True if the cached recipe still matches current inputs, is registered, and is stage-allowed. */
    private boolean isCachedRecipeStillValid() {
        if (cachedRecipe == null || cachedRecipeId == null || level == null) {
            return false;
        }
        if (RecipeBlockBridge.isRecipeBlocked(cachedRecipeId, ownerPlayer, level)) {
            return false;
        }
        ItemStack[] stacks = new ItemStack[INPUT_SLOTS];
        for (int i = 0; i < INPUT_SLOTS; i++) {
            stacks[i] = inputSlots.getStack(i);
        }
        MatterFoundryRecipeInput input = new MatterFoundryRecipeInput(stacks);
        if (!cachedRecipe.matches(input, level)) {
            return false;
        }
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return true;
        }
        var access = serverLevel.recipeAccess();
        if (!(access instanceof net.minecraft.world.item.crafting.RecipeManager manager)) {
            return true;
        }
        for (var holder : manager.getRecipes()) {
            if (holder.id().identifier().equals(cachedRecipeId) || holder.value() == cachedRecipe) {
                return true;
            }
        }
        return false;
    }

    /**
     * Abort in-progress work when the stage gate blocks the cached child recipe id.
     * Re-checked every progress step so mid-craft unlocks/locks apply immediately.
     */
    private boolean ensureCachedRecipeAllowed() {
        if (cachedRecipeId == null || level == null) {
            clearCachedRecipe();
            recipeChecked = false;
            resetProgress();
            syncToClient();
            return false;
        }
        if (!RecipeBlockBridge.isRecipeBlocked(cachedRecipeId, ownerPlayer, level)) {
            return true;
        }
        clearCachedRecipe();
        recipeChecked = false;
        resetProgress();
        syncToClient();
        return false;
    }

    private void clearCachedRecipe() {
        cachedRecipe = null;
        cachedRecipeId = null;
    }

    @Nullable
    public UUID getOwnerPlayer() {
        return ownerPlayer;
    }

    /** First claim only — never overwrites an existing owner. */
    public void tryClaimOwner(Player player) {
        if (player == null) {
            return;
        }
        tryClaimOwner(player.getUUID());
    }

    /** First claim only — never overwrites an existing owner. */
    public void tryClaimOwner(@Nullable UUID uuid) {
        if (uuid == null || ownerPlayer != null) {
            return;
        }
        ownerPlayer = uuid;
        setChanged();
    }

    // ── Energy consumption (FE buffer, fallback to AE grid) ────────────────

    /**
     * Idle trickle: keep the FE buffer topped from the AE grid so the machine can start crafts
     * without waiting for the first progress tick to extract AE.
     */
    private void rechargeFeFromAe() {
        if (!mainNode.isOnline()) {
            return;
        }
        int current = (int) Math.min(Integer.MAX_VALUE, feBuffer.getAmountAsLong());
        int capacity = (int) Math.min(Integer.MAX_VALUE, feBuffer.getCapacityAsLong());
        int room = capacity - current;
        if (room <= 0) {
            return;
        }
        var grid = mainNode.getGrid();
        if (grid == null) {
            return;
        }
        IEnergyService energy = grid.getEnergyService();
        int want = Math.min(room, Math.max(1, capacity / 20));
        double aeNeeded = PowerUnit.FE.convertTo(PowerUnit.AE, want);
        double aeExtracted = energy.extractAEPower(aeNeeded, Actionable.MODULATE, PowerMultiplier.CONFIG);
        int feGot = (int) PowerUnit.AE.convertTo(PowerUnit.FE, aeExtracted);
        if (feGot > 0) {
            feBuffer.set(current + feGot);
            setChanged();
        }
    }

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
                double aeNeeded = PowerUnit.FE.convertTo(PowerUnit.AE, remaining);
                double aeExtracted = energy.extractAEPower(aeNeeded, Actionable.MODULATE, PowerMultiplier.CONFIG);
                fromBuffer += (int) PowerUnit.AE.convertTo(PowerUnit.FE, aeExtracted);
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

    /** Fallback: drain matter cells / bridge mount via AE storage when Replication tanks are dry. */
    private void pullMatterFromAe(MatterFoundryRecipe recipe) {
        if (!mainNode.isOnline() || recipe.getMatter().isEmpty()) {
            return;
        }
        var grid = mainNode.getGrid();
        if (grid == null) {
            return;
        }
        var storage = grid.getStorageService();
        if (storage == null) {
            return;
        }
        boolean changed = false;
        var source = new appeng.me.helpers.MachineSource(this);
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            IMatterType type = cost.resolve();
            if (type == null) {
                continue;
            }
            double room = matterBuffer.roomFor(recipe, type);
            if (room < 1.0) {
                continue;
            }
            var key = net.unfamily.repae2bridge.ae2.ReplicationMatterKey.of(type);
            if (key == null) {
                continue;
            }
            long want = (long) Math.floor(room);
            long got = storage.getInventory().extract(key, want, Actionable.MODULATE, source);
            if (got > 0) {
                matterBuffer.insert(type, (double) got, recipe, false);
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
            level.invalidateCapabilities(worldPosition);
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

    /**
     * GUI helper: recipe-required matter slots (X buffer / Y required).
     * Pass {@code null} when inputs do not match — hide buffer contents.
     */
    public List<FoundryMatterBuffer.DisplaySlot> getMatterDisplaySlots(@Nullable MatterFoundryRecipe recipe) {
        if (recipe == null) {
            return java.util.Collections.nCopies(
                    FoundryMatterBuffer.MAX_TYPES, new FoundryMatterBuffer.DisplaySlot(null, 0, 0));
        }
        java.util.LinkedHashMap<Identifier, Integer> required = new java.util.LinkedHashMap<>();
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            if (cost.amount() <= 0) {
                continue;
            }
            required.put(cost.matterId(), cost.amount());
            if (cost.resolve() == null) {
                net.unfamily.repae2bridge.util.MatterTypeUtil.getMatterInfoById(cost.matterId());
            }
        }
        return matterBuffer.buildDisplayFromRequiredRecipeOnly(required);
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
     * Sided automation view for hoppers / pipes / AE2 buses.
     * <ul>
     *   <li>{@code DOWN}: extract-only output</li>
     *   <li>other sides / {@code null}: insert-only inputs (null also exposes output extract)</li>
     * </ul>
     */
    public ResourceHandler<ItemResource> getAutomationItemHandler(@Nullable Direction side) {
        if (side == Direction.DOWN) {
            return automationOutput;
        }
        if (side == null) {
            return automationCombined;
        }
        return automationInputs;
    }

    private void onInputContentsChanged() {
        clearCachedRecipe();
        recipeChecked = false;
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
        // Clear client-side Y requirements immediately when inputs no longer match a recipe.
        syncToClient();
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

    @Override
    @Nullable
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    // ── Sync ───────────────────────────────────────────────────────────────

    private void syncToClient() {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            lastMatterDisplaySyncHash = Integer.MIN_VALUE;
            sendMatterUpdatePacket(serverLevel);
        }
    }

    /** Keep open GUIs updated (buffer X + recipe Y). No-ops when nothing changed. */
    public void syncMatterDisplayToClient() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        int hash = java.util.Objects.hash(
                cachedRecipe == null ? 0 : System.identityHashCode(cachedRecipe),
                matterBuffer.displaySyncHash());
        if (hash == lastMatterDisplaySyncHash) {
            return;
        }
        lastMatterDisplaySyncHash = hash;
        sendMatterUpdatePacket(serverLevel);
    }

    /**
     * {@code sendBlockUpdated} alone does not push BE NBT; send the update packet explicitly
     * so {@code matterRequired} (including empty) reaches the client.
     */
    private void sendMatterUpdatePacket(net.minecraft.server.level.ServerLevel serverLevel) {
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
        if (packet == null) {
            return;
        }
        for (ServerPlayer player : serverLevel.getChunkSource().chunkMap.getPlayers(
                net.minecraft.world.level.ChunkPos.containing(worldPosition), false)) {
            player.connection.send(packet);
        }
    }

    // ── Serialization ──────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        upgrades.writeToNBT(output, "upgrades");
        configManager.writeToNBT(output);
        output.putInt("progress", progress);
        output.putInt("maxProgress", maxProgress);
        output.putInt("totalEnergyCost", totalEnergyCost);
        output.putInt("energySpent", energySpent);
        output.putBoolean("active", active);
        output.putInt("priority", priority);

        inputSlots.serialize(output.child("inputSlots"));
        outputSlot.serialize(output.child("outputSlot"));
        feBuffer.serialize(output.child("feBuffer"));
        matterBuffer.save(output);
        if (ownerPlayer != null) {
            output.store("ownerPlayer", UUIDUtil.CODEC, ownerPlayer);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        upgrades.readFromNBT(input, "upgrades");
        configManager.readFromNBT(input);
        progress = input.getIntOr("progress", 0);
        maxProgress = input.getIntOr("maxProgress", 0);
        totalEnergyCost = input.getIntOr("totalEnergyCost", 0);
        energySpent = input.getIntOr("energySpent", 0);
        active = input.getBooleanOr("active", false);
        priority = input.getIntOr("priority", 0);

        inputSlots.deserialize(input.childOrEmpty("inputSlots"));
        outputSlot.deserialize(input.childOrEmpty("outputSlot"));
        feBuffer.deserialize(input.childOrEmpty("feBuffer"));
        matterBuffer.load(input);
        ownerPlayer = input.read("ownerPlayer", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            output.putInt("progress", progress);
            output.putInt("maxProgress", maxProgress);
            output.putBoolean("active", active);
            matterBuffer.save(output);
            // Always write matterRequired (empty when no recipe) so the client clears stale Y values.
            var req = output.childrenList("matterRequired");
            if (cachedRecipe != null) {
                for (MatterFoundryRecipe.MatterCost cost : cachedRecipe.getMatter()) {
                    ValueOutput child = req.addChild();
                    child.store("id", net.minecraft.resources.Identifier.CODEC, cost.matterId());
                    child.putInt("amount", cost.amount());
                }
            }
            return output.buildResult();
        }
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        progress = input.getIntOr("progress", 0);
        maxProgress = input.getIntOr("maxProgress", 0);
        active = input.getBooleanOr("active", false);
        matterBuffer.load(input);
        clientRequiredMatter.clear();
        input.childrenList("matterRequired").ifPresent(children -> {
            for (ValueInput child : children) {
                var id = child.read("id", net.minecraft.resources.Identifier.CODEC).orElse(null);
                int amount = child.getIntOr("amount", 0);
                if (id != null && amount > 0) {
                    clientRequiredMatter.put(id, amount);
                }
            }
        });
    }

    public java.util.Map<net.minecraft.resources.Identifier, Integer> getClientRequiredMatter() {
        return clientRequiredMatter;
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
