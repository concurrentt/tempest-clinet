package dev.tempest.mixin;

import dev.tempest.modules.AntiLeakModule;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin {
    @Shadow @Final private NativeImage image;
    @Shadow @Final private Identifier id;
    @Unique private boolean tempest$done;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tempest$scramble(CallbackInfo ci) {
        if (tempest$done) return;
        tempest$done = true;
        try {
            AntiLeakModule.apply(image, id);
        } catch (Throwable t) {
            System.err.println("[Tempest] Anti-Leak failed for " + id + ": " + t);
        }
    }
}
