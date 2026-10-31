package net.unfamily.repae2bridge.block.chamber;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEItemKey;

import net.unfamily.repae2bridge.block.ModBlocks;
import net.unfamily.repae2bridge.block.entity.ReplicationUnitBlockEntity;

/**
 * Network-tool identity for a formed Matter Assembling Chamber:
 * one entry named "Replication Chamber" with the replication-unit icon.
 */
public final class ChamberAe2Display {

    private ChamberAe2Display() {
    }

    public static AEItemKey visualKey() {
        ItemStack stack = new ItemStack(ModBlocks.REPLICATION_UNIT.get());
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("gui.rep_ae2_bridge.replication_chamber"));
        return AEItemKey.of(stack);
    }

    /** Deterministic leader unit for the chamber (lowest BlockPos). */
    @Nullable
    public static BlockPos findLeaderUnit(Level level, ChamberStructure.FormedBox box) {
        BlockPos leader = null;
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            if (!(level.getBlockEntity(pos) instanceof ReplicationUnitBlockEntity)) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            if (leader == null || immutable.compareTo(leader) < 0) {
                leader = immutable;
            }
        }
        return leader;
    }

    public static int countUnits(Level level, ChamberStructure.FormedBox box) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            if (level.getBlockEntity(pos) instanceof ReplicationUnitBlockEntity) {
                count++;
            }
        }
        return count;
    }
}
