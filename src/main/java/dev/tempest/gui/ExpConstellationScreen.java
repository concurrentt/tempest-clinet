package dev.tempest.gui;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * EXPERIMENTAL Right Shift menu: a constellation map. Each category is a bright star with its mods as smaller stars
 * around it, joined by faint lines. Stars that are on shine and glow. Hover a star for its name, click a mod star to
 * toggle it (right-click for options), click a category star to open its list. Scroll to zoom, drag to look around.
 */
public class ExpConstellationScreen extends Screen {
    /** Category star positions, as fractions of the screen. */
    private static final float[][] SPOTS = {{0.20f, 0.30f}, {0.40f, 0.42f}, {0.68f, 0.28f}, {0.80f, 0.62f}, {0.56f, 0.72f},
            {0.30f, 0.74f}, {0.12f, 0.58f}, {0.88f, 0.22f}};
    private final Screen parent;
    private float zoom = 1f, panX, panY;
    private boolean dragging, dragMoved;
    private double lastX, lastY;
    private final long openedAt = System.nanoTime();
    private ExpSettings opts;                 // a mod's settings: a panel that grows out of its star
    private int optCat, optMod;               // which star it belongs to
    private boolean eaten;                    // the press went to the panel, so the release does nothing
    private static final float OPTS_SECS = 0.25f;

    public ExpConstellationScreen(Screen parent) {
        super(Text.literal("Tempest"));
        this.parent = parent;
    }

    private float sx(float wx) { return width / 2f + (wx - width / 2f) * zoom + panX; }
    private float sy(float wy) { return height / 2f + (wy - height / 2f) * zoom + panY; }

    private float[] catPos(int i) { return new float[]{SPOTS[i % SPOTS.length][0] * width, SPOTS[i % SPOTS.length][1] * height}; }

    private float[] modPos(int ci, int j, int n, Module m) {
        float[] c = catPos(ci);
        float a = (float) (j * Math.PI * 2 / Math.max(1, n) + ci * 0.7);
        float r = 26 + 12 * (((m.getId().hashCode() >>> 3) & 7) / 7f) + (n > 9 ? (j % 2) * 12 : 0);
        return new float[]{c[0] + (float) Math.cos(a) * r, c[1] + (float) Math.sin(a) * r * 0.8f};
    }

    /** What's under the mouse: {category index, mod index or -1}, or null. */
    private int[] hit(double mx, double my) {
        int[] best = null;
        double bd = 9 * 9;
        for (int ci = 0; ci < ExpKit.CATS.length; ci++) {
            float[] c = catPos(ci);
            double d = sq(mx - sx(c[0]), my - sy(c[1]));
            if (d < bd * 1.8) { bd = d; best = new int[]{ci, -1}; }
            List<Module> mods = ExpKit.mods(ExpKit.CATS[ci]);
            for (int j = 0; j < mods.size(); j++) {
                float[] p = modPos(ci, j, mods.size(), mods.get(j));
                double dm = sq(mx - sx(p[0]), my - sy(p[1]));
                if (dm < bd) { bd = dm; best = new int[]{ci, j}; }
            }
        }
        return best;
    }

