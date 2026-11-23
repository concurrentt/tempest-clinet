package dev.tempest.mixin;

import dev.tempest.modules.ShieldFixesModule;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow protected ItemStack activeItemStack;
    @Shadow protected int itemUseTimeLeft;

    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$shieldFix(CallbackInfoReturnable<Boolean> cir) {
        Boolean b = ShieldFixesModule.blocking((LivingEntity) (Object) this);
        if (b != null) cir.setReturnValue(b);
    }

    /**
     * Shield Fixes - other players' raised shield. The server says "using an item" with a flag; the client then takes
     * the item from that player's hand. If the flag arrives before their equipment (they started blocking as they
     * came into view, or swapped to the shield that tick), the client loses the shield and they never look like
     * they're blocking until they lower and raise it again. Every tick, put the shield back as the item in use.
     */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void tempest$shieldResync(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!ShieldFixesModule.resyncWanted(self)) return;
        ItemStack hand = self.getStackInHand(self.getActiveHand());
        if (hand.isEmpty() || !ShieldFixesModule.isShield(hand)) return;
        if (activeItemStack.isEmpty() || !ItemStack.areItemsEqual(activeItemStack, hand) || itemUseTimeLeft <= 0) {
            activeItemStack = hand;
            if (itemUseTimeLeft <= 0) itemUseTimeLeft = hand.getMaxUseTime(self);
        }
    }
}
