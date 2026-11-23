package dev.tempest.mixin;

import dev.tempest.util.Emotes;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes: after vanilla has posed a player-shaped model, apply the emote pose. Hooks the shared biped model so the
 * player model, its armour layers, cape model and ears all get the same pose.
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("TAIL"), require = 0)
    private void tempest$emote(BipedEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState p)) return;
        try {
            Emotes.pose(p.id, (BipedEntityModel<?>) (Object) this);
        } catch (RuntimeException ignored) {
            // never break player rendering over an emote
        }
    }
}
