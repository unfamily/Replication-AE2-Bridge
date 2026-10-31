package net.unfamily.repae2bridge.client.item;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * Returns 0 / 1 / 2 for stand / normal / rare-fast overlay selection.
 * Stand and normal alternate irregularly; phase 2 (fast) appears rarely.
 */
public record IrregularPulseProperty() implements RangeSelectItemModelProperty {

    public static final MapCodec<IrregularPulseProperty> MAP_CODEC = MapCodec.unit(new IrregularPulseProperty());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        long time = level != null ? level.getGameTime() : 0L;
        // Mix seed so different stacks desync slightly.
        long t = time + (seed * 31L);
        // ~80 tick windows; within each window pick phase from a cheap hash.
        long window = t / 80L;
        int hash = Long.hashCode(window * 0x9E3779B97F4A7C15L + seed);
        int roll = Math.floorMod(hash, 100);
        if (roll < 20) {
            return 2.0F; // occasional fast (reverse sprite phase)
        }
        // Alternate stand/normal with occasional sticky repeats.
        boolean sticky = Math.floorMod(hash >> 8, 5) == 0;
        boolean odd = (window & 1L) != 0L;
        if (sticky) {
            odd = ((window / 2L) & 1L) != 0L;
        }
        return odd ? 1.0F : 0.0F;
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }
}
