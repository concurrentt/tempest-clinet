package dev.tempest.mixin;

import dev.tempest.modules.ParticleFilterModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    // Filter at the particle factory so particles spawned by emitters and normal world events are covered.
    @Inject(method = "createParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$filterCreatedParticle(ParticleEffect effect, double x, double y, double z,
                                                double velocityX, double velocityY, double velocityZ,
                                                CallbackInfoReturnable<Particle> cir) {
        if (ParticleFilterModule.hides(effect) || dev.tempest.modules.FpsSaverModule.skipParticle(x, y, z)) cir.setReturnValue(null);
    }

    // Keep a second guard at the public entry point in case a particle implementation bypasses the factory.
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$filterEffect(ParticleEffect effect, double x, double y, double z,
                                      double velocityX, double velocityY, double velocityZ,
                                      CallbackInfoReturnable<Particle> cir) {
        if (ParticleFilterModule.hides(effect) || dev.tempest.modules.FpsSaverModule.skipParticle(x, y, z)) cir.setReturnValue(null);
    }

    // Some particle implementations (notably block debris) are submitted as ready-made instances.
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$filterDirectParticle(Particle particle, CallbackInfo ci) {
        if (ParticleFilterModule.hides(particle)) ci.cancel();
    }

}
