package dev.tempest.modules;

import dev.tempest.TempestConfig;
import dev.tempest.gui.ChatMacrosScreen;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chat Macros: press a key in game to send a message or a command ("/..."). Macros are edited in ChatMacrosScreen and
 * kept in config (TempestConfig.chatMacros). Only fires with no screen open, and each macro has a cooldown so a held
 * or mashed key can't spam the server (and get you kicked).
 */
public class ChatMacrosModule extends Module {
    private final NumberSetting cooldown = add(new NumberSetting("cooldown", "Cooldown (seconds)", 1.0, 0.5, 10.0, 0.5));
    private final Set<Integer> held = new HashSet<>();
    private final java.util.Map<Integer, Long> lastSent = new java.util.HashMap<>();

    public ChatMacrosModule() {
        super("chat_macros", "Chat Macros", "Keys that send a chat message or command, like /spawn or \"gg\".", Category.CHAT, false);
        add(new ActionSetting("edit", "Macros", "Edit", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            c.setScreen(new ChatMacrosScreen(c.currentScreen));
        }));
        keyHint = "Add macros with Edit. A message starting with / is sent as a command.";
    }

    @Override
    public void onTick(MinecraftClient c) {
        List<TempestConfig.ChatMacro> macros = TempestConfig.get().chatMacros;
        long handle;
        try { handle = c.getWindow().getHandle(); } catch (Throwable t) { return; }
        boolean ingame = c.currentScreen == null && c.player != null && c.getNetworkHandler() != null;
        Set<Integer> down = new HashSet<>();
        for (int i = 0; i < macros.size(); i++) {
            TempestConfig.ChatMacro m = macros.get(i);
            if (m.key < 0 || m.text == null || m.text.isBlank()) continue;
            boolean pressed = GLFW.glfwGetKey(handle, m.key) == GLFW.GLFW_PRESS;
            if (!pressed) continue;
            down.add(m.key);
            if (held.contains(m.key) || !ingame) continue;          // only on the press, not while held
            long now = System.currentTimeMillis();
            Long last = lastSent.get(i);
            if (last != null && now - last < cooldown.get() * 1000) continue;
            lastSent.put(i, now);
            send(c, m.text.trim());
        }
        held.clear();
        held.addAll(down);
    }

    private static void send(MinecraftClient c, String text) {
        if (text.length() > 256) text = text.substring(0, 256);
        var nh = c.getNetworkHandler();
        if (nh == null) return;
        if (text.startsWith("/")) nh.sendChatCommand(text.substring(1));
        else nh.sendChatMessage(text);
    }
}
