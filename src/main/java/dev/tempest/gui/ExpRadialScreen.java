package dev.tempest.gui;

import dev.tempest.TempestClient;
import dev.tempest.module.Category;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * EXPERIMENTAL Right Shift menu: a radial wheel over the game. Hold Right Shift, point at a category and let go to
 * open it. A quick tap keeps the wheel open so you can click instead. The Tempest logo sits in the middle and shows
 * the pointed-at category and how many of its mods are on.
 */
public class ExpRadialScreen extends Screen {
    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private int hovered = -1;

    public ExpRadialScreen(Screen parent) {
        super(Text.literal("Tempest"));
        this.parent = parent;
    }

    private int n() { return ExpKit.CATS.length; }
    private int cx() { return width / 2; }
    private int cy() { return height / 2; }
    private float radius() { return Math.min(height * 0.30f, 92f); }
    private int orbSize() { return Math.max(22, Math.round(radius() * 0.36f)); }

    private float intro() {
        if (!dev.tempest.modules.ClientSettingsModule.animationsOn()) return 1f;
        return MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.22f);
    }

    private int pick(double mx, double my) {
        double dx = mx - cx(), dy = my - cy();
        if (dx * dx + dy * dy < Math.pow(radius() * 0.45, 2)) return -1;     // dead zone in the middle
        double a = Math.atan2(dy, dx) + Math.PI / 2;                           // 0 = straight up
        if (a < 0) a += Math.PI * 2;
        return (int) Math.round(a / (Math.PI * 2 / n())) % n();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() { MenuBlur.restore(); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float e = intro(), t = MenuKit.time();
        ExpKit.overGame(ctx, width, height, 0.35f, t, e);
        hovered = pick(mx, my);
        float R = radius() * (0.85f + 0.15f * e);
        int ring = Math.round(R + orbSize() * 0.9f);

        // the wheel: outer disc, a lit wedge toward the pointed-at category, inner hub
        Draw.panel(ctx, cx() - ring, cy() - ring, ring * 2, ring * 2, ring, MenuKit.fa(0x96120E22, e), MenuKit.fa(0x34FFFFFF, e));
        if (hovered >= 0) {
            double a = -Math.PI / 2 + hovered * Math.PI * 2 / n();
            int hx = cx() + (int) Math.round(Math.cos(a) * R), hy = cy() + (int) Math.round(Math.sin(a) * R);
            TempestLoading.drawGlow(ctx, hx, hy, Math.round(orbSize() * 4f), Draw.accent(), 0.45f * e);
        }
        int hub = Math.round(R * 0.58f);
        Draw.panel(ctx, cx() - hub, cy() - hub, hub * 2, hub * 2, hub, MenuKit.fa(0xE00A0818, e), MenuKit.fa(0x28FFFFFF, e));

        for (int i = 0; i < n(); i++) {
            double a = -Math.PI / 2 + i * Math.PI * 2 / n();
            int s = orbSize();
            float hp = Draw.hoverProgress("expr_" + i, i == hovered);
            int size = Math.round(s * (1f + 0.18f * hp));
            int x = cx() + (int) Math.round(Math.cos(a) * R) - size / 2, y = cy() + (int) Math.round(Math.sin(a) * R) - size / 2;
            ExpKit.orb(ctx, x, y, size, Icons.forCategory(ExpKit.CATS[i]), hp, e);
        }

        // centre: logo + the pointed-at category
        int ls = Math.round(hub * 0.9f);
        TempestLoading.drawLogo(ctx, cx() - ls / 2, cy() - ls / 2 - Math.round(hub * 0.22f), ls);
        String name = hovered >= 0 ? ExpKit.title(ExpKit.CATS[hovered]) : "Tempest";
        String sub = hovered >= 0 ? ExpKit.countLine(ExpKit.CATS[hovered]) : "Point at a category";
        Draw.text(ctx, tr, name, cx() - Draw.textW(tr, name) / 2, cy() + Math.round(hub * 0.38f), MenuKit.fa(0xFFFFFFFF, e), false);
        MenuKit.scaledCentered(ctx, tr, Draw.uiText(sub), cx(), cy() + Math.round(hub * 0.38f) + 11, 0.75f, MenuKit.fa(0xC0D6CFF2, e), false);

        String hint = "Hold Right Shift, point, let go to open   or click";
        Draw.text(ctx, tr, hint, cx() - Draw.textW(tr, hint) / 2, height - 16, MenuKit.fa(0x90FFFFFF, e), false);
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void open(int i) {
        if (client == null || i < 0) return;
        Category c = ExpKit.CATS[i];
        client.setScreen(new ExpModsScreen(c, parent, ""));
    }

    /** Letting go of the menu key opens what you point at (a quick tap leaves the wheel open). */
    @Override
    public boolean keyReleased(KeyInput input) {
        if (TempestClient.openMenuKey != null && TempestClient.openMenuKey.matchesKey(input)) {
            boolean tap = (System.nanoTime() - openedAt) < 250_000_000L;
            if (!tap) {
                if (hovered >= 0) open(hovered);
                else close();
            }
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (TempestClient.openMenuKey != null && TempestClient.openMenuKey.matchesKey(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        int i = pick(click.x(), click.y());
        if (i >= 0) { open(i); return true; }
        double dx = click.x() - cx(), dy = click.y() - cy();
        if (dx * dx + dy * dy > Math.pow(radius() + orbSize() * 0.9f, 2)) { close(); return true; }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
