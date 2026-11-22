package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import org.lwjgl.glfw.GLFW;

/** Small version-proof helpers (raw GLFW / entity calls instead of APIs that move between Minecraft versions). */
public final class Keys {
    private Keys() { }

    public static boolean shift() {
        long w = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    public static boolean ctrl() {
        long w = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    public static void sound(SoundEvent e, float volume, float pitch) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null) c.player.playSound(e, volume, pitch);
    }
}
