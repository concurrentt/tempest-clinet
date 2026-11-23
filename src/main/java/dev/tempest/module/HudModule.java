package dev.tempest.module;

import dev.tempest.TempestConfig;
import dev.tempest.gui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** A module that draws a movable, scalable, styleable box on the HUD. */
public abstract class HudModule extends Module {
    /** Logical position and size (before scale). */
    public int x, y, w = 60, h = 14;
    /** On-screen size (after scale), used by the HUD editor. */
    public int sw = 60, sh = 14;
    private final int defX, defY;
    private final Map<String, SmoothMetric> smoothMetrics = new HashMap<>();
    private float visualWidth = -1f, visualHeight = -1f;
    private long lastBoxFrame;

    private record SmoothMetric(double value, long time) { }

    /** Frame-rate-independent easing for live HUD values such as FPS, ping and TPS. */
    protected double smoothMetric(String key, double target, float speed) {
        long now = System.nanoTime();
        SmoothMetric old = smoothMetrics.get(key);
        if (old == null || !Double.isFinite(target)) {
            if (Double.isFinite(target)) smoothMetrics.put(key, new SmoothMetric(target, now));
            return target;
        }
        float dt = Math.min(0.10f, Math.max(0.001f, (now - old.time()) / 1_000_000_000f));
        double value = old.value() + (target - old.value()) * (1.0 - Math.exp(-dt * speed));
        if (Math.abs(target - value) < 0.04) value = target;
        smoothMetrics.put(key, new SmoothMetric(value, now));
        return value;
    }

    protected final BoolSetting background = add(new BoolSetting("background", "Background", true));
    protected final ModeSetting style = add(new ModeSetting("style", "Box Style", "Modern", "Modern", "Glass", "Flat", "Outline"));
    protected final NumberSetting opacity = add(new NumberSetting("opacity", "Background Opacity", 56, 0, 100, 5));
    protected final ColorSetting color = add(new ColorSetting("color", "Text Color", 0xFFFFFFFF));
    protected final NumberSetting scale = add(new NumberSetting("scale", "Scale", 1.0, 0.5, 2.0, 0.1));

    protected HudModule(String id, String name, String desc, Category cat, boolean def, int dx, int dy) {
        super(id, name, desc, cat, def);
        this.defX = dx;
        this.defY = dy;
        int[] p = TempestConfig.get().hud.get(id);
        if (p != null && p.length == 2) { x = p[0]; y = p[1]; } else { x = dx; y = dy; }
    }

    public void setPos(int nx, int ny) {
        x = nx;
        y = ny;
        TempestConfig.get().hud.put(getId(), new int[]{nx, ny});
    }

    public void resetPos() { setPos(defX, defY); }

    public NumberSetting scaleSetting() { return scale; }

    @Override
    public void reloadSettings() {
        super.reloadSettings();
        int[] p = TempestConfig.get().hud.get(getId());
        if (p != null && p.length == 2) { x = p[0]; y = p[1]; }
    }

    protected int alpha() { return (int) Math.round(opacity.get() * 2.55); }
    protected int bg() { return alpha() << 24; }

    /** Draws the element's background in the chosen style, using x, y, w, h. */
    protected void box(DrawContext ctx) {
        long now = System.nanoTime();
        float dt = lastBoxFrame == 0 ? 1f : Math.min(0.10f, Math.max(0.001f, (now - lastBoxFrame) / 1_000_000_000f));
        lastBoxFrame = now;
        if (visualWidth < 0f) { visualWidth = w; visualHeight = h; }
        float ease = 1f - (float) Math.exp(-dt * 16f);
        visualWidth += (w - visualWidth) * ease;
        visualHeight += (h - visualHeight) * ease;
        if (Math.abs(w - visualWidth) < 0.25f) visualWidth = w;
        if (Math.abs(h - visualHeight) < 0.25f) visualHeight = h;
        int bw = Math.max(1, Math.round(visualWidth));
        int bh = Math.max(1, Math.round(visualHeight));
        if (!background.get()) return;
        int a = alpha();
        if (a <= 0) return;
        switch (style.get()) {
            case "Flat" -> ctx.fill(x, y, x + bw, y + bh, Draw.withAlpha(0x0A1233, a));
            case "Outline" -> {
                ctx.fill(x, y, x + bw, y + bh, Draw.withAlpha(0x0A1233, a / 2));
                Draw.outline(ctx, x, y, bw, bh, Draw.withAlpha(Draw.accent(), 0xC0));
            }
            case "Glass" -> {
                Draw.roundRectV(ctx, x, y, bw, bh, 4, Draw.withAlpha(0x2A3A78, a), Draw.withAlpha(0x0A1233, a));
                ctx.fill(x + 3, y, x + bw - 3, y + 1, Draw.withAlpha(0xFFFFFF, 0x38));
            }
            default -> {
                // Modern: soft shadow, a dark surface with a faint top-lit gradient, a hairline edge and a
                // highlight along the top, like a small piece of smoked glass.
                Draw.shadow(ctx, x, y, bw, bh, 5, 3);
                Draw.roundRectV(ctx, x, y, bw, bh, 5, Draw.withAlpha(0x1A1D2A, a), Draw.withAlpha(0x0B0C12, a));
                Draw.ring(ctx, x, y, bw, bh, 5, Draw.withAlpha(0xFFFFFF, Math.max(0x0C, a / 9)));
                ctx.fill(x + 5, y + 1, x + bw - 5, y + 2, Draw.withAlpha(0xFFFFFF, Math.max(0x08, a / 7)));
            }
        }
    }

