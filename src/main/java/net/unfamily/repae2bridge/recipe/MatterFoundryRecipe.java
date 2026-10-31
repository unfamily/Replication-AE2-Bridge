package net.unfamily.repae2bridge.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import net.unfamily.repae2bridge.RepAE2Bridge;

/**
 * Single foundry recipe (after iska_lib splits the datapack bundle {@code entries}).
 */
public class MatterFoundryRecipe implements Recipe<MatterFoundryRecipeInput> {

    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_foundry");

    /**
     * A counted ingredient: wraps a vanilla Ingredient with a count.
     */
    public record CountedIngredient(Ingredient ingredient, int count) {

        public static final Codec<CountedIngredient> CODEC = Codec.lazyInitialized(() ->
                RecordCodecBuilder.<CountedIngredient>create(inst -> inst.group(
                        Ingredient.CODEC.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
                        Codec.INT.optionalFieldOf("count", 1).forGetter(CountedIngredient::count)
                ).apply(inst, CountedIngredient::new))
        );

        public static final Codec<CountedIngredient> RAW_CODEC = Codec.lazyInitialized(() ->
                RecordCodecBuilder.<RawCI>create(inst -> inst.group(
                        Codec.STRING.optionalFieldOf("item").forGetter(RawCI::item),
                        Codec.STRING.optionalFieldOf("tag").forGetter(RawCI::tag),
                        Codec.INT.optionalFieldOf("count", 1).forGetter(RawCI::count)
                ).apply(inst, RawCI::new)).flatXmap(
                        raw -> {
                            try {
                                return DataResult.success(
                                        new CountedIngredient(parseIngredient(raw.item(), raw.tag()), Math.max(1, raw.count())));
                            } catch (Exception e) {
                                return DataResult.error(e::getMessage);
                            }
                        },
                        ci -> DataResult.success(new RawCI(Optional.empty(), Optional.empty(), ci.count()))
                )
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, CountedIngredient> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, CountedIngredient::ingredient,
                ByteBufCodecs.VAR_INT, CountedIngredient::count,
                CountedIngredient::new);

        private record RawCI(Optional<String> item, Optional<String> tag, int count) {}

        private static Ingredient parseIngredient(Optional<String> item, Optional<String> tag) {
            if (item.isPresent()) {
                ResourceLocation id = ResourceLocation.parse(item.get());
                Item resolved = BuiltInRegistries.ITEM.get(id);
                return Ingredient.of(resolved);
            }
            if (tag.isPresent()) {
                String raw = tag.get();
                if (raw.startsWith("#")) {
                    raw = raw.substring(1);
                }
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(raw));
                return Ingredient.of(tagKey);
            }
            throw new IllegalArgumentException("Foundry input requires item or tag");
        }
    }

    public record MatterCost(ResourceLocation matterId, int amount) {
        public static final Codec<MatterCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("matter").forGetter(MatterCost::matterId),
                Codec.INT.fieldOf("amount").forGetter(MatterCost::amount)
        ).apply(instance, MatterCost::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, MatterCost> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, MatterCost::matterId,
                ByteBufCodecs.VAR_INT, MatterCost::amount,
                MatterCost::new);

        public IMatterType resolve() {
            if (ReplicationRegistry.MATTER_TYPES_REGISTRY == null) {
                return null;
            }
            return ReplicationRegistry.MATTER_TYPES_REGISTRY.get(matterId);
        }
    }

    private final List<CountedIngredient> inputs;
    private final List<MatterCost> matter;
    private final ItemStack output;
    private final int energy;

    public MatterFoundryRecipe(List<CountedIngredient> inputs, List<MatterCost> matter, ItemStack output, int energy) {
        this.inputs = List.copyOf(inputs);
        this.matter = List.copyOf(matter);
        this.output = output.copy();
        this.energy = Math.max(1, energy);
    }

    public List<CountedIngredient> getInputs() {
        return inputs;
    }

    public List<MatterCost> getMatter() {
        return matter;
    }

    public int getEnergy() {
        return energy;
    }

    public ItemStack getResultItem() {
        return output.copy();
    }

    @Override
    public boolean matches(MatterFoundryRecipeInput input, Level level) {
        List<ItemStack> remaining = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                remaining.add(stack.copy());
            }
        }
        for (CountedIngredient required : inputs) {
            int needed = required.count();
            for (ItemStack stack : remaining) {
                if (needed <= 0) {
                    break;
                }
                if (!stack.isEmpty() && required.ingredient().test(stack)) {
                    int take = Math.min(needed, stack.getCount());
                    stack.shrink(take);
                    needed -= take;
                }
            }
            if (needed > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(MatterFoundryRecipeInput input, HolderLookup.Provider registries) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return output.copy();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MATTER_FOUNDRY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.MATTER_FOUNDRY_TYPE.get();
    }

    public static final MapCodec<MatterFoundryRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CountedIngredient.RAW_CODEC.listOf().fieldOf("inputs").forGetter(MatterFoundryRecipe::getInputs),
            MatterCost.CODEC.listOf().optionalFieldOf("matter", List.of()).forGetter(MatterFoundryRecipe::getMatter),
            ItemStack.CODEC.fieldOf("output").forGetter(r -> r.output),
            Codec.INT.fieldOf("energy").forGetter(MatterFoundryRecipe::getEnergy)
    ).apply(instance, MatterFoundryRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MatterFoundryRecipe> STREAM_CODEC = StreamCodec.composite(
            CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), MatterFoundryRecipe::getInputs,
            MatterCost.STREAM_CODEC.apply(ByteBufCodecs.list()), MatterFoundryRecipe::getMatter,
            ItemStack.STREAM_CODEC, r -> r.output,
            ByteBufCodecs.VAR_INT, MatterFoundryRecipe::getEnergy,
            MatterFoundryRecipe::new);
}
