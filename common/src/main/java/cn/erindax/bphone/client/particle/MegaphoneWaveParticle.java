package cn.erindax.bphone.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class MegaphoneWaveParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float baseScale;

    private MegaphoneWaveParticle(ClientLevel level, double x, double y, double z,
                                  double xSpeed, double ySpeed, double zSpeed,
                                  SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.friction = 0.92F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        if ((xSpeed * xSpeed) + (ySpeed * ySpeed) + (zSpeed * zSpeed) > 1.0E-6D) {
            double speedLength = Math.sqrt((xSpeed * xSpeed) + (ySpeed * ySpeed) + (zSpeed * zSpeed));
            double originShift = 0.08D;
            this.x += (xSpeed / speedLength) * originShift;
            this.y += (ySpeed / speedLength) * originShift;
            this.z += (zSpeed / speedLength) * originShift;
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
        }

        this.quadSize = 0.12F;
        this.baseScale = this.quadSize;
        this.lifetime = 14 + this.random.nextInt(3);
        this.alpha = 0.9F;
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;

        float progress = (float) this.age / (float) this.lifetime;
        this.alpha = 0.95F - (progress * 0.95F);
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = ((float) this.age + partialTick) / (float) this.lifetime;
        float eased = progress * progress;
        return this.baseScale * (0.60F + (eased * 3.10F));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType particleType, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new MegaphoneWaveParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
