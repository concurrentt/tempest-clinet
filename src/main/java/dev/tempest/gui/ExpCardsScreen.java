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
 * EXPERIMENTAL Right Shift menu: storm cards. The categories are a fanned hand of cards along the bottom. Hovering
 * lifts a card; clicking it flies it to the middle and flips it over to show its mods on the back (click a row to
 * toggle, right-click for options, scroll for more). Click outside the card to put it back.
 */
public class ExpCardsScreen extends Screen {
    private static final int CW = 66, CH = 96, ROW_H = 18;
    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private int flipped = -1;                 // card that is (being) flipped
    private boolean closing;
    private long flipAt;
    private int first;                        // first visible row on the back

    public ExpCardsScreen(Screen parent) {
        super(Text.literal("Tempest"));
        this.parent = parent;
    }

    private int n() { return ExpKit.CATS.length; }
    private float spread() { return Math.min(52f, (width - 80f) / n()); }

    /** Fan position of card i: {centre x, centre y, angle in radians}. */
    private float[] fan(int i, float lift) {
        float k = i - (n() - 1) / 2f;
        float x = width / 2f + k * spread();
        float y = height - CH * 0.42f + Math.abs(k) * Math.abs(k) * 2.2f - lift * 22f;
        return new float[]{x, y, (float) Math.toRadians(k * 7.5f)};
    }

    private int bw() { return Math.min(220, width - 40); }
    private int bh() { return Math.min(height - 50, 250); }

    /** 0 = in the hand, 1 = flipped open in the middle. */
    private float flip() {
        if (flipped < 0) return 0f;
        float p = MenuKit.clamp01((System.nanoTime() - flipAt) / 1e9f / 0.45f);
        if (!dev.tempest.modules.ClientSettingsModule.animationsOn()) p = 1f;
        float v = closing ? 1f - p : p;
        if (closing && p >= 1f) { flipped = -1; closing = false; }
        return v;
    }

    /** Is (mx, my) inside card i drawn at (cx, cy, angle, w, h)? */
    private static boolean inside(double mx, double my, float cx, float cy, float ang, float w, float h) {
        double dx = mx - cx, dy = my - cy;
        double lx = dx * Math.cos(-ang) - dy * Math.sin(-ang), ly = dx * Math.sin(-ang) + dy * Math.cos(-ang);
        return Math.abs(lx) <= w / 2 && Math.abs(ly) <= h / 2;
    }

