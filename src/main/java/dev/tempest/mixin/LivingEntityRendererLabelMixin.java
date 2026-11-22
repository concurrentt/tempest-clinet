package dev.tempest.mixin;

import dev.tempest.modules.NametagsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shows your own nametag in third person when Nametags -> Show Own Nametag is on. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererLabelMixin {
    @Inject(method = "hasLabel", at = @At("RETURN"), cancellable = true, require = 0)
    private void tempest$ownLabel(LivingEntity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cir.getReturnValue() && entity == mc.player && !entity.isInvisible() && NametagsModule.showOwnTag()) cir.setReturnValue(true);
    }
}
