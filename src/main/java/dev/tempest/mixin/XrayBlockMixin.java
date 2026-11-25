package dev.tempest.mixin;

import dev.tempest.modules.XrayModule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray: everything that is still drawn shows every face. (1.21.11 signature only - the old 1.20 handler that was
 * here made the whole mixin fail with "Invalid descriptor".)
 */
@Mixin(Block.class)
public class XrayBlockMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tempest$xray(BlockState state, BlockState otherState, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (!XrayModule.active()) return;
        cir.setReturnValue(XrayModule.isOre(state) || XrayModule.isOre(otherState));
    }
}
