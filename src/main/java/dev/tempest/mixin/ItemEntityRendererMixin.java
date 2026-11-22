package dev.tempest.mixin;

import dev.tempest.modules.ItemPhysicsModule;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.render.entity.ItemEntityRenderer;

/** Item physics: no bobbing, no spinning, laid flat. Each hook is separate so one failing does not break the others. */
@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    private static final String RENDER = "render";

    /** Freeze the spin: age 0 leaves only the item's own random angle. */
    @ModifyArg(method = RENDER, index = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;getRotation(FF)F"))
    private float tempest$noSpin(float age) {
        return ItemPhysicsModule.active() ? 0f : age;
    }

    /** Replace the bobbing height with a fixed resting height. */
    @ModifyArg(method = RENDER, index = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"))
    private float tempest$rest(float y) {
        return ItemPhysicsModule.active() ? 0.09f : y;
    }

    /** Tip the item over so it lies flat. */
    @ModifyArg(method = RENDER, index = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"))
    private Quaternionfc tempest$flat(Quaternionfc q) {
        return ItemPhysicsModule.active() ? new Quaternionf(q).rotateX((float) (-Math.PI / 2.0)) : q;
    }
}
