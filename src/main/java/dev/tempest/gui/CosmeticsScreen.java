package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.module.ModuleManager;
import dev.tempest.modules.CapesModule;
import dev.tempest.modules.CosmeticsModule;
import dev.tempest.util.Emotes;
import dev.tempest.util.TempestLive;
import dev.tempest.util.TempestNet;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Cosmetics: a big turntable preview of you (drag to turn, scroll to zoom) beside a grid of capes, wings, head
 * pieces and emotes. Hover to try something on, click to wear it (or play the emote). Your choices are shown to
 * other Tempest players unless you switch sharing off at the bottom.
 */
public class CosmeticsScreen extends Screen {
    public static final int CAPES = 0, WINGS = 1, HEAD = 2, EMOTES = 3;
    private static final String[] TABS = {"Capes", "Wings", "Head", "Emotes"};
    private static final String[] TAB_ICONS = {"cape", "wings", "crown", "emote_wave"};
    private static final int CARD = 62, GAP = 6, HEAD_H = 30, FOOT_H = 52;

    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private int tab;
    private double scroll, scrollTarget;
    private float yaw = (float) Math.PI * 0.85f, zoom = 1f;
    private long lastInteract;
    private boolean dragging;
    private long lastFrame;

    public CosmeticsScreen(Screen parent, int tab) {
        super(Text.literal("Cosmetics"));
        this.parent = parent;
        this.tab = Math.max(0, Math.min(TABS.length - 1, tab));
        yaw = tab == HEAD || tab == EMOTES ? 0f : (float) Math.PI * 0.85f;   // back items: show the back first
    }

    // ---------------------------------------------------------------- layout
    private int winW() { return Math.min(width - 20, 620); }
    private int winH() { return Math.min(height - 20, 350); }
    private int x0() { return (width - winW()) / 2; }
    private int y0() { return (height - winH()) / 2; }
    private int prevX() { return x0() + 10; }
    private int prevY() { return y0() + HEAD_H + 8; }
    private int prevW() { return Math.round(winW() * 0.36f); }
    private int prevH() { return winH() - HEAD_H - 18; }
    private int rx() { return prevX() + prevW() + 12; }
    private int rw() { return x0() + winW() - 10 - rx(); }
    private int tabsY() { return prevY(); }
    private int gridY() { return tabsY() + 28; }
    private int gridH() { return y0() + winH() - 10 - FOOT_H - gridY(); }
    private int cols() { return Math.max(1, (rw() + GAP) / (CARD + GAP)); }
    private int gridX() { return rx() + (rw() - (cols() * (CARD + GAP) - GAP)) / 2; }
    private int tabW() { return (rw() - 3 * 4) / 4; }

    private String[] options() {
        return switch (tab) {
            case WINGS -> CosmeticsModule.WINGS;
            case HEAD -> CosmeticsModule.HEADS;
            case EMOTES -> {
                String[] o = new String[Emotes.ALL.length];
                for (int i = 0; i < o.length; i++) o[i] = Emotes.ALL[i].name();
                yield o;
            }
            default -> CapesModule.CAPES;
        };
    }

    private String equipped() {
        CosmeticsModule cm = ModuleManager.get(CosmeticsModule.class);
        CapesModule cp = ModuleManager.get(CapesModule.class);
        return switch (tab) {
            case WINGS -> cm == null || !cm.isEnabled() ? "None" : cm.wings.get();
            case HEAD -> cm == null || !cm.isEnabled() ? "None" : cm.head.get();
            case EMOTES -> {
                Emotes.Def d = Emotes.current();
                yield d == null ? "" : d.name();
            }
            default -> cp == null || !cp.isEnabled() ? "None" : cp.cape.get();
        };
    }

    private double maxScroll() {
        int rows = (options().length + cols() - 1) / cols();
        return Math.max(0, rows * (CARD + GAP) - GAP - gridH());
    }

