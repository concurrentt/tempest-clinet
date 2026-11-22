package dev.tempest.mixin;

import dev.tempest.modules.CustomSkyModule;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom Sky: blends the fog / clear colour (the horizon) into the space palette so the new sky has no blue band. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), require = 0)
    private void tempest$spaceFog(CallbackInfoReturnable<Vector4f> cir) {
        CustomSkyModule.applyFog(cir.getReturnValue());
    }
}
