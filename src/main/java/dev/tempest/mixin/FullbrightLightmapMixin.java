package dev.tempest.mixin;

import dev.tempest.modules.FullbrightModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fullbright via gamma: when the lightmap is rebuilt, it reads the Brightness option. Here it is handed
 * Fullbright's strength (e.g. 15 = 1500%) instead of the player's slider value. The lightmap shader uses that
 * number as-is (BrightnessFactor, no clamp), so even light level 0 comes out fully bright.
 *
 * Only this read is changed: the slider, options.txt and every other mod still see the player's real value, so
 * nothing has to be swapped back on save or when the game closes.
 *
 * update() reads three options (hide lightning flashes, darkness effect scale, gamma); the redirect gets the
 * option object, so only gamma is replaced.
 */
@Mixin(LightmapTextureManager.class)
public abstract class FullbrightLightmapMixin {

    @Redirect(method = "update", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"))
    private Object tempest$gamma(SimpleOption<?> option) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.options != null && option == c.options.getGamma()) {
            double g = FullbrightModule.gammaOverride();
            if (!Double.isNaN(g)) {
                FullbrightModule.hooked = true;
                return g;
            }
        }
        return option.getValue();
    }
}
