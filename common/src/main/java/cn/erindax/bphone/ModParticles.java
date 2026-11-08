package cn.erindax.bphone;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(BPhone.MOD_ID, Registries.PARTICLE_TYPE);

    public static final RegistrySupplier<SimpleParticleType> MEGAPHONE_WAVE =
            PARTICLES.register("megaphone_wave", () -> new SimpleParticleType(false) {
            });

    private ModParticles() {
    }

    public static void init() {
        PARTICLES.register();
    }
}
