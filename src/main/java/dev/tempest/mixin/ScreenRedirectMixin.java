package dev.tempest.mixin;

import dev.tempest.gui.TempestScreens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets Tempest swap the title / singleplayer / multiplayer screens just before they are shown. */
@Mixin(MinecraftClient.class)
public class ScreenRedirectMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen tempest$redirect(Screen screen) {
        return TempestScreens.redirect(screen);
    }
}