    /** Draws with the per-element scale applied. */
    public final void render(DrawContext ctx, boolean preview) {
        float s = (float) scale.get();
        int rx = x, ry = y;
        if (s != 1f) {
            x = Math.round(rx / s);
            y = Math.round(ry / s);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().scale(s, s);
        }
        try {
            renderHud(ctx, preview);
        } finally {
            if (s != 1f) ctx.getMatrices().popMatrix();
            x = rx;
            y = ry;
        }
        sw = Math.round(w * s);
        sh = Math.round(h * s);
    }

    /** @param preview true inside the HUD editor: draw sample data. Use x, y, w, h (logical units). */
    protected abstract void renderHud(DrawContext ctx, boolean preview);

    /** Horizontal padding between the box edge / accent bar and the text. */
    protected static final int PAD_X = 8;

    protected void drawLines(DrawContext ctx, List<String> lines) { drawLines(ctx, lines, null); }

    /**
     * Shared layout for text HUDs. A line written as "Label: value" is split into a dim label column and a
     * bright value column (so FPS, Ping, CPS ... all read the same way); a line without ": " is drawn whole.
     * cols (optional) tints each line's value.
     */
    protected void drawLines(DrawContext ctx, List<String> lines, List<Integer> cols) { drawLines(ctx, lines, cols, null); }

    /**
     * Same, with a picture in front of the lines (64x64 textures, e.g. a server icon or a resource pack's icon).
     * One line gets one large icon; several lines get a small icon each. Null entries leave a gap.
     */
    protected void drawLines(DrawContext ctx, List<String> lines, List<Integer> cols, List<net.minecraft.util.Identifier> icons) {
        if (icons != null && icons.stream().anyMatch(java.util.Objects::nonNull)) { drawWithIcons(ctx, lines, cols, icons); return; }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int labelW = 0, tw = 0;
        String[] label = new String[lines.size()], value = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int cut = l.indexOf(": ");
            if (cut > 0) { label[i] = l.substring(0, cut); value[i] = l.substring(cut + 2); }
            else { label[i] = null; value[i] = l; }
            if (label[i] != null) labelW = Math.max(labelW, tr.getWidth(label[i]));
        }
        int gap = labelW > 0 ? 6 : 0;
        for (int i = 0; i < lines.size(); i++) {
            int lw = label[i] != null ? labelW + gap : 0;
            tw = Math.max(tw, lw + tr.getWidth(value[i]));
        }
        boolean modern = background.get() && style.get().equals("Modern");
        int lead = modern ? 7 + 8 + 5 : PAD_X;      // icon column in the Modern style
        int lineH = 11;
        w = lead + tw + 7;
        h = lines.size() * lineH + 9;
        box(ctx);
        if (modern) {
            int iy = y + (lines.size() == 1 ? (h - 8) / 2 : 5);
            dev.tempest.gui.Icons.draw(ctx, dev.tempest.gui.Icons.forModule(getId()), x + 7, iy, 8, Draw.accent());
        } else if (background.get()) {
            ctx.fill(x + 1, y + 4, x + 3, y + h - 4, Draw.accent());
        }
        for (int i = 0; i < lines.size(); i++) {
            int ty = y + 5 + i * lineH;
            int vx = x + lead;
            if (label[i] != null) {
                ctx.drawText(tr, label[i], vx, ty, Draw.DIM, false);
                vx += labelW + gap;
            }
            ctx.drawText(tr, value[i], vx, ty, cols != null ? cols.get(i) : color.get(), modern);
        }
    }

    private void drawWithIcons(DrawContext ctx, List<String> lines, List<Integer> cols, List<net.minecraft.util.Identifier> icons) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        boolean big = lines.size() == 1;
        int is = big ? 16 : 9, lineH = big ? 11 : 12;
        int labelW = 0, tw = 0;
        String[] label = new String[lines.size()], value = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int cut = l.indexOf(": ");
            if (cut > 0) { label[i] = l.substring(0, cut); value[i] = l.substring(cut + 2); }
            else { label[i] = null; value[i] = l; }
            if (label[i] != null) labelW = Math.max(labelW, tr.getWidth(label[i]));
        }
        int gap = labelW > 0 ? 6 : 0;
        for (int i = 0; i < lines.size(); i++) tw = Math.max(tw, (label[i] != null ? labelW + gap : 0) + tr.getWidth(value[i]));
        boolean modern = background.get() && style.get().equals("Modern");
        int lead = 6 + is + 6;
        w = lead + tw + 7;
        h = big ? is + 8 : lines.size() * lineH + 7;
        box(ctx);
        if (background.get() && !modern) ctx.fill(x + 1, y + 4, x + 3, y + h - 4, Draw.accent());
        for (int i = 0; i < lines.size(); i++) {
            int rowY = big ? y + 4 : y + 4 + i * lineH;
            net.minecraft.util.Identifier icon = i < icons.size() ? icons.get(i) : null;
            if (icon != null) {
                int iy = big ? rowY : rowY + (lineH - 1 - is) / 2;
                ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, icon, x + 6, iy, 0f, 0f, is, is, 64, 64, 64, 64, 0xFFFFFFFF);
            }
            int ty = big ? y + (h - 8) / 2 : rowY + 1;
            int vx = x + lead;
            if (label[i] != null) {
                ctx.drawText(tr, label[i], vx, ty, Draw.DIM, false);
                vx += labelW + gap;
            }
            ctx.drawText(tr, value[i], vx, ty, cols != null ? cols.get(i) : color.get(), modern);
        }
    }
}
