package net.unfamily.repae2bridge.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.resources.ResourceLocation;

import net.unfamily.repae2bridge.RepAE2Bridge;
import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.compat.FoundryRecipeLists;
import net.unfamily.repae2bridge.compat.MegaCellsCompat;
import net.unfamily.repae2bridge.item.ModItems;
import net.unfamily.repae2bridge.menu.MatterFoundryMenu;

@EmiEntrypoint
public class RepAE2BridgeEmiPlugin implements EmiPlugin {

    public static final ResourceLocation FOUNDRY_ID = ResourceLocation.fromNamespaceAndPath(RepAE2Bridge.MOD_ID, "matter_foundry");
    public static final EmiRecipeCategory FOUNDRY = new EmiRecipeCategory(FOUNDRY_ID,
            EmiStack.of(ModBlocks.MATTER_FOUNDRY.get()));

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(FOUNDRY);
        registry.addWorkstation(FOUNDRY, EmiStack.of(ModBlocks.MATTER_FOUNDRY.get()));
        registry.addRecipeHandler(MatterFoundryMenu.TYPE, new MatterFoundryEmiRecipeHandler());
        for (var holder : FoundryRecipeLists.collect()) {
            registry.addRecipe(new MatterFoundryEmiRecipe(holder));
        }
        if (!MegaCellsCompat.isLoaded()) {
            registry.removeEmiStacks(EmiStack.of(ModItems.MEGA_MATTER_CELL_HOUSING.get()));
        }
    }
}
