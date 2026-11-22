package dev.tempest.mixin;

import dev.tempest.modules.LowFireModule;
import dev.tempest.modules.XrayModule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hooks the block mesher (runs while chunks are built):
 *  - Xray: blocks that are not on the xray list are skipped completely; water (and optionally lava) too.
 *  - Low Fire: fire blocks on the ground are squashed to the chosen height, or skipped at 0%.
 * Both modules rebuild the chunks when they are switched or their settings change.
 * 1.21.11: renderBlock(BlockState, BlockPos, BlockRenderView, MatrixStack, VertexConsumer, boolean, List<BlockModelPart>).
 */
@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$beforeBlock(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices,
                                     VertexConsumer consumer, boolean cull, List<?> parts, CallbackInfo ci) {
        LowFireModule.MATRIX_SCALED.set(false);
        if (XrayModule.active() && !XrayModule.shows(state)) {
            ci.cancel();
            return;
        }
        if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) {
            float h = LowFireModule.groundHeight();
            if (h <= 0f) {
                ci.cancel();
                return;
            }
            if (h < 1f) {
                matrices.push();
                matrices.scale(1f, h, 1f);   // the block origin is the bottom, so the flames shrink down onto the ground
                LowFireModule.MATRIX_SCALED.set(true);
            }
        }
    }

    @Inject(method = "renderBlock", at = @At("RETURN"), require = 0)
    private void tempest$afterBlock(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices,
                                    VertexConsumer consumer, boolean cull, List<?> parts, CallbackInfo ci) {
        if (LowFireModule.MATRIX_SCALED.get()) {
            matrices.pop();
            LowFireModule.MATRIX_SCALED.set(false);
        }
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$fluid(BlockPos pos, BlockRenderView world, VertexConsumer consumer, BlockState blockState,
                               FluidState fluidState, CallbackInfo ci) {
        if (XrayModule.active() && !XrayModule.showsFluid(fluidState)) ci.cancel();
    }
}
