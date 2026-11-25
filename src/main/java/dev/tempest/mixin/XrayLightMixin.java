package dev.tempest.mixin;

import dev.tempest.modules.XrayModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray: everything that is still drawn is fully lit, so ores in pitch-black caves are easy to see.
 * 1.21.11 has two getLightmapCoordinates overloads; this targets the 4-argument one, which the chunk mesher's
 * brightness cache calls (the 2-argument one just forwards to it), so both paths are covered.
 */
@Mixin(WorldRenderer.class)
public abstract class XrayLightMixin {
    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/client/render/WorldRenderer$BrightnessGetter;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void tempest$fullLight(CallbackInfoReturnable<Integer> cir) {
        if (XrayModule.active()) cir.setReturnValue(0xF000F0);
    }
}
