package net.unfamily.repae2bridge.block.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;
import com.buuz135.replication.api.matter_fluid.IMatterTank;
import com.buuz135.replication.api.matter_fluid.MatterStack;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;
import net.unfamily.repae2bridge.util.MatterTypeUtil;

/**
 * Persistent Foundry matter buffer: up to {@link #MAX_TYPES} types, capped at recipe Y (required).
 * Cancelled crafts keep buffered X for future runs.
 */
public final class FoundryMatterBuffer {

    public static final int MAX_TYPES = 18;

    /**
     * Soft per-type cap while no recipe is matched, so AE2 can push matter before items arrive.
     * Once a recipe is known, inserts are capped at recipe Y instead.
     */
    public static final double OPEN_INSERT_CAP = 1_000_000.0;

    /** Buffered amounts keyed by matter registry id (one entry per type — amounts stack). */
    private final LinkedHashMap<Identifier, Double> amounts = new LinkedHashMap<>();

    public record DisplaySlot(@Nullable IMatterType type, double buffered, double required) {
        public boolean isEmpty() {
            return type == null || (buffered <= 0 && required <= 0);
        }
    }

    public double getAmount(IMatterType type) {
        Identifier id = idOf(type);
        if (id == null) {
            return 0;
        }
        return amounts.getOrDefault(id, 0.0);
    }

    /** Discard all buffered matter (GUI dump). */
    public void clearAll() {
        amounts.clear();
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public double getRequired(@Nullable MatterFoundryRecipe recipe, IMatterType type) {
        if (recipe == null || type == null) {
            return 0;
        }
        Identifier id = idOf(type);
        if (id == null) {
            return 0;
        }
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            if (id.equals(cost.matterId())) {
                return Math.max(0, cost.amount());
            }
        }
        return 0;
    }

    public double openInsertCap() {
        return OPEN_INSERT_CAP;
    }

    /**
     * How much more can be accepted for this type.
     * <ul>
     *   <li>No recipe: soft-capped open insert (AE2 may deliver matter before items).</li>
     *   <li>Recipe matched: only required types, capped at Y.</li>
     * </ul>
     * Amounts for the same type always share one buffer entry (4+4 → 8, not two slots).
     */
    public double roomFor(@Nullable MatterFoundryRecipe recipe, IMatterType type) {
        if (type == null || type.equals(MatterType.EMPTY)) {
            return 0;
        }
        Identifier id = idOf(type);
        if (id == null) {
            return 0;
        }
        if (!amounts.containsKey(id) && amounts.size() >= MAX_TYPES) {
            return 0;
        }
        if (recipe == null) {
            return Math.max(0, OPEN_INSERT_CAP - getAmount(type));
        }
        double y = getRequired(recipe, type);
        if (y <= 0) {
            return 0;
        }
        return Math.max(0, y - getAmount(type));
    }

