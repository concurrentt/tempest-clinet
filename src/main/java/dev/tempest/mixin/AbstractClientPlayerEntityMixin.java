package dev.tempest.mixin;

import dev.tempest.modules.CapesModule;
import dev.tempest.util.CapeSkins;
import dev.tempest.util.RemoteCosmetics;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the chosen Tempest cape into the local player's skin, and other Tempest players' shared capes into theirs
 * (anyone else keeps whatever cape they have). Targets are given by name (like PlayerEntityRendererMixin)
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
        if (c == null) return;
        String key;
        if ((Object) this == c.player) key = CapesModule.selected();
        else {
            // another Tempest player's shared cape
            RemoteCosmetics.Look look = RemoteCosmetics.of(((Entity) (Object) this).getUuid());
            key = look == null ? null : look.cape();
        }
        if (key == null) return;
        Object out = CapeSkins.apply(cir.getReturnValue(), key);
        if (out != null) cir.setReturnValue(out);
    }
}
