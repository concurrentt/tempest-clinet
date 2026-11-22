package dev.tempest.gui;

import dev.tempest.module.Category;
import dev.tempest.modules.ClientSettingsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/** Small 2D toolkit: rounded rects, gradients, shadows, circles, animated switches, themes. */
public final class Draw {
    public static final int TEXT = 0xFFEAEFFF, DIM = 0xFF8B96C0, DIM2 = 0xFF5A6590;

    // accent, bg, side, header, card, cardHover, off, border  (alpha is applied by the accessors below,
    // so every theme is translucent "glass" over the game instead of a solid slab)
    private static final int[][] THEMES = {
            {0xFF9B8BCB, 0xFF10131C, 0xFF0B0D14, 0xFF141824, 0xFF1C212E, 0xFF2A3142, 0xFF3A4254, 0xFFB9C0E0}, // Tempest
            {0xFF6C8CFF, 0xFF0F1220, 0xFF0A0C18, 0xFF121528, 0xFF1A1E36, 0xFF262C4D, 0xFF343A5C, 0xFFAEBBFF}, // Midnight
            {0xFFFF5470, 0xFF1B1015, 0xFF130A0E, 0xFF1D1217, 0xFF2A1A20, 0xFF3A262D, 0xFF4F3A41, 0xFFFFB0BC}, // Crimson
            {0xFF38D9A0, 0xFF0D1814, 0xFF08110E, 0xFF0F1D18, 0xFF162A22, 0xFF1F3A2F, 0xFF30493D, 0xFFA6F0D2}, // Emerald
            {0xFFB085FF, 0xFF15102A, 0xFF0D0A1C, 0xFF191330, 0xFF231B42, 0xFF30265A, 0xFF473C6C, 0xFFD2BDFF}, // Violet
            {0xFFFFB347, 0xFF1B150B, 0xFF120E07, 0xFF1F180C, 0xFF2D2415, 0xFF3D3020, 0xFF55472F, 0xFFFFDDA3}  // Amber
    };

    // glass opacities
    private static final int A_BG = 0x9A, A_SIDE = 0x70, A_HEAD = 0x60, A_CARD = 0x52, A_HOVER = 0x78, A_OFF = 0xA0, A_BORDER = 0x34;

    /** Use Minecraft's native bitmap font for crisp, pixel-aligned UI text. */
    public static Text bold(String s) { return Text.literal(s).styled(style -> style.withBold(true)); }

    private static Text styled(Text t) { return t.copy(); }

    public static int textW(TextRenderer tr, String s) { return tr.getWidth(s); }
    public static int textW(TextRenderer tr, Text t) { return tr.getWidth(styled(t)); }

