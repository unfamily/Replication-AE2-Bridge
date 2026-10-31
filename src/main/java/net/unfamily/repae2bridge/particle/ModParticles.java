package net.unfamily.repae2bridge.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.repae2bridge.RepAE2Bridge;

/**
 * Custom particle types for the bridge.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, RepAE2Bridge.MOD_ID);

    /** Green foundry flame (white sprite + tint), world particles when active. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GREEN_FLAME =
            PARTICLES.register("green_flame", () -> new SimpleParticleType(false));

    private ModParticles() {}

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
