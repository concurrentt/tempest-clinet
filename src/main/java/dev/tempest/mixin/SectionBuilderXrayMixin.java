package dev.tempest.mixin;

import dev.tempest.modules.XrayModule;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Xray, hooked where chunks are actually built (SectionBuilder#build), one block at a time.
 *
 * Why not BlockRenderManager#renderBlock: Fabric API's Indigo renderer redirects that call inside
 * SectionBuilder#build to its own terrain renderer, so a hook on renderBlock never runs for terrain and xray
 * hid nothing. The checks here come before that call, so they work with Indigo and with plain vanilla.
 *
 *  - getRenderType: blocks not on the xray list say INVISIBLE, so no model is built for them.
 *  - isOpaqueFullCube: hidden blocks don't count as solid for cave culling, otherwise sections buried in stone
 *    would be culled and the ores inside them would never be drawn.
 *  - getFluidState: water (and lava unless "Lava" is on) is skipped.
 *
 * Sodium builds chunks with its own code and never calls SectionBuilder; XrayModule tells the player if so.
 */
@Mixin(SectionBuilder.class)
public abstract class SectionBuilderXrayMixin {

    @Redirect(method = "build", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;getRenderType()Lnet/minecraft/block/BlockRenderType;"))
    private BlockRenderType tempest$xrayRenderType(BlockState state) {
        if (XrayModule.active()) {
            XrayModule.hooked = true;
            if (!XrayModule.shows(state)) return BlockRenderType.INVISIBLE;
        }
        return state.getRenderType();
    }

    @Redirect(method = "build", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;isOpaqueFullCube()Z"))
    private boolean tempest$xrayOcclusion(BlockState state) {
        if (XrayModule.active() && !XrayModule.shows(state)) return false;
        return state.isOpaqueFullCube();
    }

    @Redirect(method = "build", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;getFluidState()Lnet/minecraft/fluid/FluidState;"))
    private FluidState tempest$xrayFluid(BlockState state) {
        FluidState fluid = state.getFluidState();
        if (XrayModule.active() && !XrayModule.showsFluid(fluid)) return Fluids.EMPTY.getDefaultState();
        return fluid;
    }
}