    public static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int col, boolean shadow) {
        c.drawText(tr, s, x, y, col, shadow);
    }

    public static void text(DrawContext c, TextRenderer tr, Text t, int x, int y, int col, boolean shadow) {
        c.drawText(tr, styled(t), x, y, col, shadow);
    }

    public static void centered(DrawContext c, TextRenderer tr, String s, int cx, int y, int col) {
        text(c, tr, s, cx - textW(tr, s) / 2, y, col, true);
    }

    /** Width of letter-spaced text (see {@link #spaced}). */
    public static int spacedW(TextRenderer tr, String s, boolean bold, int spacing) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            w += bold ? tr.getWidth(bold(ch)) : tr.getWidth(ch);
        }
        return w + Math.max(0, s.length() - 1) * spacing;
    }

    /** Letter-spaced label centred on cx (the "O P T I O N S" look). */
    public static void spaced(DrawContext c, TextRenderer tr, String s, int cx, int y, int col, boolean bold, int spacing) {
        int x = cx - spacedW(tr, s, bold, spacing) / 2;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            if (bold) c.drawText(tr, bold(ch), x, y, col, false);
            else c.drawText(tr, ch, x, y, col, false);
            x += (bold ? tr.getWidth(bold(ch)) : tr.getWidth(ch)) + spacing;
        }
    }

    public static String trim(TextRenderer tr, String s, int max) {
        if (textW(tr, s) <= max) return s;
        int n = s.length();
        while (n > 0 && textW(tr, s.substring(0, n)) > max) n--;
        return s.substring(0, n);
    }

    /** Like trim() but marks the cut with "..." so truncated text reads as truncated. */
    public static String ellipsize(TextRenderer tr, String s, int max) {
        if (textW(tr, s) <= max) return s;
        int dots = textW(tr, "...");
        if (max <= dots) return "";
        int n = s.length();
        while (n > 0 && textW(tr, s.substring(0, n)) + dots > max) n--;
        return s.substring(0, n).stripTrailing() + "...";
    }

    private record HoverState(float value, long time) { }
    private static final Map<Object, HoverState> HOVER = new HashMap<>();

    private Draw() { }

    /** Shared frame-rate-independent easing for hover states across every Tempest screen. */
    public static float hoverProgress(Object key, boolean hovered) {
        long now = System.nanoTime();
        float target = hovered ? 1f : 0f;
        HoverState state = HOVER.get(key);
        float current = state == null ? target : state.value();
        float frameDt = state == null ? 0.016f : Math.min(0.05f, Math.max(0.001f, (now - state.time()) / 1_000_000_000f));
        current += (target - current) * (1f - (float) Math.exp(-frameDt * 17f));
        if (Math.abs(target - current) < 0.004f) current = target;
        if (HOVER.size() > 512) HOVER.clear();
        HOVER.put(key, new HoverState(current, now));
        return current;
    }

    private static int[] customTheme;
    private static int customFor = 0;

    private static int[] t() {
        int custom = ClientSettingsModule.customAccent();
        if (custom != -1) {
            if (customTheme == null || customFor != custom) {
                // a full palette from one colour: dark surfaces faintly tinted with it, a light border
                int[] base = THEMES[0];
                customTheme = new int[base.length];
                customTheme[0] = custom | 0xFF000000;
                for (int i = 1; i < base.length - 1; i++) customTheme[i] = lerpColor(base[i], custom | 0xFF000000, 0.08f);
                customTheme[base.length - 1] = lerpColor(custom | 0xFF000000, 0xFFFFFFFF, 0.55f);
                customFor = custom;
            }
            return customTheme;
        }
        return THEMES[Math.max(0, Math.min(THEMES.length - 1, ClientSettingsModule.themeIndex()))];
    }

    public static int accent() { return t()[0]; }
    public static int bg() { return withAlpha(t()[1], A_BG); }
    public static int side() { return withAlpha(t()[2], A_SIDE); }
    public static int header() { return withAlpha(t()[3], A_HEAD); }
    public static int card() { return withAlpha(t()[4], A_CARD); }
    public static int cardHover() { return withAlpha(t()[5], A_HOVER); }
    public static int off() { return withAlpha(t()[6], A_OFF); }
    public static int border() { return withAlpha(t()[7], A_BORDER); }
    /** Fully opaque version of the window colour, for tooltips and popups that must stay readable. */
    public static int solid() { return t()[1] | 0xFF000000; }

    // ---------- colors ----------
    public static int withAlpha(int rgb, int a) { return ((a & 255) << 24) | (rgb & 0xFFFFFF); }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    /** Soft per-category tint (mixed with the theme so every theme still looks like itself). */
    public static int catColor(Category c) {
        if (c == null) return accent();
        int base = switch (c) {
            case HUD -> 0xFF5B8CFF;
            case PVP -> 0xFFFF5470;
            case RENDER -> 0xFFB085FF;
            case VISUALS -> 0xFFFF7AD9;
            case QOL -> 0xFF38D9A0;
            case UTILITY -> 0xFF70C9D8;
            case CHAT -> 0xFFFFB347;
            case CLIENT -> 0xFF9EA2B8;
        };
        return lerpColor(base, accent(), 0.25f);
    }

    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    public static int hsvToRgb(float h, float s, float v) {
        float c = v * s, hh = (h % 1f) * 6f, x = c * (1 - Math.abs(hh % 2 - 1)), m = v - c;
        float r, g, b;
        int sector = (int) hh;
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    // ---------- hit test ----------
    public static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- shapes ----------
    private static int inset(int r, int row) {
        double dy = r - row - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    private static float insetF(int r, int row) {
        double dy = r - row - 0.5;
        return (float) (r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /**
     * GUI scale as a whole number. Rounded shapes are drawn at real screen-pixel resolution (the matrix is
     * scaled down by this much), so their corners are smooth curves instead of chunky GUI-pixel steps.
     */
    private static int hiRes() {
        try {
            double f = MinecraftClient.getInstance().getWindow().getScaleFactor();
            int k = (int) Math.round(f);
            return Math.max(1, Math.min(6, k));
        } catch (Throwable t) {
            return 1;
        }
    }

    /** Rounded rectangle with anti-aliased corners; rows never overlap so translucent colors are safe. */
    public static void roundRect(DrawContext c, int x, int y, int w, int h, int r, int col) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { c.fill(x, y, x + w, y + h, col); return; }
        int k = hiRes();
        if (k == 1) { rawRoundRect(c, x, y, w, h, r, col); return; }
        c.getMatrices().pushMatrix();
        c.getMatrices().scale(1f / k, 1f / k);
        rawRoundRect(c, x * k, y * k, w * k, h * k, r * k, col);
        c.getMatrices().popMatrix();
    }

    private static void rawRoundRect(DrawContext c, int x, int y, int w, int h, int r, int col) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { c.fill(x, y, x + w, y + h, col); return; }
        c.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) corner(c, x, y, w, h, r, i, col);
    }

    private static void corner(DrawContext c, int x, int y, int w, int h, int r, int i, int col) {
        float in = insetF(r, i);
        int full = (int) Math.ceil(in);
        c.fill(x + full, y + i, x + w - full, y + i + 1, col);
        c.fill(x + full, y + h - 1 - i, x + w - full, y + h - i, col);
        if (full > 0) {
            int a = (int) (((col >>> 24) & 255) * (full - in));
            if (a > 0) {
                int e = withAlpha(col, a);
                c.fill(x + full - 1, y + i, x + full, y + i + 1, e);
                c.fill(x + w - full, y + i, x + w - full + 1, y + i + 1, e);
                c.fill(x + full - 1, y + h - 1 - i, x + full, y + h - i, e);
                c.fill(x + w - full, y + h - 1 - i, x + w - full + 1, y + h - i, e);
            }
        }
    }

    /** Rounded rectangle with a vertical gradient. */
    public static void roundRectV(DrawContext c, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        int k = hiRes();
        if (k == 1) { rawRoundRectV(c, x, y, w, h, r, top, bottom); return; }
        c.getMatrices().pushMatrix();
        c.getMatrices().scale(1f / k, 1f / k);
        rawRoundRectV(c, x * k, y * k, w * k, h * k, r * k, top, bottom);
        c.getMatrices().popMatrix();
    }

    private static void rawRoundRectV(DrawContext c, int x, int y, int w, int h, int r, int top, int bottom) {
        // straight middle part in one gradient, then the corner rows one by one
        if (h - 2 * r > 0) {
            c.fillGradient(x, y + r, x + w, y + h - r, lerpColor(top, bottom, r / (float) Math.max(1, h - 1)),
                    lerpColor(top, bottom, (h - r) / (float) Math.max(1, h - 1)));
        }
        for (int i = 0; i < r; i++) {
            for (int side = 0; side < 2; side++) {
                int row = side == 0 ? i : h - 1 - i;
                float in = insetF(r, i);
                int full = (int) Math.ceil(in);
                int col = lerpColor(top, bottom, h <= 1 ? 0f : row / (float) (h - 1));
                c.fill(x + full, y + row, x + w - full, y + row + 1, col);
                if (full > 0) {
                    int a = (int) (((col >>> 24) & 255) * (full - in));
                    if (a > 0) {
                        int e = withAlpha(col, a);
                        c.fill(x + full - 1, y + row, x + full, y + row + 1, e);
                        c.fill(x + w - full, y + row, x + w - full + 1, y + row + 1, e);
                    }
                }
            }
        }
    }

    /** Filled rounded panel with a 1px border. The border is a ring, so translucent fills stay clean (no bleed-through). */
    public static void panel(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        if (w <= 2 || h <= 2) return;
        r = Math.min(r, Math.min(w, h) / 2);
        roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
        ring(c, x, y, w, h, r, border);
    }

    /** 1px rounded outline (1 GUI pixel thick, drawn at screen resolution with smooth corners). */
    public static void ring(DrawContext c, int x, int y, int w, int h, int r, int col) {
        if (w <= 0 || h <= 0 || ((col >>> 24) & 255) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { outline(c, x, y, w, h, col); return; }
        int k = hiRes();
        c.getMatrices().pushMatrix();
        if (k > 1) c.getMatrices().scale(1f / k, 1f / k);
        rawRing(c, x * k, y * k, w * k, h * k, r * k, k, col);
        c.getMatrices().popMatrix();
    }

    /** Ring of thickness t: straight edges as single fills, corners row by row with anti-aliased edges. */
    private static void rawRing(DrawContext c, int x, int y, int w, int h, int r, int t, int col) {
        c.fill(x + r, y, x + w - r, y + t, col);
        c.fill(x + r, y + h - t, x + w - r, y + h, col);
        c.fill(x, y + r, x + t, y + h - r, col);
        c.fill(x + w - t, y + r, x + w, y + h - r, col);
        int alpha = (col >>> 24) & 255;
        int ri = r - t;                                   // inner radius
        for (int i = 0; i < r; i++) {
            float outer = insetF(r, i);
            float inner = i < t || ri <= 0 ? r : t + insetF(ri, i - t);   // where the inside of the ring starts
            int o = (int) Math.ceil(outer);
            int n = Math.max(o, Math.min(r, (int) Math.floor(inner)));
            for (int side = 0; side < 2; side++) {
                int row = side == 0 ? y + i : y + h - 1 - i;
                if (n > o) {
                    c.fill(x + o, row, x + n, row + 1, col);
                    c.fill(x + w - n, row, x + w - o, row + 1, col);
                }
                int ao = (int) (alpha * (o - outer));         // soft outer edge
                if (o > 0 && ao > 0) {
                    int e = withAlpha(col, ao);
                    c.fill(x + o - 1, row, x + o, row + 1, e);
                    c.fill(x + w - o, row, x + w - o + 1, row + 1, e);
                }
                int ai = (int) (alpha * (inner - n));         // soft inner edge
                if (n < r && ai > 0) {
                    int e = withAlpha(col, ai);
                    c.fill(x + n, row, x + n + 1, row + 1, e);
                    c.fill(x + w - n - 1, row, x + w - n, row + 1, e);
                }
            }
        }
    }

    /** Filled rounded surface with no border: the quiet default for rows and cards. */
    public static void surface(DrawContext c, int x, int y, int w, int h, int r, int fill) {
        roundRect(c, x, y, w, h, r, fill);
    }

    /** Small chevron, 4px wide and 7px tall, centred on (cx, cy). */
    public static void chevron(DrawContext c, int cx, int cy, int col) { chevron(c, cx, cy, col, false); }

    public static void chevron(DrawContext c, int cx, int cy, int col, boolean left) {
        for (int dy = -3; dy <= 3; dy++) {
            int px = left ? cx - 3 + Math.abs(dy) : cx + 3 - Math.abs(dy);
            c.fill(px - 1, cy + dy, px + 1, cy + dy + 1, col);
        }
    }

    /** Filled circle centred on (cx, cy), smooth at any GUI scale. */
    public static void circle(DrawContext c, int cx, int cy, int r, int col) {
        if (r <= 0) { c.fill(cx, cy, cx + 1, cy + 1, col); return; }
        roundRect(c, cx - r, cy - r, 2 * r + 1, 2 * r + 1, r, col);
    }

    /** Thin horizontal line that fades out toward both ends (a quiet accent highlight along a panel's top edge). */
    public static void hairline(DrawContext c, int x, int y, int w, int rgb, int maxAlpha) {
        if (w <= 0) return;
        final int steps = 16;
        for (int i = 0; i < steps; i++) {
            int x0 = x + w * i / steps, x1 = x + w * (i + 1) / steps;
            double k = Math.sin(Math.PI * (i + 0.5) / steps);
            int a = (int) (maxAlpha * k * k);
            if (a > 0 && x1 > x0) c.fill(x0, y, x1, y + 1, withAlpha(rgb, a));
        }
    }

    /** Small rounded label chip; returns its width so callers can lay out the next thing beside it. */
    public static int chip(DrawContext c, TextRenderer tr, int x, int y, String label, int fill, int textCol) {
        int w = textW(tr, label) + 10;
        roundRect(c, x, y, w, 12, 6, fill);
        text(c, tr, label, x + 5, y + 2, textCol, false);
        return w;
    }

    /** Soft halo around a rounded rect. Drawn as rings outside the shape, so it never darkens a translucent panel. */
    public static void shadow(DrawContext c, int x, int y, int w, int h, int r, int spread) {
        for (int i = spread; i >= 1; i--) {
            float f = 1f - i / (float) (spread + 1);
            int a = (int) (0x2C * f * f * f);
            if (a <= 0) continue;
            ring(c, x - i, y - i + 1, w + 2 * i, h + 2 * i, r + i, a << 24);
        }
    }

    /** 1px rectangle outline. */
    public static void outline(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y + 1, x + 1, y + h - 1, col);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
    }

    // ---------- shared screen chrome ----------
    /** Soft wash over the game: lighter than before so the world stays visible behind the glass. */
    public static void backdrop(DrawContext c, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.world == null) {
            menuBackground(c, w, h); // menus without a world: the night-sky backdrop instead of a black void
            return;
        }
        c.fillGradient(0, 0, w, h, 0x7A06080F, 0x4A06080F);
    }

    /** The main-menu night sky, a little darker so lists and panels read on top of it. */
    public static void menuBackground(DrawContext c, int w, int h) {
        TempestTitleScreen.drawBackdrop(c, w, h, MenuKit.time());
        c.fill(0, 0, w, h, 0x50050409);
    }

    /** Glass pane: translucent fill, a faint sheen across the top and a hairline highlight. */
    public static void glass(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        panel(c, x, y, w, h, r, fill, border);
        roundRectV(c, x + 1, y + 1, w - 2, Math.min(h - 2, 46), Math.max(0, r - 1), withAlpha(0xFFFFFF, 0x12), withAlpha(0xFFFFFF, 0x00));
        hairline(c, x + r + 6, y + 1, w - 2 * r - 12, 0xFFFFFF, 0x34);
    }

    /** Full window: backdrop, halo, glass panel and a quiet header with title and subtitle. */
    public static void window(DrawContext c, TextRenderer tr, int sw, int sh, int x, int y, int w, int h, int head, String title, String sub) {
        backdrop(c, sw, sh);
        TempestLoading.drawGlow(c, x + w / 2, y + h / 2, Math.round(Math.max(w, h) * 1.6f), accent(), 0.16f);
        shadow(c, x, y, w, h, 10, 14);
        glass(c, x, y, w, h, 10, bg(), border());
        roundRectV(c, x + 1, y + 1, w - 2, head, 9, header(), withAlpha(t()[3], 0x20));
        c.fill(x + 12, y + head, x + w - 12, y + head + 1, border());
        hairline(c, x + 18, y + 1, w - 36, accent(), 0x70);
        TempestLoading.drawLogo(c, x + 14, y + (head - 22) / 2, 22);
        Draw.text(c, tr, bold(title), x + 44, y + (sub == null ? (head - 8) / 2 : 10), TEXT, false);
        if (sub != null) Draw.text(c, tr, sub, x + 44, y + 23, DIM, false);
    }

    /** Standard button: soft translucent gradient with a light top edge; brighter and slightly lifted on hover. */
    public static void button(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String label, boolean hover, int rgb) {
        int base = 0xFF000000 | rgb;
        float p = hoverProgress("button:" + label + ":" + x + ":" + y, hover);
        int top = withAlpha(lerpColor(lerpColor(base, 0xFFFFFFFF, 0.10f), lerpColor(base, 0xFFFFFFFF, 0.24f), p), Math.round(0xC0 + 0x30 * p));
        int bot = withAlpha(lerpColor(lerpColor(base, 0xFF000000, 0.18f), base, p), Math.round(0xB0 + 0x40 * p));
        if (p > 0.02f) roundRect(c, x - 1, y - 1, w + 2, h + 2, 6, withAlpha(base, Math.round(0x30 * p)));
        roundRectV(c, x, y, w, h, 5, top, bot);
        hairline(c, x + 4, y, w - 8, 0xFFFFFF, Math.round(0x38 + 0x20 * p));
        Draw.text(c, tr, label, x + (w - Draw.textW(tr, label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
    }

    /** Muted secondary button: a glass chip that picks up the accent on hover. */
    public static void ghost(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String label, boolean hover) {
        float p = hoverProgress("ghost:" + label + ":" + x + ":" + y, hover);
        panel(c, x, y, w, h, 5, lerpColor(card(), cardHover(), p), lerpColor(border(), withAlpha(accent(), 0x90), p));
        Draw.text(c, tr, label, x + (w - Draw.textW(tr, label)) / 2, y + (h - 8) / 2, lerpColor(DIM, TEXT, p), false);
    }

    // ---------- ambient background (title screen & friends) ----------
    private static final float[][] MOTES = new float[48][];
    static {
        java.util.Random rnd = new java.util.Random(7331);
        for (int i = 0; i < MOTES.length; i++) {
            MOTES[i] = new float[]{rnd.nextFloat(), rnd.nextFloat(), 0.4f + rnd.nextFloat() * 1.6f, 0.012f + rnd.nextFloat() * 0.035f, rnd.nextFloat() * 6.28f};
        }
    }

    /** Slow animated backdrop: deep gradient, drifting coloured glows and rising dust. Cheap: a gradient, 4 textures, ~48 dots. */
    public static void ambient(DrawContext c, int w, int h, float time) {
        int acc = accent();
        c.fillGradient(0, 0, w, h, 0xFF080A12, 0xFF0D0F1B);
        // drifting glows
        float s = Math.max(w, h);
        TempestLoading.drawGlow(c, (int) (w * (0.22f + 0.06f * Math.sin(time * 0.17f))), (int) (h * (0.30f + 0.05f * Math.cos(time * 0.13f))), Math.round(s * 1.1f), acc, 0.30f);
        TempestLoading.drawGlow(c, (int) (w * (0.80f + 0.05f * Math.cos(time * 0.11f))), (int) (h * (0.72f + 0.06f * Math.sin(time * 0.19f))), Math.round(s * 1.0f), lerpColor(acc, 0xFF4E7CFF, 0.65f), 0.24f);
        TempestLoading.drawGlow(c, (int) (w * (0.55f + 0.08f * Math.sin(time * 0.09f))), (int) (h * 0.05f), Math.round(s * 0.8f), lerpColor(acc, 0xFFFFFFFF, 0.3f), 0.10f);
        // rising dust
        for (float[] m : MOTES) {
            float y = (m[1] - time * m[3]) % 1f;
            if (y < 0) y += 1f;
            float x = m[0] + 0.015f * (float) Math.sin(time * 0.4f + m[4]);
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 1.3f + m[4] * 3f);
            int a = (int) (0x16 + 0x40 * tw * Math.min(1f, y * 5f) * Math.min(1f, (1f - y) * 5f));
            int px = (int) (x * w), py = (int) (y * h), sz = m[2] > 1.4f ? 2 : 1;
            c.fill(px, py, px + sz, py + sz, withAlpha(lerpColor(acc, 0xFFFFFFFF, 0.6f), a));
        }
        // vignette
        c.fillGradient(0, 0, w, Math.max(1, h / 6), 0x70000000, 0x00000000);
        c.fillGradient(0, h - Math.max(1, h / 5), w, h, 0x00000000, 0x90000000);
    }

    public static void scrollbar(DrawContext c, int x, int y, int viewH, double max, double scroll) {
        if (max <= 0) return;
        int thumbH = Math.max(24, (int) (viewH * (viewH / (viewH + max))));
        int thumbY = y + (int) ((viewH - thumbH) * (scroll / max));
        roundRect(c, x, thumbY, 3, thumbH, 1, withAlpha(0xFFFFFF, 0x28));
    }

    // ---------- widgets ----------
    /** 28x14 animated switch (p = 0..1). Soft track that glows when on, white knob with a faint halo. */
    public static void toggle(DrawContext c, int x, int y, float p) {
        if (p > 0.02f) roundRect(c, x - 2, y - 2, 32, 18, 9, withAlpha(accent(), Math.round(0x26 * p)));
        roundRect(c, x, y, 28, 14, 7, lerpColor(off(), withAlpha(accent(), 0xE6), p));
        int kx = x + 7 + Math.round(p * 14);
        circle(c, kx, y + 7, 6, withAlpha(0x000000, 0x30));
        circle(c, kx, y + 7, 5, lerpColor(0xFFC3C7D6, 0xFFFFFFFF, p));
    }

    public static void toggle(DrawContext c, int x, int y, boolean on) { toggle(c, x, y, on ? 1f : 0f); }

    /** Small pill button; hover = 0..1. */
    public static void pill(DrawContext c, int x, int y, int w, int h, int base, int hover, float t) {
        roundRect(c, x, y, w, h, Math.min(h / 2, 8), lerpColor(base, hover, t));
    }
}
