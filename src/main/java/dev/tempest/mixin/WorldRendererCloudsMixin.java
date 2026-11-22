package dev.tempest.mixin;

import dev.tempest.modules.CustomSkyModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Sky: no clouds. */
@Mixin(WorldRenderer.class)
public class WorldRendererCloudsMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$noClouds(CallbackInfo ci) {
        if (CustomSkyModule.hideClouds()) ci.cancel();
    }
}
