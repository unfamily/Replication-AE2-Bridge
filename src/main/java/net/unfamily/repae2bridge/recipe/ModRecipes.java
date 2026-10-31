package net.unfamily.repae2bridge.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.unfamily.repae2bridge.RepAE2Bridge;

public final class ModRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, RepAE2Bridge.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, RepAE2Bridge.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<MatterFoundryRecipe>> MATTER_FOUNDRY_TYPE =
            RECIPE_TYPES.register("matter_foundry", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return MatterFoundryRecipe.TYPE_ID.toString();
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MatterFoundryRecipe>> MATTER_FOUNDRY_SERIALIZER =
            RECIPE_SERIALIZERS.register("matter_foundry", MatterFoundryRecipeSerializer::create);

    private ModRecipes() {
    }

    public static void register(IEventBus bus) {
        RECIPE_TYPES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
    }
}
