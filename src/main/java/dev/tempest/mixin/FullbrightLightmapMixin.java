package dev.tempest.mixin;

import dev.tempest.module.ModuleManager;
import dev.tempest.modules.FullbrightModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fullbright: the lightmap is built as if you had Night Vision (full strength), so caves are lit no matter how the
 * game treats a brightness value above 100%. Only changes your own screen.
 */
@Mixin(LightmapTextureManager.class)
public abstract class FullbrightLightmapMixin {
    private static boolean tempest$on() { return ModuleManager.enabled(FullbrightModule.class); }

    @Redirect(method = "update", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
    private boolean tempest$hasEffect(ClientPlayerEntity player, RegistryEntry<StatusEffect> effect) {
        if (tempest$on() && effect == StatusEffects.NIGHT_VISION) return true;
        return player.hasStatusEffect(effect);
    }

    @Redirect(method = "update", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F"))
    private float tempest$nightVision(LivingEntity entity, float tickProgress) {
        if (tempest$on()) return 1f;
        return GameRenderer.getNightVisionStrength(entity, tickProgress);
    }
}
