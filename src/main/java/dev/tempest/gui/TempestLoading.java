package dev.tempest.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.IntSupplier;

/**
 * The "Welcome to Tempest" loading screen.
 *
 * It is drawn on top of Mojang's start-up overlay (see SplashOverlayMixin), so the overlay's own
 * timing and fade-out are untouched: Mojang decides <i>when</i> loading is finished, this class only
 * decides <i>what it looks like</i>.
 *
 * Every piece of text is a pre-rendered texture. Minecraft's fonts are themselves loaded by the
 * resource reload that this screen is covering, so asking the text renderer for glyphs this early
 * would produce missing-character boxes.
 */
public final class TempestLoading {
    /** Flat backdrop colour. Mojang's brand colour is switched to this so the fade-out has no red tint. */
    public static final int BG = 0xFF0B0D13;

    private static final int ACCENT = 0xFF7C48F0;      // the bolt in the logo
    private static final int ACCENT_SOFT = 0xFFA98BFF;

    private static final Identifier LOGO = Identifier.of("tempest", "textures/gui/loading/logo.png");
    private static final Identifier GLOW = Identifier.of("tempest", "textures/gui/loading/glow.png");
    private static final Identifier WELCOME = Identifier.of("tempest", "textures/gui/loading/welcome.png");
    private static final Identifier LOADING = Identifier.of("tempest", "textures/gui/loading/loading.png");
    private static final Identifier GLOW_SOFT = Identifier.of("tempest", "textures/gui/glow_soft.png");
    private static final Identifier APPLYING = Identifier.of("tempest", "textures/gui/loading/applying.png");
    private static final Identifier RELOADING = Identifier.of("tempest", "textures/gui/loading/reloading.png");

    private static final int LOGO_TEX = 512, GLOW_TEX = 256;
    private static final int WELCOME_W = 631, WELCOME_H = 112;
    private static final int LOADING_W = 175, LOADING_H = 56;
    private static final int APPLYING_W = 667, APPLYING_H = 112;
    private static final int RELOADING_W = 398, RELOADING_H = 56;

    /** True only while a SplashOverlay frame is rendering (set/cleared by the mixin). */
    private static boolean takeover;
    private static boolean resetOnNext;
    private static long startMs = -1, lastMs;
    private static float shownProgress;

    private TempestLoading() { }

    // ---------------------------------------------------------------- hooks used by the mixins
    /** Restarts the entrance animation only when a new overlay appears (not every frame). */
    private static Object currentOverlay;

    public static void begin(Object overlay) {
        if (overlay != currentOverlay) {
            currentOverlay = overlay;
            resetOnNext = true;
        }
        takeover = true;
    }
    public static void end() { takeover = false; }

    /** Mojang's own logo is hidden while the Tempest screen is up. */
    public static boolean hidesMojangLogo(Identifier texture) {
        return takeover && texture != null && texture.equals(net.minecraft.client.gui.screen.SplashOverlay.LOGO);
    }

    /** Replaces Mojang's brand colour (red) with the Tempest backdrop. */
    public static final class Brand implements IntSupplier {
        @SuppressWarnings("unused")
        private final IntSupplier vanilla;
        public Brand(IntSupplier vanilla) { this.vanilla = vanilla; }
        @Override public int getAsInt() { return BG; }
    }

