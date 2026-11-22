package dev.tempest.mixin;

import net.minecraft.client.particle.FireworksSparkParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Drops malformed firework emitter particles rather than crashing the render thread. */
@Mixin(ParticleRenderer.class)
public abstract class ParticleRendererMixin {
    @Redirect(method = "tickParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;tick()V"), require = 0)
    private void tempest$guardMalformedFirework(Particle particle) {
        try {
            particle.tick();
        } catch (NullPointerException exception) {
            if (particle instanceof FireworksSparkParticle.FireworkParticle) {
                particle.markDead();
                return;
            }
            throw exception;
        }
    }
}
