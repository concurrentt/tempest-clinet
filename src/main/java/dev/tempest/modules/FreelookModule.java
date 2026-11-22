package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class FreelookModule extends Module {
    private static boolean active = false;
    private static float yaw, pitch;

    private final KeyBinding key = bindKey("freelook", "Freelook Key", GLFW.GLFW_KEY_LEFT_ALT);
    private final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Switch to Third Person", true));
    private Perspective previous;

    public FreelookModule() { super("freelook", "Freelook", "Hold a key to look around without turning your character.", Category.RENDER, true); }

    @Override
    public void onTick(MinecraftClient c) {
        boolean down = c.player != null && c.currentScreen == null && key.isPressed();
        if (down && !active) {
            active = true;
            yaw = c.player.getYaw();
            pitch = c.player.getPitch();
            previous = c.options.getPerspective();
            if (thirdPerson.get()) c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!down && active) {
            stop(c);
        }
    }

    @Override public void onDisable() { stop(MinecraftClient.getInstance()); }

    private void stop(MinecraftClient c) {
        if (active && previous != null && c.options != null) c.options.setPerspective(previous);
        active = false;
    }

    public static boolean isActive() { return active && ModuleManager.enabled(FreelookModule.class); }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    public static void rotate(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }
}
