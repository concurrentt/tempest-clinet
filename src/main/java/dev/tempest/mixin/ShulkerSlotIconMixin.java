package dev.tempest.mixin;

import dev.tempest.modules.ShulkerPreviewModule;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shulker Preview: an icon of what's inside, drawn on shulker boxes wherever an item slot is drawn. */
@Mixin(DrawContext.class)
public abstract class ShulkerSlotIconMixin {
    @Inject(method = "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"), require = 0)
    private void tempest$shulkerIcon(TextRenderer textRenderer, ItemStack stack, int x, int y, String countText, CallbackInfo ci) {
        ShulkerPreviewModule.drawSlotIcon((DrawContext) (Object) this, stack, x, y);
    }
}
