package net.unfamily.repae2bridge.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * 5 foundry input slots as a recipe input view.
 */
public class MatterFoundryRecipeInput implements RecipeInput {

    private final ItemStack[] stacks;

    public MatterFoundryRecipeInput(ItemStack[] stacks) {
        this.stacks = stacks;
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < stacks.length ? stacks[index] : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return stacks.length;
    }
}
