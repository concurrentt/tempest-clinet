package dev.tempest.mixin;

import dev.tempest.modules.FriendsModule;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-side glow outline for Tempest friends (only changes how they are drawn on your screen). */
@Mixin(Entity.class)
public class EntityGlowMixin {
    @Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true)
    private void tempest$glow(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && (Object) this instanceof AbstractClientPlayerEntity p && FriendsModule.glows(p)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
    private void tempest$glowColor(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof AbstractClientPlayerEntity p && FriendsModule.glows(p)) {
            cir.setReturnValue(FriendsModule.glowColor());
        }
    }
}
