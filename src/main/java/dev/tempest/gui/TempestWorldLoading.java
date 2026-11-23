package dev.tempest.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * The Tempest screen shown while joining a multiplayer server, opening a singleplayer world, and
 * leaving either one. It reuses the look of the start-up screen (see {@link TempestLoading}): dark
 * backdrop, breathing glow, floating logo, thin accent progress bar.
 *
 * Status lines ("Connecting to server", "Joining world", ...) are put on and taken off with a short
 * fade, so the text is never just swapped. Unlike the start-up screen, fonts are loaded by now, so
 * the normal text renderer is used.
 *
 * This class only draws. Which screens it is drawn over is decided in {@code WorldLoadingHooks}.
 */
public final class TempestWorldLoading {
    public enum Phase {
        /** Multiplayer: resolving the address and opening the connection. */
        CONNECTING(new String[]{"Connecting to server", "Logging in", "Joining world"}),
        /** Singleplayer or after login: waiting for the world and terrain. */
        JOINING(new String[]{"Loading world", "Loading terrain", "Almost there"}),
        /** Leaving a world or server. */
        LEAVING(new String[]{"Disconnecting", "Heading back"});

        final String[] lines;
        Phase(String[] lines) { this.lines = lines; }
    }

    private static final int BG = TempestLoading.BG;
    private static final int ACCENT = 0xFF7C48F0;
    private static final int ACCENT_SOFT = 0xFFA98BFF;

    private static final Identifier LOGO = Identifier.of("tempest", "textures/gui/loading/logo.png");
    private static final Identifier GLOW = Identifier.of("tempest", "textures/gui/loading/glow.png");
    private static final int LOGO_TEX = 512, GLOW_TEX = 256;

    /** Seconds each status line stays up before the next one is put on. */
    private static final float LINE_SECONDS = 2.6f;
    /** Seconds a line takes to fade in, and to fade out. */
    private static final float LINE_FADE = 0.35f;

    private static Phase phase;
    private static long phaseStartMs;
    private static long lastMs;
    private static float screenFade;      // 0..1, eases in when a loading screen appears
    private static String customLine;     // optional override (e.g. a real status from vanilla)
    private static int lastBarBottom;

    private TempestWorldLoading() { }

    /** Call when a loading screen (re)appears so the animation starts from the top. */
    public static void start(Phase p) {
        if (phase != p) {
            phase = p;
            phaseStartMs = Util.getMeasuringTimeMs();
            customLine = null;
        }
        if (lastMs == 0) screenFade = 0f;
    }

    /** Call when the loading screen goes away. */
    public static void stop() {
        phase = null;
        lastMs = 0;
        screenFade = 0f;
        customLine = null;
        lastBarBottom = 0;
    }

    /** Move to the next phase without restarting the fade (e.g. connecting -> joining). */
    public static void advance(Phase p) {
        if (phase == p) return;
        phase = p;
        phaseStartMs = Util.getMeasuringTimeMs();
        customLine = null;
    }

    public static Phase phase() { return phase; }
    public static int lastBarBottom() { return lastBarBottom; }

    /** Lets a caller show a real status line instead of the timed ones. Pass null to go back. */
    public static void setLine(String s) { customLine = s; }

