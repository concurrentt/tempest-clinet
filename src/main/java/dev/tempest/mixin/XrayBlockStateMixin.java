package dev.tempest.mixin;

import dev.tempest.modules.XrayModule;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray: no ambient-occlusion darkening, so ores are evenly lit.
 * The old isOpaque / getOpacity / isSideInvisible overrides are gone: they changed every block in the game (client
 * lighting, suffocation checks) and one had a pre-1.21 signature that made this whole mixin fail. Hiding blocks is
 * now done only where chunks are built (ChunkRegionXrayMixin).
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class XrayBlockStateMixin {
    @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$xrayLight(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (XrayModule.active()) cir.setReturnValue(1.0f);
    }
}
