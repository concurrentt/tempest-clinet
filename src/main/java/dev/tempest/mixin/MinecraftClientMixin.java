package dev.tempest.mixin;

import dev.tempest.modules.MiddleClickFriendModule;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$middleClickFriend(CallbackInfo ci) {
        if (MiddleClickFriendModule.onMiddleClick((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }

    /** Window title: "Tempest Client 1.21.11" instead of "Minecraft* 1.21.11 - Singleplayer". */
    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true, require = 0)
    private void tempest$windowTitle(CallbackInfoReturnable<String> cir) {
        String t = cir.getReturnValue();
        if (t != null && t.startsWith("Minecraft")) {
            cir.setReturnValue(t.replaceFirst("^Minecraft\\*?\\s*(\\S+).*$", "Tempest Client $1"));
        }
    }
}
