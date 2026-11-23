package dev.tempest.gui;

import dev.tempest.module.ModuleManager;
import dev.tempest.modules.EmotesModule;
import dev.tempest.util.Emotes;
import dev.tempest.util.TempestLive;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The emote wheel (B by default). Hold the key, point at an emote and let go; or tap the key and click one.
 * Number keys 1-8 pick directly. The middle stops the emote you're playing.
 */
public class EmoteWheelScreen extends Screen {
    private static final int R = 92, BUBBLE = 26, INNER = 34;
    private final boolean openedByKey;
    private final long openedAt = System.nanoTime();
    private boolean released;
    private int hovered = -1;

    public EmoteWheelScreen(boolean openedByKey) {
        super(Text.literal("Emotes"));
        this.openedByKey = openedByKey;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean shouldPause() { return false; }

    private float intro() { return MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.18f); }

    private int bx(int i, float e) { return width / 2 + Math.round((float) Math.sin(angle(i)) * R * (0.6f + 0.4f * e)); }
    private int by(int i, float e) { return height / 2 - Math.round((float) Math.cos(angle(i)) * R * (0.6f + 0.4f * e)); }
    private static double angle(int i) { return i * Math.PI * 2 / Emotes.ALL.length; }

    /** The bubble the mouse points at (by direction, once it's out of the middle), or -1. */
    private int pick(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < INNER * INNER) return -1;
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += Math.PI * 2;
        int n = Emotes.ALL.length;
        return (int) Math.round(a / (Math.PI * 2 / n)) % n;
    }

    @Override
    public void tick() {
        // hold-to-pick: when the wheel key comes back up, play what you point at
        if (!openedByKey || released || client == null) return;
        EmotesModule m = ModuleManager.get(EmotesModule.class);
        int code = m == null ? GLFW.GLFW_KEY_B : m.wheelKeyCode();
        boolean down = code > 0 && GLFW.glfwGetKey(client.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
        if (down) return;
        released = true;
        long held = (System.nanoTime() - openedAt) / 1_000_000;
        if (hovered >= 0 && held > 200) choose(hovered);
        // a quick tap leaves the wheel open for clicking
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float e = intro();
        int cx = width / 2, cy = height / 2;
        ctx.fillGradient(0, 0, width, height, MenuKit.fa(0x60000000, e), MenuKit.fa(0x40000000, e));
        TempestLoading.drawGlow(ctx, cx, cy, Math.round(R * 3.2f), Draw.accent(), 0.20f * e);
        Draw.circle(ctx, cx, cy, R + BUBBLE + 8, MenuKit.fa(0x70101218, e));
        hovered = pick(mx, my);

        Emotes.Def playing = Emotes.current();
        for (int i = 0; i < Emotes.ALL.length; i++) {
            Emotes.Def d = Emotes.ALL[i];
            boolean hv = i == hovered, on = playing != null && playing.id().equals(d.id());
            float p = Draw.hoverProgress("emote_wheel_" + i, hv);
            int x = bx(i, e), y = by(i, e), r = BUBBLE + Math.round(3 * p);
            if (hv) Draw.circle(ctx, x, y, r + 2, MenuKit.fa(Draw.withAlpha(Draw.accent(), 0xC0), e));
            Draw.circle(ctx, x, y, r, MenuKit.fa(Draw.lerpColor(0xE01A1C24, Draw.lerpColor(0xF0262A3A, Draw.accent(), 0.35f), p), e));
            if (on) Draw.circle(ctx, x, y + r - 4, 2, MenuKit.fa(0xFF6AF09A, e));
            int is = 22 + Math.round(4 * p);
            Icons.draw(ctx, "emote_" + d.id(), x - is / 2, y - is / 2 - 4, is, MenuKit.fa(0xFFFFFFFF, e));
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x, y + 12);
            ctx.getMatrices().scale(0.75f, 0.75f);
            Draw.text(ctx, tr, d.name(), -Draw.textW(tr, d.name()) / 2, 0, MenuKit.fa(hv ? 0xFFFFFFFF : 0xFFC9CCD6, e), false);
            ctx.getMatrices().popMatrix();
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x + r - 6, y - r + 2);
            ctx.getMatrices().scale(0.6f, 0.6f);
            Draw.text(ctx, tr, Integer.toString(i + 1), 0, 0, MenuKit.fa(0x70FFFFFF, e), false);
            ctx.getMatrices().popMatrix();
        }

        // middle: what you'll do / stop
        boolean midHover = hovered < 0 && playing != null && (mx - cx) * (mx - cx) + (my - cy) * (my - cy) < INNER * INNER;
        Draw.circle(ctx, cx, cy, INNER - 4, MenuKit.fa(midHover ? 0xF0402630 : 0xE0121318, e));
        String mid = hovered >= 0 ? Emotes.ALL[hovered].name() : playing != null ? "Stop" : "Emotes";
        Draw.centered(ctx, tr, mid, cx, cy - 4, MenuKit.fa(0xFFFFFFFF, e));

        String hint = openedByKey && !released ? "Point and let go" : "Click an emote - Esc to close";
        Draw.centered(ctx, tr, hint, cx, cy + R + BUBBLE + 18, MenuKit.fa(0xB0FFFFFF, e));
        String sub = TempestLive.connected() ? "Tempest players near you see it" : "Only you see it right now (not connected)";
        Draw.centered(ctx, tr, sub, cx, cy + R + BUBBLE + 30, MenuKit.fa(0x80FFFFFF, e));
        super.render(ctx, mx, my, delta);
    }

    private void choose(int i) {
        if (client != null) client.setScreen(null);
        Emotes.play(Emotes.ALL[i].id());
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int i = pick(click.x(), click.y());
        if (i >= 0) { choose(i); return true; }
        if (Emotes.current() != null) Emotes.stop();
        close();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k >= GLFW.GLFW_KEY_1 && k < GLFW.GLFW_KEY_1 + Emotes.ALL.length) { choose(k - GLFW.GLFW_KEY_1); return true; }
        EmotesModule m = ModuleManager.get(EmotesModule.class);
        if (m != null && k == m.wheelKeyCode() && (released || !openedByKey)) { close(); return true; }
        return super.keyPressed(input);
    }
}
