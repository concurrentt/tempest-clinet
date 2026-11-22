package dev.tempest.mixin;

import dev.tempest.modules.HitboxModule;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recolours vanilla's hitbox box. In 1.21.11 hitboxes are drawn by EntityHitboxDebugRenderer.drawHitbox(entity,
 * tickDelta, server) with the colour as an int constant (white, -1); the eye line and look arrow keep their colours.
 */
@Mixin(targets = "net.minecraft.client.render.debug.EntityHitboxDebugRenderer")
public abstract class HitboxColorMixin {
    @Unique private static Entity tempest$entity;

    @Inject(method = "drawHitbox", at = @At("HEAD"), require = 0)
    private void tempest$begin(Entity entity, float tickDelta, boolean server, CallbackInfo ci) {
        tempest$entity = entity;
    }

    @ModifyConstant(method = "drawHitbox", constant = @Constant(intValue = -1), require = 0)
    private int tempest$color(int vanilla) {
        return HitboxModule.color(tempest$entity, vanilla);
    }

    @Inject(method = "drawHitbox", at = @At("RETURN"), require = 0)
    private void tempest$end(Entity entity, float tickDelta, boolean server, CallbackInfo ci) {
        tempest$entity = null;
    }
}
