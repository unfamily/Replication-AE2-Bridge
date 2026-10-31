package net.unfamily.repae2bridge.client.item;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * Picks animated crystal timeline: cycle A / cycle B (shuffled teal↔green↔purple),
 * or anomaly strip (amber frame). Anomaly is uncommon but clearly visible when it hits.
 */
public record MatterCrystalPulseProperty() implements RangeSelectItemModelProperty {

    public static final MapCodec<MatterCrystalPulseProperty> MAP_CODEC = MapCodec.unit(new MatterCrystalPulseProperty());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        long time = level != null ? level.getGameTime() : 0L;
        long t = time + (seed * 47L);
        // ~80 tick windows: anomaly rolls often enough to notice while watching an item.
        long window = t / 80L;
        int hash = Long.hashCode(window * 0xC2B2AE3D27D4EB4FL + seed);
        int roll = Math.floorMod(hash, 100);
        // ~28% of windows → anomaly strip (amber).
        if (roll < 28) {
            return 2.0F;
        }
        boolean sticky = Math.floorMod(hash >> 9, 4) == 0;
        boolean useB = sticky ? ((window / 2L) & 1L) != 0L : (window & 1L) != 0L;
        return useB ? 1.0F : 0.0F;
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }
}
