package net.unfamily.repae2bridge.client.item;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Returns 0 / 0.5 / 1.0 for stand / normal / rare-fast overlay selection.
 * Values stay in [0, 1] because {@link net.minecraft.client.renderer.item.ItemProperties} clamps.
 * Predicates use thresholds 0.5 and 1.0.
 */
public final class IrregularPulseProperty {

    private IrregularPulseProperty() {
    }

    public static float get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        long time = level != null ? level.getGameTime() : 0L;
        long t = time + (seed * 31L);
        long window = t / 80L;
        int hash = Long.hashCode(window * 0x9E3779B97F4A7C15L + seed);
        int roll = Math.floorMod(hash, 100);
        if (roll < 20) {
            return 1.0F;
        }
        boolean sticky = Math.floorMod(hash >> 8, 5) == 0;
        boolean odd = (window & 1L) != 0L;
        if (sticky) {
            odd = ((window / 2L) & 1L) != 0L;
        }
        return odd ? 0.5F : 0.0F;
    }
}
