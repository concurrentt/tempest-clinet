package dev.tempest.mixin;

import dev.tempest.modules.FpsSaverModule;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** FPS Saver: block entities (chests, signs, banners...) past "Block Entity Distance" are skipped. */
@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityDistanceMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "getRenderState", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;isInRenderDistance(Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/util/math/Vec3d;)Z"))
    private boolean tempest$distance(BlockEntityRenderer renderer, BlockEntity be, Vec3d cam) {
        return renderer.isInRenderDistance(be, cam) && FpsSaverModule.blockEntityInRange(be, cam);
    }
}
