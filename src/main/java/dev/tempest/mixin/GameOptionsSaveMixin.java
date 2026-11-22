package dev.tempest.mixin;

import dev.tempest.modules.FullbrightModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright's huge brightness value never reaches options.txt: the real value is saved instead. */
@Mixin(GameOptions.class)
public abstract class GameOptionsSaveMixin {
    @Inject(method = "write", at = @At("HEAD"), require = 0)
    private void tempest$beforeWrite(CallbackInfo ci) {
        FullbrightModule.beforeSave(MinecraftClient.getInstance());
    }

    @Inject(method = "write", at = @At("RETURN"), require = 0)
    private void tempest$afterWrite(CallbackInfo ci) {
        FullbrightModule.afterSave(MinecraftClient.getInstance());
    }
}
