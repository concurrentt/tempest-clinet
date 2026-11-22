package dev.tempest.mixin;

import dev.tempest.modules.TotemTweaksModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Shadow private ItemStack mainHand;
    @Shadow private ItemStack offHand;
    @Shadow private float equipProgressMainHand;
    @Shadow private float equipProgressOffHand;
    @Shadow private float lastEquipProgressMainHand;
    @Shadow private float lastEquipProgressOffHand;

    @Unique private int tempest$heldPushes;

    /** Held totem size: scale around the item itself while it is drawn in first person. */
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"), require = 0)
    private void tempest$heldSizeStart(LivingEntity entity, ItemStack stack, @Coerce Object displayContext, MatrixStack matrices,
                                      @Coerce Object queue, int light, CallbackInfo ci) {
        float f = TotemTweaksModule.heldScale(entity, stack, displayContext);
        if (f == 1f) return;
        matrices.push();
        matrices.scale(f, f, f);
        tempest$heldPushes++;
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("RETURN"), require = 0)
    private void tempest$heldSizeEnd(LivingEntity entity, ItemStack stack, @Coerce Object displayContext, MatrixStack matrices,
                                    @Coerce Object queue, int light, CallbackInfo ci) {
        if (tempest$heldPushes > 0 && TotemTweaksModule.heldScale(entity, stack, displayContext) != 1f) {
            matrices.pop();
            tempest$heldPushes--;
        }
    }

    /** Keeps a held totem fully raised so it never plays the equip animation. */
    @Inject(method = "updateHeldItems", at = @At("TAIL"))
    private void tempest$noTotemEquip(CallbackInfo ci) {
        if (!TotemTweaksModule.noEquip()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ItemStack actualMain = client.player.getMainHandStack();
        ItemStack actualOff = client.player.getOffHandStack();

        // The renderer keeps its own copies of both held stacks. When a totem is
        // consumed or swapped, synchronize those copies immediately so the old
        // totem model cannot remain visible until the next equip interpolation.
        if (mainHand.isOf(Items.TOTEM_OF_UNDYING) || actualMain.isOf(Items.TOTEM_OF_UNDYING)) {
            if (!ItemStack.areEqual(mainHand, actualMain)) {
                mainHand = actualMain.copy();
                equipProgressMainHand = 1f;
                lastEquipProgressMainHand = 1f;
            } else if (actualMain.isOf(Items.TOTEM_OF_UNDYING)) {
                equipProgressMainHand = 1f;
                lastEquipProgressMainHand = 1f;
            }
        }

        if (offHand.isOf(Items.TOTEM_OF_UNDYING) || actualOff.isOf(Items.TOTEM_OF_UNDYING)) {
            if (!ItemStack.areEqual(offHand, actualOff)) {
                offHand = actualOff.copy();
                equipProgressOffHand = 1f;
                lastEquipProgressOffHand = 1f;
            } else if (actualOff.isOf(Items.TOTEM_OF_UNDYING)) {
                equipProgressOffHand = 1f;
                lastEquipProgressOffHand = 1f;
            }
        }
    }
}
