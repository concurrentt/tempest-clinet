package dev.tempest.mixin;

import dev.tempest.modules.ItemPhysicsModule;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item physics. Flat: no bobbing, no spinning, laid flat. Hover: floats higher and bobs smoothly, spinning if you
 * like. Each hook is separate so one failing does not break the others.
 */
@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    /** Every method named render (as before, so Flat keeps behaving exactly as it did). */
    private static final String RENDER = "render";
    /** Only the per-item render (not the static stack-drawing ones). */
    private static final String RENDER_ITEM =
            "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V";

    /** The item being drawn right now (items are drawn one at a time on the render thread). */
    @Unique private static ItemEntityRenderState tempest$state;

    @Inject(method = RENDER_ITEM, at = @At("HEAD"), require = 0)
    private void tempest$grab(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        tempest$state = state;
    }

    /** Freeze the spin (Flat, or Hover with spinning off): age 0 leaves only the item's own random angle. */
    @ModifyArg(method = RENDER, index = 0, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;getRotation(FF)F"))
    private float tempest$noSpin(float age) {
        if (ItemPhysicsModule.flat()) return 0f;
        if (ItemPhysicsModule.hover() && !ItemPhysicsModule.hoverSpin()) return 0f;
        return age;
    }

    /** Flat: a fixed resting height. */
    @ModifyArg(method = RENDER, index = 1, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"))
    private float tempest$rest(float y) {
        return ItemPhysicsModule.flat() ? 0.09f : y;
    }

    /** Hover: the model's own lift plus our float and bob, instead of vanilla's small bob. */
    @ModifyArg(method = RENDER_ITEM, index = 1, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", ordinal = 0))
    private float tempest$hover(float y) {
        ItemEntityRenderState s = tempest$state;
        if (!ItemPhysicsModule.hover() || s == null) return y;
        // vanilla's y = its bob (sin(age / 10 + offset) * 0.1 + 0.1) + the model's lift; keep only the lift
        float vanillaBob = (float) Math.sin(s.age / 10f + s.uniqueOffset) * 0.1f + 0.1f;
        return y - vanillaBob + ItemPhysicsModule.hoverY(s.age, s.uniqueOffset);
    }

    /** Flat: tip the item over so it lies on the ground. */
    @ModifyArg(method = RENDER, index = 0, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"))
    private Quaternionfc tempest$flat(Quaternionfc q) {
        return ItemPhysicsModule.flat() ? new Quaternionf(q).rotateX((float) (-Math.PI / 2.0)) : q;
    }
}
