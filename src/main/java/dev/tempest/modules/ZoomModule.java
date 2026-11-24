package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class ZoomModule extends Module {
    private static float current = 1f, last = 1f;
    private static boolean active = false;

    private final KeyBinding zoomKey = bindKey("zoom", "Zoom Key", GLFW.GLFW_KEY_C);
    private final NumberSetting level = add(new NumberSetting("level", "Zoom Level", 4.0, 1.5, 30.0, 0.5));
    private final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth Zoom", true));
    private final BoolSetting scroll = add(new BoolSetting("scroll", "Scroll to Adjust", true));
    /** Mouse sensitivity while zoomed, as a % of the normal zoomed sensitivity (100 = default). */
    private final NumberSetting sensitivity = add(new NumberSetting("sensitivity", "Zoom Sensitivity %", 100, 10, 200, 5));

    public ZoomModule() { super("zoom", "Zoom", "Hold a key to zoom in. Scroll while zooming to adjust.", Category.RENDER, true); }

    @Override
    public void onTick(MinecraftClient client) {
        active = client.currentScreen == null && zoomKey.isPressed();
        last = current;
        float target = active ? level.getFloat() : 1f;
        current = smooth.get() ? MathHelper.lerp(0.35f, current, target) : target;
        if (!active && Math.abs(current - 1f) < 0.005f) current = 1f;
    }

    public static boolean isActive() { return ModuleManager.enabled(ZoomModule.class) && active; }

    public static float factor(float tickProgress) {
        if (!ModuleManager.enabled(ZoomModule.class)) return 1f;
        return MathHelper.lerp(tickProgress, last, current);
    }

    public static double sensitivityScale() {
        float f = factor(1f);
        if (f <= 1.0001f) return 1.0;
        ZoomModule m = ModuleManager.get(ZoomModule.class);
        double pct = m != null ? m.sensitivity.get() / 100.0 : 1.0;
        return pct / f;
    }

    public static boolean onScroll(double amount) {
        ZoomModule m = ModuleManager.get(ZoomModule.class);
        if (m == null || !m.isEnabled() || !active || !m.scroll.get() || amount == 0) return false;
        m.level.set(m.level.get() + Math.signum(amount) * 0.5);
        return true;
    }
}
