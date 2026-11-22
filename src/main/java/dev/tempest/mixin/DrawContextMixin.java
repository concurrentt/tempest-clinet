package dev.tempest.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.tempest.gui.TempestLoading;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides Mojang's logo while the Tempest loading screen is showing (including during its fade-out,
 * where the logo would otherwise ghost through). Every drawTexture overload is covered because the
 * overlay may use any of them; the check is a single identifier comparison.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Inject(method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void tempest$hideLogoA(RenderPipeline pipeline, Identifier id, int x, int y, float u, float v,
                                   int width, int height, int textureWidth, int textureHeight, CallbackInfo ci) {
        if (TempestLoading.hidesMojangLogo(id)) ci.cancel();
    }

    @Inject(method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void tempest$hideLogoB(RenderPipeline pipeline, Identifier id, int x, int y, float u, float v,
                                   int width, int height, int textureWidth, int textureHeight, int color, CallbackInfo ci) {
        if (TempestLoading.hidesMojangLogo(id)) ci.cancel();
    }

    @Inject(method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void tempest$hideLogoC(RenderPipeline pipeline, Identifier id, int x, int y, float u, float v,
                                   int width, int height, int regionWidth, int regionHeight,
                                   int textureWidth, int textureHeight, CallbackInfo ci) {
        if (TempestLoading.hidesMojangLogo(id)) ci.cancel();
    }

    @Inject(method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void tempest$hideLogoD(RenderPipeline pipeline, Identifier id, int x, int y, float u, float v,
                                   int width, int height, int regionWidth, int regionHeight,
                                   int textureWidth, int textureHeight, int color, CallbackInfo ci) {
        if (TempestLoading.hidesMojangLogo(id)) ci.cancel();
    }
}
