package dev.tempest.mixin;

import dev.tempest.modules.ShieldFixesModule;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$shieldFix(CallbackInfoReturnable<Boolean> cir) {
        Boolean b = ShieldFixesModule.blocking((LivingEntity) (Object) this);
        if (b != null) cir.setReturnValue(b);
    }
}
