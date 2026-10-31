package net.unfamily.repae2bridge.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Foundry flame: white_flame sprite tinted green (iska-style soft grayscale RGB).
 */
public class GreenFlameParticle extends FlameParticle {
    private static final float TINT_R = 0x5e / 255f;
    private static final float TINT_G = 0xc9 / 255f;
    private static final float TINT_B = 0x62 / 255f;

    public GreenFlameParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        super(level, x, y, z, xd, yd, zd);
        setColor(TINT_R, TINT_G, TINT_B);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd) {
            GreenFlameParticle particle = new GreenFlameParticle(level, x, y, z, xd, yd, zd);
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