    private static double sq(double a, double b) { return a * a + b * b; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() {
        MenuBlur.restore();
        if (opts != null) opts.close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float t = MenuKit.time();
        float e = dev.tempest.modules.ClientSettingsModule.animationsOn() ? MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.5f) : 1f;
        ExpOrbitScreen.paintBackground(ctx, width, height, width / 2, height / 2, t, e);
        ctx.fill(0, 0, width, height, MenuKit.fa(0x50060414, e));
        int[] h = dragMoved ? null : hit(mx, my);

        shootingStars(ctx, t, e);

        for (int ci = 0; ci < ExpKit.CATS.length; ci++) {
            Category cat = ExpKit.CATS[ci];
            float[] c = catPos(ci);
            int cx = Math.round(sx(c[0])), cy = Math.round(sy(c[1]));
            int tint = TINTS[ci % TINTS.length];
            float z = Math.min(2f, zoom);
            List<Module> mods = ExpKit.mods(cat);
            for (int j = 0; j < mods.size(); j++) {
                Module m = mods.get(j);
                float[] p = modPos(ci, j, mods.size(), m);
                int px = Math.round(sx(p[0])), py = Math.round(sy(p[1]));
                boolean lit = m.isEnabled();
                line(ctx, cx, cy, px, py, MenuKit.fa(lit ? Draw.withAlpha(tint, 0x48) : 0x1EB4AAFF, e));
                float seed = ((m.getId().hashCode() >>> 4) & 1023) / 1023f;
                if (lit) {
                    // a spark of light running out along the line to every star that's on
                    float f = (t * (0.28f + seed * 0.2f) + seed) % 1f;
                    int lx = Math.round(cx + (px - cx) * f), ly = Math.round(cy + (py - cy) * f);
                    int la = Math.round(0xC0 * (float) Math.sin(Math.PI * f) * e);
                    ctx.fill(lx, ly, lx + 1, ly + 1, la << 24 | (tint & 0xFFFFFF));
                    if (la > 0x60) { ctx.fill(lx - 1, ly, lx + 2, ly + 1, (la / 3) << 24 | (tint & 0xFFFFFF)); ctx.fill(lx, ly - 1, lx + 1, ly + 2, (la / 3) << 24 | (tint & 0xFFFFFF)); }
                }
                boolean hv = h != null && h[0] == ci && h[1] == j;
                float on = Draw.hoverProgress("expc_" + m.getId(), lit);
                // twinkle: two waves at the star's own speeds, so no two stars pulse together
                float tw = 0.62f + 0.24f * (float) Math.sin(t * (1.2f + seed * 2.2f) + seed * 40f)
                        + 0.14f * (float) Math.sin(t * (3.4f + seed * 3.1f) + seed * 17f);
                // every few seconds each star flares for a moment
                float period = 4.5f + seed * 5f, ph = (t + seed * period) % period;
                float flare = ph < 0.7f ? (float) Math.sin(Math.PI * ph / 0.7f) : 0f;
                int col = Draw.lerpColor(0xFFFFFFFF, tint | 0xFF000000, 0.35f);
                if (on > 0.05f) TempestLoading.drawGlow(ctx, px, py, Math.round((18 + 14 * flare) * zoom), tint, (0.42f * tw + 0.35f * flare) * on * e);
                int r = Math.max(1, Math.round((on > 0.5f ? 1.8f : 1.1f) * z + (hv ? 1 : 0)));
                float bright = on > 0.5f ? 0.75f + 0.25f * tw : 0.35f + 0.25f * tw;
                int spike = Math.round((on > 0.5f ? 3.5f : 1.5f) * z * (0.7f + 0.3f * tw) + flare * (on > 0.5f ? 7f : 4f));
                shimmer(ctx, px, py, r, spike, flare, col, Math.min(1f, bright + flare * 0.5f) * e);
                if (zoom >= 1.8f && !hv) {
                    String l = m.getName();
                    MenuKit.scaledCentered(ctx, tr, Draw.uiText(l), px, py + r + 4, 0.6f, MenuKit.fa(0x90FFFFFF, e), false);
                }
            }
            boolean hv = h != null && h[0] == ci && h[1] == -1;
            float hp = Draw.hoverProgress("expc_cat" + ci, hv);
            float breathe = 0.85f + 0.15f * (float) Math.sin(t * 1.1f + ci * 0.8f);
            TempestLoading.drawGlow(ctx, cx, cy, Math.round((44 + 20 * hp) * z), Draw.accent(), (0.45f + 0.3f * hp) * breathe * e);
            TempestLoading.drawGlow(ctx, cx, cy, Math.round(18 * z), tint, 0.5f * e);
            // a soft ring that keeps rippling outward from each category star
            for (int k = 0; k < 2; k++) {
                float q = ((t * 0.35f + ci * 0.37f + k * 0.5f) % 1f);
                int rr = Math.round((5 + q * 22) * z) / 2 * 2;            // even sizes only: fewer cached ring textures
                Draw.ring(ctx, cx - rr, cy - rr, rr * 2, rr * 2, rr, MenuKit.fa(Draw.withAlpha(tint, Math.round(0x70 * (1f - q) * (1f - q))), e));
            }
            shimmer(ctx, cx, cy, Math.round((2.5f + hp) * z), Math.round((9 + 4 * hp) * z * breathe), 0.4f + 0.6f * hp, 0xFFFFFFFF, e);
            String name = ExpKit.title(cat);
            Draw.text(ctx, tr, name, cx - Draw.textW(tr, name) / 2, cy + Math.round(48 * Math.min(1.4f, zoom) * 0.8f), MenuKit.fa(Draw.lerpColor(0xB0FFFFFF, 0xFFFFFFFF, hp), e), false);
        }

        drawOptions(ctx, tr, mx, my, e);

        // tooltip
        if (h != null && opts == null) {
            Category cat = ExpKit.CATS[h[0]];
            String a, b;
            if (h[1] < 0) { a = ExpKit.title(cat); b = ExpKit.countLine(cat) + ". Click to open"; }
            else {
                Module m = ExpKit.mods(cat).get(h[1]);
                a = m.getName();
                b = (m.isEnabled() ? "On" : "Off") + ". Click to turn " + (m.isEnabled() ? "off" : "on") + ", right-click for options";
            }
            int w = Math.max(Draw.textW(tr, a), Draw.textW(tr, b)) + 14, tx = Math.min(width - w - 4, mx + 10), ty = Math.max(4, my - 30);
            ExpKit.glass(ctx, tx, ty, w, 26, 7, 1f);
            Draw.text(ctx, tr, a, tx + 7, ty + 4, 0xFFFFFFFF, false);
            Draw.text(ctx, tr, b, tx + 7, ty + 15, 0xB0D6CFF2, false);
        }
        String hint = "Each star is a mod. Bright stars are on.   Scroll to zoom, drag to look around";
        Draw.text(ctx, tr, hint, width / 2 - Draw.textW(tr, hint) / 2, height - 14, MenuKit.fa(0x90FFFFFF, e), false);
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    /** Where the open star is on screen, and the panel rect next to it: {starX, starY, x, y, w, h}. */
    private int[] optsLayout() {
        List<Module> mods = ExpKit.mods(ExpKit.CATS[optCat]);
        float[] p = modPos(optCat, Math.min(optMod, mods.size() - 1), mods.size(), opts.mod);
        int px = Math.round(sx(p[0])), py = Math.round(sy(p[1]));
        int w = Math.min(210, width - 20), h = Math.min(250, height - 40);
        int x = px + 18;
        if (x + w > width - 8) x = px - 18 - w;
        x = Math.max(8, Math.min(width - w - 8, x));
        int y = Math.max(10, Math.min(height - h - 24, py - h / 2));
        return new int[]{px, py, x, y, w, h};
    }

    private void drawOptions(DrawContext ctx, TextRenderer tr, int mx, int my, float e) {
        if (opts == null) return;
        if (opts.gone(OPTS_SECS)) { opts = null; return; }
        float p = MenuKit.ease(opts.progress(OPTS_SECS));
        int[] l = optsLayout();
        int ex = l[2] > l[0] ? l[2] : l[2] + l[4];               // the panel edge facing the star
        line(ctx, l[0], l[1], l[0] + Math.round((ex - l[0]) * p), l[1] + Math.round((l[3] + 20 - l[1]) * p), MenuKit.fa(0x90C8BEFF, p));
        TempestLoading.drawGlow(ctx, l[0], l[1], 30, Draw.accent(), 0.6f * p);
        boolean live = p >= 1f && !opts.closing();
        if (live) { opts.render(ctx, tr, l[2], l[3], l[4], l[5], mx, my, e, true); return; }
        // grows out of the star
        float k = 0.35f + 0.65f * p;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(l[0] + (l[2] - l[0]) * k, l[1] + (l[3] - l[1]) * k);
        m.scale(k, k);
        opts.render(ctx, tr, 0, 0, l[4], l[5], mx, my, p, false);
        m.popMatrix();
    }

    /** A soft tint per category, so each constellation has its own colour. */
    private static final int[] TINTS = {0xB9A8FF, 0x8EC5FF, 0xFFB3E6, 0x9EF0D6, 0xFFE3A8, 0xC8B8FF, 0x8FE3FF, 0xFFC2A8};

    /**
     * A star that shimmers: a bright core with four light spikes that fade out from it (longer while it flares),
     * plus faint diagonal spikes during a flare.
     */
    private static void shimmer(DrawContext ctx, int x, int y, int r, int len, float flare, int col, float a) {
        if (a <= 0.01f) return;
        int rgb = col & 0xFFFFFF;
        for (int i = 1; i <= len; i++) {
            float f = 1f - i / (float) (len + 1);
            int al = Math.round(0xE0 * f * f * a);
            if (al <= 2) continue;
            int c = al << 24 | rgb;
            ctx.fill(x + r + i - 1, y, x + r + i, y + 1, c);
            ctx.fill(x - r - i + 1, y, x - r - i + 2, y + 1, c);
            ctx.fill(x, y + r + i - 1, x + 1, y + r + i, c);
            ctx.fill(x, y - r - i + 1, x + 1, y - r - i + 2, c);
        }
        if (flare > 0.2f) {
            int dl = Math.round(len * 0.45f);
            for (int i = 1; i <= dl; i++) {
                int al = Math.round(0x90 * (1f - i / (float) (dl + 1)) * flare * a);
                int c = al << 24 | rgb;
                ctx.fill(x + i, y + i, x + i + 1, y + i + 1, c);
                ctx.fill(x - i, y + i, x - i + 1, y + i + 1, c);
                ctx.fill(x + i, y - i, x + i + 1, y - i + 1, c);
                ctx.fill(x - i, y - i, x - i + 1, y - i + 1, c);
            }
        }
        Draw.circle(ctx, x, y, r, Math.round(255 * Math.min(1f, a)) << 24 | rgb);
    }

    /** Now and then a shooting star streaks across the sky. */
    private void shootingStars(DrawContext ctx, float t, float e) {
        float every = 6.5f, dur = 0.9f;
        int n = (int) (t / every);
        float ph = t - n * every;
        if (ph > dur) return;
        float p = ph / dur;
        float x0 = (0.2f + 0.7f * h(n * 3 + 1)) * width, y0 = (0.05f + 0.35f * h(n * 3 + 2)) * height;
        float dx = -(0.25f + 0.15f * h(n * 3 + 3)) * width, dy = 0.22f * height;
        float hx = x0 + dx * p, hy = y0 + dy * p;
        float fade = (float) Math.sin(Math.PI * p);
        int tail = 34;
        for (int i = 0; i < tail; i++) {
            float k = i / (float) tail;
            int x = Math.round(hx - dx * 0.12f * k), y = Math.round(hy - dy * 0.12f * k);
            int al = Math.round(0xE0 * (1f - k) * (1f - k) * fade * e);
            ctx.fill(x, y, x + 1, y + 1, al << 24 | 0xFFFFFF);
        }
        TempestLoading.drawGlow(ctx, Math.round(hx), Math.round(hy), 14, 0xDCD4FF, 0.6f * fade * e);
    }

    private static float h(int i) {
        int x = i * 0x27D4EB2D;
        x ^= x >>> 15;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Dotted line (DrawContext only fills rectangles). */
    private static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        int steps = (int) (len / 3);
        for (int i = 1; i < steps; i++) {
            int x = (int) Math.round(x0 + (x1 - x0) * i / (double) steps), y = (int) Math.round(y0 + (y1 - y0) * i / (double) steps);
            ctx.fill(x, y, x + 1, y + 1, col);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        eaten = false;
        if (opts != null && !opts.closing()) {
            if (opts.mouseClicked(click.x(), click.y(), click.button())) {
                if (opts.closeRequested) opts.startClosing();
                eaten = true;
                return true;
            }
            opts.startClosing();                       // clicking the sky puts the panel away
            eaten = true;
            return true;
        }
        dragging = true;
        dragMoved = false;
        lastX = click.x();
        lastY = click.y();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (opts != null && opts.mouseDragged(click.x(), click.y())) return true;
        if (!dragging) return false;
        if (Math.abs(click.x() - lastX) + Math.abs(click.y() - lastY) > 2 || dragMoved) {
            dragMoved = true;
            panX += (float) (click.x() - lastX);
            panY += (float) (click.y() - lastY);
            lastX = click.x();
            lastY = click.y();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (opts != null) opts.mouseReleased();
        if (eaten) { eaten = false; dragging = false; return true; }
        boolean wasDrag = dragMoved;
        dragging = false;
        dragMoved = false;
        if (wasDrag) return true;
        int[] h = hit(click.x(), click.y());
        if (h == null || client == null) return true;
        Category cat = ExpKit.CATS[h[0]];
        if (h[1] < 0) client.setScreen(new ExpModsScreen(cat, this, ""));
        else {
            Module m = ExpKit.mods(cat).get(h[1]);
            if (click.button() == 1) { opts = new ExpSettings(m, false); optCat = h[0]; optMod = h[1]; }
            else ExpKit.toggle(m);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (opts != null && opts.mouseScrolled(mouseX, mouseY, vertical)) return true;
        float old = zoom;
        zoom = Math.max(0.7f, Math.min(3f, zoom * (vertical > 0 ? 1.15f : 1 / 1.15f)));
        // keep the point under the mouse still
        float k = zoom / old;
        panX = (float) (mouseX - width / 2f - (mouseX - width / 2f - panX) * k);
        panY = (float) (mouseY - height / 2f - (mouseY - height / 2f - panY) * k);
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (opts != null && opts.keyPressed(input.key())) return true;
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (opts != null && !opts.closing()) { opts.startClosing(); return; }
        if (client != null) client.setScreen(parent);
    }
}