    private int hoveredCard(double mx, double my) {
        if (flipped >= 0) return -1;
        for (int i = n() - 1; i >= 0; i--) {
            float[] f = fan(i, 0f);
            if (inside(mx, my, f[0], f[1], f[2], CW, CH)) return i;
        }
        return -1;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() { MenuBlur.restore(); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float t = MenuKit.time();
        float e = dev.tempest.modules.ClientSettingsModule.animationsOn() ? MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.45f) : 1f;
        ExpOrbitScreen.paintBackground(ctx, width, height, width / 2, height / 3, t, e);
        int ls = Math.max(28, Math.min(48, height / 7));
        TempestLoading.drawLogo(ctx, width / 2 - ls / 2, 10 + Math.round((float) Math.sin(t * 1.1f) * 2), ls);

        float fl = flip();
        int hov = hoveredCard(mx, my);
        float[] lift = new float[n()];
        for (int i = 0; i < n(); i++) lift[i] = Draw.hoverProgress("expcard_" + i, i == hov);
        for (int i = 0; i < n(); i++) {
            if (i == hov || i == flipped) continue;
            drawCard(ctx, tr, i, fan(i, lift[i]), 1f, e, mx, my, lift[i]);
        }
        if (hov >= 0) drawCard(ctx, tr, hov, fan(hov, lift[hov]), 1f, e, mx, my, lift[hov]);
        if (flipped >= 0) {
            float[] a = fan(flipped, 1f);
            float p = MenuKit.ease(fl);
            float cx = a[0] + (width / 2f - a[0]) * p, cy = a[1] + (height / 2f + 10 - a[1]) * p;
            float ang = a[2] * (1f - MenuKit.clamp01(fl * 2f));
            drawCard(ctx, tr, flipped, new float[]{cx, cy, ang}, fl, e, mx, my, 1f);
        }
        String hint = flipped >= 0 ? "Click a mod to toggle   Right-click for options   Click outside to put the card back"
                : "Hover to lift a card, click to flip it over";
        Draw.text(ctx, tr, hint, width / 2 - Draw.textW(tr, hint) / 2, height - 12, MenuKit.fa(0x80FFFFFF, e), false);
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    /** Draws card i; {@code fl} 0..1 grows it into the big card and flips it (back side after halfway). */
    private void drawCard(DrawContext ctx, TextRenderer tr, int i, float[] pos, float fl, float e, int mx, int my, float lift) {
        Category c = ExpKit.CATS[i];
        boolean flipping = i == flipped;
        float grow = flipping ? MenuKit.ease(fl) : 0f;
        float w = CW + (bw() - CW) * grow, h = CH + (bh() - CH) * grow;
        float sxk = flipping ? Math.abs((float) Math.cos(fl * Math.PI)) : 1f;
        boolean back = flipping && fl > 0.5f;
        boolean lifted = !flipping && lift > 0.5f;

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(pos[0], pos[1]);
        m.rotate(pos[2]);
        m.scale(Math.max(0.02f, sxk), 1f);
        int x = Math.round(-w / 2), y = Math.round(-h / 2), iw = Math.round(w), ih = Math.round(h);
        if (lifted || flipping) TempestLoading.drawGlow(ctx, 0, 0, Math.round(Math.max(w, h) * 1.6f), Draw.accent(), 0.35f * e);
        Draw.shadow(ctx, x, y, iw, ih, 10, 6);
        int fill = back ? 0xF4181430 : (lifted ? (Draw.accent() | 0xFA000000) : 0xF5201A3A);
        Draw.panel(ctx, x, y, iw, ih, 10, MenuKit.fa(fill, e), MenuKit.fa(lifted || back ? 0xA0FFFFFF : 0x40FFFFFF, e));
        if (!back) {
            int is = 26;
            Icons.draw(ctx, Icons.forCategory(c), -is / 2, y + 22, is, MenuKit.fa(0xFFFFFFFF, e));
            String name = ExpKit.title(c);
            if (Draw.textW(tr, name) > iw - 8) name = Draw.ellipsize(tr, name, iw - 8);
            Draw.text(ctx, tr, name, -Draw.textW(tr, name) / 2, y + 58, MenuKit.fa(0xFFFFFFFF, e), false);
            String cnt = ExpKit.counts(c)[0] + " mods";
            MenuKit.scaledCentered(ctx, tr, Draw.uiText(cnt), 0, y + 71, 0.75f, MenuKit.fa(0xC0E6E2FA, e), false);
        } else {
            Draw.text(ctx, tr, ExpKit.title(c), x + 12, y + 10, 0xFFFFFFFF, false);
            String cl = ExpKit.countLine(c);
            Draw.text(ctx, tr, cl, x + iw - 12 - Draw.textW(tr, cl), y + 10, 0xA0D6CFF2, false);
            List<Module> mods = ExpKit.mods(c);
            int rows = (ih - 34) / ROW_H;
            first = Math.max(0, Math.min(first, Math.max(0, mods.size() - rows)));
            boolean open = fl >= 1f;
            for (int k = 0; k < rows && first + k < mods.size(); k++) {
                int ry = y + 26 + k * ROW_H;
                boolean hv = open && Draw.hit(mx - pos[0], my - pos[1], x + 10, ry, iw - 20, ROW_H);
                ExpKit.modRow(ctx, tr, mods.get(first + k), x + 10, ry, iw - 20, ROW_H, hv, 1f);
            }
            if (mods.size() > rows) {
                String more = (first + rows < mods.size()) ? "Scroll for more" : "";
                MenuKit.scaledCentered(ctx, tr, Draw.uiText(more), 0, y + ih - 9, 0.7f, 0x80FFFFFF, false);
            }
        }
        m.popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        double mx = click.x(), my = click.y();
        if (flipped >= 0) {
            if (closing || flip() < 1f) return true;
            float cx = width / 2f, cy = height / 2f + 10;
            int x = Math.round(cx - bw() / 2f), y = Math.round(cy - bh() / 2f);
            if (!Draw.hit(mx, my, x, y, bw(), bh())) { closing = true; flipAt = System.nanoTime(); return true; }
            List<Module> mods = ExpKit.mods(ExpKit.CATS[flipped]);
            int k = (int) Math.floor((my - (y + 26)) / ROW_H);
            int rows = (bh() - 34) / ROW_H;
            if (k >= 0 && k < rows && first + k < mods.size() && mx >= x + 10 && mx < x + bw() - 10) {
                Module m = mods.get(first + k);
                if (click.button() == 1) ExpKit.openOptions(this, m);
                else ExpKit.toggle(m);
            }
            return true;
        }
        int i = hoveredCard(mx, my);
        if (i >= 0) { flipped = i; closing = false; first = 0; flipAt = System.nanoTime(); return true; }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (flipped >= 0) first += vertical > 0 ? -1 : 1;
        if (first < 0) first = 0;
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (flipped >= 0 && !closing) { closing = true; flipAt = System.nanoTime(); return; }
        if (client != null) client.setScreen(parent);
    }
}
