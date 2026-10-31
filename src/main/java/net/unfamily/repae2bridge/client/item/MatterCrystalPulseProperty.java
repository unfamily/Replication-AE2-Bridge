package net.unfamily.repae2bridge.client.item;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Picks animated crystal timeline: cycle A / cycle B / anomaly strip.
 * Returns 0 / 0.5 / 1.0 for ItemProperties clamp-safe override selection.
 */
public final class MatterCrystalPulseProperty {

    private MatterCrystalPulseProperty() {
    }

    public static float get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        long time = level != null ? level.getGameTime() : 0L;
        long t = time + (seed * 47L);
        long window = t / 80L;
        int hash = Long.hashCode(window * 0xC2B2AE3D27D4EB4FL + seed);
        int roll = Math.floorMod(hash, 100);
        if (roll < 28) {
            return 1.0F;
        }
        boolean sticky = Math.floorMod(hash >> 9, 4) == 0;
        boolean useB = sticky ? ((window / 2L) & 1L) != 0L : (window & 1L) != 0L;
        return useB ? 0.5F : 0.0F;
    }
}
