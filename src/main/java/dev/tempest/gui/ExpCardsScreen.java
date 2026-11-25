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
 * Right Shift menu: storm cards. The categories are a hand of cards in a gentle arc above the bottom of the screen.
 * Hovering lifts a card; clicking it flies it to the middle and flips it over to show its mods on the back (click a
 * row to toggle, right-click for options, scroll for more). Click outside the card to put it back.
 *
 * Everything is drawn upright on whole pixels (no rotation, no stretching once a card has landed) so the text stays
 * sharp. The only transform is the short flip animation itself.
 */
public class ExpCardsScreen extends Screen {
    private static final int GAP = 6, ROW_H = 18, HEAD_H = 34;
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

    // ---------------------------------------------------------------- hand layout (all whole pixels)
    private int cw() { return Math.max(38, Math.min(70, (width - 40) / n() - GAP)); }
    private int ch() { return Math.max(60, Math.min(Math.round(cw() * 1.42f), (height - 100) / 2 + 30)); }
    private int handW() { return n() * cw() + (n() - 1) * GAP; }
    private int cardX(int i) { return width / 2 - handW() / 2 + i * (cw() + GAP); }

    /** Top of card i in the hand: the middle cards sit a little higher, so the row reads as a fanned hand. */
    private int cardY(int i, float lift) {
        float k = i - (n() - 1) / 2f, edge = (n() - 1) / 2f;
        int arc = Math.round((edge * edge - k * k) * 1.1f);
        return height - 30 - ch() - arc - Math.round(lift * 10f);
    }

    // ---------------------------------------------------------------- the open card (back side)
    private int bw() { return Math.min(240, width - 40); }
    private int bh() { return Math.min(height - 60, 260); }
    private int bx() { return width / 2 - bw() / 2; }
    private int by() { return (height - bh()) / 2 + 8; }
    private int rows() { return Math.max(1, (bh() - HEAD_H - 14) / ROW_H); }

    /** 0 = in the hand, 1 = flipped open in the middle. */
    private float flip() {
        if (flipped < 0) return 0f;
        float p = MenuKit.clamp01((System.nanoTime() - flipAt) / 1e9f / 0.45f);
        if (!dev.tempest.modules.ClientSettingsModule.animationsOn()) p = 1f;
        float v = closing ? 1f - p : p;
        if (closing && p >= 1f) { flipped = -1; closing = false; }
        return v;
    }

    private int hoveredCard(double mx, double my) {
        if (flipped >= 0) return -1;
        for (int i = 0; i < n(); i++) {
            int y = cardY(i, 0f);
            if (Draw.hit(mx, my, cardX(i), y - 10, cw(), ch() + 10)) return i;
        }
        return -1;
    }

