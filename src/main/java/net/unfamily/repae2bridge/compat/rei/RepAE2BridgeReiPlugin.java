package net.unfamily.repae2bridge.compat.rei;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.client.gui.MatterFoundryScreen;
import net.unfamily.repae2bridge.compat.FoundryRecipeLists;
import net.unfamily.repae2bridge.compat.MegaCellsCompat;
import net.unfamily.repae2bridge.item.ModItems;

@REIPluginClient
public class RepAE2BridgeReiPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new MatterFoundryReiCategory());
        registry.addWorkstations(MatterFoundryReiCategory.CATEGORY_ID,
                EntryStacks.of(ModBlocks.MATTER_FOUNDRY.get()));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        for (var holder : FoundryRecipeLists.collect()) {
            // Keep RecipeHolder as display origin so AE2/REI transfer can resolve the recipe.
            registry.add(new MatterFoundryReiDisplay(holder), holder);
        }
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        // GUI click zone (99,41)-(120,52) opens Matter Foundry recipes.
        registry.registerContainerClickArea(
                new Rectangle(99, 41, 21, 11),
                MatterFoundryScreen.class,
                MatterFoundryReiCategory.CATEGORY_ID);
    }

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(new MatterFoundryReiTransferHandler());
        registry.register(new FoundryPatternReiTransferHandler());
    }

    @Override
    public void registerEntries(EntryRegistry registry) {
        if (!MegaCellsCompat.isLoaded()) {
            registry.removeEntryIf(stack -> stack.getValue() instanceof net.minecraft.world.item.ItemStack item
                    && item.is(ModItems.MEGA_MATTER_CELL_HOUSING.get()));
        }
    }
}
