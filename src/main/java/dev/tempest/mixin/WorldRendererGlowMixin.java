package dev.tempest.mixin;

import dev.tempest.modules.FriendsModule;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Social -> Light Glow: swaps the glow-outline post effect (minecraft:entity_outline - edge detect, two blur passes
 * and a copy) for Tempest's lighter one (edge detect and a copy: assets/tempest/post_effect/entity_outline_light.json).
 * The post effect is looked up every frame, so the setting applies straight away.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererGlowMixin {
    @Redirect(method = "render", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lnet/minecraft/client/render/WorldRenderer;ENTITY_OUTLINE:Lnet/minecraft/util/Identifier;"), require = 0)
    private Identifier tempest$outlineEffect() {
        return FriendsModule.outlineEffect();
    }
}
