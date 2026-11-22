package dev.tempest.mixin;

import dev.tempest.modules.FastScreensModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
        "net.minecraft.client.gui.screen.DownloadingTerrainScreen",
        "net.minecraft.client.gui.screen.ReceivingLevelScreen"
})
public abstract class DownloadingTerrainScreenMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tempest$fastClose(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Close as soon as the player exists. Until then let the screen draw normally: cancelling every frame
        // left nothing drawn at all (a glitched / frozen-looking screen while the world was still loading).
        if (FastScreensModule.skipWorldLoading()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null && client.world != null) {
                client.setScreen(null);
                ci.cancel();
            }
        }
    }
}
