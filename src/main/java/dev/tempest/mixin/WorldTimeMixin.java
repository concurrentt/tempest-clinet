package dev.tempest.mixin;

import dev.tempest.modules.TimeChangerModule;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldTimeMixin {
    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true)
    private void tempest$time(CallbackInfoReturnable<Long> cir) {
        if (!((World) (Object) this).isClient()) return;
        long t = TimeChangerModule.override();
        if (t >= 0) cir.setReturnValue(t);
    }

    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
    private void tempest$rain(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (!((World) (Object) this).isClient()) return;
        float t = TimeChangerModule.rainOverride();
        if (t >= 0f) cir.setReturnValue(t);
    }

    @Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true)
    private void tempest$thunder(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (!((World) (Object) this).isClient()) return;
        float t = TimeChangerModule.thunderOverride();
        if (t >= 0f) cir.setReturnValue(t);
    }
}
