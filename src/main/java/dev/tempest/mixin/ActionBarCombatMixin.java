package dev.tempest.mixin;

import dev.tempest.modules.CombatTimerModule;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Combat Timer: the server's combat countdown on the action bar is moved into the (movable) Combat Timer element. */
@Mixin(InGameHud.class)
public abstract class ActionBarCombatMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$combat(Text message, boolean tinted, CallbackInfo ci) {
        if (CombatTimerModule.takeActionBar(message)) ci.cancel();
    }
}
