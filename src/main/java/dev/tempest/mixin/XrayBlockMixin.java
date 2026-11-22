package dev.tempest.mixin;

import dev.tempest.modules.XrayModule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class XrayBlockMixin {
    // 1.20.x style
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tempest$xray(BlockState state, BlockView world, BlockPos pos, Direction side, BlockPos otherPos, CallbackInfoReturnable<Boolean> cir) {
        if (!XrayModule.active()) return;
        boolean ore = XrayModule.isOre(state);
        if (ore) {
            cir.setReturnValue(true);
        } else {
            BlockState other = world.getBlockState(otherPos);
            cir.setReturnValue(XrayModule.isOre(other));
        }
    }

    // 1.21.2+ style
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tempest$xrayNew(BlockState state, BlockState otherState, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (!XrayModule.active()) return;
        boolean ore = XrayModule.isOre(state);
        if (ore) cir.setReturnValue(true);
        else cir.setReturnValue(XrayModule.isOre(otherState));
    }
}
