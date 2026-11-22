package dev.tempest.mixin;

import dev.tempest.gui.VanillaStyle;
import dev.tempest.modules.ClientSettingsModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tempest slider: rounded track, accent fill, white knob. */
@Mixin(SliderWidget.class)
public abstract class VanillaSliderStyleMixin {
    @Shadow protected double value;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$slider(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!ClientSettingsModule.styledWidgetsOn()) return;
        ClickableWidget w = (ClickableWidget) (Object) this;
        VanillaStyle.slider(context, w.getX(), w.getY(), w.getWidth(), w.getHeight(), value, w.getMessage(), w.isSelected(), w.active);
        ci.cancel();
    }
}
