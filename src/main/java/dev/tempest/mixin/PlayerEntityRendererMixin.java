package dev.tempest.mixin;

import dev.tempest.modules.FreelookModule;
import dev.tempest.modules.WhoAmIModule;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows the local player's ordinary nameplate to render above their own model. */
@Mixin(targets = "net.minecraft.client.render.entity.PlayerEntityRenderer")
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void tempest$showOwnName(@Coerce Object entity, double squaredDistanceToCamera,
                                     CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || entity != client.player) return;
        if (!ModuleManager.enabled(WhoAmIModule.class)) return;

        // In first person the local model is not rendered. Free Look may keep a
        // third-person view while the player turns independently of the camera.
        boolean thirdPerson = client.options != null
                && client.options.getPerspective() != Perspective.FIRST_PERSON;
        if (thirdPerson || FreelookModule.isActive()) cir.setReturnValue(true);
    }

    /** The player renderer fills its own state after the base class; decorate again once it is done. */
    @Inject(method = "updateRenderState", at = @At("RETURN"), require = 0)
    private void tempest$playerNametag(@Coerce Object entity, @Coerce Object state, float tickProgress, CallbackInfo ci) {
        if (entity instanceof net.minecraft.entity.Entity e && state instanceof net.minecraft.client.render.entity.state.EntityRenderState s
                && s.displayName != null) {
            s.displayName = dev.tempest.modules.NametagsModule.decorate(e, s.displayName);
        }
    }

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), require = 0)
    private void tempest$playerLabel(@Coerce Object state, @Coerce Object matrices, @Coerce Object queue, @Coerce Object camera, CallbackInfo ci) {
        dev.tempest.modules.NametagsModule.decorateState(state);
    }
}
