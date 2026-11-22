package dev.tempest.mixin;

import dev.tempest.gui.TempestLoading;
import dev.tempest.modules.FastScreensModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

/**
 * Paints the Tempest loading screen over Mojang's start-up overlay.
 *
 * Vanilla keeps owning the lifecycle (when loading is done, the hold, the fade into the title screen),
 * so nothing about how the game loads or reports errors changes.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Shadow @Final @Mutable private static IntSupplier BRAND_ARGB;

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private boolean reloading;
    @Shadow private long reloadCompleteTime;
    @Shadow private float progress;

    /** Mojang red -> Tempest dark, so neither the clear colour nor the fade-out is red. */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void tempest$brand(CallbackInfo ci) {
        BRAND_ARGB = new TempestLoading.Brand(BRAND_ARGB);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void tempest$begin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TempestLoading.begin(this);
        dev.tempest.modules.AppleSkinModule.resetOutline();   // a resource reload may change the hunger icon
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void tempest$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.reloading) {
            TempestLoading.renderReload(context, this.reloadCompleteTime, this.progress);
            if (FastScreensModule.fastResourceReload() && this.reloadCompleteTime != -1L) {
                this.client.setOverlay(null);
            }
            TempestLoading.end();
            return;
        }
        TempestLoading.render(context, this.reloadCompleteTime, this.progress);
        TempestLoading.end();
    }

    /** Vanilla's progress bar is replaced by the Tempest one. */
    @Inject(method = "renderProgressBar", at = @At("HEAD"), cancellable = true)
    private void tempest$hideBar(DrawContext context, int minX, int minY, int maxX, int maxY, float opacity, CallbackInfo ci) {
        ci.cancel();
    }
}
