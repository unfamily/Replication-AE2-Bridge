package net.unfamily.repae2bridge.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;

import net.unfamily.repae2bridge.block.ModBlocks;
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
            registry.add(new MatterFoundryReiDisplay(holder));
        }
    }

    @Override
    public void registerEntries(EntryRegistry registry) {
        if (!MegaCellsCompat.isLoaded()) {
            registry.removeEntryIf(stack -> stack.getValue() instanceof net.minecraft.world.item.ItemStack item
                    && item.is(ModItems.MEGA_MATTER_CELL_HOUSING.get()));
        }
    }
}
