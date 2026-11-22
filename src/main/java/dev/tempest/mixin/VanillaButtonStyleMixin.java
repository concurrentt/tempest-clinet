package dev.tempest.mixin;

import dev.tempest.gui.VanillaStyle;
import dev.tempest.modules.ClientSettingsModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the stone button sprite for the Tempest glass button (only the background; vanilla still draws the label/icon). */
@Mixin(PressableWidget.class)
public abstract class VanillaButtonStyleMixin {
    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$button(DrawContext context, CallbackInfo ci) {
        if (!ClientSettingsModule.styledWidgetsOn()) return;
        ClickableWidget w = (ClickableWidget) (Object) this;
        VanillaStyle.button(context, w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.isSelected(), w.active);
        ci.cancel();
    }
}
