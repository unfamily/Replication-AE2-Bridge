package net.unfamily.repae2bridge.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * RecipeSerializer implementation for the Matter Foundry recipe.
 * On 1.21.1 RecipeSerializer is an interface with codec() + streamCodec().
 */
public class MatterFoundryRecipeSerializer implements RecipeSerializer<MatterFoundryRecipe> {

    @Override
    public MapCodec<MatterFoundryRecipe> codec() {
        return MatterFoundryRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, MatterFoundryRecipe> streamCodec() {
        return MatterFoundryRecipe.STREAM_CODEC;
    }
}
