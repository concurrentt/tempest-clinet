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
 * EXPERIMENTAL Right Shift menu: a dock along the bottom of the screen, like the Mac dock. Icons grow as the mouse
 * gets near them. Click a category to pop up a card above it with its mods and switches; Social and Edit HUD open
 * their screens. The game stays visible above.
 */
public class ExpDockScreen extends Screen {
    private static final int BASE = 22, MAXS = 38, GAP = 6, ROW_H = 18;
    private final Screen parent;
    private Category open;
    private double scroll;
    private final long openedAt = System.nanoTime();
    private final float[] sizes = new float[ExpKit.CATS.length + 2];
    private ExpSettings opts;                                      // a mod's settings, slid out to the right of the card
    private static final float OPTS_SECS = 0.22f;

    public ExpDockScreen(Screen parent) {
        super(Text.literal("Tempest"));
        this.parent = parent;
    }

    private int count() { return ExpKit.CATS.length + 2; }        // categories + Social + Edit HUD
    private String icon(int i) {
        if (i < ExpKit.CATS.length) return Icons.forCategory(ExpKit.CATS[i]);
        return i == ExpKit.CATS.length ? "people" : "layout";
    }
    private String label(int i) {
        if (i < ExpKit.CATS.length) return ExpKit.title(ExpKit.CATS[i]);
        return i == ExpKit.CATS.length ? "Social" : "Edit HUD";
    }
    private float rise() {
        if (!dev.tempest.modules.ClientSettingsModule.animationsOn()) return 1f;
        return MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.3f);
    }
    private int baseY() { return height - 12 + Math.round((1f - rise()) * 50); }

    /** Icon left edges for the current sizes (computed in render). */
    private float[] lefts() {
        float total = 0;
        for (int i = 0; i < count(); i++) total += sizes[i] + (i > 0 ? GAP : 0);
        float[] out = new float[count()];
        float x = width / 2f - total / 2f;
        for (int i = 0; i < count(); i++) { out[i] = x; x += sizes[i] + GAP; }
        return out;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        backdrop(ctx, rise());
        // magnify by distance from the mouse (only while it's near the dock)
        float[] base = new float[count()];
        float total = count() * BASE + (count() - 1) * GAP, x0 = width / 2f - total / 2f;
        boolean near = my > baseY() - MAXS - 20;
        for (int i = 0; i < count(); i++) {
            float c = x0 + i * (BASE + GAP) + BASE / 2f;
            float d = Math.abs(mx - c);
            float target = near ? BASE + (MAXS - BASE) * (float) Math.max(0, Math.cos(Math.min(Math.PI / 2, d / 70f * Math.PI / 2))) : BASE;
            sizes[i] = sizes[i] == 0 ? BASE : sizes[i] + (target - sizes[i]) * 0.3f;
        }
        float[] lx = lefts();
        float dockW = lx[count() - 1] + sizes[count() - 1] - lx[0] + 20;
        int dy = baseY();
        int gx = Math.round(lx[0] - 10), gy = dy - BASE - 12, gw = Math.round(dockW), gh = BASE + 18;
        ExpKit.glass(ctx, gx, gy, gw, gh, 12, 1f);
        // frosted sheen on the top half and a bright rim, like a real glass shelf
        Draw.roundRectV(ctx, gx + 1, gy + 1, gw - 2, gh / 2, 11, 0x1CFFFFFF, 0x00FFFFFF);
        ctx.fill(gx + 12, gy + 1, gx + gw - 12, gy + 2, 0x50FFFFFF);

        int hovered = -1;
        for (int i = 0; i < count(); i++) {
            int s = Math.round(sizes[i]), x = Math.round(lx[i]), y = dy - 6 - s;
            boolean hv = Draw.hit(mx, my, x, y, s, s);
            if (hv) hovered = i;
            boolean sel = i < ExpKit.CATS.length && ExpKit.CATS[i] == open;
            Draw.panel(ctx, x, y, s, s, Math.round(s * 0.28f), sel ? (Draw.accent() | 0xFF000000) : 0xF02C2646, sel ? 0xA0FFFFFF : 0x30FFFFFF);
            int is = Math.round(s * 0.56f);
            Icons.draw(ctx, icon(i), x + s / 2 - is / 2, y + s / 2 - is / 2, is, 0xFFFFFFFF);
            if (sel) Draw.circle(ctx, x + s / 2, dy - 2, 1, 0xFFFFFFFF);
        }
        if (hovered >= 0 && open == null) {
            String l = label(hovered);
            int s = Math.round(sizes[hovered]), lw = Draw.textW(tr, l) + 12, x = Math.round(lx[hovered]) + s / 2 - lw / 2, y = dy - 6 - s - 20;
            Draw.panel(ctx, x, y, lw, 14, 7, 0xF0181430, 0x40FFFFFF);
            Draw.text(ctx, tr, l, x + 6, y + 3, 0xFFFFFFFF, false);
        }

        // pop-up card above the open category
        if (open != null) {
            int idx = open.ordinal();
            int s = Math.round(sizes[idx]);
            int cw = 180, cx = Math.max(6, Math.min(width - cw - 6, Math.round(lx[idx]) + s / 2 - cw / 2));
            List<Module> mods = ExpKit.mods(open);
            int listH = Math.min(mods.size() * ROW_H, Math.max(ROW_H * 3, dy - BASE - 70 - 40));
            int ch = 30 + listH + 8, cy = dy - BASE - 24 - ch;
            ExpKit.glass(ctx, cx, cy, cw, ch, 12, 1f);
            Draw.text(ctx, tr, ExpKit.title(open), cx + 10, cy + 9, 0xFFFFFFFF, false);
            String cl = ExpKit.countLine(open);
            Draw.text(ctx, tr, cl, cx + cw - 10 - Draw.textW(tr, cl), cy + 9, 0xA0D6CFF2, false);
            double max = Math.max(0, mods.size() * ROW_H - listH);
            scroll = Math.max(0, Math.min(max, scroll));
            ctx.enableScissor(cx, cy + 26, cx + cw, cy + 26 + listH);
            for (int i = 0; i < mods.size(); i++) {
                int ry = cy + 26 + i * ROW_H - (int) scroll;
                if (ry + ROW_H < cy + 26 || ry > cy + 26 + listH) continue;
                boolean hv = Draw.hit(mx, my, cx + 10, ry, cw - 20, ROW_H) && my >= cy + 26 && my < cy + 26 + listH;
                boolean shown = opts != null && !opts.closing() && opts.mod == mods.get(i);
                if (shown) Draw.ring(ctx, cx + 6, ry, cw - 12, ROW_H, 6, Draw.withAlpha(Draw.accent(), 0xC0));
                ExpKit.modRow(ctx, tr, mods.get(i), cx + 10, ry, cw - 20, ROW_H, hv || shown, 1f);
            }
            ctx.disableScissor();
            drawOptions(ctx, tr, mx, my);
        }
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    /**
     * Behind the dock: the game (or the title wallpaper) blurred, darkening toward the bottom, a pool of accent light
     * the dock sits in, and soft dark edges.
     */
    private void backdrop(DrawContext ctx, float e) {
        if (client != null && client.world == null) Draw.menuBackground(ctx, width, height);
        boolean blurred = MenuBlur.apply(ctx);
        ctx.fillGradient(0, 0, width, height, MenuKit.fa(blurred ? 0x18080614 : 0x28080614, e), MenuKit.fa(0x98080614, e));
        float t = MenuKit.time();
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(width / 2f, baseY() - BASE / 2f - 6);
        m.scale(1f, 0.28f);
        float breathe = 0.85f + 0.15f * (float) Math.sin(t * 0.9f);
        TempestLoading.drawGlow(ctx, 0, 0, Math.round(width * 1.0f), Draw.accent(), 0.50f * breathe * e);
        TempestLoading.drawGlow(ctx, 0, 0, Math.round(width * 0.45f), 0xE8E0FF, 0.12f * e);
        m.popMatrix();
        ExpOrbitScreen.vignette(ctx, width, height, 0.8f * e);
    }

    /** Settings panel beside the pop-up card: to its right, or its left when there is no room. {x, y, w, h} */
    private int[] optsRect() {
        int[] r = cardRect();
        int w = Math.min(210, width - 20);
        int x = r[0] + r[2] + 8;
        if (x + w > width - 6) x = Math.max(6, r[0] - 8 - w);
        int h = Math.max(r[3], Math.min(250, r[1] + r[3] - 34));
        return new int[]{x, r[1] + r[3] - h, w, h};
    }

    private boolean rightSide() { return optsRect()[0] > cardRect()[0]; }

    private void drawOptions(DrawContext ctx, TextRenderer tr, int mx, int my) {
        if (opts == null) return;
        if (opts.gone(OPTS_SECS)) { opts = null; return; }
        float p = MenuKit.ease(opts.progress(OPTS_SECS));
        int[] o = optsRect();
        int slide = Math.round((1f - p) * 18) * (rightSide() ? -1 : 1);     // comes out from behind the card
        ctx.enableScissor(rightSide() ? cardRect()[0] + cardRect()[2] + 2 : 0, 0, rightSide() ? width : cardRect()[0] - 2, height);
        opts.render(ctx, tr, o[0] + slide, o[1], o[2], o[3], mx, my, p, p >= 1f && !opts.closing());
        ctx.disableScissor();
    }

    private void closeOptions() { if (opts != null) opts.startClosing(); }

    private int[] cardRect() {
        int idx = open.ordinal();
        float[] lx = lefts();
        int s = Math.round(sizes[idx]);
        int cw = 180, cx = Math.max(6, Math.min(width - cw - 6, Math.round(lx[idx]) + s / 2 - cw / 2));
        int dy = baseY();
        int listH = Math.min(ExpKit.mods(open).size() * ROW_H, Math.max(ROW_H * 3, dy - BASE - 70 - 40));
        int ch = 30 + listH + 8, cy = dy - BASE - 24 - ch;
        return new int[]{cx, cy, cw, ch, listH};
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        double mx = click.x(), my = click.y();
        if (opts != null && !opts.closing() && opts.mouseClicked(mx, my, click.button())) {
            if (opts.closeRequested) closeOptions();
            return true;
        }
        float[] lx = lefts();
        int dy = baseY();
        for (int i = 0; i < count(); i++) {
            int s = Math.round(sizes[i]);
            if (!Draw.hit(mx, my, Math.round(lx[i]), dy - 6 - s, s, s)) continue;
            if (i < ExpKit.CATS.length) { open = open == ExpKit.CATS[i] ? null : ExpKit.CATS[i]; scroll = 0; if (opts != null) opts.close(); opts = null; }
            else if (client != null) client.setScreen(i == ExpKit.CATS.length ? new SocialScreen(this) : new HudEditorScreen(this));
            return true;
        }
        if (open != null) {
            int[] r = cardRect();
            if (Draw.hit(mx, my, r[0], r[1], r[2], r[3])) {
                if (my >= r[1] + 26 && my < r[1] + 26 + r[4]) {
                    List<Module> mods = ExpKit.mods(open);
                    int i = (int) ((my - r[1] - 26 + scroll) / ROW_H);
                    if (i >= 0 && i < mods.size()) {
                        Module m = mods.get(i);
                        if (click.button() == 1) {
                            if (opts != null && !opts.closing() && opts.mod == m) closeOptions();
                            else { if (opts != null) opts.close(); opts = new ExpSettings(m, false); }
                        } else ExpKit.toggle(m);
                    }
                }
                return true;
            }
            if (opts != null && !opts.closing()) { closeOptions(); return true; }
            open = null;
            if (opts != null) opts.close();
            opts = null;
            return true;
        }
        close();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (opts != null && opts.mouseScrolled(mouseX, mouseY, vertical)) return true;
        scroll -= vertical * 24;
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (opts != null && opts.mouseDragged(click.x(), click.y())) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (opts != null) opts.mouseReleased();
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (opts != null && opts.keyPressed(input.key())) return true;
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        MenuBlur.restore();
        if (opts != null) opts.close();
    }

    @Override
    public void close() {
        if (opts != null && !opts.closing()) { closeOptions(); return; }
        if (client != null) client.setScreen(parent);
    }
}
