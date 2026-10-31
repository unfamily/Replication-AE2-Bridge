package net.unfamily.repae2bridge.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import net.unfamily.repae2bridge.recipe.MatterFoundryRecipe;

/**
 * Shared helpers for JEI / EMI / REI Matter Foundry categories on 26.x.
 */
public final class FoundryRecipeLists {

    private FoundryRecipeLists() {
    }

    public static List<RecipeHolder<MatterFoundryRecipe>> collect() {
        List<RecipeHolder<MatterFoundryRecipe>> list = new ArrayList<>();
        RecipeManager manager = serverRecipeManager();
        if (manager == null) {
            return list;
        }
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (holder.value() instanceof MatterFoundryRecipe) {
                @SuppressWarnings("unchecked")
                RecipeHolder<MatterFoundryRecipe> typed = (RecipeHolder<MatterFoundryRecipe>) holder;
                list.add(typed);
            }
        }
        return list;
    }

    private static RecipeManager serverRecipeManager() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        return server != null ? server.getRecipeManager() : null;
    }

    public static List<ItemStack> expandIngredient(Ingredient ingredient, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        int safe = Math.max(1, count);
        ingredient.items().forEach(holder -> stacks.add(new ItemStack(holder.value(), safe)));
        return stacks;
    }
}
