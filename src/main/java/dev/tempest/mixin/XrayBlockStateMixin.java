package dev.tempest.mixin;

import dev.tempest.modules.XrayModule;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class XrayBlockStateMixin {
    @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$xrayLight(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (XrayModule.active()) cir.setReturnValue(1.0f);
    }

    @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$xrayLightNoArgs(CallbackInfoReturnable<Float> cir) {
        if (XrayModule.active()) cir.setReturnValue(1.0f);
    }

    @Inject(method = "isOpaque", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$xrayOpaque(CallbackInfoReturnable<Boolean> cir) {
        if (!XrayModule.active()) return;
        if ((Object) this instanceof BlockState bs) cir.setReturnValue(XrayModule.isOre(bs));
    }

    @Inject(method = "getOpacity", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$xrayOpacity(BlockView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!XrayModule.active()) return;
        if ((Object) this instanceof BlockState bs) cir.setReturnValue(XrayModule.isOre(bs) ? 15 : 0);
    }

    @Inject(method = "getOpacity", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$xrayOpacityNoArgs(CallbackInfoReturnable<Integer> cir) {
        if (!XrayModule.active()) return;
        if ((Object) this instanceof BlockState bs) cir.setReturnValue(XrayModule.isOre(bs) ? 15 : 0);
    }

    @Inject(method = "isSideInvisible", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$xraySideInvisible(BlockState state, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!XrayModule.active()) return;
        if (!((Object) this instanceof BlockState bs)) return;
        boolean ore = XrayModule.isOre(bs);
        boolean otherOre = XrayModule.isOre(state);
        cir.setReturnValue(!ore && !otherOre);
    }
}