    // ---------------------------------------------------------------- render
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float e = MenuKit.ease((now - openedAt) / 1e9f / 0.25f);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 16f));
        if (!dragging && System.currentTimeMillis() - lastInteract > 2500) yaw += dt * 0.55f;

        if (client != null && client.world == null) Draw.menuBackground(ctx, width, height);
        else ctx.fillGradient(0, 0, width, height, MenuKit.fa(0x80000000, e), MenuKit.fa(0x60000000, e));

        int x = x0(), y = y0(), w = winW(), h = winH();
        Draw.shadow(ctx, x, y, w, h, 10, 12);
        Draw.glass(ctx, x, y, w, h, 10, MenuKit.fa(0xE0101218, e), MenuKit.fa(0x40FFFFFF, e));
        MenuKit.titleBar(ctx, tr, x + 10, y + 6, "Cosmetics", null, Draw.hit(mx, my, x + 10, y + 6, 24, 24), e);
        String status = sharingLine();
        Draw.text(ctx, tr, status, x + w - 12 - tr.getWidth(status), y + 14, MenuKit.fa(0xFF8B90A0, e), false);

        // --- hover = try on
        int hovered = cardAt(mx, my);
        applyPreview(hovered);

        drawPreview(ctx, tr, mx, my, e);
        drawTabs(ctx, tr, mx, my, e);
        drawGrid(ctx, tr, mx, my, hovered, e);
        drawFooter(ctx, tr, mx, my, e);
        super.render(ctx, mx, my, delta);
    }

    private String sharingLine() {
        if (!dev.tempest.util.RemoteCosmetics.share()) return "Only you see your cosmetics";
        if (!TempestNet.signedIn()) return TempestNet.status() == null ? "Connecting…" : TempestNet.status();
        return TempestLive.connected() ? "Tempest players near you see these" : "Other Tempest players see these";
    }

    private void drawPreview(DrawContext ctx, TextRenderer tr, int mx, int my, float e) {
        int px = prevX(), py = prevY(), pw = prevW(), ph = prevH();
        Draw.roundRectV(ctx, px, py, pw, ph, 8, MenuKit.fa(Draw.withAlpha(Draw.lerpColor(0xFF15182A, Draw.accent(), 0.20f), 0xF0), e),
                MenuKit.fa(0xF00A0B10, e));
        TempestLoading.drawGlow(ctx, px + pw / 2, py + ph - 30, pw + 30, Draw.accent(), 0.32f * e);
        Draw.roundRect(ctx, px + pw / 2 - 44, py + ph - 30, 88, 8, 4, MenuKit.fa(Draw.withAlpha(Draw.accent(), 0x40), e));
        if (client == null || client.player == null) {
            String[] o = options();
            Icons.draw(ctx, TAB_ICONS[tab], px + pw / 2 - 28, py + ph / 2 - 40, 56, MenuKit.fa(0xD0FFFFFF, e));
            Draw.centered(ctx, tr, "Join a world to see", px + pw / 2, py + ph / 2 + 26, MenuKit.fa(0xFFB0B4C4, e));
            Draw.centered(ctx, tr, "yourself wearing it", px + pw / 2, py + ph / 2 + 38, MenuKit.fa(0xFFB0B4C4, e));
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
            org.joml.Quaternionf rot = new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateY(yaw);
            float size = Math.min(pw * 0.95f, ph * 0.62f) * 0.62f * zoom;
            org.joml.Vector3f t = new org.joml.Vector3f(0f, client.player.getHeight() / 2f + 0.0625f, 0f);
            ctx.addEntity(state, size, t, rot, null, px, py + 6, px + pw, py + ph - 14);
        } catch (Throwable ignored) { }
        String hint = "Drag to turn · Scroll to zoom";
        Draw.centered(ctx, tr, hint, px + pw / 2, py + ph - 12, MenuKit.fa(0x90FFFFFF, e));
    }

    private void drawTabs(DrawContext ctx, TextRenderer tr, int mx, int my, float e) {
        for (int i = 0; i < TABS.length; i++) {
            int tx = rx() + i * (tabW() + 4), ty = tabsY();
            boolean sel = i == tab, hv = Draw.hit(mx, my, tx, ty, tabW(), 22);
            float p = Draw.hoverProgress("cos_tab" + i, hv || sel);
            Draw.panel(ctx, tx, ty, tabW(), 22, 11, MenuKit.fa(sel ? Draw.withAlpha(Draw.accent(), 0xD0) : Draw.lerpColor(0x5A26272C, 0x8A3A3C44, p), e),
                    MenuKit.fa(sel ? 0x60FFFFFF : Draw.lerpColor(0x30FFFFFF, 0x80FFFFFF, p), e));
            int is = 10, lw = tr.getWidth(TABS[i]), sx = tx + (tabW() - is - 5 - lw) / 2;
            Icons.draw(ctx, TAB_ICONS[i], sx, ty + 6, is, MenuKit.fa(0xFFFFFFFF, e));
            Draw.text(ctx, tr, TABS[i], sx + is + 5, ty + 7, MenuKit.fa(0xFFFFFFFF, e), false);
        }
    }

    private int cardX(int i) { return gridX() + (i % cols()) * (CARD + GAP); }
    private int cardY(int i) { return gridY() + (i / cols()) * (CARD + GAP) - (int) Math.round(scroll); }

    private int cardAt(double mx, double my) {
        if (!Draw.hit(mx, my, rx(), gridY(), rw(), gridH())) return -1;
        String[] o = options();
        for (int i = 0; i < o.length; i++) if (Draw.hit(mx, my, cardX(i), cardY(i), CARD, CARD)) return i;
        return -1;
    }

    private void drawGrid(DrawContext ctx, TextRenderer tr, int mx, int my, int hovered, float e) {
        String[] o = options();
        String eq = equipped();
        ctx.enableScissor(rx() - 2, gridY() - 2, rx() + rw() + 2, gridY() + gridH() + 2);
        for (int i = 0; i < o.length; i++) {
            int cx = cardX(i), cy = cardY(i);
            if (cy + CARD < gridY() - 4 || cy > gridY() + gridH() + 4) continue;
            boolean hv = i == hovered, on = o[i].equalsIgnoreCase(eq);
            float p = Draw.hoverProgress("cos_card" + tab + "_" + i, hv);
            if (p > 0.02f) Draw.shadow(ctx, cx, cy, CARD, CARD, 8, Math.round(4 * p));
            int fill = on ? Draw.lerpColor(0xFF1A1D2A, Draw.accent(), 0.25f) : Draw.lerpColor(0xFF15171F, 0xFF22252F, p);
            int border = on ? Draw.accent() : Draw.lerpColor(0x30FFFFFF, 0x90FFFFFF, p);
            Draw.panel(ctx, cx, cy, CARD, CARD, 8, MenuKit.fa(fill, e), MenuKit.fa(border, e));
            drawThumb(ctx, o[i], i, cx, cy, e);
            String label = Draw.ellipsize(tr, o[i], CARD - 6);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx + CARD / 2f, cy + CARD - 11);
            ctx.getMatrices().scale(0.8f, 0.8f);
            Draw.text(ctx, tr, label, -tr.getWidth(label) / 2, 0, MenuKit.fa(on ? 0xFFFFFFFF : 0xFFD0D3DC, e), false);
            ctx.getMatrices().popMatrix();
            if (on) {
                Draw.circle(ctx, cx + CARD - 8, cy + 8, 4, MenuKit.fa(0xFF2FA866, e));
            }
        }
        ctx.disableScissor();
        MenuKit.listFades(ctx, rx(), gridY(), rw(), gridH(), scroll, maxScroll());
    }

    private void drawThumb(DrawContext ctx, String opt, int i, int cx, int cy, float e) {
        int col = MenuKit.fa(0xFFFFFFFF, e);
        if (opt.equals("None")) {
            Icons.draw(ctx, "power", cx + CARD / 2 - 12, cy + 12, 24, MenuKit.fa(0x90FFFFFF, e));
            return;
        }
        switch (tab) {
            case CAPES -> {
                // the cape's front panel (texture is 256x128; the front is 40x64 at 4,4)
                Identifier tex = Identifier.of("tempest", "textures/cape/" + opt.toLowerCase(Locale.ROOT) + ".png");
                int w = 26, h = 41;
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, cx + (CARD - w) / 2, cy + 5, 4f, 4f, w, h, 40, 64, 256, 128, col);
            }
            case WINGS -> {
                Identifier tex = Identifier.of("tempest", "textures/cosmetics/wings_" + opt.toLowerCase(Locale.ROOT) + ".png");
                int s = 44;
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, cx + (CARD - s) / 2 + 2, cy + 3, 0f, 0f, s, s, 512, 512, 512, 512, col);
            }
            case HEAD -> {
                String icon = switch (opt) {
                    case "Halo" -> "halo"; case "Devil Horns" -> "horns"; case "Crown" -> "crown"; case "Bunny Ears" -> "bunny";
                    case "Antlers" -> "antlers"; case "Flower Crown" -> "flower"; case "Witch Hat" -> "witch_hat"; case "Top Hat" -> "top_hat";
                    default -> "cat_ears";
                };
                Icons.draw(ctx, icon, cx + CARD / 2 - 15, cy + 9, 30, col);
            }
            default -> Icons.draw(ctx, "emote_" + Emotes.ALL[i].id(), cx + CARD / 2 - 17, cy + 6, 34, col);
        }
    }

    // ---------------------------------------------------------------- footer: per-tab options + sharing
    private int footY() { return y0() + winH() - 10 - FOOT_H + 6; }

    private void drawFooter(DrawContext ctx, TextRenderer tr, int mx, int my, float e) {
        int fx = rx(), fy = footY();
        ctx.fill(fx, fy - 4, fx + rw(), fy - 3, MenuKit.fa(0x18FFFFFF, e));
        CosmeticsModule cm = ModuleManager.get(CosmeticsModule.class);
        int dim = MenuKit.fa(0xFF9DA2B3, e), white = MenuKit.fa(0xFFFFFFFF, e);
        switch (tab) {
            case WINGS -> {
                if (cm != null) {
                    stepper(ctx, tr, fx, fy, "Size", String.format(Locale.ROOT, "%.2f", cm.wingSize.get()), mx, my, e);
                    stepper(ctx, tr, fx + rw() / 2, fy, "Flap", String.format(Locale.ROOT, "%.1f", cm.flapSpeed.get()), mx, my, e);
                }
            }
            case HEAD -> {
                if (cm != null && cm.head.is("Halo")) {
                    Draw.text(ctx, tr, "Halo", fx, fy + 4, dim, false);
                    for (int i = 0; i < CosmeticsModule.HALO_COLORS.length; i++) {
                        int sx = fx + 34 + i * 20, sy = fy + 8;
                        boolean sel = cm.haloColor.is(CosmeticsModule.HALO_COLORS[i]);
                        if (sel) Draw.circle(ctx, sx, sy, 8, white);
                        Draw.circle(ctx, sx, sy, 6, MenuKit.fa(0xFF000000 | CosmeticsModule.haloRgb(CosmeticsModule.HALO_COLORS[i]), e));
                    }
                } else Draw.text(ctx, tr, "Pick a halo to choose its colour", fx, fy + 4, dim, false);
            }
            case EMOTES -> {
                String k = "Click to play · in game, hold B for the wheel";
                Draw.text(ctx, tr, Draw.ellipsize(tr, k, rw()), fx, fy + 4, dim, false);
            }
            default -> Draw.text(ctx, tr, "Your cape also shows on your elytra", fx, fy + 4, dim, false);
        }
        if (cm != null) {
            toggleRow(ctx, tr, fx, fy + 24, "Show mine to others", cm.share.get(), mx, my, e);
            toggleRow(ctx, tr, fx + rw() / 2, fy + 24, "See other players'", cm.seeOthers.get(), mx, my, e);
        }
    }

    private void stepper(DrawContext ctx, TextRenderer tr, int x, int y, String label, String value, int mx, int my, float e) {
        Draw.text(ctx, tr, label, x, y + 4, MenuKit.fa(0xFF9DA2B3, e), false);
        int bx = x + 34;
        for (int k = 0; k < 2; k++) {
            int ox = bx + k * 60;
            boolean hv = Draw.hit(mx, my, ox, y, 16, 16);
            Draw.panel(ctx, ox, y, 16, 16, 6, MenuKit.fa(hv ? 0xEC2A2C33 : 0xD81A1B20, e), MenuKit.fa(hv ? 0x70FFFFFF : 0x30FFFFFF, e));
            Draw.centered(ctx, tr, k == 0 ? "-" : "+", ox + 8, y + 4, MenuKit.fa(0xFFFFFFFF, e));
        }
        Draw.centered(ctx, tr, value, bx + 38, y + 4, MenuKit.fa(0xFFFFFFFF, e));
    }

    private void toggleRow(DrawContext ctx, TextRenderer tr, int x, int y, String label, boolean on, int mx, int my, float e) {
        Draw.toggle(ctx, x, y, Draw.hoverProgress("cos_tg_" + label, on));
        Draw.text(ctx, tr, Draw.ellipsize(tr, label, rw() / 2 - 38), x + 34, y + 3, MenuKit.fa(0xFFE4E6EC, e), false);
    }

    // ---------------------------------------------------------------- try-on
    private void applyPreview(int hovered) {
        CosmeticsModule.clearPreview();
        Emotes.preview(null);
        if (hovered < 0) return;
        String o = options()[hovered];
        switch (tab) {
            case WINGS -> CosmeticsModule.previewWings = o;
            case HEAD -> CosmeticsModule.previewHead = o;
            case EMOTES -> Emotes.preview(Emotes.ALL[hovered].id());
            default -> CapesModule.previewCape = o;
        }
    }

    private void equip(int i) {
        String o = options()[i];
        CosmeticsModule cm = ModuleManager.get(CosmeticsModule.class);
        CapesModule cp = ModuleManager.get(CapesModule.class);
        switch (tab) {
            case WINGS -> { if (cm != null) { cm.wings.set(o); if (!cm.isEnabled() && !o.equals("None")) cm.setEnabled(true); } }
            case HEAD -> { if (cm != null) { cm.head.set(o); if (!cm.isEnabled() && !o.equals("None")) cm.setEnabled(true); } }
            case EMOTES -> {
                if (client == null || client.player == null) return;
                String id = Emotes.ALL[i].id();
                client.setScreen(null);
                Emotes.play(id);
                return;
            }
            default -> { if (cp != null) { cp.cape.set(o); if (!cp.isEnabled() && !o.equals("None")) cp.setEnabled(true); } }
        }
        TempestConfig.save();
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int x = x0(), y = y0();
        if (Draw.hit(mx, my, x + 10, y + 6, 24, 24)) { close(); return true; }
        if (Draw.hit(mx, my, prevX(), prevY(), prevW(), prevH())) {
            dragging = true;
            lastInteract = System.currentTimeMillis();
            return true;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (Draw.hit(mx, my, rx() + i * (tabW() + 4), tabsY(), tabW(), 22)) {
                if (tab != i) { tab = i; scroll = scrollTarget = 0; }
                return true;
            }
        }
        int card = cardAt(mx, my);
        if (card >= 0) { equip(card); return true; }
        if (clickFooter(mx, my)) return true;
        return super.mouseClicked(click, doubled);
    }

    private boolean clickFooter(double mx, double my) {
        CosmeticsModule cm = ModuleManager.get(CosmeticsModule.class);
        if (cm == null) return false;
        int fx = rx(), fy = footY();
        if (tab == WINGS) {
            for (int s = 0; s < 2; s++) {
                int bx = fx + s * (rw() / 2) + 34;
                var setting = s == 0 ? cm.wingSize : cm.flapSpeed;
                double step = s == 0 ? 0.1 : 0.25;
                if (Draw.hit(mx, my, bx, fy, 16, 16)) { setting.set(setting.get() - step); TempestConfig.save(); return true; }
                if (Draw.hit(mx, my, bx + 60, fy, 16, 16)) { setting.set(setting.get() + step); TempestConfig.save(); return true; }
            }
        }
        if (tab == HEAD && cm.head.is("Halo")) {
            for (int i = 0; i < CosmeticsModule.HALO_COLORS.length; i++) {
                int sx = fx + 34 + i * 20, sy = fy + 8;
                if (Draw.hit(mx, my, sx - 8, sy - 8, 16, 16)) { cm.haloColor.set(CosmeticsModule.HALO_COLORS[i]); TempestConfig.save(); return true; }
            }
        }
        if (Draw.hit(mx, my, fx, fy + 22, rw() / 2 - 4, 18)) { cm.share.toggle(); TempestConfig.save(); return true; }
        if (Draw.hit(mx, my, fx + rw() / 2, fy + 22, rw() / 2, 18)) { cm.seeOthers.toggle(); TempestConfig.save(); return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging) {
            yaw += (float) dx * 0.025f;
            lastInteract = System.currentTimeMillis();
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Draw.hit(mx, my, prevX(), prevY(), prevW(), prevH())) {
            zoom = Math.max(0.7f, Math.min(1.8f, zoom + (float) vertical * 0.1f));
            lastInteract = System.currentTimeMillis();
            return true;
        }
        if (Draw.hit(mx, my, rx(), gridY(), rw(), gridH())) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - vertical * 40));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_LEFT) { yaw -= 0.4f; lastInteract = System.currentTimeMillis(); return true; }
        if (k == GLFW.GLFW_KEY_RIGHT) { yaw += 0.4f; lastInteract = System.currentTimeMillis(); return true; }
        if (k == GLFW.GLFW_KEY_TAB) { tab = (tab + 1) % TABS.length; scroll = scrollTarget = 0; return true; }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        CosmeticsModule.clearPreview();
        Emotes.preview(null);
        TempestConfig.save();
        super.removed();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
