package net.unfamily.repae2bridge.compat.jei;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.client.gui.MatterFoundryScreen;
import net.unfamily.repae2bridge.compat.FoundryRecipeLists;
import net.unfamily.repae2bridge.compat.MegaCellsCompat;
import net.unfamily.repae2bridge.item.ModItems;

@JeiPlugin
public class RepAE2BridgeJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new MatterFoundryRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(MatterFoundryRecipeCategory.RECIPE_TYPE, FoundryRecipeLists.collect());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(MatterFoundryRecipeCategory.RECIPE_TYPE,
                new ItemStack(ModBlocks.MATTER_FOUNDRY.get()));
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(MatterFoundryScreen.class, 153, 36, 4, 16,
                MatterFoundryRecipeCategory.RECIPE_TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        var recipes = FoundryRecipeLists.collect();
        if (!recipes.isEmpty()) {
            var manager = jeiRuntime.getRecipeManager();
            var existing = manager.createRecipeLookup(MatterFoundryRecipeCategory.RECIPE_TYPE).get().toList();
            if (existing.size() < recipes.size()) {
                manager.addRecipes(MatterFoundryRecipeCategory.RECIPE_TYPE, recipes);
            }
        }
        if (!MegaCellsCompat.isLoaded()) {
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
                    List.of(new ItemStack(ModItems.MEGA_MATTER_CELL_HOUSING.get())));
        }
    }
}
