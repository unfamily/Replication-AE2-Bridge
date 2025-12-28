package net.unfamily.repae2bridge.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Base class for all virtual matter items.
 * These items will disappear if left in the player's inventory.
 * Made public for use in event handlers and type checking.
 */
public class MatterItem extends Item {
    public MatterItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // Makes the item disappear if it's in a player's inventory
        if (entity instanceof Player && !level.isClientSide()) {
            // Remove the item from inventory
            stack.setCount(0);
        }
    }
}