    // ------------------------------------------------------------------------------------------
    public static void render(DrawContext ctx, int mouseX, int mouseY) {
        if (phase == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        long now = Util.getMeasuringTimeMs();
        if (lastMs == 0) lastMs = now;
        float dt = Math.min(0.1f, Math.max(0f, (now - lastMs) / 1000f));
        lastMs = now;
        screenFade += (1f - screenFade) * (1f - (float) Math.exp(-dt * 9f));
        float a = clamp01(screenFade);
        float t = (now - phaseStartMs) / 1000f;
        float clock = now / 1000f;

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        // backdrop: always fully solid, so not even one frame of the vanilla screen shows through
        ctx.fill(0, 0, sw, sh, BG);

        // --- layout ------------------------------------------------------------------------
        int logo = clamp((int) (sh * 0.26f), 48, 170);
        int barW = Math.max(100, Math.round(logo * 1.5f));
        int barH = Math.max(2, Math.round(logo / 48f));
        int g1 = Math.round(logo * 0.16f), g2 = Math.round(logo * 0.14f);
        int textH = tr.fontHeight;
        int total = logo + g1 + textH + g2 + barH;
        int top = (sh - total) / 2 - Math.round(sh * 0.03f);
        int cx = sw / 2;

        // --- glow + logo ---------------------------------------------------------------------
        float flicker = flash(clock);
        float glow = 0.28f + 0.06f * (float) Math.sin(clock * 1.6f) + 0.30f * flicker;
        int glowSize = Math.round(logo * 2.6f);
        texture(ctx, GLOW, cx - glowSize / 2, top + logo / 2 - glowSize / 2, glowSize, glowSize, GLOW_TEX, GLOW_TEX, glow * a);

        float bob = (float) Math.sin(clock * 1.3f) * 1.5f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0f, bob);
        texture(ctx, LOGO, cx - logo / 2, top, logo, logo, LOGO_TEX, LOGO_TEX, a);
        ctx.getMatrices().popMatrix();

        // --- status text: put on, hold, take off -----------------------------------------------
        int ty = top + logo + g1;
        String line;
        float lineAlpha;
        if (customLine != null) {
            line = customLine;
            lineAlpha = 1f;
        } else {
            String[] lines = phase.lines;
            // The last line stays up; the earlier ones rotate.
            int idx = Math.min(lines.length - 1, (int) (t / LINE_SECONDS));
            float local = t - idx * LINE_SECONDS;
            line = lines[idx];
            float in = clamp01(local / LINE_FADE);
            float out = idx < lines.length - 1 ? clamp01((LINE_SECONDS - local) / LINE_FADE) : 1f;
            lineAlpha = ease(in) * ease(out);
            // Lines slide up a few pixels as they come on.
            ty += Math.round((1f - ease(in)) * 4f);
        }
        int dots = (int) ((clock * 2.2f) % 4);
        String shown = line + ".".repeat(dots);
        int tw = Draw.textW(tr, line + "...");
        int ta = Math.round(255 * clamp01(lineAlpha * a));
        if (ta > 4) {
            Draw.text(ctx, tr, shown, cx - tw / 2, ty, (ta << 24) | 0xE6EBFF, false);
        }

        // --- indeterminate progress bar -----------------------------------------------------------
        int by = top + logo + g1 + textH + g2;
        int bx = cx - barW / 2;
        int trackA = Math.round(0x22 * a);
        if (trackA > 0) Draw.roundRect(ctx, bx, by, barW, barH, barH / 2, (trackA << 24) | 0xFFFFFF);
        // A comet that sweeps across, easing in and out at each end.
        float sweep = ((clock * 0.75f) % 1.6f) / 1.6f;
        float eased = sweep * sweep * (3f - 2f * sweep);
        int segW = Math.max(18, barW / 3);
        int segX = bx - segW + Math.round(eased * (barW + segW));
        int x0 = Math.max(segX, bx), x1 = Math.min(segX + segW, bx + barW);
        if (x1 > x0) {
            Draw.roundRect(ctx, x0, by, x1 - x0, barH, barH / 2, Draw.withAlpha(ACCENT, Math.round(255 * a)));
            int headW = Math.max(6, segW / 4);
            int hx0 = Math.max(x1 - headW, x0);
            if (x1 > hx0) ctx.fill(hx0, by, x1, by + barH, Draw.withAlpha(ACCENT_SOFT, Math.round(0xB0 * a)));
        }
        lastBarBottom = by + barH;
    }

    // ------------------------------------------------------------------------------------------
    private static void texture(DrawContext ctx, Identifier id, int x, int y, int w, int h, int texW, int texH, float alpha) {
        int a = Math.round(clamp01(alpha) * 255f);
        if (a <= 0) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH, (a << 24) | 0xFFFFFF);
    }

    /** Same two soft lightning flickers as the start-up screen. */
    private static float flash(float t) {
        float p = t % 5.5f;
        float a = 0f;
        if (p > 3.00f && p < 3.14f) a = 1f - (p - 3.00f) / 0.14f;
        if (p > 3.24f && p < 3.60f) a = Math.max(a, 0.7f * (1f - (p - 3.24f) / 0.36f));
        return a;
    }

    private static float ease(float x) { return 1f - (1f - x) * (1f - x) * (1f - x); }
    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }
    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
}
