package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Drag HUD elements with snapping and alignment guides (S / G toggles, Shift = free), arrows nudge, scroll to resize, R to reset. */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudModule dragged;
    private HudModule selected;
    private int offX, offY, lastMx, lastMy;
    private final List<Integer> guidesX = new ArrayList<>(), guidesY = new ArrayList<>();
    private static boolean snapOn = true, gridOn = false;
    private static final int GRID = 8, SNAP = 6, GAP = 2, MARGIN = 4;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Edit HUD"));
        this.parent = parent;
    }

    private List<HudModule> enabled() {
        List<HudModule> out = new ArrayList<>();
        for (HudModule m : ModuleManager.huds()) if (m.isEnabled()) out.add(m);
        return out;
    }

    private HudModule hoveredAt(int mx, int my) {
        List<HudModule> l = enabled();
        for (int i = l.size() - 1; i >= 0; i--) {
            HudModule m = l.get(i);
            if (Draw.hit(mx, my, m.x, m.y, Math.max(m.sw, 10), Math.max(m.sh, 10))) return m;
        }
        return null;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        lastMx = mx;
        lastMy = my;
        TextRenderer tr = textRenderer;

        ctx.fill(0, 0, width, height, 0x70000000);
        for (int gx = 16; gx < width; gx += 16) for (int gy = 16; gy < height; gy += 16) ctx.fill(gx, gy, gx + 1, gy + 1, 0x22FFFFFF);
        for (int yy = 0; yy < height; yy += 8) ctx.fill(width / 2, yy, width / 2 + 1, yy + 4, 0x30FFFFFF);
        for (int xx = 0; xx < width; xx += 8) ctx.fill(xx, height / 2, xx + 4, height / 2 + 1, 0x30FFFFFF);
        if (gridOn) {
            for (int gx = 0; gx < width; gx += GRID) ctx.fill(gx, 0, gx + 1, height, 0x0EFFFFFF);
            for (int gy = 0; gy < height; gy += GRID) ctx.fill(0, gy, width, gy + 1, 0x0EFFFFFF);
        }
        if (dragged != null) {
            int g = Draw.withAlpha(Draw.accent(), 0xC0), glow = Draw.withAlpha(Draw.accent(), 0x30);
            for (int gx : guidesX) { ctx.fill(gx - 1, 0, gx + 2, height, glow); ctx.fill(gx, 0, gx + 1, height, g); }
            for (int gy : guidesY) { ctx.fill(0, gy - 1, width, gy + 2, glow); ctx.fill(0, gy, width, gy + 1, g); }
        }

        HudModule hovered = dragged == null ? hoveredAt(mx, my) : null;
        for (HudModule m : enabled()) {
            m.render(ctx, true);
            boolean sel = m == dragged || (dragged == null && m == selected);
            boolean hv = m == hovered;
            if (sel || hv) {
                int col = sel ? Draw.accent() : Draw.withAlpha(Draw.accent(), 0x99);
                Draw.outline(ctx, m.x - 2, m.y - 2, m.sw + 4, m.sh + 4, col);
                if (sel) {
                    int[][] corners = {{m.x - 3, m.y - 3}, {m.x + m.sw, m.y - 3}, {m.x - 3, m.y + m.sh}, {m.x + m.sw, m.y + m.sh}};
                    for (int[] c : corners) { ctx.fill(c[0] - 1, c[1] - 1, c[0] + 5, c[1] + 5, Draw.accent()); ctx.fill(c[0], c[1], c[0] + 4, c[1] + 4, 0xFFFFFFFF); }
                }
                String tag = m.getName() + (sel ? "   " + m.x + ", " + m.y : "");
                int tw = Draw.textW(tr, tag) + 12;
                int ty = m.y - 19 < 2 ? m.y + m.sh + 5 : m.y - 19;
                Draw.roundRect(ctx, m.x - 2, ty, tw, 14, 5, Draw.accent());
                Draw.text(ctx, tr, tag, m.x + 4, ty + 3, 0xFFFFFFFF, false);
            } else {
                Draw.outline(ctx, m.x - 1, m.y - 1, m.sw + 2, m.sh + 2, 0x30FFFFFF);
            }
        }

        // title + hint pills
        Text title = Text.literal("HUD EDITOR").formatted(Formatting.BOLD);
        int tw = Draw.textW(tr, title) + 24;
        Draw.shadow(ctx, width / 2 - tw / 2, 10, tw, 20, 8, 4);
        Draw.panel(ctx, width / 2 - tw / 2, 10, tw, 20, 8, Draw.bg(), Draw.withAlpha(Draw.accent(), 0xC0));
        Draw.text(ctx, tr, title, width / 2 - Draw.textW(tr, title) / 2, 16, Draw.accent(), false);

        // snap / grid toggles
        int ty2 = height - 60;
        toggle(ctx, tr, "Snap  (S)", snapOn, width / 2 - 82, ty2, mx, my);
        toggle(ctx, tr, "Grid  (G)", gridOn, width / 2 + 4, ty2, mx, my);

        String hint = "Drag to move    Shift = no snap    Arrows nudge    Scroll resize    R reset    Esc done";
        int hw = Draw.textW(tr, hint) + 28;
        int hy = height - 34;
        Draw.shadow(ctx, width / 2 - hw / 2, hy, hw, 22, 8, 4);
        Draw.panel(ctx, width / 2 - hw / 2, hy, hw, 22, 8, Draw.bg(), Draw.border());
        ctx.fill(width / 2 - hw / 2 + 8, hy + 1, width / 2 + hw / 2 - 8, hy + 2, Draw.withAlpha(Draw.accent(), 0x80));
        Draw.text(ctx, tr, hint, width / 2 - Draw.textW(tr, hint) / 2, hy + 7, Draw.DIM, false);
    }

    private static final int TOG_W = 78, TOG_H = 18;

    private void toggle(DrawContext ctx, TextRenderer tr, String label, boolean on, int x, int y, int mx, int my) {
        boolean hv = Draw.hit(mx, my, x, y, TOG_W, TOG_H);
        float p = Draw.hoverProgress("hudtog" + label, hv);
        int fill = on ? Draw.withAlpha(Draw.accent(), 0x60 + Math.round(0x30 * p)) : Draw.lerpColor(0xB0101118, 0xC8202230, p);
        Draw.panel(ctx, x, y, TOG_W, TOG_H, TOG_H / 2, fill, on ? Draw.withAlpha(Draw.accent(), 0xE0) : Draw.border());
        Draw.circle(ctx, x + 10, y + TOG_H / 2, 3, on ? 0xFFFFFFFF : 0x60FFFFFF);
        Draw.text(ctx, tr, label, x + 18, y + 5, on ? 0xFFFFFFFF : Draw.DIM, false);
    }

    private boolean shiftDown() {
        try {
            long h = client.getWindow().getHandle();
            return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Snapping: screen edges (with a small margin) and centre, and other elements - their edges, centres, and
     * "next to it with a 2px gap". Falls back to the grid when Grid is on. Every alignment that matches the final
     * position gets a guide line. Hold Shift to place freely.
     */
    private int[] snap(HudModule m, int nx, int ny) {
        guidesX.clear();
        guidesY.clear();
        if (!snapOn || shiftDown()) return new int[]{nx, ny};
        // candidate positions for the element's left edge (x) / top edge (y), each with the line to draw
        List<int[]> cx = new ArrayList<>(), cy = new ArrayList<>();
        cx.add(new int[]{MARGIN, MARGIN});
        cx.add(new int[]{width - MARGIN - m.sw, width - MARGIN});
        cx.add(new int[]{width / 2 - m.sw / 2, width / 2});
        cy.add(new int[]{MARGIN, MARGIN});
        cy.add(new int[]{height - MARGIN - m.sh, height - MARGIN});
        cy.add(new int[]{height / 2 - m.sh / 2, height / 2});
        for (HudModule o : enabled()) {
            if (o == m) continue;
            int ox2 = o.x + o.sw, oy2 = o.y + o.sh;
            cx.add(new int[]{o.x, o.x});                       // left edges line up
            cx.add(new int[]{ox2 - m.sw, ox2});                // right edges line up
            cx.add(new int[]{o.x + o.sw / 2 - m.sw / 2, o.x + o.sw / 2});
            cx.add(new int[]{ox2 + GAP, ox2 + GAP});           // just right of it
            cx.add(new int[]{o.x - GAP - m.sw, o.x - GAP});    // just left of it
            cy.add(new int[]{o.y, o.y});
            cy.add(new int[]{oy2 - m.sh, oy2});
            cy.add(new int[]{o.y + o.sh / 2 - m.sh / 2, o.y + o.sh / 2});
            cy.add(new int[]{oy2 + GAP, oy2 + GAP});           // stacked below it
            cy.add(new int[]{o.y - GAP - m.sh, o.y - GAP});    // stacked above it
        }
        int sx = pick(cx, nx), sy = pick(cy, ny);
        if (sx == Integer.MIN_VALUE) sx = gridOn ? Math.round(nx / (float) GRID) * GRID : nx;
        if (sy == Integer.MIN_VALUE) sy = gridOn ? Math.round(ny / (float) GRID) * GRID : ny;
        for (int[] c : cx) if (c[0] == sx && !guidesX.contains(c[1])) guidesX.add(c[1]);
        for (int[] c : cy) if (c[0] == sy && !guidesY.contains(c[1])) guidesY.add(c[1]);
        return new int[]{sx, sy};
    }

    private static int pick(List<int[]> candidates, int v) {
        int best = SNAP + 1, out = Integer.MIN_VALUE;
        for (int[] c : candidates) {
            int d = Math.abs(c[0] - v);
            if (d < best) { best = d; out = c[0]; }
        }
        return out;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int ty2 = height - 60;
            if (Draw.hit(click.x(), click.y(), width / 2 - 82, ty2, TOG_W, TOG_H)) { snapOn = !snapOn; return true; }
            if (Draw.hit(click.x(), click.y(), width / 2 + 4, ty2, TOG_W, TOG_H)) { gridOn = !gridOn; return true; }
            HudModule m = hoveredAt((int) click.x(), (int) click.y());
            if (m != null) {
                dragged = m;
                selected = m;
                offX = (int) click.x() - m.x;
                offY = (int) click.y() - m.y;
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragged != null) {
            int nx = MathHelper.clamp((int) click.x() - offX, 0, Math.max(0, width - dragged.sw));
            int ny = MathHelper.clamp((int) click.y() - offY, 0, Math.max(0, height - dragged.sh));
            int[] s = snap(dragged, nx, ny);
            dragged.setPos(MathHelper.clamp(s[0], 0, Math.max(0, width - dragged.sw)),
                    MathHelper.clamp(s[1], 0, Math.max(0, height - dragged.sh)));
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragged = null;
        guidesX.clear();
        guidesY.clear();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        HudModule m = dragged != null ? dragged : hoveredAt((int) mouseX, (int) mouseY);
        if (m == null) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        m.scaleSetting().set(m.scaleSetting().get() + Math.signum(vertical) * 0.1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_S) { snapOn = !snapOn; return true; }
        if (k == GLFW.GLFW_KEY_G) { gridOn = !gridOn; return true; }
        HudModule target = dragged != null ? dragged : selected != null && selected.isEnabled() ? selected : hoveredAt(lastMx, lastMy);
        if (target != null && (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_RIGHT || k == GLFW.GLFW_KEY_UP || k == GLFW.GLFW_KEY_DOWN)) {
            int step = shiftDown() ? 5 : 1;
            int dx = k == GLFW.GLFW_KEY_LEFT ? -step : k == GLFW.GLFW_KEY_RIGHT ? step : 0;
            int dy = k == GLFW.GLFW_KEY_UP ? -step : k == GLFW.GLFW_KEY_DOWN ? step : 0;
            target.setPos(MathHelper.clamp(target.x + dx, 0, Math.max(0, width - target.sw)),
                    MathHelper.clamp(target.y + dy, 0, Math.max(0, height - target.sh)));
            selected = target;
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_R) {
            HudModule m = dragged != null ? dragged : hoveredAt(lastMx, lastMy);
            if (m != null) { m.resetPos(); return true; }
        }
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }

    @Override
    public void removed() { TempestConfig.save(); }
}
