package dev.tempest.mixin;

import dev.tempest.modules.NametagsModule;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the Tempest logo and totem pops into the name label (1.21.11: updateRenderState(T, S, float)). */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNametagMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"), require = 0)
    private void tempest$nametag(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (dev.tempest.gui.PlayerCardScreen.drawingModel) { state.displayName = null; return; }   // no label on the card's model
        if (state.displayName != null) state.displayName = NametagsModule.decorate(entity, state.displayName);
    }

    /** Last chance, right before the label is drawn. */
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), require = 0)
    private void tempest$labelFallback(EntityRenderState state, net.minecraft.client.util.math.MatrixStack matrices,
                                       @org.spongepowered.asm.mixin.injection.Coerce Object queue,
                                       @org.spongepowered.asm.mixin.injection.Coerce Object camera, CallbackInfo ci) {
        NametagsModule.decorateState(state);
    }
}
