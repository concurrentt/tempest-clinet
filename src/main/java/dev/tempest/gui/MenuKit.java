package dev.tempest.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Shared pieces for the Tempest main menu, singleplayer and multiplayer screens. */
public final class MenuKit {
    private static final long T0 = System.nanoTime();

    private MenuKit() { }

    /** Seconds since the game started; drives every ambient animation. */
    public static float time() { return (System.nanoTime() - T0) / 1_000_000_000f; }

    public static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

    public static float ease(float x) { x = clamp01(x); return 1f - (1f - x) * (1f - x) * (1f - x); }

    /** Multiplies the alpha channel of a colour. */
    public static int fa(int col, float alpha) {
        int a = Math.round(((col >>> 24) & 255) * clamp01(alpha));
        return (a << 24) | (col & 0xFFFFFF);
    }

    /** Text scaled up (or down) around a centre x. Uses the 2D matrix stack, like the HUD modules do. */
    public static void scaledCentered(DrawContext c, TextRenderer tr, Text t, float cx, float y, float scale, int col, boolean shadow) {
        if (Math.abs(scale - 1f) < 0.01f) { Draw.text(c, tr, t, Math.round(cx - Draw.textW(tr, t) / 2f), Math.round(y), col, shadow); return; }
        t = Draw.uiScaled(t, scale);
        int w = tr.getWidth(t);
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(Draw.snap(cx - w * scale / 2f), Draw.snap(y));
        c.getMatrices().scale(scale, scale);
        c.drawText(tr, t, 0, 0, col, shadow);
        c.getMatrices().popMatrix();
    }

    public static final int DARK = 0, GREEN = 1, OUTLINE = 2;

    /**
     * Main-menu style button: dark rounded fill with an icon beside the label. GREEN is the highlighted action,
     * OUTLINE is a quiet secondary one. Disabled buttons are dimmed and ignore hover.
     */
    public static void flatButton(DrawContext c, TextRenderer tr, String key, int x, int y, int w, int h,
                                  String icon, String label, int variant, boolean hover, boolean enabled, float alpha) {
        float a = alpha * (enabled ? 1f : 0.45f);
        float p = Draw.hoverProgress("fb:" + key, hover && enabled);
        int fill, border, fg;
        switch (variant) {
            case GREEN -> {
                fill = Draw.lerpColor(0xD8123A22, 0xF0185030, p);
                border = Draw.lerpColor(0x9031B35F, 0xE046D27A, p);
                fg = Draw.lerpColor(0xFF6AF09A, 0xFFB6FFCF, p);
            }
            case OUTLINE -> {
                fill = Draw.lerpColor(0x40141519, 0x9026282F, p);
                border = Draw.lerpColor(0x30FFFFFF, 0x66FFFFFF, p);
                fg = Draw.lerpColor(0xFFC9CCD6, 0xFFFFFFFF, p);
            }
            default -> {
                fill = Draw.lerpColor(0xD81A1B20, 0xEC2A2C33, p);
                border = Draw.lerpColor(0x26FFFFFF, 0x55FFFFFF, p);
                fg = Draw.lerpColor(0xFFE4E6EC, 0xFFFFFFFF, p);
            }
        }
        Draw.panel(c, x, y, w, h, Math.min(6, h / 2), fa(fill, a), fa(border, a));
        int is = icon == null ? 0 : Math.min(10, h - 10);
        int lw = label == null ? 0 : Draw.textW(tr, label);
        int gap = is > 0 && lw > 0 ? 6 : 0;
        int cx = x + (w - (is + gap + lw)) / 2;
        if (is > 0) Icons.draw(c, icon, cx, y + (h - is) / 2, is, fa(fg, a));
        if (lw > 0) Draw.text(c, tr, label, cx + is + gap, y + (h - 8) / 2, fa(fg, a), false);
    }

    /** Square dark button holding just an icon. */
    public static void iconButton(DrawContext c, String key, int x, int y, int size, String icon, boolean hover, int tint, float alpha) {
        float p = Draw.hoverProgress("ib:" + key, hover);
        Draw.panel(c, x, y, size, size, 6, fa(Draw.lerpColor(0xD81A1B20, 0xEC2A2C33, p), alpha),
                fa(Draw.lerpColor(0x26FFFFFF, 0x55FFFFFF, p), alpha));
        int is = Math.max(8, size - 12);
        Icons.draw(c, icon, x + (size - is) / 2, y + (size - is) / 2, is, fa(Draw.lerpColor(tint, 0xFFFFFFFF, p * 0.6f), alpha));
    }

