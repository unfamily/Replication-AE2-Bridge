package net.unfamily.repae2bridge.recipe;

import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Helper class providing a static factory for the Matter Foundry recipe serializer.
 * In MC 26+ RecipeSerializer is a record, so we just create instances directly.
 */
public final class MatterFoundryRecipeSerializer {

    public static RecipeSerializer<MatterFoundryRecipe> create() {
        return new RecipeSerializer<>(MatterFoundryRecipe.CODEC, MatterFoundryRecipe.STREAM_CODEC);
    }

    private MatterFoundryRecipeSerializer() {}
}
