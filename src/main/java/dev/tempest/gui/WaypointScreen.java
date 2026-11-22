package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.module.ColorSetting;
import dev.tempest.modules.StreamerModeModule;
import dev.tempest.modules.WaypointsModule;
import dev.tempest.util.Waypoint;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class WaypointScreen extends Screen {
    private static final int ROW_H = 34, PITCH = 38, HEAD = 44, FOOT = 44;
    private final Screen parent;
    private int pX, pY, pW, pH;
    private double scroll = 0, target = 0;
    private long last = System.nanoTime();

    public WaypointScreen(Screen parent) {
        super(Text.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pW = Math.min(width - 20, 400);
        pH = Math.min(height - 20, 300);
        pX = (width - pW) / 2;
        pY = (height - pH) / 2;
    }

    private int listTop() { return pY + HEAD + 4; }
    private int listH() { return pH - HEAD - FOOT - 8; }
    private double max() { return Math.max(0, TempestConfig.get().waypoints.size() * PITCH - listH()); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1_000_000_000f);
        last = now;
        target = Math.max(0, Math.min(max(), target));
        scroll += (target - scroll) * (1.0 - Math.exp(-dt * 13.0));
        if (Math.abs(target - scroll) < 0.08) scroll = target;

        TextRenderer tr = textRenderer;
        List<Waypoint> list = TempestConfig.get().waypoints;
        boolean hide = StreamerModeModule.hideCoords();

        Draw.window(ctx, tr, width, height, pX, pY, pW, pH, HEAD, "Waypoints", list.size() + " saved");

        Draw.button(ctx, tr, pX + pW - 96, pY + 10, 82, 24, "+ Add Here", Draw.hit(mx, my, pX + pW - 96, pY + 10, 82, 24), Draw.accent() & 0xFFFFFF);

        ctx.enableScissor(pX + 1, listTop(), pX + pW - 1, listTop() + listH());
        if (list.isEmpty()) {
            Draw.centered(ctx, tr, "No waypoints yet", pX + pW / 2, listTop() + 40, Draw.DIM);
            Draw.centered(ctx, tr, "Press Add Here or bind a key in Options", pX + pW / 2, listTop() + 54, Draw.DIM2);
        }
        for (int i = 0; i < list.size(); i++) {
            Waypoint wp = list.get(i);
            int ry = listTop() + i * PITCH - (int) Math.round(scroll);
            if (ry + ROW_H < listTop() || ry > listTop() + listH()) continue;
            int rx = pX + 12, rw = pW - 24;
            boolean hv = Draw.hit(mx, my, rx, ry, rw, ROW_H) && my >= listTop() && my < listTop() + listH();
            float rowHover = Draw.hoverProgress("waypoint-row-" + i, hv);
            int bg = Draw.lerpColor(Draw.card(), Draw.cardHover(), rowHover);
            int border = Draw.lerpColor(Draw.border(), Draw.withAlpha(wp.color, 0xFF), rowHover * 0.35f);
            Draw.panel(ctx, rx, ry, rw, ROW_H, 7, bg, border);
            Draw.roundRect(ctx, rx + 4, ry + 6, 2, ROW_H - 12, 1, Draw.withAlpha(wp.color, 0xCC));
            Draw.circle(ctx, rx + 20, ry + ROW_H / 2, 8, Draw.withAlpha(wp.color, 0x28));
            Draw.circle(ctx, rx + 20, ry + ROW_H / 2, 6, wp.color);

            Draw.text(ctx, tr, Text.literal(Draw.trim(tr, wp.name, rw - 160)).formatted(Formatting.BOLD), rx + 36, ry + 7, Draw.TEXT, false);
            String coords = hide ? "coordinates hidden" : wp.x + ", " + wp.y + ", " + wp.z;
            String dim = wp.dim == null ? "" : wp.dim.replace("minecraft:", "");
            Draw.text(ctx, tr, coords + "  ·  " + dim, rx + 36, ry + 19, Draw.DIM, false);

            Draw.button(ctx, tr, rx + rw - 66, ry + 7, 56, 20, "Delete", Draw.hit(mx, my, rx + rw - 66, ry + 7, 56, 20), 0xE0475B);
        }
        ctx.disableScissor();

        Draw.scrollbar(ctx, pX + pW - 7, listTop(), listH(), max(), scroll);

        ctx.fill(pX + 1, pY + pH - FOOT, pX + pW - 1, pY + pH - FOOT + 1, Draw.border());
        int dx = pX + pW / 2 - 50, dy = pY + pH - FOOT + 10;
        Draw.ghost(ctx, tr, dx, dy, 100, 24, "Done", Draw.hit(mx, my, dx, dy, 100, 24));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        List<Waypoint> list = TempestConfig.get().waypoints;

        if (Draw.hit(mx, my, pX + pW - 96, pY + 10, 82, 24)) {
            WaypointsModule.addHere(client);
            return true;
        }
        if (Draw.hit(mx, my, pX + pW / 2 - 50, pY + pH - FOOT + 10, 100, 24)) {
            close();
            return true;
        }
        if (my >= listTop() && my < listTop() + listH()) {
            for (int i = 0; i < list.size(); i++) {
                Waypoint wp = list.get(i);
                int ry = listTop() + i * PITCH - (int) Math.round(scroll);
                int rx = pX + 12, rw = pW - 24;
                if (Draw.hit(mx, my, rx + rw - 66, ry + 7, 56, 20)) {
                    list.remove(i);
                    TempestConfig.save();
                    return true;
                }
                if (Draw.hit(mx, my, rx + 8, ry + ROW_H / 2 - 8, 16, 16)) {
                    int idx = 0;
                    for (int k = 0; k < ColorSetting.PALETTE.length; k++) if (ColorSetting.PALETTE[k] == wp.color) idx = k;
                    wp.color = ColorSetting.PALETTE[(idx + 1) % ColorSetting.PALETTE.length];
                    TempestConfig.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        target = Math.max(0, Math.min(max(), target - vertical * 30));
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        TempestConfig.save();
        if (client != null) client.setScreen(parent);
    }
}
