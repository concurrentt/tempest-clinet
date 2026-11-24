package dev.tempest.mixin;

import dev.tempest.modules.ClientSideCrystalsModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    /** Clientside Crystals: a hit on our stand-in crystal goes to the real one (now, or the moment it spawns). */
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$crystalHit(net.minecraft.entity.player.PlayerEntity player, net.minecraft.entity.Entity target,
                                    org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (ClientSideCrystalsModule.redirectAttack(player, target)) ci.cancel();
    }

    /** Every hit you land: reach display, combo counter, target HUD, Auto GG. */
    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void tempest$onHit(net.minecraft.entity.player.PlayerEntity player, net.minecraft.entity.Entity target,
                               org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.tempest.util.CombatTracker.onHit(net.minecraft.client.MinecraftClient.getInstance(), target);
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), require = 0)
    private void tempest$clientCrystals(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        ClientSideCrystalsModule.onInteractBlock(hitResult);
    }
}
