package dev.tempest.mixin;

import dev.tempest.modules.KillEffectsModule;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws Kill Effects and sky lyrics in the world, right after entities are submitted (the camera is at the matrix origin there). */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererEffectsMixin {
    @Inject(method = "pushEntityRenders", at = @At("TAIL"), require = 0)
    private void tempest$killEffects(MatrixStack matrices, @Coerce Object renderStates, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        KillEffectsModule.render(matrices, queue);
        dev.tempest.render.SkyLyrics.render(matrices, queue);
    }
}