    /** Header used by the list screens: back button, big title, small subtitle. */
    public static void titleBar(DrawContext c, TextRenderer tr, int x, int y, String title, String sub, boolean backHover, float alpha) {
        iconButton(c, "back_" + title, x, y, 24, "back", backHover, 0xFFE4E6EC, alpha);
        scaledLeft(c, tr, Draw.bold(title), x + 34, y + 1, 1.5f, fa(0xFFFFFFFF, alpha), true);
        if (sub != null) Draw.text(c, tr, sub, x + 34, y + 15, fa(0xFF8B90A0, alpha), false);
    }

    /** Rounded list row; returns nothing, just paints the background with hover / selected states. */
    public static void row(DrawContext c, String key, int x, int y, int w, int h, boolean hover, boolean selected, float alpha) {
        float p = Draw.hoverProgress("row:" + key, hover);
        float s = Draw.hoverProgress("rowsel:" + key, selected);
        int fill = Draw.lerpColor(Draw.lerpColor(0xB41A1B20, 0xD026282F, p), 0xDC2C2E37, s);
        int border = Draw.lerpColor(Draw.lerpColor(0x1EFFFFFF, 0x40FFFFFF, p), 0xB0FFFFFF, s);
        Draw.panel(c, x, y, w, h, 7, fa(fill, alpha), fa(border, alpha));
    }

    /** Coloured icon tile at the start of a row. */
    public static void iconTile(DrawContext c, int x, int y, int size, String icon, int rgb, float alpha) {
        Draw.roundRect(c, x, y, size, size, 6, fa(Draw.withAlpha(rgb, 0x38), alpha));
        Draw.ring(c, x, y, size, size, 6, fa(Draw.withAlpha(rgb, 0x90), alpha));
        int is = Math.round(size * 0.55f);
        Icons.draw(c, icon, x + (size - is) / 2, y + (size - is) / 2, is, fa(Draw.lerpColor(0xFF000000 | rgb, 0xFFFFFFFF, 0.35f), alpha));
    }

