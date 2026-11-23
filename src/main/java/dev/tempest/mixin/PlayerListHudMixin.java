package dev.tempest.mixin;

import dev.tempest.modules.NametagsModule;
import dev.tempest.util.ProfileIds;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tempest logo in front of Tempest users' names in the tab list. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void tempest$logo(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Text name = cir.getReturnValue();
        Text out = NametagsModule.decorateTab(ProfileIds.of(entry.getProfile()), name);
        if (out != name) cir.setReturnValue(out);
    }
}
