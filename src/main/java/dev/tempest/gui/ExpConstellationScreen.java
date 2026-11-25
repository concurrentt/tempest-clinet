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
    public void removed() { MenuBlur.restore(); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float t = MenuKit.time();
        float e = dev.tempest.modules.ClientSettingsModule.animationsOn() ? MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.5f) : 1f;
        ExpOrbitScreen.paintBackground(ctx, width, height, width / 2, height / 2, t, e);
        ctx.fill(0, 0, width, height, MenuKit.fa(0x50060414, e));
        int[] h = dragMoved ? null : hit(mx, my);

        for (int ci = 0; ci < ExpKit.CATS.length; ci++) {
            Category cat = ExpKit.CATS[ci];
            float[] c = catPos(ci);
            int cx = Math.round(sx(c[0])), cy = Math.round(sy(c[1]));
            List<Module> mods = ExpKit.mods(cat);
            for (int j = 0; j < mods.size(); j++) {
                Module m = mods.get(j);
                float[] p = modPos(ci, j, mods.size(), m);
                int px = Math.round(sx(p[0])), py = Math.round(sy(p[1]));
                line(ctx, cx, cy, px, py, MenuKit.fa(m.isEnabled() ? 0x50B4AAFF : 0x22B4AAFF, e));
                boolean hv = h != null && h[0] == ci && h[1] == j;
                float on = Draw.hoverProgress("expc_" + m.getId(), m.isEnabled());
                float tw = 0.75f + 0.25f * (float) Math.sin(t * 2.4f + j * 1.3f + ci);
                if (on > 0.05f) TempestLoading.drawGlow(ctx, px, py, Math.round(18 * zoom), 0xC8BEFF, 0.5f * on * tw * e);
                int r = Math.max(1, Math.round((on > 0.5f ? 2.2f : 1.4f) * Math.min(2f, zoom) + (hv ? 1 : 0)));
                Draw.circle(ctx, px, py, r, MenuKit.fa(Draw.withAlpha(0xFFFFFF, on > 0.5f ? 0xFF : 0x8C), e));
                if (zoom >= 1.8f && !hv) {
                    String l = m.getName();
                    MenuKit.scaledCentered(ctx, tr, Draw.uiText(l), px, py + r + 3, 0.6f, MenuKit.fa(0x90FFFFFF, e), false);
                }
            }
            boolean hv = h != null && h[0] == ci && h[1] == -1;
            float hp = Draw.hoverProgress("expc_cat" + ci, hv);
            TempestLoading.drawGlow(ctx, cx, cy, Math.round((44 + 20 * hp) * Math.min(2f, zoom)), Draw.accent(), (0.5f + 0.3f * hp) * e);
            Draw.circle(ctx, cx, cy, Math.round((3.5f + hp) * Math.min(2f, zoom)), MenuKit.fa(0xFFFFFFFF, e));
            String name = ExpKit.title(cat);
            Draw.text(ctx, tr, name, cx - Draw.textW(tr, name) / 2, cy + Math.round(48 * Math.min(1.4f, zoom) * 0.8f), MenuKit.fa(Draw.lerpColor(0xB0FFFFFF, 0xFFFFFFFF, hp), e), false);
        }

        // tooltip
        if (h != null) {
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
        dragging = true;
        dragMoved = false;
        lastX = click.x();
        lastY = click.y();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
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
            if (click.button() == 1) ExpKit.openOptions(this, m);
            else ExpKit.toggle(m);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
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
    public void close() { if (client != null) client.setScreen(parent); }
}
