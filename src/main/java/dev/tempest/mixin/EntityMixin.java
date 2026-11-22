package dev.tempest.mixin;

import dev.tempest.module.ModuleManager;
import dev.tempest.modules.FreelookModule;
import dev.tempest.modules.TotemPopModule;
import dev.tempest.modules.ZoomModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void tempest$freelook(double dx, double dy, CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity && FreelookModule.isActive()) {
            FreelookModule.rotate(dx, dy);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private double tempest$zoomDx(double dx) {
        if ((Object) this instanceof ClientPlayerEntity) {
            return dx * ZoomModule.sensitivityScale();
        }
        return dx;
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private double tempest$zoomDy(double dy) {
        if ((Object) this instanceof ClientPlayerEntity) {
            return dy * ZoomModule.sensitivityScale();
        }
        return dy;
    }

    // Totem pops used to be appended to getName()/getDisplayName() here. That renamed players everywhere
    // (chat, friends, Auto GG, and the singleplayer server), so it was removed: the nametag decoration
    // (NametagsModule) shows pops on the nametag only.
}
