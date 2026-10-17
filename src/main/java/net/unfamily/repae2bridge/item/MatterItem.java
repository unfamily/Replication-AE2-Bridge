package net.unfamily.repae2bridge.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (entity instanceof Player) {
            stack.setCount(0);
        }
    }
}
