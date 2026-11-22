package dev.tempest.mixin;

import dev.tempest.modules.ZoomModule;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(Mouse.class)
public class MouseMixin {
    @Unique private static Field tempest$cursorDeltaX;
    @Unique private static Field tempest$cursorDeltaY;
    @Unique private static boolean tempest$lookedUp;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void tempest$scrollZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ZoomModule.onScroll(vertical)) ci.cancel();
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), require = 0)
    private void tempest$zoomSensitivity(double timeDelta, CallbackInfo ci) {
        double scale = ZoomModule.sensitivityScale();
        if (scale >= 0.9999) return;
        try {
            ensureFields();
            if (tempest$cursorDeltaX != null) tempest$cursorDeltaX.setDouble(this, tempest$cursorDeltaX.getDouble(this) * scale);
            if (tempest$cursorDeltaY != null) tempest$cursorDeltaY.setDouble(this, tempest$cursorDeltaY.getDouble(this) * scale);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static void ensureFields() {
        if (tempest$lookedUp) return;
        tempest$lookedUp = true;
        tempest$cursorDeltaX = findField("cursorDeltaX", "field_1796");
        tempest$cursorDeltaY = findField("cursorDeltaY", "field_1797");
    }

    @Unique
    private static Field findField(String... names) {
        for (String name : names) {
            try {
                Field f = Mouse.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
