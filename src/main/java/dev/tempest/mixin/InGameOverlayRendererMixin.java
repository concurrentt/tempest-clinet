package dev.tempest.mixin;

import dev.tempest.modules.LowFireModule;
import dev.tempest.modules.TotemTweaksModule;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Shadow public abstract void tickFloatingItemTimer();
    @Shadow private float floatingItemOffsetX;
    @Shadow private float floatingItemOffsetY;

    @Unique private static boolean tempest$ticking;
    @Unique private float tempest$acc;
    @Unique private float tempest$oldOffsetX;
    @Unique private float tempest$oldOffsetY;
    @Unique private boolean tempest$offsetsChanged;

    // Move only the fire overlay while it is being rendered, then restore the matrix.
    // The explicit restore is important because some versions do not push/pop internally.
    @Unique private static double tempest$fireOffset;

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void tempest$lowFireStart(net.minecraft.client.util.math.MatrixStack matrices,
                                              net.minecraft.client.render.VertexConsumerProvider consumers,
                                              net.minecraft.client.texture.Sprite sprite, CallbackInfo ci) {
        if (LowFireModule.shouldHideOverlay()) {
            ci.cancel();
            return;
        }
        tempest$fireOffset = LowFireModule.offset();
        if (tempest$fireOffset > 0.0) matrices.translate(0.0, -tempest$fireOffset, 0.0);
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private static void tempest$lowFireEnd(net.minecraft.client.util.math.MatrixStack matrices,
                                            net.minecraft.client.render.VertexConsumerProvider consumers,
                                            net.minecraft.client.texture.Sprite sprite, CallbackInfo ci) {
        if (tempest$fireOffset > 0.0) matrices.translate(0.0, tempest$fireOffset, 0.0);
        tempest$fireOffset = 0.0;
    }

    // ---- Totem Tweaks ----

    /**
     * Pop size. Primary: shrink the item's own scale call inside renderFloatingItem (vanilla scales the model by
     * 50..225 there), which shrinks it in place. Fallback, only if that call isn't found: scale X/Y of the stack.
     * (A uniform X/Y/Z scale does nothing visible in a perspective view: the item just moves closer.)
     */
    @Unique private static boolean tempest$argScaled;
    @Unique private boolean tempest$pushed;

    @ModifyArg(method = "renderFloatingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"), index = 0, require = 0)
    private float tempest$popScaleX(float v) { return tempest$shrink(v); }

    @ModifyArg(method = "renderFloatingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"), index = 1, require = 0)
    private float tempest$popScaleY(float v) { return tempest$shrink(v); }

    @ModifyArg(method = "renderFloatingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"), index = 2, require = 0)
    private float tempest$popScaleZ(float v) { return tempest$shrink(v); }

    @Unique
    private static float tempest$shrink(float v) {
        if (Math.abs(v) < 2f) return v;          // only the big model scale, not small helper scales
        float s = TotemTweaksModule.popScale();
        if (s == 1f) return v;
        tempest$argScaled = true;
        return v * s;
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"))
    private void tempest$scaleTotemPop(net.minecraft.client.util.math.MatrixStack matrices,
                                       float tickProgress,
                                       OrderedRenderCommandQueue queue,
                                       CallbackInfo ci) {
        float scale = TotemTweaksModule.popScale();
        tempest$pushed = false;
        if (scale != 1.0f && !tempest$argScaled) {
            matrices.push();
            matrices.scale(scale, scale, 1f);
            tempest$pushed = true;
        }

        if (TotemTweaksModule.lockPos()) {
            tempest$oldOffsetX = floatingItemOffsetX;
            tempest$oldOffsetY = floatingItemOffsetY;
            floatingItemOffsetX = 0.0f;
            floatingItemOffsetY = 0.0f;
            tempest$offsetsChanged = true;
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("TAIL"))
    private void tempest$restoreTotemPop(net.minecraft.client.util.math.MatrixStack matrices,
                                         float tickProgress,
                                         OrderedRenderCommandQueue queue,
                                         CallbackInfo ci) {
        if (tempest$pushed) matrices.pop();
        tempest$pushed = false;
        if (tempest$offsetsChanged) {
            floatingItemOffsetX = tempest$oldOffsetX;
            floatingItemOffsetY = tempest$oldOffsetY;
            tempest$offsetsChanged = false;
        }
    }

    @Inject(method = "setFloatingItem", at = @At("HEAD"), cancellable = true)
    private void tempest$hidePop(ItemStack stack, Random random, CallbackInfo ci) {
        if (stack != null && stack.isOf(Items.TOTEM_OF_UNDYING) && TotemTweaksModule.popHidden()) ci.cancel();
    }

    /** Slower than vanilla: skip some ticks. */
    @Inject(method = "tickFloatingItemTimer", at = @At("HEAD"), cancellable = true)
    private void tempest$slowPop(CallbackInfo ci) {
        if (tempest$ticking) return;
        float s = TotemTweaksModule.popSpeed();
        if (s >= 1f) return;
        tempest$acc += s;
        if (tempest$acc < 1f) ci.cancel();
        else tempest$acc -= 1f;
    }

    /** Faster than vanilla: run the vanilla tick extra times. */
    @Inject(method = "tickFloatingItemTimer", at = @At("TAIL"))
    private void tempest$fastPop(CallbackInfo ci) {
        if (tempest$ticking) return;
        float s = TotemTweaksModule.popSpeed();
        if (s <= 1f) return;
        tempest$acc += s - 1f;
        int extra = (int) tempest$acc;
        tempest$acc -= extra;
        tempest$ticking = true;
        try {
            for (int i = 0; i < extra; i++) tickFloatingItemTimer();
        } finally {
            tempest$ticking = false;
        }
    }

    // The constants below only apply if vanilla still uses them; if not, they silently do nothing.
    @ModifyConstant(method = "renderFloatingItem", constant = @Constant(floatValue = 900.0F))
    private float tempest$noSpin(float original) { return TotemTweaksModule.noSpin() ? 0f : original; }

}
