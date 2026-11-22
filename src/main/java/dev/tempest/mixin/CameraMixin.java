package dev.tempest.mixin;

import dev.tempest.modules.FreelookModule;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public class CameraMixin {
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void tempest$freelookRotation(Args args) {
        if (FreelookModule.isActive()) {
            args.set(0, FreelookModule.yaw());
            args.set(1, FreelookModule.pitch());
        }
    }
}