    /** Short names so every label fits on a card. */
    private static String shortTitle(Category c) {
        return c == Category.QOL ? "QoL" : ExpKit.title(c);
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
        int rise = Math.round((1f - e) * 30);                  // the hand slides up when the menu opens
        for (int i = 0; i < n(); i++) {
            float lift = Draw.hoverProgress("expcard_" + i, i == hov);
            if (i == flipped) continue;
            frontCard(ctx, tr, i, cardX(i), cardY(i, lift) + rise, cw(), ch(), lift, e);
        }

        if (flipped >= 0) {
            float p = MenuKit.ease(fl);
            ctx.fill(0, 0, width, height, MenuKit.fa(0x90060410, p * e));
            // fly from the card's place in the hand to the middle, growing, and turn it over halfway
            float sx = cardX(flipped) + cw() / 2f, sy = cardY(flipped, 1f) + ch() / 2f;
            float ex = bx() + bw() / 2f, ey = by() + bh() / 2f;
            int cx = Math.round(sx + (ex - sx) * p), cy = Math.round(sy + (ey - sy) * p);
            int w = Math.round(cw() + (bw() - cw()) * p), h = Math.round(ch() + (bh() - ch()) * p);
            float turn = Math.abs((float) Math.cos(fl * Math.PI));
            boolean back = fl > 0.5f;
            if (fl >= 1f) {
                backCard(ctx, tr, flipped, bx(), by(), bw(), bh(), mx, my, true, e);
            } else {
                var m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(cx, cy);
                m.scale(Math.max(0.02f, turn), 1f);
                if (back) backCard(ctx, tr, flipped, -w / 2, -h / 2, w, h, mx, my, false, e);
                else frontCard(ctx, tr, flipped, -w / 2, -h / 2, w, h, 1f, e);
                m.popMatrix();
            }
        }

        String hint = flipped >= 0 ? "Click a mod to toggle   Right-click for options   Click outside to put it back"
                : "Hover a card to lift it, click to flip it over";
        Draw.text(ctx, tr, hint, width / 2 - Draw.textW(tr, hint) / 2, height - 16, MenuKit.fa(0x90FFFFFF, e), false);
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    /** Face of card i: icon in a lit circle, the name and how many of its mods are on. */
    private void frontCard(DrawContext ctx, TextRenderer tr, int i, int x, int y, int w, int h, float lift, float e) {
        Category c = ExpKit.CATS[i];
        int[] k = ExpKit.counts(c);
        int acc = Draw.accent() | 0xFF000000;
        if (lift > 0.05f) TempestLoading.drawGlow(ctx, x + w / 2, y + h / 2, Math.round(Math.max(w, h) * 1.7f), Draw.accent(), 0.32f * lift * e);
        Draw.shadow(ctx, x, y, w, h, 9, 5 + Math.round(3 * lift));
        int top = Draw.lerpColor(0xF62A2348, Draw.lerpColor(0xF62A2348, acc, 0.55f), lift);
        Draw.roundRectV(ctx, x, y, w, h, 9, MenuKit.fa(top, e), MenuKit.fa(0xF6141024, e));
        Draw.ring(ctx, x, y, w, h, 9, MenuKit.fa(Draw.lerpColor(0x40FFFFFF, Draw.withAlpha(Draw.accent(), 0xE0), lift), e));
        // a thin bright line along the top edge, like light catching the card
        ctx.fill(x + 9, y + 1, x + w - 9, y + 2, MenuKit.fa(Draw.withAlpha(0xFFFFFF, 0x18 + Math.round(0x30 * lift)), e));

        int os = Math.max(18, Math.min(34, Math.round(w * 0.48f)));
        int ox = x + w / 2 - os / 2, oy = y + Math.max(8, Math.round(h * 0.16f));
        int orbFill = Draw.lerpColor(0xFF221C3A, acc, 0.35f + 0.65f * lift);
        Draw.panel(ctx, ox, oy, os, os, os / 2, MenuKit.fa(orbFill, e), MenuKit.fa(0x50FFFFFF, e));
        int is = Math.round(os * 0.56f);
        Icons.draw(ctx, Icons.forCategory(c), ox + os / 2 - is / 2, oy + os / 2 - is / 2, is, MenuKit.fa(0xFFFFFFFF, e));

        String name = Draw.ellipsize(tr, shortTitle(c), w - 8);
        int ny = oy + os + Math.max(6, Math.round(h * 0.08f));
        Draw.text(ctx, tr, name, x + w / 2 - Draw.textW(tr, name) / 2, ny, MenuKit.fa(0xFFFFFFFF, e), false);

        // "3/12" in a small pill near the bottom; the dot is lit when anything in the category is on
        String cnt = k[1] + "/" + k[0];
        int pw = Draw.textW(tr, cnt) + 16, ph = 12;
        int px = x + w / 2 - pw / 2, py = Math.min(y + h - ph - 6, ny + 14);
        Draw.roundRect(ctx, px, py, pw, ph, ph / 2, MenuKit.fa(0x40000000, e));
        Draw.roundRect(ctx, px + 4, py + 4, 4, 4, 2, MenuKit.fa(k[1] > 0 ? 0xFF6EE7A0 : 0x60FFFFFF, e));
        Draw.text(ctx, tr, cnt, px + 11, py + 2, MenuKit.fa(0xD0E6E2FA, e), false);
    }

    /** Back of card i: its mods as rows with switches. Hit-testing only happens once it has landed ({@code live}). */
    private void backCard(DrawContext ctx, TextRenderer tr, int i, int x, int y, int w, int h, int mx, int my, boolean live, float e) {
        Category c = ExpKit.CATS[i];
        TempestLoading.drawGlow(ctx, x + w / 2, y + h / 2, Math.round(Math.max(w, h) * 1.5f), Draw.accent(), 0.30f * e);
        Draw.shadow(ctx, x, y, w, h, 12, 10);
        Draw.roundRectV(ctx, x, y, w, h, 12, MenuKit.fa(0xF8221C3E, e), MenuKit.fa(0xF8120E22, e));
        Draw.ring(ctx, x, y, w, h, 12, MenuKit.fa(Draw.withAlpha(Draw.accent(), 0xB0), e));

        int is = 14;
        Icons.draw(ctx, Icons.forCategory(c), x + 12, y + 10, is, MenuKit.fa(0xFFFFFFFF, e));
        Draw.text(ctx, tr, Draw.bold(ExpKit.title(c)), x + 12 + is + 6, y + 13, MenuKit.fa(0xFFFFFFFF, e), false);
        String cl = ExpKit.countLine(c);
        Draw.text(ctx, tr, cl, x + w - 12 - Draw.textW(tr, cl), y + 13, MenuKit.fa(0xA0D6CFF2, e), false);
        ctx.fill(x + 10, y + HEAD_H - 4, x + w - 10, y + HEAD_H - 3, MenuKit.fa(0x24FFFFFF, e));

        List<Module> mods = ExpKit.mods(c);
        int rows = Math.max(1, (h - HEAD_H - 14) / ROW_H);
        if (live) first = Math.max(0, Math.min(first, Math.max(0, mods.size() - rows)));
        for (int r = 0; r < rows && first + r < mods.size(); r++) {
            int ry = y + HEAD_H + r * ROW_H;
            boolean hv = live && Draw.hit(mx, my, x + 10, ry, w - 20, ROW_H);
            ExpKit.modRow(ctx, tr, mods.get(first + r), x + 12, ry, w - 24, ROW_H, hv, live ? 1f : 0.5f);
        }
        if (mods.size() > rows) {
            // little scroll bar on the right edge
            int trackY = y + HEAD_H, trackH = rows * ROW_H;
            int barH = Math.max(10, trackH * rows / mods.size());
            int barY = trackY + Math.round((trackH - barH) * (first / (float) Math.max(1, mods.size() - rows)));
            Draw.roundRect(ctx, x + w - 6, barY, 2, barH, 1, MenuKit.fa(0x60FFFFFF, e));
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        double mx = click.x(), my = click.y();
        if (flipped >= 0) {
            if (closing || flip() < 1f) return true;
            int x = bx(), y = by();
            if (!Draw.hit(mx, my, x, y, bw(), bh())) { closing = true; flipAt = System.nanoTime(); return true; }
            List<Module> mods = ExpKit.mods(ExpKit.CATS[flipped]);
            int k = (int) Math.floor((my - (y + HEAD_H)) / ROW_H);
            if (k >= 0 && k < rows() && first + k < mods.size() && mx >= x + 10 && mx < x + bw() - 10) {
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
