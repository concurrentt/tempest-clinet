package dev.tempest.mixin;

import dev.tempest.modules.XrayModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray: everything that is still drawn is fully lit, so ores in pitch-black caves are easy to see. */
@Mixin(WorldRenderer.class)
public abstract class XrayLightMixin {
    @Inject(method = "getLightmapCoordinates", at = @At("RETURN"), cancellable = true, require = 0)
    private static void tempest$fullLight(CallbackInfoReturnable<Integer> cir) {
        if (XrayModule.active()) cir.setReturnValue(0xF000F0);
    }
}