    // ---------------------------------------------------------------- rendering
    /**
     * @param completeTime SplashOverlay.reloadCompleteTime (-1 while still loading)
     * @param vanillaProgress SplashOverlay.progress, 0..1
     */
    public static void render(DrawContext ctx, long completeTime, float vanillaProgress) {
        if (resetOnNext) { startMs = -1; shownProgress = 0f; resetOnNext = false; }
        long now = Util.getMeasuringTimeMs();
        if (startMs < 0) { startMs = now; lastMs = now; }
        float dt = Math.min(0.1f, Math.max(0f, (now - lastMs) / 1000f));
        lastMs = now;
        float t = (now - startMs) / 1000f;

        // Same timeline Mojang uses: once loading is done it holds for 1s, then fades out over 1s.
        float f = completeTime > -1L ? (now - completeTime) / 1000f : -1f;
        float fade = f >= 1f ? 1f - clamp01(f - 1f) : 1f;
        if (fade <= 0.01f) return;

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        // While Mojang's layer is fully opaque we paint the backdrop; during the fade-out Mojang's own
        // (now dark) fade shows the title screen coming through underneath.
        if (f < 1f) ctx.fill(0, 0, sw, sh, BG);

        // --- layout --------------------------------------------------------------------------
        int logo = clamp((int) (sh * 0.30f), 56, 200);
        int wordH = Math.max(9, Math.round(logo * 0.17f));
        int wordW = Math.round(wordH * (WELCOME_W / (float) WELCOME_H));
        int barW = Math.max(90, Math.round(logo * 1.35f));
        int barH = Math.max(2, Math.round(logo / 48f));
        int labH = Math.max(6, Math.round(wordH * 0.42f));
        int labW = Math.round(labH * (LOADING_W / (float) LOADING_H));
        int g1 = Math.round(logo * 0.05f), g2 = Math.round(logo * 0.15f), g3 = Math.round(logo * 0.075f);
        int total = logo + g1 + wordH + g2 + barH + g3 + labH;
        int top = (sh - total) / 2 - Math.round(sh * 0.02f);
        int cx = sw / 2;

        // --- staggered entrance ---------------------------------------------------------------
        float aLogo = ease(clamp01(t / 0.7f)) * fade;
        float aWord = ease(clamp01((t - 0.25f) / 0.7f)) * fade;
        float aBar = ease(clamp01((t - 0.55f) / 0.6f)) * fade;

        // --- glow behind the logo (breathes, with an occasional lightning flicker) -----------------
        float glow = 0.30f + 0.06f * (float) Math.sin(t * 1.6f) + 0.34f * flash(t);
        int glowSize = Math.round(logo * 2.7f);
        int logoCy = top + logo / 2;
        texture(ctx, GLOW, cx - glowSize / 2, logoCy - glowSize / 2, glowSize, glowSize, GLOW_TEX, GLOW_TEX, glow * aLogo);

        // --- logo, gently floating --------------------------------------------------------------
        float bob = (float) Math.sin(t * 1.3f) * 1.6f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0f, bob);
        texture(ctx, LOGO, cx - logo / 2, top, logo, logo, LOGO_TEX, LOGO_TEX, aLogo);
        ctx.getMatrices().popMatrix();

        // --- "Welcome to Tempest" ---------------------------------------------------------------
        int wy = top + logo + g1;
        texture(ctx, WELCOME, cx - wordW / 2, wy, wordW, wordH, WELCOME_W, WELCOME_H, aWord);

        // --- progress bar -----------------------------------------------------------------------
        float target = f >= 0f ? 1f : Math.max(0.04f, vanillaProgress);
        shownProgress += (target - shownProgress) * (1f - (float) Math.exp(-dt * 5f));
        int by = wy + wordH + g2;
        int bx = cx - barW / 2;
        int trackA = Math.round(0x22 * aBar);
        if (trackA > 0) Draw.roundRect(ctx, bx, by, barW, barH, barH / 2, (trackA << 24) | 0xFFFFFF);
        int fillW = Math.round(barW * clamp01(shownProgress));
        if (fillW > 0 && aBar > 0.02f) {
            int fillCol = (Math.round(255 * aBar) << 24) | (ACCENT & 0xFFFFFF);
            Draw.roundRect(ctx, bx, by, Math.max(barH, fillW), barH, barH / 2, fillCol);
            // moving highlight so the bar still looks alive if loading stalls for a moment
            int sheenW = Math.max(8, barW / 5);
            int sheenX = bx - sheenW + Math.round(((t * 0.55f) % 1.4f) / 1.4f * (barW + sheenW));
            int x0 = Math.max(sheenX, bx), x1 = Math.min(sheenX + sheenW, bx + fillW);
            if (x1 > x0) ctx.fill(x0, by, x1, by + barH, ((Math.round(0x70 * aBar)) << 24) | (ACCENT_SOFT & 0xFFFFFF));
        }

