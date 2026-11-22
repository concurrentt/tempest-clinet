package dev.tempest.mixin;

import dev.tempest.modules.MiddleClickFriendModule;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$middleClickFriend(CallbackInfo ci) {
        if (MiddleClickFriendModule.onMiddleClick((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }
}
