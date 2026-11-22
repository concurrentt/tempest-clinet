package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;

/**
 * Counts real mouse clicks for the CPS counter. Sits in front of Minecraft's own GLFW mouse-button callback
 * (and passes every event on to it), so each physical press is counted exactly once, at any frame rate,
 * no matter which game method ends up handling it.
 */
public final class InputHooks {
    private static GLFWMouseButtonCallback previous;
    private static GLFWMouseButtonCallbackI ours;   // strong reference: GLFW only keeps a native pointer
    private static long installedOn;

    private InputHooks() { }

    /** Called every client tick; installs once per window. */
    public static void ensureInstalled(MinecraftClient c) {
        long handle;
        try {
            handle = c.getWindow().getHandle();
        } catch (Throwable t) {
            return;
        }
        if (handle == 0 || handle == installedOn) return;
        installedOn = handle;
        ours = (window, button, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen == null) {          // only clicks in game, like every PvP client
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        CombatTracker.onLeftClick();
                        dev.tempest.modules.OldAnimationsModule.onLeftClick(mc);
                    }
                    else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) CombatTracker.onRightClick();
                }
            }
            if (previous != null) previous.invoke(window, button, action, mods);
        };
        previous = GLFW.glfwSetMouseButtonCallback(handle, ours);
    }
}
