package dev.tempest.mixin;

import dev.tempest.modules.XrayModule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray, at the source: ChunkRendererRegion is the snapshot of blocks a chunk section is built from, and nothing but
 * chunk meshing reads it. While xray is on, every block not on the xray list reads as air (and water as no fluid),
 * so the chunk builder - vanilla or Fabric's Indigo renderer - simply has nothing to draw there:
 *  - ores keep all their faces (their neighbours are "air", so no face gets culled),
 *  - sections buried in stone are no longer culled as solid,
 *  - chests and other block entities vanish too unless "Chests & Barrels" is on.
 *
 * This replaces hooking SectionBuilder#build: Indigo already @Redirects the getRenderType call there, and two
 * redirects on one call can't coexist, so the game skipped ours (see the "@Redirect conflict" line in the log).
 * Plain @Injects here don't conflict with anything.
 */
@Mixin(ChunkRendererRegion.class)
public abstract class ChunkRegionXrayMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true, require = 0)
    private void tempest$xrayBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!XrayModule.active()) return;
        XrayModule.hooked = true;
        BlockState state = cir.getReturnValue();
        if (state != null && !state.isAir() && !XrayModule.keepInChunk(state)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getFluidState", at = @At("RETURN"), cancellable = true, require = 0)
    private void tempest$xrayFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (!XrayModule.active()) return;
        FluidState fluid = cir.getReturnValue();
        if (fluid != null && !fluid.isEmpty() && !XrayModule.showsFluid(fluid)) {
            cir.setReturnValue(Fluids.EMPTY.getDefaultState());
        }
    }
}
