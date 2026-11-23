package dev.tempest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.tempest.util.PackLooks;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tempest Editor's "Invert colours" switch for the crosshair: vanilla draws it inverted against the world (so a
 * coloured crosshair shows the wrong colour); switched off, it's drawn as a normal picture in its real colours.
 */
@Mixin(InGameHud.class)
public abstract class CrosshairStyleMixin {
    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"),
            require = 0)
    private void tempest$plainCrosshair(DrawContext ctx, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h,
                                        Operation<Void> original) {
        if (!PackLooks.invertCrosshair() && sprite.getPath().endsWith("crosshair")) pipeline = RenderPipelines.GUI_TEXTURED;
        original.call(ctx, pipeline, sprite, x, y, w, h);
    }
}
