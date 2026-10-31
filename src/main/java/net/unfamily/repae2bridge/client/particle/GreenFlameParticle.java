package net.unfamily.repae2bridge.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Foundry flame: same white_flame sprite as iska_utils entropic spawner, tinted green.
 * Soft edges come from grayscale RGB on the sprite (cutout alpha), like {@code EntropicFlameParticle}.
 */
public class GreenFlameParticle extends FlameParticle {
    private static final float TINT_R = 0x5e / 255f;
    private static final float TINT_G = 0xc9 / 255f;
    private static final float TINT_B = 0x62 / 255f;

    public GreenFlameParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, xd, yd, zd, sprites.get(random));
        setColor(TINT_R, TINT_G, TINT_B);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd, RandomSource random) {
            return new GreenFlameParticle(level, x, y, z, xd, yd, zd, sprites, random);
        }
    }
}
