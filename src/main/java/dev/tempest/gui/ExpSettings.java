package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.KeySetting;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.NumberSetting;
import dev.tempest.module.Setting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * A mod's settings as a glass panel that any Right Shift style can place wherever suits it (beside a list, over a
 * card, under a star...). Every kind of setting works here: switches, sliders, modes, keybinds, buttons and colours
 * (palette plus a hue strip for any other colour). The owning screen passes the rect every frame and forwards input;
 * {@link #closeRequested} goes true when the close / back button is pressed.
 */
final class ExpSettings {
    static final int HEAD = 40, GAP = 2;
    private static final int SWATCH = 12;

    final Module mod;
    private final boolean backArrow;              // "<" on the left instead of "x" on the right
    private int x, y, w, h;                       // where it was last drawn
    private double scroll, scrollTarget;
    private NumberSetting dragging;
    private KeySetting listening;
    private ColorSetting hueOpen;
    private boolean hueDrag;
    boolean closeRequested;

    ExpSettings(Module m, boolean backArrow) {
        this.mod = m;
        this.backArrow = backArrow;
    }

    // ---------------------------------------------------------------- open / close animation
    private final long openedAt = System.nanoTime();
    private long closingAt = -1;

    /** 0..1 how far the panel is in (falls back to 0 while closing). {@code secs} = length of the animation. */
    float progress(float secs) {
        if (!dev.tempest.modules.ClientSettingsModule.animationsOn()) return closingAt >= 0 ? 0f : 1f;
        long now = System.nanoTime();
        float p = MenuKit.clamp01((now - openedAt) / 1e9f / secs);
        if (closingAt >= 0) p = Math.min(p, 1f - MenuKit.clamp01((now - closingAt) / 1e9f / secs));
        return p;
    }

    /** Starts the closing animation (and saves). */
    void startClosing() {
        if (closingAt >= 0) return;
        closingAt = System.nanoTime();
        listening = null;
        dragging = null;
        close();
    }

    boolean closing() { return closingAt >= 0; }

    /** True once the closing animation has finished, so the screen can drop the panel. */
    boolean gone(float secs) { return closingAt >= 0 && progress(secs) <= 0f; }

    /** True while the panel is waiting for a key (the screen should send every key here). */
    boolean listening() { return listening != null; }

    boolean inside(double mx, double my) { return Draw.hit(mx, my, x, y, w, h); }

    /** Saves the settings file; call when the panel goes away. */
    void close() { TempestConfig.save(); }

    // ---------------------------------------------------------------- layout
    private int rowH(Setting s) {
        if (s instanceof NumberSetting) return 30;
        if (s instanceof ColorSetting c) return c == hueOpen ? 46 : 32;
        return 22;
    }

    private int listTop() { return y + HEAD; }
    private int listBottom() { return y + h - 8; }
    private int contentH() {
        int t = 0;
        for (Setting s : mod.getSettings()) t += rowH(s) + GAP;
        return t;
    }
    private double maxScroll() { return Math.max(0, contentH() - (listBottom() - listTop())); }

    private int closeX() { return backArrow ? x + 8 : x + w - 26; }
    private int closeY() { return y + 12; }
    private int toggleX() { return x + w - (backArrow ? 40 : 62); }
    private int textX() { return backArrow ? x + 58 : x + 40; }

    // ---------------------------------------------------------------- drawing
    /**
     * Draws the panel at (x, y, w, h). {@code a} fades it; {@code live} = false while it is still animating in, so
     * nothing reacts to the mouse yet.
     */
    void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, int mx, int my, float a, boolean live) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        if (!live) { mx = -9999; my = -9999; }
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scroll += (scrollTarget - scroll) * 0.35;
        if (Math.abs(scrollTarget - scroll) < 0.5) scroll = scrollTarget;

        Draw.shadow(ctx, x, y, w, h, 12, 10);
        Draw.roundRectV(ctx, x, y, w, h, 12, MenuKit.fa(0xF4201A3A, a), MenuKit.fa(0xF4120E22, a));
        Draw.ring(ctx, x, y, w, h, 12, MenuKit.fa(Draw.withAlpha(Draw.accent(), 0x90), a));

        // header: close / back, the mod's orb, name, line, on/off switch
        boolean ch = Draw.hit(mx, my, closeX(), closeY(), 16, 16);
        float cp = Draw.hoverProgress("exps_close", ch);
        Draw.circle(ctx, closeX() + 8, closeY() + 8, 8, MenuKit.fa(Draw.withAlpha(0xFFFFFF, 0x14 + Math.round(0x28 * cp)), a));
        if (backArrow) Icons.draw(ctx, "back", closeX() + 3, closeY() + 3, 10, MenuKit.fa(0xE0FFFFFF, a));
        else Draw.text(ctx, tr, "×", closeX() + 5, closeY() + 4, MenuKit.fa(0xE0FFFFFF, a), false);

        float on = Draw.hoverProgress("exps_on_" + mod.getId(), mod.isEnabled());
        ExpKit.orb(ctx, textX() - 30, y + 9, 24, Icons.forModule(mod.getId()), on, a);
        int textW = (mod.alwaysOn() ? x + w - 10 : toggleX() - 6) - textX();
        Draw.text(ctx, tr, Draw.bold(Draw.ellipsize(tr, mod.getName(), textW)), textX(), y + 10, MenuKit.fa(0xFFFFFFFF, a), false);
        String d = mod.getDescription() == null ? "" : mod.getDescription();
        Draw.text(ctx, tr, Draw.ellipsize(tr, d, textW), textX(), y + 22, MenuKit.fa(0x9AD6CFF2, a), false);
        if (!mod.alwaysOn() && a > 0.6f) Draw.toggle(ctx, toggleX(), y + 14, on);
        ctx.fill(x + 10, y + HEAD - 4, x + w - 10, y + HEAD - 3, MenuKit.fa(0x24FFFFFF, a));

        List<Setting> list = mod.getSettings();
        if (list.isEmpty()) {
            String s = "No settings for this mod";
            Draw.text(ctx, tr, s, x + w / 2 - Draw.textW(tr, s) / 2, listTop() + 12, MenuKit.fa(0x90FFFFFF, a), false);
            return;
        }
        int top = listTop(), bottom = listBottom();
        ctx.enableScissor(x, top, x + w, bottom);
        int ry = top - (int) Math.round(scroll);
        boolean inList = my >= top && my < bottom;
        for (Setting s : list) {
            int rh = rowH(s);
            if (ry + rh >= top && ry <= bottom) row(ctx, tr, s, x + 8, ry, w - 16, rh, inList ? mx : -9999, my, a);
            ry += rh + GAP;
        }
        ctx.disableScissor();
        double max = maxScroll();
        if (max > 0) {
            int viewH = bottom - top;
            int barH = Math.max(14, (int) (viewH * (viewH / (viewH + max))));
            int barY = top + (int) ((viewH - barH) * (scroll / max));
            Draw.roundRect(ctx, x + w - 5, barY, 2, barH, 1, MenuKit.fa(0x50FFFFFF, a));
        }
    }

    private void row(DrawContext ctx, TextRenderer tr, Setting s, int x, int y, int w, int h, int mx, int my, float a) {
        boolean hv = Draw.hit(mx, my, x, y, w, h);
        float hp = Draw.hoverProgress("exps_row_" + mod.getId() + s.id, hv);
        if (hp > 0.02f) Draw.roundRect(ctx, x, y, w, h, 6, MenuKit.fa(Draw.withAlpha(0xFFFFFF, Math.round(0x14 * hp)), a));
        int nameCol = MenuKit.fa(Draw.lerpColor(0xFFCFCBE4, 0xFFFFFFFF, hp), a);

        if (s instanceof BoolSetting b) {
            Draw.text(ctx, tr, Draw.ellipsize(tr, s.name, w - 44), x + 6, y + 7, nameCol, false);
            if (a > 0.6f) Draw.toggle(ctx, x + w - 34, y + 4, Draw.hoverProgress("exps_b_" + mod.getId() + s.id, b.get()));
        } else if (s instanceof NumberSetting n) {
            String v = n.display();
            Draw.text(ctx, tr, Draw.ellipsize(tr, s.name, w - 18 - Draw.textW(tr, v)), x + 6, y + 4, nameCol, false);
            Draw.text(ctx, tr, v, x + w - 6 - Draw.textW(tr, v), y + 4, MenuKit.fa(Draw.accent() | 0xFF000000, a), false);
            int tx = x + 8, tw = w - 16, ty = y + 19;
            Draw.roundRect(ctx, tx, ty, tw, 4, 2, MenuKit.fa(0xFF2E2848, a));
            int fw = (int) Math.round(tw * n.fraction());
            if (fw > 0) Draw.roundRect(ctx, tx, ty, Math.max(4, fw), 4, 2, MenuKit.fa(Draw.accent() | 0xFF000000, a));
            if (dragging == n || hp > 0.5f) Draw.circle(ctx, tx + fw, ty + 2, 7, MenuKit.fa(Draw.withAlpha(Draw.accent(), dragging == n ? 0x50 : 0x2C), a));
            Draw.circle(ctx, tx + fw, ty + 2, 5, MenuKit.fa(0xFFFFFFFF, a));
        } else if (s instanceof ModeSetting mo) {
            int bw = Math.min(110, w / 2), bx = x + w - bw - 2, by = y + 2;
            Draw.text(ctx, tr, Draw.ellipsize(tr, s.name, bx - x - 10), x + 6, y + 7, nameCol, false);
            Draw.roundRect(ctx, bx, by, bw, 18, 6, MenuKit.fa(0xC0161228, a));
            boolean lh = Draw.hit(mx, my, bx, by, 18, 18), rh = Draw.hit(mx, my, bx + bw - 18, by, 18, 18);
            Draw.text(ctx, tr, "<", bx + 6, by + 5, MenuKit.fa(lh ? Draw.accent() | 0xFF000000 : 0xA0FFFFFF, a), false);
            Draw.text(ctx, tr, ">", bx + bw - 11, by + 5, MenuKit.fa(rh ? Draw.accent() | 0xFF000000 : 0xA0FFFFFF, a), false);
            String v = Draw.ellipsize(tr, mo.get(), bw - 34);
            Draw.text(ctx, tr, v, bx + bw / 2 - Draw.textW(tr, v) / 2, by + 5, MenuKit.fa(0xFFFFFFFF, a), false);
        } else if (s instanceof KeySetting ks) {
            boolean listen = listening == ks;
            int bw = Math.min(96, w / 2), bx = x + w - bw - 2, by = y + 2;
            Draw.text(ctx, tr, Draw.ellipsize(tr, s.name, bx - x - 10), x + 6, y + 7, nameCol, false);
            Draw.panel(ctx, bx, by, bw, 18, 6, MenuKit.fa(listen ? Draw.withAlpha(Draw.accent(), 0x50) : 0xC0161228, a),
                    MenuKit.fa(listen ? Draw.accent() | 0xFF000000 : 0x30FFFFFF, a));
            String v = Draw.ellipsize(tr, listen ? "Press a key..." : ks.label(), bw - 10);
            Draw.text(ctx, tr, v, bx + bw / 2 - Draw.textW(tr, v) / 2, by + 5, MenuKit.fa(listen ? Draw.accent() | 0xFF000000 : 0xFFFFFFFF, a), false);
        } else if (s instanceof ActionSetting as) {
            int bw = Math.min(90, Draw.textW(tr, as.label) + 20), bx = x + w - bw - 2, by = y + 2;
            Draw.text(ctx, tr, Draw.ellipsize(tr, s.name, bx - x - 10), x + 6, y + 7, nameCol, false);
            boolean bh = Draw.hit(mx, my, bx, by, bw, 18);
            Draw.roundRect(ctx, bx, by, bw, 18, 6, MenuKit.fa(Draw.lerpColor(0xFF2E2848, Draw.accent() | 0xFF000000, bh ? 1f : 0.55f), a));
            String v = Draw.ellipsize(tr, as.label, bw - 8);
            Draw.text(ctx, tr, v, bx + bw / 2 - Draw.textW(tr, v) / 2, by + 5, MenuKit.fa(0xFFFFFFFF, a), false);
        } else if (s instanceof ColorSetting c) {
            Draw.text(ctx, tr, Draw.ellipsize(tr, s.name, w - 30), x + 6, y + 4, nameCol, false);
            Draw.roundRect(ctx, x + w - 20, y + 3, 14, 10, 4, MenuKit.fa(c.get(), a));
            boolean inPalette = false;
            for (int k = 0; k < ColorSetting.PALETTE.length; k++) {
                int sx = swatchX(x, w, k), sy = y + 16;
                int col = ColorSetting.PALETTE[k];
                if (col == c.get()) { Draw.circle(ctx, sx + SWATCH / 2, sy + SWATCH / 2, SWATCH / 2 + 1, MenuKit.fa(0xFFFFFFFF, a)); inPalette = true; }
                Draw.circle(ctx, sx + SWATCH / 2, sy + SWATCH / 2, SWATCH / 2 - 1, MenuKit.fa(col, a));
            }
            // last swatch: a rainbow dot that opens the hue strip (ringed when the colour is a custom one)
            int kx = swatchX(x, w, ColorSetting.PALETTE.length), ky = y + 16;
            if (!inPalette || hueOpen == c) Draw.circle(ctx, kx + SWATCH / 2, ky + SWATCH / 2, SWATCH / 2 + 1, MenuKit.fa(0xFFFFFFFF, a));
            for (int dx = 0; dx < SWATCH - 2; dx++) ctx.fill(kx + 1 + dx, ky + 2, kx + 2 + dx, ky + SWATCH - 2, MenuKit.fa(Draw.hsvToRgb(dx / (float) (SWATCH - 2), 0.8f, 1f), a));
            if (hueOpen == c) {
                int hx = x + 6, hw = w - 12, hy = y + 33;
                for (int i = 0; i < hw; i++) ctx.fill(hx + i, hy, hx + i + 1, hy + 8, MenuKit.fa(Draw.hsvToRgb(i / (float) hw, 0.85f, 1f), a));
                float hue = Draw.rgbToHsv(c.get())[0];
                int px = hx + Math.round(hue * (hw - 1));
                ctx.fill(px - 1, hy - 2, px + 2, hy + 10, MenuKit.fa(0xFF000000, a));
                ctx.fill(px, hy - 1, px + 1, hy + 9, MenuKit.fa(0xFFFFFFFF, a));
            }
        }
    }

    /** Swatch k in a colour row of width w: 16px apart, closer together when the panel is narrow. */
    private static int swatchX(int x, int w, int k) {
        int n = ColorSetting.PALETTE.length + 1;
        int step = Math.max(SWATCH + 1, Math.min(SWATCH + 4, (w - 12 - SWATCH) / (n - 1)));
        return x + 6 + k * step;
    }

    // ---------------------------------------------------------------- input
    /** Returns true if the click landed on the panel (it always eats those). */
    boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my)) return false;
        KeySetting wasListening = listening;
        listening = null;
        if (Draw.hit(mx, my, closeX(), closeY(), 16, 16)) { closeRequested = true; return true; }
        if (!mod.alwaysOn() && Draw.hit(mx, my, toggleX() - 2, y + 10, 32, 22)) { mod.toggle(); return true; }
        if (my < listTop() || my >= listBottom()) return true;
        int ry = listTop() - (int) Math.round(scroll), rx = x + 8, rw = w - 16;
        for (Setting s : mod.getSettings()) {
            int rh = rowH(s);
            if (my >= ry && my < ry + rh) {
                click(s, mx, my, button, rx, ry, rw, wasListening);
                return true;
            }
            ry += rh + GAP;
        }
        return true;
    }

    private void click(Setting s, double mx, double my, int button, int x, int y, int w, KeySetting wasListening) {
        if (s instanceof BoolSetting b) b.toggle();
        else if (s instanceof NumberSetting n) {
            dragging = n;
            n.setFraction((mx - (x + 8)) / (double) (w - 16));
        } else if (s instanceof ModeSetting mo) {
            int bw = Math.min(110, w / 2), bx = x + w - bw - 2;
            mo.cycle(button == 1 || mx < bx + bw / 2.0 ? -1 : 1);
        } else if (s instanceof KeySetting ks) {
            if (button == 1) ks.assign(-1);
            else listening = wasListening == ks ? null : ks;
        } else if (s instanceof ActionSetting as) {
            as.run();
        } else if (s instanceof ColorSetting c) {
            if (hueOpen == c && my >= y + 31) {
                hueDrag = true;
                pickHue(c, mx);
                return;
            }
            for (int k = 0; k <= ColorSetting.PALETTE.length; k++) {
                if (!Draw.hit(mx, my, swatchX(x, w, k) - 1, y + 15, SWATCH + 2, SWATCH + 2)) continue;
                if (k < ColorSetting.PALETTE.length) { c.set(ColorSetting.PALETTE[k]); hueOpen = null; }
                else hueOpen = hueOpen == c ? null : c;
                return;
            }
        }
    }

    private void pickHue(ColorSetting c, double mx) {
        int hx = x + 8 + 6, hw = w - 16 - 12;
        float hue = (float) Math.max(0, Math.min(0.999, (mx - hx) / (double) hw));
        c.set(Draw.hsvToRgb(hue, 0.85f, 1f));
    }

    boolean mouseDragged(double mx, double my) {
        if (dragging != null) {
            int tx = x + 16, tw = w - 32;
            dragging.setFraction((mx - tx) / (double) tw);
            return true;
        }
        if (hueDrag && hueOpen != null) { pickHue(hueOpen, mx); return true; }
        return false;
    }

    void mouseReleased() { dragging = null; hueDrag = false; }

    boolean mouseScrolled(double mx, double my, double vertical) {
        if (!inside(mx, my)) return false;
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - vertical * 30));
        return true;
    }

    /** Keys while waiting for a keybind; Esc unbinds. Returns true if used. */
    boolean keyPressed(int key) {
        if (listening == null) return false;
        listening.assign(key == 256 ? -1 : key);
        listening = null;
        return true;
    }
}