        // --- "LOADING" label, softly pulsing ------------------------------------------------------
        float pulse = 0.65f + 0.35f * (float) Math.sin(t * 2.4f);
        texture(ctx, LOADING, cx - labW / 2, by + barH + g3, labW, labH, LOADING_W, LOADING_H, aBar * pulse);
    }

    /**
     * Resource reload / texture pack screen: "Applying texture pack", an orbiting ring around the logo,
     * a progress bar with a spark on its tip, and a small "RELOADING RESOURCES" caption.
     *
     * All text is a pre-rendered texture for the same reason as the start-up screen: the fonts are
     * part of what is being reloaded, so the text renderer can be briefly unusable.
     */
    public static void renderReload(DrawContext ctx, long completeTime, float vanillaProgress) {
        if (resetOnNext) { startMs = -1; shownProgress = 0f; resetOnNext = false; }
        long now = Util.getMeasuringTimeMs();
        if (startMs < 0) { startMs = now; lastMs = now; }
        float dt = Math.min(0.1f, Math.max(0f, (now - lastMs) / 1000f));
        lastMs = now;
        float t = (now - startMs) / 1000f;

        float f = completeTime > -1L ? (now - completeTime) / 1000f : -1f;
        float fade = f >= 1f ? 1f - clamp01(f - 1f) : 1f;
        if (fade <= 0.01f) return;

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        if (f < 1f) ctx.fill(0, 0, sw, sh, BG);

        // --- layout --------------------------------------------------------------------------
        int logo = clamp((int) (sh * 0.24f), 52, 150);
        int ringR = Math.round(logo * 0.74f);
        int titleH = Math.max(11, Math.round(logo * 0.17f));
        int titleW = Math.round(titleH * (APPLYING_W / (float) APPLYING_H));
        int barW = Math.max(110, Math.round(logo * 1.9f));
        int barH = Math.max(2, Math.round(logo / 40f));
        int capH = Math.max(7, Math.round(titleH * 0.46f));
        int capW = Math.round(capH * (RELOADING_W / (float) RELOADING_H));
        int g1 = Math.round(ringR - logo / 2f + logo * 0.14f);   // logo -> title (clears the ring)
        int g2 = Math.round(logo * 0.17f);                         // title -> bar
        int g3 = Math.round(logo * 0.10f);                         // bar -> caption
        int total = logo + g1 + titleH + g2 + barH + g3 + capH;
        int top = (sh - total) / 2 - Math.round(sh * 0.02f);
        int cx = sw / 2;
        int logoCy = top + logo / 2;

        // --- staggered entrance ---------------------------------------------------------------
        float aLogo = ease(clamp01(t / 0.6f)) * fade;
        float aTitle = ease(clamp01((t - 0.2f) / 0.6f)) * fade;
        float aBar = ease(clamp01((t - 0.4f) / 0.6f)) * fade;

        // --- glow, ring, logo ---------------------------------------------------------------------
        float glow = 0.30f + 0.06f * (float) Math.sin(t * 1.6f) + 0.34f * flash(t);
        int glowSize = Math.round(logo * 2.7f);
        texture(ctx, GLOW, cx - glowSize / 2, logoCy - glowSize / 2, glowSize, glowSize, GLOW_TEX, GLOW_TEX, glow * aLogo);

        drawOrbit(ctx, cx, logoCy, ringR, t, aLogo, f >= 0f);

        float bob = (float) Math.sin(t * 1.3f) * 1.6f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0f, bob);
        texture(ctx, LOGO, cx - logo / 2, top, logo, logo, LOGO_TEX, LOGO_TEX, aLogo);
        ctx.getMatrices().popMatrix();

        // --- title: slides up a few pixels as it comes on ---------------------------------------
        int ty = top + logo + g1 + Math.round((1f - ease(clamp01((t - 0.2f) / 0.6f))) * 5f);
        texture(ctx, APPLYING, cx - titleW / 2, ty, titleW, titleH, APPLYING_W, APPLYING_H, aTitle);

        // --- progress bar -----------------------------------------------------------------------
        float target = f >= 0f ? 1f : Math.max(0.04f, vanillaProgress);
        shownProgress += (target - shownProgress) * (1f - (float) Math.exp(-dt * 5f));
        int by = top + logo + g1 + titleH + g2;
        drawBar(ctx, cx - barW / 2, by, barW, barH, shownProgress, t, aBar);

        // --- caption, softly pulsing --------------------------------------------------------------
        float pulse = 0.60f + 0.40f * (float) Math.sin(t * 2.4f);
        texture(ctx, RELOADING, cx - capW / 2, by + barH + g3, capW, capH, RELOADING_W, RELOADING_H, aBar * pulse);
    }

    /** Thin progress bar: faint track, accent fill, moving sheen, and a small spark on the tip. */
    private static void drawBar(DrawContext ctx, int bx, int by, int barW, int barH, float progress, float t, float alpha) {
        int trackA = Math.round(0x22 * alpha);
        if (trackA > 0) Draw.roundRect(ctx, bx, by, barW, barH, barH / 2, (trackA << 24) | 0xFFFFFF);
        int fillW = Math.round(barW * clamp01(progress));
        if (fillW <= 0 || alpha <= 0.02f) return;
        fillW = Math.max(barH, fillW);
        Draw.roundRect(ctx, bx, by, fillW, barH, barH / 2, (Math.round(255 * alpha) << 24) | (ACCENT & 0xFFFFFF));
        int sheenW = Math.max(8, barW / 5);
        int sheenX = bx - sheenW + Math.round(((t * 0.55f) % 1.4f) / 1.4f * (barW + sheenW));
        int x0 = Math.max(sheenX, bx), x1 = Math.min(sheenX + sheenW, bx + fillW);
        if (x1 > x0) ctx.fill(x0, by, x1, by + barH, ((Math.round(0x70 * alpha)) << 24) | (ACCENT_SOFT & 0xFFFFFF));
        // spark: a soft glow riding the leading edge of the fill
        int hx = bx + fillW, hy = by + barH / 2;
        drawGlow(ctx, hx, hy, Math.max(16, barH * 12), ACCENT_SOFT, 0.85f * alpha);
        ctx.fill(hx - 1, hy - Math.max(1, barH / 2), hx + 1, hy + Math.max(1, barH / 2) + (barH % 2), ((Math.round(0xE0 * alpha)) << 24) | 0xFFFFFF);
    }

    /** A ring of small dots with a bright head that circles the logo; settles into a full ring when done. */
    private static void drawOrbit(DrawContext ctx, int cx, int cy, int radius, float t, float alpha, boolean done) {
        if (alpha <= 0.02f) return;
        final int n = 24;
        float head = (t * 0.9f) % 1f;
        for (int i = 0; i < n; i++) {
            float p = i / (float) n;
            float behind = (head - p + 1f) % 1f;              // 0 at the head, ->1 along the tail
            float lit = done ? 0.55f : (float) Math.pow(1f - behind, 2.6);
            float a = (0.10f + 0.90f * lit) * alpha;
            double ang = p * Math.PI * 2.0 - Math.PI / 2.0;
            int x = cx + (int) Math.round(Math.cos(ang) * radius);
            int y = cy + (int) Math.round(Math.sin(ang) * radius);
            int r = lit > 0.6f ? 2 : 1;
            int col = Draw.lerpColor(0xFF6E7BB0, ACCENT_SOFT, lit);
            Draw.circle(ctx, x, y, r, (Math.round(255 * clamp01(a)) << 24) | (col & 0xFFFFFF));
        }
    }

    // ---------------------------------------------------------------- helpers
    /** Draws a whole texture scaled to w x h with the given opacity. Skips fully transparent draws. */
    private static void texture(DrawContext ctx, Identifier id, int x, int y, int w, int h, int texW, int texH, float alpha) {
        int a = Math.round(clamp01(alpha) * 255f);
        if (a <= 0) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH, (a << 24) | 0xFFFFFF);
    }

    /** Soft white glow, tinted with an RGB colour. Used behind menus. */
    public static void drawGlow(DrawContext ctx, int cx, int cy, int size, int rgb, float alpha) {
        int a = Math.round(clamp01(alpha) * 255f);
        if (a <= 0) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GLOW_SOFT, cx - size / 2, cy - size / 2, 0f, 0f, size, size,
                GLOW_TEX, GLOW_TEX, GLOW_TEX, GLOW_TEX, (a << 24) | (rgb & 0xFFFFFF));
    }

    /** Draws the Tempest logo (used by the in-game menu header too). */
    public static void drawLogo(DrawContext ctx, int x, int y, int size) {
        texture(ctx, LOGO, x, y, size, size, LOGO_TEX, LOGO_TEX, 1f);
    }

    /** Two quick, soft flickers every few seconds. Deliberately gentle. */
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
