package dev.tempest.mixin;

import dev.tempest.modules.BlockOutlineModule;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block Outline. 1.21.11: drawBlockOutline(MatrixStack, VertexConsumer, double x, double y, double z,
 * OutlineRenderState state, int color, float lineWidth). Changes the colour and width going in, and adds the
 * optional fill afterwards.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererOutlineMixin {
    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, require = 0)
    private int tempest$outlineColor(int color) {
        return BlockOutlineModule.color(color);
    }

    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, require = 0)
    private float tempest$outlineWidth(float width) {
        return BlockOutlineModule.width(width);
    }

    @Inject(method = "drawBlockOutline", at = @At("TAIL"), require = 0)
    private void tempest$outlineFill(MatrixStack matrices, VertexConsumer consumer, double x, double y, double z,
                                     @Coerce Object state, int color, float width, CallbackInfo ci) {
        BlockOutlineModule.drawFill(matrices.peek().getPositionMatrix(), x, y, z);
    }
}
