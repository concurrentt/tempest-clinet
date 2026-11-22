package dev.tempest.mixin;

import dev.tempest.gui.Draw;
import dev.tempest.modules.ClientSettingsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menus opened from the title screen (Options, Video Settings, Controls ...) get the Tempest night sky instead of the panorama. */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$background(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null || !ClientSettingsModule.styledWidgetsOn()) return;
        Draw.menuBackground(context, width, height);
        ci.cancel();
    }
}