    /** A server's own 64x64 icon, scaled into a square with a faint frame. */
    public static void serverIcon(DrawContext c, net.minecraft.util.Identifier icon, int x, int y, int size, float alpha) {
        Draw.roundRect(c, x - 1, y - 1, size + 2, size + 2, 4, fa(0xFF101116, alpha));
        c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, size, size, 64, 64, 64, 64, fa(0xFFFFFFFF, alpha));
        Draw.ring(c, x - 1, y - 1, size + 2, size + 2, 4, fa(0x30FFFFFF, alpha));
    }

    /** Soft fades at the top and bottom of a scrolling list (drawn over the rows, inside the scissor). */
    public static void listFades(DrawContext c, int x, int y, int w, int h, double scroll, double max) {
        float top = (float) Math.min(1.0, scroll / 20.0), bot = (float) Math.min(1.0, (max - scroll) / 20.0);
        if (top > 0.01f) c.fillGradient(x, y, x + w, y + 14, fa(0xC0080A12, top), 0x00080A12);
        if (bot > 0.01f) c.fillGradient(x, y + h - 14, x + w, y + h, 0x00080A12, fa(0xC0080A12, bot));
    }

    /** Text scaled up from its top-left corner. */
    public static void scaledLeft(DrawContext c, TextRenderer tr, Text t, float x, float y, float scale, int col, boolean shadow) {
        if (Math.abs(scale - 1f) < 0.01f) { Draw.text(c, tr, t, Math.round(x), Math.round(y), col, shadow); return; }
        t = Draw.uiScaled(t, scale);
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(Draw.snap(x), Draw.snap(y));
        c.getMatrices().scale(scale, scale);
        c.drawText(tr, t, 0, 0, col, shadow);
        c.getMatrices().popMatrix();
    }

    /**
     * Big glass button. {@code primary} tints it with the theme accent.
     * {@code alpha} fades the whole thing (used for the staggered entrance).
     */
    public static void button(DrawContext c, TextRenderer tr, String key, int x, int y, int w, int h,
                              String label, String sub, boolean hover, boolean primary, float alpha) {
        if (alpha <= 0.01f) return;
        float p = Draw.hoverProgress("mk:" + key, hover);
        int acc = Draw.accent();
        int r = Math.min(10, h / 2);
        int fill = primary
                ? Draw.lerpColor(Draw.withAlpha(acc, 0x58), Draw.withAlpha(acc, 0x96), p)
                : Draw.lerpColor(Draw.card(), Draw.cardHover(), p);
        int border = primary
                ? Draw.lerpColor(Draw.withAlpha(acc, 0x90), Draw.withAlpha(acc, 0xE0), p)
                : Draw.lerpColor(Draw.border(), Draw.withAlpha(acc, 0xA0), p);
        if (p > 0.02f) Draw.roundRect(c, x - 3, y - 3, w + 6, h + 6, r + 3, fa(Draw.withAlpha(acc, 0x2A), p * alpha));
        Draw.panel(c, x, y, w, h, r, fa(fill, alpha), fa(border, alpha));
        Draw.roundRectV(c, x + 1, y + 1, w - 2, Math.max(2, h / 2), Math.max(0, r - 1), fa(Draw.withAlpha(0xFFFFFF, 0x1C), alpha), 0x00FFFFFF);

        int textCol = Draw.lerpColor(primary ? 0xFFF1EEFF : Draw.DIM, 0xFFFFFFFF, primary ? p : Math.max(0.35f, p));
        int ty = y + (h - 8) / 2;
        if (sub != null) {
            Draw.text(c, tr, primary ? Draw.bold(label) : Text.literal(label), x + (w - Draw.textW(tr, primary ? Draw.bold(label) : Text.literal(label))) / 2, ty - 5, fa(textCol, alpha), false);
            Draw.text(c, tr, sub, x + (w - Draw.textW(tr, sub)) / 2, ty + 7, fa(Draw.DIM2, alpha), false);
        } else {
            Text t = primary ? Draw.bold(label) : Text.literal(label);
            Draw.text(c, tr, t, x + (w - Draw.textW(tr, t)) / 2, ty, fa(textCol, alpha), false);
        }
    }

    /** A soft, rounded avatar tile with a letter, tinted by the name so lists are easy to scan. */
    public static void tile(DrawContext c, TextRenderer tr, int x, int y, int size, String name, float alpha) {
        int hash = name == null ? 0 : name.hashCode();
        int base = Draw.hsvToRgb(((hash >>> 3) % 360) / 360f, 0.45f, 0.95f);
        Draw.roundRect(c, x, y, size, size, 8, fa(Draw.withAlpha(base, 0x34), alpha));
        Draw.ring(c, x, y, size, size, 8, fa(Draw.withAlpha(base, 0x70), alpha));
        String ini = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        Text t = Draw.bold(ini);
        Draw.text(c, tr, t, x + (size - Draw.textW(tr, t)) / 2, y + (size - 8) / 2, fa(Draw.withAlpha(Draw.lerpColor(base, 0xFFFFFFFF, 0.45f), 0xFF), alpha), false);
    }

    /** Header strip used by the list screens: logo, title, subtitle. */
    public static void header(DrawContext c, TextRenderer tr, int x, int y, int w, String title, String sub) {
        TempestLoading.drawLogo(c, x + 16, y + 12, 26);
        Draw.text(c, tr, Draw.bold(title), x + 50, y + 13, Draw.TEXT, false);
        if (sub != null) Draw.text(c, tr, sub, x + 50, y + 26, Draw.DIM, false);
        c.fill(x + 14, y + 46, x + w - 14, y + 47, Draw.border());
    }

    /** Compact "5 min ago" style text for a millisecond timestamp. */
    public static String ago(long epochMs) {
        if (epochMs <= 0) return "never played";
        long s = Math.max(0, (System.currentTimeMillis() - epochMs) / 1000);
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        if (s < 86400L * 30) return (s / 86400) + " d ago";
        return (s / (86400L * 30)) + " mo ago";
    }
}
