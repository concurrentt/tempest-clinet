package dev.tempest.mixin;

import dev.tempest.modules.CustomSkyModule;
import dev.tempest.render.SkyRenderer;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Sky: recolours the Overworld sky dome, draws the Tempest sky right after it (so terrain drawn later
 * covers it), and removes the sun, moon, vanilla stars and sunset glow when asked.
 */
@Mixin(SkyRendering.class)
public class SkyRenderingMixin {
    @ModifyVariable(method = "renderTopSky", at = @At("HEAD"), argsOnly = true, require = 0)
    private int tempest$skyColor(int color) {
        return CustomSkyModule.skyColor(color);
    }

    @Inject(method = "renderTopSky", at = @At("TAIL"), require = 0)
    private void tempest$drawSky(CallbackInfo ci) {
        SkyRenderer.render();
    }

    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$noCelestial(CallbackInfo ci) {
        if (CustomSkyModule.hideCelestial()) ci.cancel();
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$noSunsetGlow(CallbackInfo ci) {
        if (CustomSkyModule.hideSunsetGlow()) ci.cancel();
    }
}
