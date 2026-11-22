package dev.tempest.mixin;

import dev.tempest.modules.OldAnimationsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Old Animations (see OldAnimationsModule). Separate from HeldItemRendererMixin so one can't break the other. */
@Mixin(HeldItemRenderer.class)
public abstract class OldAnimationsMixin {
    @Shadow private ItemStack mainHand;
    @Shadow private float equipProgressMainHand;
    @Shadow private float lastEquipProgressMainHand;

    @Unique private int tempest$posePushes;

    /** No cooldown dip: while the same item stays in hand it stays fully raised. Switching items still animates. */
    @Inject(method = "updateHeldItems", at = @At("TAIL"), require = 0)
    private void tempest$noDip(CallbackInfo ci) {
        if (!OldAnimationsModule.noDip()) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || !ItemStack.areEqual(mainHand, c.player.getMainHandStack())) return;
        equipProgressMainHand = 1f;
        lastEquipProgressMainHand = 1f;
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$blockPose(LivingEntity entity, ItemStack stack, @Coerce Object displayContext, MatrixStack matrices,
                                   @Coerce Object queue, int light, CallbackInfo ci) {
        if (OldAnimationsModule.hideItem(entity, stack, displayContext)) { ci.cancel(); return; }
        if (OldAnimationsModule.applyBlockPose(entity, stack, displayContext, matrices)) tempest$posePushes++;
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("RETURN"), require = 0)
    private void tempest$blockPoseEnd(LivingEntity entity, ItemStack stack, @Coerce Object displayContext, MatrixStack matrices,
                                      @Coerce Object queue, int light, CallbackInfo ci) {
        if (tempest$posePushes > 0) {
            matrices.pop();
            tempest$posePushes--;
        }
    }
}
