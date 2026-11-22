package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Smooth Chat: new chat lines slide up into place instead of popping in (like the Chat Animation mod).
 * The chat is drawn shifted down by the height of the new lines and eases back to rest; the part below the
 * chat's bottom edge is clipped so nothing slides over the hotbar. Hook: ChatHudMixin.
 */
public final class ChatAnimationModule extends Module {
    private final NumberSetting duration = add(new NumberSetting("duration", "Duration (ms)", 180, 60, 500, 10));

    private static long startedAt;
    private static float fromPx;            // offset when the current slide started

    public ChatAnimationModule() {
        super("chat_animation", "Chat Animation", "New chat messages slide in smoothly.", Category.CHAT, true);
    }

    private static ChatAnimationModule on() {
        ChatAnimationModule m = ModuleManager.get(ChatAnimationModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    private static float linePx() {
        MinecraftClient c = MinecraftClient.getInstance();
        double scale = c.options.getChatScale().getValue();
        double spacing = c.options.getChatLineSpacing().getValue();
        return (float) ((int) (9.0 * (spacing + 1.0)) * scale);
    }

    /** Some wrapped lines were just added at the bottom. */
    public static void onLinesAdded(int lines) {
        ChatAnimationModule m = on();
        if (m == null || lines <= 0) return;
        float current = offset();                         // keep sliding smoothly when messages arrive quickly
        fromPx = Math.min(current + lines * linePx(), 6 * linePx());
        startedAt = System.currentTimeMillis();
    }

    /** How far down the chat is drawn right now (pixels), 0 when at rest. */
    public static float offset() {
        ChatAnimationModule m = on();
        if (m == null || fromPx <= 0f) return 0f;
        float t = (System.currentTimeMillis() - startedAt) / (float) m.duration.get();
        if (t >= 1f) { fromPx = 0f; return 0f; }
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);     // ease-out cubic
        return fromPx * (1f - ease);
    }
}
