package net.unfamily.repae2bridge.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.shorts.ShortSet;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.ConfigInventory;

import net.unfamily.repae2bridge.ae2.encoding.MatterEncodingHelper;
import net.unfamily.repae2bridge.ae2.encoding.MatterEncodingLogicAccess;
import net.unfamily.repae2bridge.menu.MatterEncodingMenuAccess;

/**
 * Matter-disintegration encoding tab on the AE2 PET.
 * <p>
 * Uses the stonecutting input slot layout, but persists as its own flag on
 * {@link PatternEncodingLogic} so reopening the terminal restores Matter (not Stonecutting).
 */
@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public abstract class PatternEncodingTermMenuMixin extends AEBaseMenu implements MatterEncodingMenuAccess {

    @Unique
    private static final ClientActionKey<Boolean> REPAE2_SET_MATTER_MODE =
            new ClientActionKey<>("repAe2Bridge$setMatterMode");

    @Unique
    private static final ClientActionKey<Void> REPAE2_ENCODE_MATTER =
            new ClientActionKey<>("repAe2Bridge$encodeMatter");

    @Unique
    private static final ClientActionKey<List<String>> REPAE2_SYNC_MATTER_ORDER =
            new ClientActionKey<>("repAe2Bridge$syncMatterOrder");

    @Shadow
    @Final
    private PatternEncodingLogic encodingLogic;

    @Shadow
    @Final
    private ConfigInventory encodedInputsInv;

    @Shadow
    @Final
    private RestrictedInputSlot blankPatternSlot;

    @Shadow
    @Final
    private RestrictedInputSlot encodedPatternSlot;

    @Shadow
    @Final
    private FakeSlot stonecuttingInputSlot;

    @Shadow
    public EncodingMode mode;

    @Shadow
    public abstract void setMode(EncodingMode mode);

    /**
     * Synced so the client restores the Matter tab after closing/reopening the PET.
     * Persisted on the encoding logic separately from {@link EncodingMode}.
     */
    @Unique
    @GuiSync(220)
    public boolean repAe2Bridge$matterMode;

    @Unique
    private boolean repAe2Bridge$suppressMatterClear;

    /** Preferred matter output order (primary first). Survives amount recalculation. */
    @Unique
    private final List<Identifier> repAe2Bridge$matterOutputOrder = new ArrayList<>();

    @Unique
    @Nullable
    private AEKey repAe2Bridge$lastMatterInputKey;

    public PatternEncodingTermMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;)V",
            at = @At("RETURN"))
    private void repAe2Bridge$registerMatterActions(int id, Inventory inv, IPatternTerminalMenuHost host,
            CallbackInfo ci) {
        registerClientAction(REPAE2_SET_MATTER_MODE, ByteBufCodecs.BOOL, this::repAe2Bridge$setMatterModeServer);
        registerClientAction(REPAE2_ENCODE_MATTER, this::repAe2Bridge$encodeMatterServer);
        registerClientAction(REPAE2_SYNC_MATTER_ORDER, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                this::repAe2Bridge$syncMatterOrderServer);

        // Restore Matter tab from terminal NBT (independent of EncodingMode.STONECUTTING).
        if (encodingLogic instanceof MatterEncodingLogicAccess access && access.repAe2Bridge$isMatterEncoding()) {
            this.mode = EncodingMode.STONECUTTING;
            this.repAe2Bridge$matterMode = true;
            repAe2Bridge$applyMatterInputAmountVisibility(true);
        }
    }

    @Inject(method = "setMode", at = @At("HEAD"))
    private void repAe2Bridge$clearMatterModeOnAe2Mode(EncodingMode newMode, CallbackInfo ci) {
        // Any AE2 tab click leaves Matter. setMatterMode(true) suppresses this while it
        // switches the underlying mode to STONECUTTING for the shared input slot.
        // Encode/load of a matter processing pattern is handled in PatternEncodingLogicMixin
        // (keeps STONECUTTING), so broadcastChanges will not call setMode(PROCESSING).
        if (repAe2Bridge$matterMode && !repAe2Bridge$suppressMatterClear) {
            repAe2Bridge$matterMode = false;
            repAe2Bridge$applyMatterInputAmountVisibility(false);
            if (isClientSide()) {
                sendClientAction(REPAE2_SET_MATTER_MODE, false);
            } else if (encodingLogic instanceof MatterEncodingLogicAccess access) {
                access.repAe2Bridge$setMatterEncoding(false);
            }
        }
    }

    @Inject(method = "onServerDataSync", at = @At("RETURN"))
    private void repAe2Bridge$onMatterModeSynced(ShortSet updatedFields, CallbackInfo ci) {
        // After GuiSync applies matterMode from the server, restore slot amount visibility.
        if (repAe2Bridge$matterMode) {
            repAe2Bridge$applyMatterInputAmountVisibility(true);
        }
    }

    @Inject(method = "encode", at = @At("HEAD"), cancellable = true)
    private void repAe2Bridge$encodeMatterPattern(CallbackInfo ci) {
        if (!repAe2Bridge$matterMode) {
            return;
        }
        if (isClientSide()) {
            repAe2Bridge$getMatterPreviewOutputs();
            sendClientAction(REPAE2_SYNC_MATTER_ORDER, repAe2Bridge$orderAsStrings());
            sendClientAction(REPAE2_ENCODE_MATTER);
        } else {
            repAe2Bridge$encodeMatterServer();
        }
        ci.cancel();
    }

    @Inject(method = "canModifyAmountForSlot", at = @At("HEAD"), cancellable = true)
    private void repAe2Bridge$matterInputAmount(Slot slot, CallbackInfoReturnable<Boolean> cir) {
        if (repAe2Bridge$matterMode && slot == stonecuttingInputSlot && slot.hasItem()) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean repAe2Bridge$isMatterMode() {
        return repAe2Bridge$matterMode;
    }

    @Override
    public void repAe2Bridge$setMatterMode(boolean enabled) {
        if (isClientSide()) {
            // Do NOT call setMode() here first: ACTION_SET_MODE hits encodingLogic.setMode
            // before matterEncoding is set and AE2 clamps input amounts to 1.
            if (enabled) {
                sendClientAction(REPAE2_SET_MATTER_MODE, true);
                repAe2Bridge$matterMode = true;
                this.mode = EncodingMode.STONECUTTING;
                repAe2Bridge$applyMatterInputAmountVisibility(true);
            } else {
                sendClientAction(REPAE2_SET_MATTER_MODE, false);
                repAe2Bridge$matterMode = false;
                repAe2Bridge$applyMatterInputAmountVisibility(false);
            }
        } else {
            repAe2Bridge$setMatterModeServer(enabled);
        }
    }

    @Unique
    private void repAe2Bridge$setMatterModeServer(boolean enabled) {
        // Enable stacked-input bypass BEFORE setMode so fixCraftingRecipes does not clamp.
        if (encodingLogic instanceof MatterEncodingLogicAccess access) {
            access.repAe2Bridge$setAllowStackedInputs(enabled);
            access.repAe2Bridge$setMatterEncoding(enabled);
        }
        if (enabled) {
            this.mode = EncodingMode.STONECUTTING;
            encodingLogic.setMode(EncodingMode.STONECUTTING);
            repAe2Bridge$matterMode = true;
            stonecuttingInputSlot.setHideAmount(false);
            // Re-apply amount from the encoded processing pattern (or keep Processing slot amount).
            repAe2Bridge$restoreMatterInputAmount();
        } else {
            repAe2Bridge$matterMode = false;
            stonecuttingInputSlot.setHideAmount(true);
        }
    }

    /**
     * Matter shares {@code encodedInputsInv} with Processing. When entering the Matter tab,
     * restore the primary input amount from an encoded processing pattern if present; otherwise
     * keep whatever amount is already in slot 0 (e.g. carried over from Processing).
     */
    @Unique
    private void repAe2Bridge$restoreMatterInputAmount() {
        ItemStack encoded = this.encodedPatternSlot.getItem();
        if (!PatternDetailsHelper.isEncodedPattern(encoded)) {
            return;
        }
        var details = PatternDetailsHelper.decodePattern(encoded, getPlayer().level());
        if (!(details instanceof appeng.crafting.pattern.AEProcessingPattern processing)) {
            return;
        }
        var sparse = processing.getSparseInputs();
        if (sparse == null || sparse.isEmpty()) {
            return;
        }
        GenericStack patternInput = null;
        for (GenericStack stack : sparse) {
            if (stack != null && stack.what() instanceof AEItemKey && stack.amount() > 0) {
                patternInput = stack;
                break;
            }
        }
        if (patternInput == null) {
            return;
        }
        GenericStack current = encodedInputsInv.getStack(0);
        if (current == null || !current.what().equals(patternInput.what()) || current.amount() != patternInput.amount()) {
            encodedInputsInv.setStack(0, patternInput);
        }
    }

    @Unique
    private void repAe2Bridge$applyMatterInputAmountVisibility(boolean showAmount) {
        stonecuttingInputSlot.setHideAmount(!showAmount);
        if (encodingLogic instanceof MatterEncodingLogicAccess access) {
            access.repAe2Bridge$setAllowStackedInputs(showAmount);
        }
    }

    @Unique
    private void repAe2Bridge$encodeMatterServer() {
        GenericStack input = encodedInputsInv.getStack(0);
        if (input == null) {
            return;
        }
        repAe2Bridge$syncOrderForInput(input);
        ItemStack encoded = MatterEncodingHelper.encodeProcessingPattern(
                input.what(), input.amount(), repAe2Bridge$matterOutputOrder);
        if (encoded == null || encoded.isEmpty()) {
            return;
        }

        ItemStack encodeOutput = this.encodedPatternSlot.getItem();
        if (!encodeOutput.isEmpty()
                && !PatternDetailsHelper.isEncodedPattern(encodeOutput)
                && !AEItems.BLANK_PATTERN.is(encodeOutput)) {
            return;
        }
        if (encodeOutput.isEmpty()) {
            ItemStack blankPattern = this.blankPatternSlot.getItem();
            if (blankPattern.isEmpty() || !AEItems.BLANK_PATTERN.is(blankPattern)) {
                return;
            }
            blankPattern.shrink(1);
            if (blankPattern.getCount() <= 0) {
                this.blankPatternSlot.set(ItemStack.EMPTY);
            }
        }
        this.encodedPatternSlot.set(encoded);
    }

    @Override
    public List<GenericStack> repAe2Bridge$getMatterPreviewOutputs() {
        GenericStack input = encodedInputsInv.getStack(0);
        if (input == null || !(input.what() instanceof AEItemKey itemKey)) {
            repAe2Bridge$lastMatterInputKey = null;
            repAe2Bridge$matterOutputOrder.clear();
            return List.of();
        }
        repAe2Bridge$syncOrderForInput(input);
        var outputs = MatterEncodingHelper.computeMatterOutputs(
                itemKey.toStack(1), input.amount(), repAe2Bridge$matterOutputOrder);
        if (outputs == null || outputs.isEmpty()) {
            return List.of();
        }
        List<Identifier> ids = MatterEncodingHelper.idsOf(outputs);
        if (!ids.equals(repAe2Bridge$matterOutputOrder)) {
            repAe2Bridge$matterOutputOrder.clear();
            repAe2Bridge$matterOutputOrder.addAll(ids);
        }
        return outputs;
    }

    @Override
    public void repAe2Bridge$cycleMatterOutputs() {
        repAe2Bridge$getMatterPreviewOutputs();
        MatterEncodingHelper.cyclePreferredOrder(repAe2Bridge$matterOutputOrder);
        if (isClientSide()) {
            sendClientAction(REPAE2_SYNC_MATTER_ORDER, repAe2Bridge$orderAsStrings());
        }
    }

    @Override
    public boolean repAe2Bridge$canCycleMatterOutputs() {
        return repAe2Bridge$matterMode && repAe2Bridge$getMatterPreviewOutputs().size() > 1;
    }

    @Override
    public void repAe2Bridge$ensureMatterInputAmountVisible() {
        if (repAe2Bridge$matterMode) {
            stonecuttingInputSlot.setHideAmount(false);
            if (encodingLogic instanceof MatterEncodingLogicAccess access) {
                access.repAe2Bridge$setAllowStackedInputs(true);
            }
        }
    }

    @Unique
    private void repAe2Bridge$syncOrderForInput(GenericStack input) {
        AEKey key = input.what();
        if (!Objects.equals(key, repAe2Bridge$lastMatterInputKey)) {
            repAe2Bridge$lastMatterInputKey = key;
            repAe2Bridge$matterOutputOrder.clear();
        }
    }

    @Unique
    private void repAe2Bridge$syncMatterOrderServer(List<String> ids) {
        repAe2Bridge$matterOutputOrder.clear();
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed != null) {
                repAe2Bridge$matterOutputOrder.add(parsed);
            }
        }
    }

    @Unique
    private List<String> repAe2Bridge$orderAsStrings() {
        List<String> out = new ArrayList<>(repAe2Bridge$matterOutputOrder.size());
        for (Identifier id : repAe2Bridge$matterOutputOrder) {
            out.add(id.toString());
        }
        return out;
    }
}
