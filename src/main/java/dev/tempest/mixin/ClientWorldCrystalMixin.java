package dev.tempest.mixin;

import dev.tempest.modules.ClientSideCrystalsModule;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clientside Crystals: notice the server's real crystal the moment it spawns (not on the next tick). */
@Mixin(ClientWorld.class)
public abstract class ClientWorldCrystalMixin {
    @Inject(method = "addEntity", at = @At("TAIL"), require = 0)
    private void tempest$added(Entity entity, CallbackInfo ci) {
        ClientSideCrystalsModule.onEntityAdded(entity);
    }
}
