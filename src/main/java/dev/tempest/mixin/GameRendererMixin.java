package dev.tempest.mixin;

import dev.tempest.modules.CustomFovModule;
import dev.tempest.modules.ZoomModule;
import dev.tempest.util.Projection;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void tempest$zoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        float f = ZoomModule.factor(tickProgress);
        float v = cir.getReturnValue() * CustomFovModule.fovScale();
        if (f != 1f) v = v / f;
        v = Math.max(1f, Math.min(170f, v));
        cir.setReturnValue(v);
        Projection.fov = v;
    }
}