    public boolean hasAllRequired(@Nullable MatterFoundryRecipe recipe) {
        if (recipe == null || recipe.getMatter().isEmpty()) {
            return true;
        }
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            IMatterType type = cost.resolve();
            if (type == null) {
                return false;
            }
            if (getAmount(type) + 1e-6 < cost.amount()) {
                return false;
            }
        }
        return true;
    }

    /** Consume recipe costs from the buffer. @return false if short */
    public boolean consumeRequired(@Nullable MatterFoundryRecipe recipe) {
        if (recipe == null || recipe.getMatter().isEmpty()) {
            return true;
        }
        if (!hasAllRequired(recipe)) {
            return false;
        }
        for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
            IMatterType type = cost.resolve();
            if (type == null) {
                return false;
            }
            Identifier id = idOf(type);
            if (id == null) {
                return false;
            }
            double left = amounts.getOrDefault(id, 0.0) - cost.amount();
            if (left <= 1e-9) {
                amounts.remove(id);
            } else {
                amounts.put(id, left);
            }
        }
        return true;
    }

    public double insert(IMatterType type, double amount, @Nullable MatterFoundryRecipe recipe, boolean simulate) {
        if (type == null || type.equals(MatterType.EMPTY) || amount <= 0) {
            return 0;
        }
        Identifier id = idOf(type);
        if (id == null) {
            return 0;
        }
        double room = roomFor(recipe, type);
        if (room <= 0) {
            return 0;
        }
        double toAdd = Math.min(amount, room);
        if (!simulate && toAdd > 0) {
            amounts.put(id, amounts.getOrDefault(id, 0.0) + toAdd);
        }
        return toAdd;
    }

    /**
     * Build up to {@link #MAX_TYPES} tank views for capability / GUI:
     * recipe-required types first, then leftover buffered types not in recipe.
     */
    public List<IMatterTank> buildTanks(@Nullable MatterFoundryRecipe recipe) {
        List<IMatterTank> tanks = new ArrayList<>();
        LinkedHashMap<Identifier, Double> required = new LinkedHashMap<>();
        if (recipe != null) {
            for (MatterFoundryRecipe.MatterCost cost : recipe.getMatter()) {
                if (cost.resolve() != null) {
                    required.put(cost.matterId(), (double) cost.amount());
                }
            }
        }
        for (Map.Entry<Identifier, Double> e : required.entrySet()) {
            if (tanks.size() >= MAX_TYPES) {
                break;
            }
            tanks.add(new SlotTank(e.getKey(), e.getValue(), recipe));
        }
        for (Map.Entry<Identifier, Double> e : amounts.entrySet()) {
            if (tanks.size() >= MAX_TYPES) {
                break;
            }
            if (required.containsKey(e.getKey())) {
                continue;
            }
            if (e.getValue() <= 0) {
                continue;
            }
            tanks.add(new SlotTank(e.getKey(), 0, recipe));
        }
        return tanks;
    }

    public List<DisplaySlot> buildDisplay(@Nullable MatterFoundryRecipe recipe) {
        List<DisplaySlot> slots = new ArrayList<>(MAX_TYPES);
        for (IMatterTank tank : buildTanks(recipe)) {
            if (tank instanceof SlotTank st) {
                slots.add(new DisplaySlot(st.resolveType(), st.getMatterAmount(), st.capacityY));
            }
        }
        while (slots.size() < MAX_TYPES) {
            slots.add(new DisplaySlot(null, 0, 0));
        }
        return slots;
    }

    /** Cheap fingerprint for GUI sync (buffer X changes). */
    public int displaySyncHash() {
        int hash = amounts.size();
        for (Map.Entry<Identifier, Double> e : amounts.entrySet()) {
            hash = 31 * hash + e.getKey().hashCode();
            hash = 31 * hash + Double.hashCode(e.getValue());
        }
        return hash;
    }

    /** Client sync path: required amounts by registry id (no live recipe object). */
    public List<DisplaySlot> buildDisplayFromRequired(Map<Identifier, Integer> required) {
        List<DisplaySlot> slots = buildDisplayFromRequiredRecipeOnly(required);
        LinkedHashMap<Identifier, Double> req = new LinkedHashMap<>();
        if (required != null) {
            for (Map.Entry<Identifier, Integer> e : required.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    req.put(e.getKey(), e.getValue().doubleValue());
                }
            }
        }
        for (Map.Entry<Identifier, Double> e : amounts.entrySet()) {
            if (slots.size() >= MAX_TYPES) {
                break;
            }
            if (req.containsKey(e.getKey()) || e.getValue() <= 0) {
                continue;
            }
            int last = slots.size() - 1;
            while (last >= 0 && slots.get(last).isEmpty()) {
                slots.remove(last);
                last--;
            }
            if (slots.size() >= MAX_TYPES) {
                break;
            }
            var info = MatterTypeUtil.getMatterInfoById(e.getKey());
            IMatterType type = info != null ? info.matterType() : null;
            slots.add(new DisplaySlot(type, e.getValue(), 0));
        }
        while (slots.size() < MAX_TYPES) {
            slots.add(new DisplaySlot(null, 0, 0));
        }
        return slots;
    }

    /**
     * Recipe matter rows only: X = buffered amount of that type (may be {@code > Y}), Y = required.
     */
    public List<DisplaySlot> buildDisplayFromRequiredRecipeOnly(Map<Identifier, Integer> required) {
        List<DisplaySlot> slots = new ArrayList<>(MAX_TYPES);
        if (required != null) {
            for (Map.Entry<Identifier, Integer> e : required.entrySet()) {
                if (slots.size() >= MAX_TYPES) {
                    break;
                }
                if (e.getValue() == null || e.getValue() <= 0) {
                    continue;
                }
                var info = MatterTypeUtil.getMatterInfoById(e.getKey());
                IMatterType type = info != null ? info.matterType() : null;
                slots.add(new DisplaySlot(type, amounts.getOrDefault(e.getKey(), 0.0), e.getValue().doubleValue()));
            }
        }
        while (slots.size() < MAX_TYPES) {
            slots.add(new DisplaySlot(null, 0, 0));
        }
        return slots;
    }

    public void save(ValueOutput output) {
        var list = output.childrenList("matterBuffer");
        for (Map.Entry<Identifier, Double> e : amounts.entrySet()) {
            if (e.getValue() <= 0) {
                continue;
            }
            ValueOutput child = list.addChild();
            child.store("id", Identifier.CODEC, e.getKey());
            child.putDouble("amount", e.getValue());
        }
    }

    public void load(ValueInput input) {
        amounts.clear();
        input.childrenList("matterBuffer").ifPresent(children -> {
            for (ValueInput child : children) {
                Identifier id = child.read("id", Identifier.CODEC).orElse(null);
                double amount = child.getDoubleOr("amount", 0);
                if (id == null || amount <= 0) {
                    continue;
                }
                amounts.put(id, amount);
                if (amounts.size() >= MAX_TYPES) {
                    break;
                }
            }
        });
    }

    @Nullable
    private static Identifier idOf(@Nullable IMatterType type) {
        if (type == null || type.equals(MatterType.EMPTY)) {
            return null;
        }
        var info = MatterTypeUtil.getMatterInfo(type);
        if (info != null && info.registryId() != null) {
            return info.registryId();
        }
        return null;
    }

    private final class SlotTank implements IMatterTank {
        private final Identifier matterId;
        private final double capacityY;
        private final MatterFoundryRecipe recipe;

        private SlotTank(Identifier matterId, double capacityY, @Nullable MatterFoundryRecipe recipe) {
            this.matterId = matterId;
            this.capacityY = capacityY;
            this.recipe = recipe;
        }

        @Nullable
        private IMatterType resolveType() {
            var info = MatterTypeUtil.getMatterInfoById(matterId);
            return info != null ? info.matterType() : null;
        }

        @Override
        public @NotNull MatterStack getMatter() {
            IMatterType type = resolveType();
            double amt = amounts.getOrDefault(matterId, 0.0);
            if (type == null || amt <= 0) {
                return MatterStack.EMPTY;
            }
            return new MatterStack(type, amt);
        }

        @Override
        public double getMatterAmount() {
            return amounts.getOrDefault(matterId, 0.0);
        }

        @Override
        public double getCapacity() {
            // Keep existing buffered amount visible; accept only up to recipe Y.
            double x = getMatterAmount();
            return Math.max(x, capacityY);
        }

        @Override
        public boolean isMatterValid(MatterStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            Identifier id = idOf(stack.getMatterType());
            return matterId.equals(id) && roomFor(recipe, stack.getMatterType()) > 0;
        }

        @Override
        public double fill(MatterStack resource, IFluidHandler.FluidAction action) {
            IMatterType type = resolveType();
            if (type == null || !isMatterValid(resource)) {
                return 0;
            }
            return insert(type, resource.getAmount(), recipe, !action.execute());
        }

        @Override
        public @NotNull MatterStack drain(double maxDrain, IFluidHandler.FluidAction action) {
            // External drain of craft buffer is not allowed (persist for future crafts).
            return MatterStack.EMPTY;
        }

        @Override
        public @NotNull MatterStack drain(MatterStack resource, IFluidHandler.FluidAction action) {
            return MatterStack.EMPTY;
        }
    }
}
