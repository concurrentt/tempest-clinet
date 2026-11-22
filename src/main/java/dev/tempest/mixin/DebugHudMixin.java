package dev.tempest.mixin;

import dev.tempest.modules.StreamerModeModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugHud.class)
public class DebugHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tempest$hideF3(DrawContext context, CallbackInfo ci) {
        if (StreamerModeModule.hideF3()) ci.cancel();
    }
}
