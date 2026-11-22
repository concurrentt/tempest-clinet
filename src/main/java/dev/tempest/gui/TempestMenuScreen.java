package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.module.ModuleManager;
import dev.tempest.modules.CapesModule;
import dev.tempest.modules.CosmeticsModule;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Right Shift menu: the Tempest logo and wordmark over the game, with one row of buttons underneath:
 * [emotes] [ MODS ] [cosmetics], and shortcut chips (Edit HUD, Custom Sky, Settings). The world stays visible behind it.
 */
public class TempestMenuScreen extends Screen {
    private static final String[] CAPES = {"None", "Tempest", "Stormcloud", "Voltage", "Ember", "Frostbite",
            "Aurora", "Galaxy", "Sakura", "Inferno", "Ocean", "Void", "Royal", "Synthwave"};
    private static final int MID_W = 200, BTN_H = 40, SIDE = 40, GAP = 6;

    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private int panel = 0; // 0 none, 1 emotes, 2 cosmetics

    public TempestMenuScreen(Screen parent) {
        super(Text.literal("Tempest Client"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- layout
    private static final String[][] CHIPS = {{"hud", "layout", "Edit HUD"}, {"sky", "planet_stars", "Custom Sky"}, {"theme", "palette", "Theme"}, {"settings", "gear", "Settings"}};
    private static final int CHIP_W = 74, CHIP_H = 20;

    private int logoSize() { return Math.max(64, Math.min(104, Math.round(height * 0.15f))); }
    private int baseLogoY() { return Math.max(10, Math.round(height * 0.27f) - logoSize() / 2 - 10); }
    private float lift;   // how far everything slides up so an open panel fits on small screens
    private int logoY() { return baseLogoY() - Math.round(lift); }

    private void updateLift() {
        int panelH = panel == 2 ? PANEL_H + 18 : panel == 1 ? 88 : 0;
        int panelTopBase = baseLogoY() + logoSize() + 132;
        float target = panelH == 0 ? 0f : Math.max(0f, panelTopBase + panelH + 10 - height);
        lift += (target - lift) * 0.25f;
        if (Math.abs(target - lift) < 0.5f) lift = target;
    }
    private int titleY() { return logoY() + logoSize() + 8; }
    private int rowY() { return titleY() + 42; }
    private int rowX() { return width / 2 - (SIDE + GAP + MID_W + GAP + SIDE) / 2; }
    private int midX() { return rowX() + SIDE + GAP; }
    private int rightX() { return midX() + MID_W + GAP; }
    private int chipsY() { return rowY() + BTN_H + 10; }
    private int chipX(int i) { return width / 2 - (CHIPS.length * CHIP_W + (CHIPS.length - 1) * GAP) / 2 + i * (CHIP_W + GAP); }
    private int panelTop() { return chipsY() + CHIP_H + 12; }

    private float intro() { return intro(0f); }

    private float intro(float delay) {
        return ClientSettingsAnim.on() ? MenuKit.ease(((System.nanoTime() - openedAt) / 1_000_000_000f - delay) / 0.35f) : 1f;
    }

    // ---------------------------------------------------------------- render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        updateLift();
        float e = intro();
        if (client != null && client.world == null) Draw.menuBackground(ctx, width, height);
        else {
            // darken the world, most of all behind the menu
            ctx.fillGradient(0, 0, width, height, MenuKit.fa(0x70000000, e), MenuKit.fa(0x50000000, e));
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
        sideButton(ctx, tr, "runner", "EMOTES", rowX(), ry, mx, my, panel == 1, eb);
        modsButton(ctx, tr, midX(), ry, mx, my, eb);
        sideButton(ctx, tr, "shirt", "LOOKS", rightX(), ry, mx, my, panel == 2, eb);

        float ec = intro(0.16f);
        int cy = chipsY() + Math.round((1f - ec) * 8);
        for (int i = 0; i < CHIPS.length; i++) chip(ctx, tr, i, chipX(i), cy, mx, my, ec);

        if (panel == 1) drawEmotesPanel(ctx, tr, cx, panelTop());
        if (panel == 2) drawCosmeticsPanel(ctx, tr, mx, my, cx, panelTop());

        float ef = intro(0.25f);
        String hint = "Right Shift or Esc to close";
        Draw.text(ctx, tr, hint, cx - tr.getWidth(hint) / 2, height - 16, MenuKit.fa(0x90FFFFFF, ef), false);

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
        int tw = Draw.spacedW(tr, "MODS", true, 3), cw = tr.getWidth(count) + 10, is = 14;
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
        boolean hv = Draw.hit(mx, my, x, y, CHIP_W, CHIP_H);
        float p = Draw.hoverProgress("tm_chip" + i, hv);
        Draw.panel(ctx, x, y, CHIP_W, CHIP_H, CHIP_H / 2, MenuKit.fa(Draw.lerpColor(0x8015161C, 0xB8242838, p), e),
                MenuKit.fa(Draw.lerpColor(0x26FFFFFF, Draw.withAlpha(Draw.accent(), 0xA0), p), e));
        String label = CHIPS[i][2];
        int is = 9, total = is + 5 + tr.getWidth(label);
        int sx = x + (CHIP_W - total) / 2;
        int col = MenuKit.fa(Draw.lerpColor(0xFFB8BED0, 0xFFFFFFFF, p), e);
        Icons.draw(ctx, CHIPS[i][1], sx, y + (CHIP_H - is) / 2, is, col);
        Draw.text(ctx, tr, label, sx + is + 5, y + (CHIP_H - 8) / 2, col, false);
    }

    private void drawEmotesPanel(DrawContext ctx, TextRenderer tr, int cx, int top) {
        int w = 260, h = 70, x = cx - w / 2;
        Draw.panel(ctx, x, top, w, h, 6, 0xC0141519, 0x40FFFFFF);
        Icons.draw(ctx, "runner", cx - 7, top + 12, 14, 0xFFFFFFFF);
        Draw.centered(ctx, tr, "Emotes are coming soon", cx, top + 36, 0xFFD0D3DC);
        Draw.centered(ctx, tr, "Stay tuned", cx, top + 50, 0xFF8B90A0);
    }

    // ---------------------------------------------------------------- cosmetics: preview + carousel
    private static final String[] TABS = {"Capes", "Wings", "Head", "Aura"};
    private static final int PANEL_W = 470, PANEL_H = 196, PREV_W = 132, CARD = 108;
    private int tab = 0;
    private int focus = -1;          // carousel index being looked at (-1 = start on the equipped one)
    private float slide = 0f;        // animated carousel position
    private long lastFrame;

    private String[] tabOptions() {
        return switch (tab) {
            case 1 -> CosmeticsModule.WINGS;
            case 2 -> CosmeticsModule.HEADS;
            case 3 -> CosmeticsModule.AURAS;
            default -> CAPES;
        };
    }

    private String tabSelected() {
        CosmeticsModule cm = ModuleManager.get(CosmeticsModule.class);
        CapesModule cp = ModuleManager.get(CapesModule.class);
        return switch (tab) {
            case 1 -> cm == null || !cm.isEnabled() ? "None" : cm.wings.get();
            case 2 -> cm == null || !cm.isEnabled() ? "None" : cm.head.get();
            case 3 -> cm == null || !cm.isEnabled() ? "None" : cm.aura.get();
            default -> cp == null || !cp.isEnabled() ? "None" : cp.cape.get();
        };
    }

    private int selectedIndex() {
        String[] o = tabOptions();
        String sel = tabSelected();
        for (int i = 0; i < o.length; i++) if (o[i].equalsIgnoreCase(sel)) return i;
        return 0;
    }

    private static String optionIcon(int tab, String opt) {
        if (opt.equals("None")) return "power";
        return switch (tab) {
            case 1 -> "wings";
            case 2 -> switch (opt) {
                case "Halo" -> "halo"; case "Devil Horns" -> "horns"; case "Crown" -> "crown"; case "Bunny Ears" -> "bunny";
                case "Antlers" -> "antlers"; case "Flower Crown" -> "flower"; case "Witch Hat" -> "witch_hat"; case "Top Hat" -> "top_hat";
                default -> "cat_ears";
            };
            case 3 -> switch (opt) {
                case "Hearts" -> "heart"; case "Flames" -> "flame"; case "Snow" -> "snowflake"; case "Notes" -> "note";
                case "Theme Orbit" -> "orbs"; default -> "sparkles";
            };
            default -> "cape";
        };
    }

    private int panelX() { return width / 2 - PANEL_W / 2; }
    private int tabX(int i) { return panelX() + PREV_W + 22 + i * 70; }
    private int carX() { return panelX() + PREV_W + 16; }
    private int carW() { return PANEL_W - PREV_W - 28; }
    private int carCx() { return carX() + carW() / 2; }
    private int carY(int top) { return top + 38; }

    /** While browsing, the focused option is tried on (so the preview and F5 show it). */
    private void applyPreview() {
        CosmeticsModule.clearPreview();
        if (panel != 2 || focus < 0) return;
        String o = tabOptions()[focus];
        switch (tab) {
            case 1 -> CosmeticsModule.previewWings = o;
            case 2 -> CosmeticsModule.previewHead = o;
            case 3 -> CosmeticsModule.previewAura = o;
            default -> CapesModule.previewCape = o;
        }
    }

    private void drawCosmeticsPanel(DrawContext ctx, TextRenderer tr, int mx, int my, int cx, int top) {
        String[] opts = tabOptions();
        if (focus < 0 || focus >= opts.length) { focus = selectedIndex(); slide = focus; applyPreview(); }
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        slide += (focus - slide) * (1f - (float) Math.exp(-dt * 12f));
        if (Math.abs(focus - slide) < 0.002f) slide = focus;

        int x = panelX();
        Draw.shadow(ctx, x, top, PANEL_W, PANEL_H, 8, 6);
        Draw.panel(ctx, x, top, PANEL_W, PANEL_H, 8, 0xD0101218, 0x40FFFFFF);

        // --- turntable preview
        int px = x + 10, py = top + 10, ph = PANEL_H - 20;
        Draw.roundRectV(ctx, px, py, PREV_W, ph, 6, Draw.withAlpha(Draw.lerpColor(0xFF15182A, Draw.accent(), 0.18f), 0xF0), 0xF00A0B10);
        TempestLoading.drawGlow(ctx, px + PREV_W / 2, py + ph - 26, PREV_W + 20, Draw.accent(), 0.30f);
        Draw.roundRect(ctx, px + PREV_W / 2 - 34, py + ph - 24, 68, 6, 3, Draw.withAlpha(Draw.accent(), 0x40));
        drawTurntable(ctx, px, py + 6, PREV_W, ph - 18);
        String hint = client != null && client.player != null ? "Previewing" : "Join a world to preview";
        Draw.text(ctx, tr, hint, px + PREV_W / 2 - tr.getWidth(hint) / 2, py + ph - 12, 0xA0FFFFFF, false);

        // --- tabs
        for (int i = 0; i < TABS.length; i++) {
            boolean sel = i == tab, hv = Draw.hit(mx, my, tabX(i), top + 10, 64, 20);
            float p = Draw.hoverProgress("tm_tab" + i, hv || sel);
            Draw.panel(ctx, tabX(i), top + 10, 64, 20, 10, sel ? Draw.withAlpha(Draw.accent(), 0xD0) : Draw.lerpColor(0x5A26272C, 0x8A3A3C44, p),
                    sel ? 0x60FFFFFF : Draw.lerpColor(0x30FFFFFF, 0x80FFFFFF, p));
            Draw.spaced(ctx, tr, TABS[i].toUpperCase(java.util.Locale.ROOT), tabX(i) + 32, top + 16, 0xFFFFFFFF, true, 1);
        }

        // --- carousel
        int ccx = carCx(), cy = carY(top);
        int equipped = selectedIndex();
        ctx.enableScissor(carX(), cy - 6, carX() + carW(), cy + CARD + 8);
        for (int pass = 0; pass < 2; pass++) {                 // far cards first, the focused one on top
            for (int i = 0; i < opts.length; i++) {
                float d = i - slide;
                float ad = Math.abs(d);
                if (ad > 2.6f || (pass == 0) != (ad >= 0.5f)) continue;
                float scale = 1f - Math.min(1f, ad) * 0.26f - Math.max(0f, ad - 1f) * 0.12f;
                float alpha = Math.max(0f, 1f - ad * 0.38f);
                int cw = Math.round(CARD * scale), chh = Math.round(CARD * scale);
                int cardX = Math.round(ccx + d * 86f - cw / 2f), cardY = cy + (CARD - chh) / 2;
                boolean isFocus = i == focus, isEq = i == equipped;
                boolean hv = Draw.hit(mx, my, cardX, cardY, cw, chh);
                int fill = isFocus ? Draw.lerpColor(0xFF1A1D2A, Draw.accent(), 0.22f) : 0xFF15171F;
                if (isFocus) Draw.shadow(ctx, cardX, cardY, cw, chh, 8, 5);
                Draw.panel(ctx, cardX, cardY, cw, chh, 8, MenuKit.fa(fill, alpha), MenuKit.fa(isFocus ? Draw.accent() : hv ? 0x90FFFFFF : 0x30FFFFFF, alpha));
                int is = Math.round(38 * scale);
                Icons.draw(ctx, optionIcon(tab, opts[i]), cardX + (cw - is) / 2, cardY + Math.round(18 * scale), is,
                        MenuKit.fa(opts[i].equals("None") ? 0x90FFFFFF : 0xFFFFFFFF, alpha));
                if (ad < 1.4f) {
                    String label = Draw.ellipsize(tr, opts[i], cw - 8);
                    Draw.text(ctx, tr, label, cardX + (cw - tr.getWidth(label)) / 2, cardY + chh - 30, MenuKit.fa(0xFFFFFFFF, alpha), false);
                    if (isEq) {
                        String eq = "EQUIPPED";
                        int bw = Draw.spacedW(tr, eq, true, 1) + 10;
                        Draw.roundRect(ctx, cardX + (cw - bw) / 2, cardY + chh - 17, bw, 11, 5, MenuKit.fa(0xFF2FA866, alpha));
                        ctx.getMatrices().pushMatrix();
                        ctx.getMatrices().translate(cardX + cw / 2f, cardY + chh - 15);
                        ctx.getMatrices().scale(0.75f, 0.75f);
                        Draw.spaced(ctx, tr, eq, 0, 0, MenuKit.fa(0xFFFFFFFF, alpha), true, 1);
                        ctx.getMatrices().popMatrix();
                    } else if (isFocus) {
                        String c = "Click to equip";
                        Draw.text(ctx, tr, c, cardX + (cw - tr.getWidth(c)) / 2, cardY + chh - 16, MenuKit.fa(0xB0FFFFFF, alpha), false);
                    }
                }
            }
        }
        ctx.disableScissor();

        // arrows
        int ay = cy + CARD / 2 - 10;
        arrow(ctx, tr, carX() + 2, ay, "<", mx, my, focus > 0);
        arrow(ctx, tr, carX() + carW() - 22, ay, ">", mx, my, focus < opts.length - 1);

        // dots
        int dw = opts.length * 8;
        for (int i = 0; i < opts.length; i++) {
            int dx = ccx - dw / 2 + i * 8;
            Draw.roundRect(ctx, dx, cy + CARD + 10, i == focus ? 6 : 4, 4, 2, i == focus ? Draw.accent() : 0x50FFFFFF);
        }
        Draw.centered(ctx, tr, "Only you can see your cosmetics", cx, top + PANEL_H + 6, 0xFF8B90A0);
    }

    private void arrow(DrawContext ctx, TextRenderer tr, int x, int y, String s, int mx, int my, boolean enabled) {
        boolean hv = enabled && Draw.hit(mx, my, x, y, 20, 20);
        Draw.panel(ctx, x, y, 20, 20, 10, hv ? 0xD0303448 : 0xA0181A22, enabled ? (hv ? Draw.accent() : 0x50FFFFFF) : 0x20FFFFFF);
        Draw.text(ctx, tr, s, x + 10 - tr.getWidth(s) / 2, y + 6, enabled ? 0xFFFFFFFF : 0x50FFFFFF, false);
    }

    /** Your player, turning slowly like a shop turntable, wearing the focused option. */
    private void drawTurntable(DrawContext ctx, int x, int y, int w, int h) {
        if (client == null || client.player == null) {
            String[] opts = tabOptions();
            int f = Math.max(0, Math.min(opts.length - 1, focus));
            Icons.draw(ctx, optionIcon(tab, opts[f]), x + w / 2 - 24, y + h / 2 - 34, 48, 0xE0FFFFFF);
            return;
        }
        try {
            var state = client.getEntityRenderDispatcher().getAndUpdateRenderState(client.player, 1f);
            if (state instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState ls) {
                ls.bodyYaw = 0f;
                ls.relativeHeadYaw = 0f;
                ls.pitch = 0f;
            }
            state.light = 0xF000F0;
            state.outlineColor = 0;
            float angle = (float) (MenuKit.time() * 0.9);
            // tabs that sit on the back (capes, wings) turn around further so the back faces you more often
            org.joml.Quaternionf rot = new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateY(angle);
            float size = Math.min(w, h) * 0.42f;
            org.joml.Vector3f tr = new org.joml.Vector3f(0f, client.player.getHeight() / 2f + 0.0625f, 0f);
            ctx.addEntity(state, size, tr, rot, null, x, y, x + w, y + h);
        } catch (Throwable ignored) { }
    }

    private boolean clickCosmetics(double mx, double my) {
        int top = panelTop();
        for (int i = 0; i < TABS.length; i++) {
            if (Draw.hit(mx, my, tabX(i), top + 10, 64, 20)) {
                tab = i;
                focus = -1;
                CosmeticsModule.clearPreview();
                return true;
            }
        }
        String[] opts = tabOptions();
        int cy = carY(top), ay = cy + CARD / 2 - 10;
        if (Draw.hit(mx, my, carX() + 2, ay, 20, 20)) { move(-1); return true; }
        if (Draw.hit(mx, my, carX() + carW() - 22, ay, 20, 20)) { move(1); return true; }
        // the focused card equips; a side card is brought to the middle
        for (int i = 0; i < opts.length; i++) {
            float d = i - slide;
            if (Math.abs(d) > 2.6f) continue;
            float scale = 1f - Math.min(1f, Math.abs(d)) * 0.26f - Math.max(0f, Math.abs(d) - 1f) * 0.12f;
            int cw = Math.round(CARD * scale);
            int cardX = Math.round(carCx() + d * 86f - cw / 2f), cardY = cy + (CARD - cw) / 2;
            if (!Draw.hit(mx, my, cardX, cardY, cw, cw)) continue;
            if (i != focus) { focus = i; applyPreview(); return true; }
            equip(opts[i]);
            return true;
        }
        return Draw.hit(mx, my, panelX(), top, PANEL_W, PANEL_H);   // a click inside the panel never closes it
    }

    private void move(int dir) {
        int n = tabOptions().length;
        focus = Math.max(0, Math.min(n - 1, focus + dir));
        applyPreview();
    }

    private void equip(String o) {
        CosmeticsModule cm = ModuleManager.get(CosmeticsModule.class);
        CapesModule cp = ModuleManager.get(CapesModule.class);
        switch (tab) {
            case 1 -> { if (cm != null) { cm.wings.set(o); if (!cm.isEnabled()) cm.setEnabled(true); } }
            case 2 -> { if (cm != null) { cm.head.set(o); if (!cm.isEnabled()) cm.setEnabled(true); } }
            case 3 -> { if (cm != null) { cm.aura.set(o); if (!cm.isEnabled()) cm.setEnabled(true); } }
            default -> { if (cp != null) { cp.cape.set(o); if (!cp.isEnabled() && !o.equals("None")) cp.setEnabled(true); } }
        }
        TempestConfig.save();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (panel == 2) {
            int k = input.key();
            if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) { move(-1); return true; }
            if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) { move(1); return true; }
            if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && focus >= 0) { equip(tabOptions()[focus]); return true; }
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        CosmeticsModule.clearPreview();
        super.removed();
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int ry = rowY();
        if (Draw.hit(mx, my, midX(), ry, MID_W, BTN_H)) {
            if (client != null) client.setScreen(new ClickGuiScreen(this));
            return true;
        }
        if (Draw.hit(mx, my, rowX(), ry, SIDE, BTN_H)) { panel = panel == 1 ? 0 : 1; return true; }
        if (Draw.hit(mx, my, rightX(), ry, SIDE, BTN_H)) {
            panel = panel == 2 ? 0 : 2;
            focus = -1;
            CosmeticsModule.clearPreview();
            return true;
        }
        for (int i = 0; i < CHIPS.length; i++) {
            if (!Draw.hit(mx, my, chipX(i), chipsY(), CHIP_W, CHIP_H) || client == null) continue;
            switch (CHIPS[i][0]) {
                case "hud" -> client.setScreen(new HudEditorScreen(this));
                case "sky" -> client.setScreen(ClickGuiScreen.openTo(dev.tempest.module.Category.VISUALS, this));
                case "theme" -> client.setScreen(new ThemeScreen(this));
                default -> client.setScreen(ClickGuiScreen.openTo(dev.tempest.module.Category.CLIENT, this));
            }
            return true;
        }

        if (panel == 2 && clickCosmetics(mx, my)) return true;
        if (panel != 0) { panel = 0; CosmeticsModule.clearPreview(); return true; }
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
