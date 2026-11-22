package dev.tempest.mixin;

import dev.tempest.modules.CapesModule;
import dev.tempest.util.CapeSkins;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the chosen Tempest cape into the local player's skin. Only the local player is touched, so
 * other players keep whatever cape they have. Targets are given by name (like PlayerEntityRendererMixin)
 * so no skin class has to be referenced directly.
 */
@Mixin(targets = {
        "net.minecraft.client.network.AbstractClientPlayerEntity",
        "net.minecraft.client.network.ClientPlayerEntity"
})
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void tempest$cape(CallbackInfoReturnable<Object> cir) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || (Object) this != c.player) return;
        String key = CapesModule.selected();
        if (key == null) return;
        Object out = CapeSkins.apply(cir.getReturnValue(), key);
        if (out != null) cir.setReturnValue(out);
    }
}
