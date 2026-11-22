package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Smooth, frame-rate-independent hotbar selection indicator. */
public final class SmoothHotbarModule extends Module {
    private final ColorSetting color = add(new ColorSetting("color", "Highlight Color", 0xFF8B9DFF));
    private final NumberSetting speed = add(new NumberSetting("speed", "Slide Speed", 14.0, 4.0, 28.0, 1.0));
    private final BoolSetting fill = add(new BoolSetting("fill", "Soft Fill", true));
    private final BoolSetting glow = add(new BoolSetting("glow", "Outer Glow", true));

    private static float animatedSlot = -1f;
    private static long lastFrameNanos;
    private static Object lastPlayer;

    public SmoothHotbarModule() {
        super("smooth_hotbar", "Smooth Hotbar", "Smoothly glides a customizable highlight between hotbar slots.", Category.QOL, true);
    }

    public static void render(DrawContext ctx, float tickDelta) {
        SmoothHotbarModule m = ModuleManager.get(SmoothHotbarModule.class);
        MinecraftClient client = MinecraftClient.getInstance();
        if (m == null || !m.isEnabled() || client.player == null || client.options.hudHidden) return;

        int selected = client.player.getInventory().getSelectedSlot();
        long now = System.nanoTime();
        if (animatedSlot < 0f || lastPlayer != client.player) {
            animatedSlot = selected;
            lastPlayer = client.player;
            lastFrameNanos = now;
        }

        // Use real frame time rather than tickDelta (which is only a partial-tick fraction).
        float dt = lastFrameNanos == 0L ? 1f / 60f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Math.max(0f, Math.min(dt, 0.05f));
        float response = 1f - (float) Math.exp(-m.speed.getFloat() * dt);
        animatedSlot += (selected - animatedSlot) * response;
        if (Math.abs(selected - animatedSlot) < 0.001f) animatedSlot = selected;

        int center = ctx.getScaledWindowWidth() / 2;
        int x = Math.round(center - 91 + animatedSlot * 20f) - 1;
        int y = ctx.getScaledWindowHeight() - 23;
        int rgb = m.color.get() & 0x00FFFFFF;

        // Keep the item readable while giving the moving selection a little depth.
        if (m.glow.get()) {
            ctx.fill(x - 2, y - 2, x + 26, y + 26, withAlpha(rgb, 0x18));
            ctx.fill(x - 1, y - 1, x + 25, y + 25, withAlpha(rgb, 0x20));
        }
        if (m.fill.get()) {
            ctx.fill(x + 1, y + 1, x + 23, y + 23, withAlpha(rgb, 0x25));
        }

        int edge = withAlpha(rgb, 0xE8);
        // Fine outline plus a stronger lower accent; the vanilla item icons remain visible.
        ctx.fill(x, y, x + 24, y + 1, edge);
        ctx.fill(x, y + 23, x + 24, y + 24, edge);
        ctx.fill(x, y + 1, x + 1, y + 23, edge);
        ctx.fill(x + 23, y + 1, x + 24, y + 23, edge);
        ctx.fill(x + 2, y + 21, x + 22, y + 23, withAlpha(rgb, 0xFF));
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
