package dev.tempest.gui;

import dev.tempest.module.ModuleManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Right Shift menu: the Tempest logo and wordmark over the game, with one row of buttons underneath:
 * [emotes] [ MODS ] [looks] (both open CosmeticsScreen), and shortcut chips (Edit HUD, Custom Sky, Theme, Textures,
 * Social, Settings). The world stays visible behind it.
 */
public class TempestMenuScreen extends Screen {
    private static final int MID_W = 200, BTN_H = 40, SIDE = 40, GAP = 6;

    private final Screen parent;
    private final long openedAt = System.nanoTime();

    public TempestMenuScreen(Screen parent) {
        super(Text.literal("Tempest Client"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- layout
    private static final String[][] CHIPS = {{"hud", "layout", "Edit HUD"}, {"sky", "planet_stars", "Custom Sky"}, {"theme", "palette", "Theme"},
            {"editor", "brush", "Editor"}, {"social", "people", "Social"}, {"settings", "gear", "Settings"}};
    private static final int CHIP_H = 20, CHIP_PAD = 9, CHIP_ICON = 9, CHIP_GAP = 5;

    private int logoSize() { return Math.max(64, Math.min(104, Math.round(height * 0.15f))); }
    private int baseLogoY() { return Math.max(10, Math.round(height * 0.27f) - logoSize() / 2 - 10); }
    private int logoY() { return baseLogoY(); }
    private int titleY() { return logoY() + logoSize() + 8; }
    private int rowY() { return titleY() + 42; }
    private int rowX() { return width / 2 - (SIDE + GAP + MID_W + GAP + SIDE) / 2; }
    private int midX() { return rowX() + SIDE + GAP; }
    private int rightX() { return midX() + MID_W + GAP; }
    private int chipsY() { return rowY() + BTN_H + 10; }
    /** Each chip is as wide as its icon and label plus even padding, so short and long labels both fit snugly. */
    private int chipW(int i) { return CHIP_PAD + CHIP_ICON + 5 + Draw.textW(textRenderer, CHIPS[i][2]) + CHIP_PAD; }
    private int chipX(int i) {
        int total = (CHIPS.length - 1) * CHIP_GAP;
        for (int k = 0; k < CHIPS.length; k++) total += chipW(k);
        int x = width / 2 - total / 2;
        for (int k = 0; k < i; k++) x += chipW(k) + CHIP_GAP;
        return x;
    }

    private float intro() { return intro(0f); }

    private float intro(float delay) {
        return ClientSettingsAnim.on() ? MenuKit.ease(((System.nanoTime() - openedAt) / 1_000_000_000f - delay) / 0.35f) : 1f;
    }

    // ---------------------------------------------------------------- render
    /** The menu draws its own background (render: blur and wash). */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() { MenuBlur.restore(); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float e = intro();
        if (client != null && client.world == null) {
            Draw.menuBackground(ctx, width, height);
            MenuBlur.apply(ctx);
        } else {
            // blur the world (GUI & Configs -> Menu Blur), then darken it - less when blurred - most of all behind the menu
            boolean blurred = MenuBlur.apply(ctx);
            ctx.fillGradient(0, 0, width, height, MenuKit.fa(blurred ? 0x48000000 : 0x70000000, e), MenuKit.fa(blurred ? 0x30000000 : 0x50000000, e));
            TempestLoading.drawGlow(ctx, width / 2, rowY(), Math.round(Math.max(width, height) * 1.1f), 0x000000, 0.45f * e);
        }

        int cx = width / 2;
        float time = MenuKit.time();
        int ls = logoSize(), ly = logoY() + Math.round((1f - e) * -8);
        float breathe = 0.5f + 0.5f * (float) Math.sin(time * 1.2f);
        TempestLoading.drawGlow(ctx, cx, ly + ls / 2, Math.round(ls * (2.7f + 0.2f * breathe)), Draw.accent(), 0.22f * e);
        TempestLoading.drawLogo(ctx, cx - ls / 2, ly, ls);

        float et = intro(0.05f);
        int ty = titleY() + Math.round((1f - et) * 6);
        TempestTitleScreen.drawWordmark(ctx, cx, ty, 20, MenuKit.fa(0xFFFFFFFF, et));
        Draw.spaced(ctx, tr, "CLIENT", cx, ty + 25, MenuKit.fa(Draw.lerpColor(0xFFB8BED8, Draw.accent(), 0.45f), et), false, 3);

        float eb = intro(0.10f);
        int ry = rowY() + Math.round((1f - eb) * 10);
        sideButton(ctx, tr, "emote_wave", "EMOTES", rowX(), ry, mx, my, false, eb);
        modsButton(ctx, tr, midX(), ry, mx, my, eb);
        sideButton(ctx, tr, "shirt", "LOOKS", rightX(), ry, mx, my, false, eb);

        float ec = intro(0.16f);
        int cy = chipsY() + Math.round((1f - ec) * 8);
        for (int i = 0; i < CHIPS.length; i++) chip(ctx, tr, i, chipX(i), cy, mx, my, ec);


        float ef = intro(0.25f);
        String hint = "Right Shift or Esc to close";
        Draw.text(ctx, tr, hint, cx - Draw.textW(tr, hint) / 2, height - 16, MenuKit.fa(0x90FFFFFF, ef), false);
        ExpKit.styleButton(ctx, tr, width, mx, my);

        super.render(ctx, mx, my, delta);
    }

    /** Glass frame shared by every button in the row. */
    private static void frame(DrawContext ctx, int x, int y, int w, int h, float hover, boolean active, float e) {
        if (hover > 0.01f) Draw.shadow(ctx, x, y, w, h, 10, Math.round(5 * hover));
        int fill = active ? Draw.lerpColor(0xB0202436, 0xC8283048, hover) : Draw.lerpColor(0x9A15161C, 0xC2222533, hover);
        int border = active ? Draw.withAlpha(Draw.accent(), 0xE0) : Draw.lerpColor(0x3CFFFFFF, Draw.withAlpha(Draw.accent(), 0xB0), hover);
        Draw.panel(ctx, x, y, w, h, 10, MenuKit.fa(fill, e), MenuKit.fa(border, e));
        ctx.fill(x + 10, y + 1, x + w - 10, y + 2, MenuKit.fa(Draw.withAlpha(0xFFFFFF, 0x12 + Math.round(0x16 * hover)), e));
    }

    private boolean sideButton(DrawContext ctx, TextRenderer tr, String icon, String label, int x, int y, int mx, int my, boolean active, float e) {
        boolean hv = Draw.hit(mx, my, x, y, SIDE, BTN_H);
        float p = Draw.hoverProgress("tm_side_" + icon, hv || active);
        frame(ctx, x, y, SIDE, BTN_H, p, active, e);
        int s = 16;
        Icons.draw(ctx, icon, x + (SIDE - s) / 2, y + 7, s, MenuKit.fa(Draw.lerpColor(0xC8FFFFFF, 0xFFFFFFFF, p), e));
        // tiny caption under the icon
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x + SIDE / 2f, y + BTN_H - 11);
        ctx.getMatrices().scale(0.5f, 0.5f);
        Draw.spaced(ctx, tr, label, 0, 0, MenuKit.fa(Draw.lerpColor(0x90FFFFFF, 0xFFFFFFFF, p), e), true, 1);
        ctx.getMatrices().popMatrix();
        return hv;
    }

    private boolean modsButton(DrawContext ctx, TextRenderer tr, int x, int y, int mx, int my, float e) {
        boolean hv = Draw.hit(mx, my, x, y, MID_W, BTN_H);
        float p = Draw.hoverProgress("tm_mods", hv);
        frame(ctx, x, y, MID_W, BTN_H, p, false, e);
        String count = Integer.toString(ModuleManager.all().size());
        int tw = Draw.spacedW(tr, "MODS", true, 3), cw = Draw.textW(tr, count) + 10, is = 14;
        int total = is + 8 + tw + 8 + cw;
        int sx = x + (MID_W - total) / 2, cy = y + (BTN_H - 8) / 2;
        Icons.draw(ctx, "grid", sx, y + (BTN_H - is) / 2, is, MenuKit.fa(Draw.lerpColor(Draw.accent(), 0xFFFFFFFF, 0.35f + 0.4f * p), e));
        Draw.spaced(ctx, tr, "MODS", sx + is + 8 + tw / 2, cy, MenuKit.fa(0xFFFFFFFF, e), true, 3);
        int bx = sx + is + 8 + tw + 8;
        Draw.roundRect(ctx, bx, y + (BTN_H - 14) / 2, cw, 14, 5, MenuKit.fa(Draw.withAlpha(Draw.accent(), 0x55 + Math.round(0x40 * p)), e));
        Draw.text(ctx, tr, count, bx + 5, cy, MenuKit.fa(0xFFFFFFFF, e), false);
        return hv;
    }

    private void chip(DrawContext ctx, TextRenderer tr, int i, int x, int y, int mx, int my, float e) {
        int w = chipW(i);
        boolean hv = Draw.hit(mx, my, x, y, w, CHIP_H);
        float p = Draw.hoverProgress("tm_chip" + i, hv);
        Draw.panel(ctx, x, y, w, CHIP_H, CHIP_H / 2, MenuKit.fa(Draw.lerpColor(0x8015161C, 0xB8242838, p), e),
                MenuKit.fa(Draw.lerpColor(0x26FFFFFF, Draw.withAlpha(Draw.accent(), 0xA0), p), e));
        String label = CHIPS[i][2];
        int is = CHIP_ICON;
        int sx = x + CHIP_PAD;
        int col = MenuKit.fa(Draw.lerpColor(0xFFB8BED0, 0xFFFFFFFF, p), e);
        Icons.draw(ctx, CHIPS[i][1], sx, y + (CHIP_H - is) / 2, is, col);
        Draw.text(ctx, tr, label, sx + is + 5, y + (CHIP_H - 8) / 2, col, false);
        // Social: a red "+N" badge on the corner while friend requests are waiting
        int pending = "social".equals(CHIPS[i][0]) ? dev.tempest.util.Social.pendingCount() : 0;
        if (pending > 0) {
            String b = "+" + Math.min(pending, 99);
            int bw = Draw.textW(tr, b) + 8, bh = 11;
            int bx = x + w - bw + 4, by = y - 5;
            Draw.panel(ctx, bx, by, bw, bh, bh / 2, MenuKit.fa(0xFFE5484D, e), MenuKit.fa(0xFF15161C, e));
            Draw.text(ctx, tr, b, bx + 4, by + 2, MenuKit.fa(0xFFFFFFFF, e), false);
        }
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (ExpKit.clickStyleButton(this, parent, mx, my)) return true;
        int ry = rowY();
        if (Draw.hit(mx, my, midX(), ry, MID_W, BTN_H)) {
            if (client != null) client.setScreen(new ClickGuiScreen(this));
            return true;
        }
        if (Draw.hit(mx, my, rowX(), ry, SIDE, BTN_H)) {
            if (client != null) client.setScreen(new CosmeticsScreen(this, CosmeticsScreen.EMOTES));
            return true;
        }
        if (Draw.hit(mx, my, rightX(), ry, SIDE, BTN_H)) {
            if (client != null) client.setScreen(new CosmeticsScreen(this, CosmeticsScreen.CAPES));
            return true;
        }
        for (int i = 0; i < CHIPS.length; i++) {
            if (!Draw.hit(mx, my, chipX(i), chipsY(), chipW(i), CHIP_H) || client == null) continue;
            switch (CHIPS[i][0]) {
                case "hud" -> client.setScreen(new HudEditorScreen(this));
                case "sky" -> client.setScreen(ClickGuiScreen.openTo(dev.tempest.module.Category.VISUALS, this));
                case "theme" -> client.setScreen(new ThemeScreen(this));
                case "social" -> client.setScreen(new SocialScreen(this));
                case "editor" -> client.setScreen(new TempestEditorScreen(this));
                default -> client.setScreen(ClickGuiScreen.openTo(dev.tempest.module.Category.CLIENT, this));
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    /** Respects the "Menu Animations" setting without pulling the settings module into every call. */
    private static final class ClientSettingsAnim {
        static boolean on() { return dev.tempest.modules.ClientSettingsModule.animationsOn(); }
    }
}
