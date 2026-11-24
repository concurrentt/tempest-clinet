package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.modules.QuickServersModule;
import dev.tempest.util.FavoriteServer;
import dev.tempest.util.ServerPinger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tempest main menu: animated backdrop, centred logo, glass buttons, favourites and profile cards. */
public class TempestTitleScreen extends Screen {
    private record Btn(String id, int x, int y, int w, int h, String icon, String label, String badge, int variant, float delay) { }

    private final long openedAt = System.nanoTime();
    private final List<Btn> buttons = new ArrayList<>();
    private int logoSize, logoY, titleY, tagY, favX, favY, favW, favH, profX, profY, profW, profH;
    private boolean sideCards;

    public TempestTitleScreen() { super(Text.literal("Tempest")); }

    // ---------------------------------------------------------------- layout
    @Override
    protected void init() {
        dev.tempest.util.Updater.checkIfStale();
        buttons.clear();
        int bw = Math.min(260, width - 40);
        int bh = height >= 300 ? 24 : 20;
        int gap = height >= 320 ? 9 : 6;                 // breathing room between the buttons
        int promoH = height >= 340 ? 62 : 0;            // the feature card only shows when there is room
        logoSize = Math.max(40, Math.min(80, Math.round(height * 0.15f)));
        int titleH = height >= 300 ? 26 : 16;
        int stack = logoSize + 8 + titleH + 4 + 10 + 18 + 3 * bh + 2 * gap + (promoH > 0 ? gap + 6 + promoH : 0) + gap + 6 + 20;
        int top = Math.max(8, (height - stack) / 2 - 4);

        logoY = top;
        titleY = logoY + logoSize + 8;
        tagY = titleY + titleH + 4;
        int y = tagY + 10 + 18;
        int x = (width - bw) / 2;
        buttons.add(new Btn("single", x, y, bw, bh, "person", "Singleplayer", null, 0, 0.00f));
        y += bh + gap;
        buttons.add(new Btn("multi", x, y, bw, bh, "people", "Multiplayer", null, 0, 0.05f));
        y += bh + gap;
        int half = (bw - gap) / 2;
        buttons.add(new Btn("mods", x, y, half, bh, "grid", "Tempest Mods", Integer.toString(dev.tempest.module.ModuleManager.all().size()), 0, 0.10f));
        buttons.add(new Btn("visuals", x + half + gap, y, bw - half - gap, bh, "planet_stars", "Visuals", null, 1, 0.13f));
        y += bh + gap;
        if (promoH > 0) {
            y += 6;
            buttons.add(new Btn("promo", x, y, bw, promoH, "arrow_circle", "CUSTOM SKY", "NEW!", 2, 0.17f));
            y += promoH + gap;
        }
        y += 6;
        buttons.add(new Btn("options", x, y, half, 20, "gear", "Options", null, 0, 0.21f));
        buttons.add(new Btn("quit", x + half + gap, y, bw - half - gap, 20, "power", "Quit", null, 0, 0.24f));

        sideCards = width >= 720;
        // favourite servers in the top-left corner, your profile card in the top-right corner
        favW = 196; favH = 146; favX = 6; favY = 6;
        profW = 176; profH = 116; profX = width - profW - 14; profY = 14;
    }

    private float entrance(float delay) {
        float t = (System.nanoTime() - openedAt) / 1_000_000_000f;
        return MenuKit.ease((t - delay) / 0.55f);
    }

    // ---------------------------------------------------------------- render
    private static final net.minecraft.util.Identifier BG = net.minecraft.util.Identifier.of("tempest", "textures/gui/menu_bg.png");
    private static final net.minecraft.util.Identifier BG_GALAXY = net.minecraft.util.Identifier.of("tempest", "textures/gui/menu_bg_galaxy.png");
    private static final net.minecraft.util.Identifier BG_AURORA = net.minecraft.util.Identifier.of("tempest", "textures/gui/menu_bg_aurora.png");
    private static final net.minecraft.util.Identifier BG_BLACKHOLE = net.minecraft.util.Identifier.of("tempest", "textures/gui/menu_bg_blackhole.png");
    private static final net.minecraft.util.Identifier BG_PLANET = net.minecraft.util.Identifier.of("tempest", "textures/gui/menu_bg_planet.png");
    private static final net.minecraft.util.Identifier BG_BLOODMOON = net.minecraft.util.Identifier.of("tempest", "textures/gui/menu_bg_bloodmoon.png");
    private static final net.minecraft.util.Identifier WORDMARK = net.minecraft.util.Identifier.of("tempest", "textures/gui/wordmark.png");
    private static final net.minecraft.util.Identifier PROMO = net.minecraft.util.Identifier.of("tempest", "textures/gui/promo_sky.png");
    private static final int BG_W = 1920, BG_H = 1080, WM_W = 594, WM_H = 91, PROMO_W = 1040, PROMO_H = 300;

    /** Picture that fills the screen (cover fit), drifting very slowly so the menu feels alive. */
    public static void drawBackdrop(DrawContext ctx, int width, int height, float time) {
        String choice = dev.tempest.modules.ClientSettingsModule.menuBackground();
        if (choice.equals("Animated")) { Draw.ambient(ctx, width, height, time); return; }
        if (choice.equals("Plain")) {
            ctx.fillGradient(0, 0, width, height, Draw.lerpColor(0xFF0B0D13, Draw.accent(), 0.10f), 0xFF07080C);
            return;
        }
        net.minecraft.util.Identifier bg = switch (choice) {
            case "Galaxy" -> BG_GALAXY;
            case "Aurora" -> BG_AURORA;
            case "Black Hole" -> BG_BLACKHOLE;
            case "Ringed Planet" -> BG_PLANET;
            case "Blood Moon" -> BG_BLOODMOON;
            default -> BG;
        };
        float zoom = 1.10f;
        float scale = Math.max(width / (float) BG_W, height / (float) BG_H) * zoom;
        int rw = Math.round(width / scale), rh = Math.round(height / scale);
        float sx = (BG_W - rw) / 2f + (float) Math.sin(time * 0.045f) * (BG_W - rw) * 0.45f;
        float sy = (BG_H - rh) / 2f + (float) Math.cos(time * 0.035f) * (BG_H - rh) * 0.35f;
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, bg, 0, 0, sx, sy, width, height, rw, rh, BG_W, BG_H, 0xFFFFFFFF);
        // soft darkening at the bottom so the footer reads
        ctx.fillGradient(0, height - 90, width, height, 0x00000000, 0x9006050C);
    }

    /** The pre-rendered TEMPEST wordmark, centred on cx, h pixels tall. */
    public static void drawWordmark(DrawContext ctx, int cx, int y, int h, int color) {
        int w = Math.round(h * WM_W / (float) WM_H);
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, WORDMARK, cx - w / 2, y, 0f, 0f, w, h, WM_W, WM_H, WM_W, WM_H, color);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float time = MenuKit.time();
        drawBackdrop(ctx, width, height, time);

        int cx = width / 2;
        float eLogo = entrance(0f);
        int lyOff = Math.round((1f - eLogo) * -10);

        // logo with a breathing glow
        float breathe = 0.5f + 0.5f * (float) Math.sin(time * 1.1f);
        TempestLoading.drawGlow(ctx, cx, logoY + logoSize / 2 + lyOff, Math.round(logoSize * (3.1f + 0.25f * breathe)), Draw.accent(), 0.30f * eLogo);
        TempestLoading.drawLogo(ctx, cx - logoSize / 2, logoY + lyOff, logoSize);

        // wordmark (pre-rendered, letter-spaced)
        float eTitle = entrance(0.05f);
        int titleH = height >= 300 ? 26 : 16;
        int ww = Math.round(titleH * WM_W / (float) WM_H);
        int wy = Math.round(titleY + (1f - eTitle) * 6f);
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, WORDMARK, cx - ww / 2, wy, 0f, 0f, ww, titleH, WM_W, WM_H, WM_W, WM_H,
                MenuKit.fa(0xFFFFFFFF, eTitle));
        Draw.spaced(ctx, tr, "CLIENT", cx, tagY, MenuKit.fa(Draw.lerpColor(Draw.DIM, Draw.accent(), 0.55f), eTitle), false, 3);
        Draw.hairline(ctx, cx - 60, tagY + 14, 120, Draw.accent(), Math.round(0x70 * eTitle));

        for (Btn b : buttons) {
            float e = entrance(0.10f + b.delay());
            int oy = Math.round((1f - e) * 10);
            boolean hv = Draw.hit(mx, my, b.x(), b.y(), b.w(), b.h());
            if (b.variant() == 2) promo(ctx, tr, b, b.y() + oy, hv, e);
            else menuButton(ctx, tr, b, b.y() + oy, hv, e);
        }

        if (sideCards) {
            renderFavorites(ctx, tr, mx, my, entrance(0.25f));
            renderProfile(ctx, tr, mx, my, entrance(0.30f));
        } else {
            renderAccountChip(ctx, tr, mx, my, entrance(0.30f));
        }

        // footer
        float eFoot = entrance(0.4f);
        String ver = "Tempest " + version() + "  \u00b7  Minecraft 1.21.11";
        Draw.text(ctx, tr, ver, 10, height - 14, MenuKit.fa(0xFFB9BED6, eFoot), false);
        updatePill(ctx, tr, mx, my, eFoot, time);
        String hint = "Right Shift opens the Tempest menu in game";
        int hintX = width - 10 - Draw.textW(tr, hint);
        int[] ext = externalModsBox(tr);
        if (ext != null) {
            boolean hv = Draw.hit(mx, my, ext[0], ext[1], ext[2], ext[3]);
            float p = Draw.hoverProgress("title_external_mods", hv);
            Draw.panel(ctx, ext[0], ext[1], ext[2], ext[3], ext[3] / 2, MenuKit.fa(Draw.lerpColor(0xB0121319, 0xD0202230, p), eFoot),
                    MenuKit.fa(Draw.lerpColor(0x26FFFFFF, Draw.withAlpha(Draw.accent(), 0xA0), p), eFoot));
            int fg = MenuKit.fa(Draw.lerpColor(0xFFC9CCD6, 0xFFFFFFFF, p), eFoot);
            Icons.draw(ctx, "layers", ext[0] + 9, ext[1] + (ext[3] - 9) / 2, 9, fg);
            Draw.text(ctx, tr, EXTERNAL_MODS, ext[0] + 23, ext[1] + (ext[3] - 8) / 2, fg, false);
        }
        // the hint makes room for the button on narrow screens
        if (ext == null || hintX > ext[0] + ext[2] + 8) {
            Draw.text(ctx, tr, hint, hintX, height - 14, MenuKit.fa(0xFF9096B4, eFoot), false);
        }
    }

    private static final String EXTERNAL_MODS = "Fabric External Mods";

    /** Mod Menu's own list of every installed mod (only when Mod Menu is installed). */
    private static boolean modMenuInstalled() { return FabricLoader.getInstance().isModLoaded("modmenu"); }

    /** {x, y, w, h} of the bottom-centre "Fabric External Mods" button, or null without Mod Menu. */
    private int[] externalModsBox(TextRenderer tr) {
        if (!modMenuInstalled()) return null;
        int w = Draw.textW(tr, EXTERNAL_MODS) + 32, h = 18;
        return new int[]{(width - w) / 2, height - 22, w, h};
    }

    /** Opens Mod Menu's mods screen (looked up by name so Tempest doesn't need Mod Menu to build or run). */
    private void openModMenu() {
        try {
            Class<?> c = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            client.setScreen((Screen) c.getConstructor(Screen.class).newInstance(this));
        } catch (Throwable t) {
            dev.tempest.util.Chat.info("Couldn't open Mod Menu.");
        }
    }

    /** Glass button with an icon beside the label (green variant for the highlighted action). */
    private void menuButton(DrawContext ctx, TextRenderer tr, Btn b, int y, boolean hover, float e) {
        float p = Draw.hoverProgress("title_" + b.id(), hover);
        boolean green = b.variant() == 1;
        int fill = green ? Draw.lerpColor(0xC0102A1A, 0xDC173A24, p) : Draw.lerpColor(0xB0121319, 0xD0202230, p);
        int border = green ? Draw.lerpColor(0x8C2E9E55, 0xD040C46C, p) : Draw.lerpColor(0x26FFFFFF, 0x5CFFFFFF, p);
        int fg = green ? 0xFF5BE38A : Draw.lerpColor(0xFFE4E6EC, 0xFFFFFFFF, p);
        int x = b.x(), w = b.w(), h = b.h();
        if (p > 0.01f) Draw.shadow(ctx, x, y, w, h, 10, Math.round(5 * p));
        Draw.panel(ctx, x, y, w, h, 10, MenuKit.fa(fill, e), MenuKit.fa(border, e));
        // thin top highlight, like light catching a glass edge
        ctx.fill(x + 10, y + 1, x + w - 10, y + 2, MenuKit.fa(Draw.withAlpha(0xFFFFFF, 0x10 + Math.round(0x18 * p)), e));

        int is = Math.min(10, h - 8), igap = 6;
        int lw = Draw.textW(tr, b.label());
        int bwid = b.badge() == null ? 0 : Draw.textW(tr, b.badge()) + 10;
        int total = is + igap + lw + (bwid > 0 ? 7 + bwid : 0);
        int cx = x + (w - total) / 2, ty = y + (h - 8) / 2;
        Icons.draw(ctx, b.icon(), cx, y + (h - is) / 2, is, MenuKit.fa(fg, e));
        Draw.text(ctx, tr, b.label(), cx + is + igap, ty, MenuKit.fa(fg, e), false);
        if (bwid > 0) {
            int bx = cx + is + igap + lw + 7;
            Draw.roundRect(ctx, bx, y + (h - 14) / 2, bwid, 14, 5, MenuKit.fa(0xFF34363F, e));
            Draw.text(ctx, tr, b.badge(), bx + 5, y + (h - 8) / 2, MenuKit.fa(0xFFC9CCD6, e), false);
        }
    }

    /** Feature card: a rendered black-hole banner, big title, one line of text, an arrow and a green tag. */
    private void promo(DrawContext ctx, TextRenderer tr, Btn b, int y, boolean hover, float e) {
        float p = Draw.hoverProgress("title_promo", hover);
        int x = b.x(), w = b.w(), h = b.h();
        if (p > 0.01f) Draw.shadow(ctx, x, y, w, h, 10, Math.round(6 * p));
        Draw.roundRect(ctx, x, y, w, h, 10, MenuKit.fa(0xFF07080D, e));
        // banner image, cover fit, gently zooming in on hover
        float scale = Math.max((w - 2) / (float) PROMO_W, (h - 2) / (float) PROMO_H) * (1f + 0.06f * p);
        int rw = Math.round((w - 2) / scale), rh = Math.round((h - 2) / scale);
        float u = (PROMO_W - rw) * 0.62f, v = (PROMO_H - rh) / 2f;
        // the banner is clipped to the card's rounded corners: one band for the middle, thin bands for the corner rows
        int tint = MenuKit.fa(Draw.lerpColor(0xFFD0D0D8, 0xFFFFFFFF, p), e);
        final int R = 9;
        for (int band = -R; band <= R; band++) {
            int y0, y1, inset;
            if (band == 0) { y0 = y + 1 + R; y1 = y + h - 1 - R; inset = 0; }
            else {
                int row = Math.abs(band) - 1;                         // 0 = outermost row
                double dy = R - row - 0.5;
                inset = (int) Math.ceil(R - Math.sqrt(Math.max(0, R * R - dy * dy)));
                y0 = band < 0 ? y + 1 + row : y + h - 2 - row;
                y1 = y0 + 1;
            }
            if (y1 <= y0) continue;
            ctx.enableScissor(x + 1 + inset, y0, x + w - 1 - inset, y1);
            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, PROMO, x + 1, y + 1, u, v, w - 2, h - 2, rw, rh, PROMO_W, PROMO_H, tint);
            ctx.disableScissor();
        }
        Draw.ring(ctx, x, y, w, h, 10, MenuKit.fa(Draw.lerpColor(0x30FFFFFF, 0x70FFFFFF, p), e));

        int tx = x + 14;
        MenuKit.scaledLeft(ctx, tr, Draw.bold(b.label()), tx, y + h / 2 - 15, 2f, MenuKit.fa(0xFFFFFFFF, e), true);
        Draw.text(ctx, tr, "Space, galaxies, aurora, black holes", tx, y + h / 2 + 6, MenuKit.fa(0xFFC9CCD6, e), true);
        int ax = x + w - 24 + Math.round(3 * p);
        Draw.roundRect(ctx, ax - 4, y + h - 26, 20, 20, 10, MenuKit.fa(Draw.withAlpha(0xFFFFFF, 0x18 + Math.round(0x20 * p)), e));
        Icons.draw(ctx, b.icon(), ax, y + h - 22, 12, MenuKit.fa(Draw.lerpColor(0xD0FFFFFF, 0xFFFFFFFF, p), e));

        if (b.badge() != null) {
            int bw = Draw.spacedW(tr, b.badge(), true, 1) + 12;
            int bx = x + w - bw - 8, by = y - 6;
            Draw.roundRect(ctx, bx, by, bw, 14, 4, MenuKit.fa(0xFF1FA34E, e));
            Draw.spaced(ctx, tr, b.badge(), bx + bw / 2, by + 3, MenuKit.fa(0xFFFFFFFF, e), true, 1);
        }
    }

    private void renderFavorites(DrawContext ctx, TextRenderer tr, int mx, int my, float e) {
        if (e <= 0.01f) return;
        int x = favX - Math.round((1f - e) * 14);
        List<FavoriteServer> favs = TempestConfig.get().favoriteServers;
        int rows = Math.min(favs.size(), 4);
        int h = 36 + Math.max(1, rows) * 26 + 10;
        favH = h;
        favY = 6;   // pinned to the very top-left corner
        Draw.shadow(ctx, x, favY, favW, h, 10, 8);
        Draw.glass(ctx, x, favY, favW, h, 10, MenuKit.fa(Draw.bg(), e), MenuKit.fa(Draw.border(), e));
        Draw.text(ctx, tr, Draw.bold("\u2605 Favorites"), x + 12, favY + 11, MenuKit.fa(Draw.TEXT, e), false);
        Draw.text(ctx, tr, "Quick join", x + favW - 12 - Draw.textW(tr, "Quick join"), favY + 11, MenuKit.fa(Draw.DIM2, e), false);
        ctx.fill(x + 10, favY + 26, x + favW - 10, favY + 27, MenuKit.fa(Draw.border(), e));
        if (favs.isEmpty()) {
            Draw.text(ctx, tr, "No favorites yet", x + 12, favY + 40, MenuKit.fa(Draw.DIM2, e), false);
            Draw.text(ctx, tr, "Star servers in Multiplayer", x + 12, favY + 52, MenuKit.fa(Draw.DIM2, e), false);
            return;
        }
        for (int i = 0; i < rows; i++) {
            FavoriteServer f = favs.get(i);
            ServerPinger.ping(f.address);
            int ry = favY + 32 + i * 26;
            boolean hv = Draw.hit(mx, my, x + 6, ry, favW - 12, 24);
            float p = Draw.hoverProgress("title_fav" + i, hv);
            if (p > 0.01f) Draw.roundRect(ctx, x + 6, ry, favW - 12, 24, 7, MenuKit.fa(Draw.withAlpha(0xFFFFFF, 0x14), p * e));
            ServerPinger.Result r = ServerPinger.get(f.address);
            boolean up = r != null && !r.failed && r.pingMs >= 0;
            int dot = r == null || (!r.done && r.pingMs < 0) ? 0xFF8B96C0 : !up ? 0xFFFF5555 : r.pingMs < 80 ? 0xFF55FF55 : r.pingMs < 200 ? 0xFFFFD83A : 0xFFFF5555;
            net.minecraft.util.Identifier ic = r != null ? dev.tempest.util.ServerIcons.get(f.address, r.icon()) : null;
            if (ic != null) {
                MenuKit.serverIcon(ctx, ic, x + 11, ry + 5, 14, e);
                Draw.circle(ctx, x + 24, ry + 18, 2, MenuKit.fa(dot, e));
            } else {
                Draw.circle(ctx, x + 16, ry + 12, 3, MenuKit.fa(dot, e));
            }
            String right = up ? (r.online >= 0 ? r.online + "" : r.pingMs + "ms") : "";
            int nameMax = favW - 54 - Draw.textW(tr, right);
            Draw.text(ctx, tr, Draw.ellipsize(tr, f.name, nameMax), x + 26, ry + 8, MenuKit.fa(Draw.lerpColor(Draw.DIM, Draw.TEXT, p), e), false);
            if (!right.isEmpty()) Draw.text(ctx, tr, right, x + favW - 14 - Draw.textW(tr, right), ry + 8, MenuKit.fa(Draw.DIM2, e), false);
        }
    }

    private void renderProfile(DrawContext ctx, TextRenderer tr, int mx, int my, float e) {
        if (e <= 0.01f) return;
        int x = profX + Math.round((1f - e) * 14), y = profY;
        Draw.shadow(ctx, x, y, profW, profH, 10, 8);
        Draw.glass(ctx, x, y, profW, profH, 10, MenuKit.fa(Draw.bg(), e), MenuKit.fa(Draw.border(), e));
        String name = playerName();
        java.util.UUID uuid = null;
        try { if (client != null && client.getSession() != null) uuid = client.getSession().getUuidOrNull(); } catch (Throwable ignored) { }
        net.minecraft.util.Identifier head = dev.tempest.util.PlayerHeads.get(uuid);
        if (head != null) {
            Draw.roundRect(ctx, x + 11, y + 11, 36, 36, 6, MenuKit.fa(0xFF0C0D12, e));
            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, head, x + 12, y + 12, 0f, 0f, 34, 34, 32, 32, 32, 32, MenuKit.fa(0xFFFFFFFF, e));
            Draw.ring(ctx, x + 11, y + 11, 36, 36, 6, MenuKit.fa(0x40FFFFFF, e));
        } else {
            MenuKit.tile(ctx, tr, x + 12, y + 12, 34, name, e);
        }
        Draw.text(ctx, tr, Draw.ellipsize(tr, name, profW - 70), x + 54, y + 16, MenuKit.fa(Draw.TEXT, e), false);
        if (ACCOUNT_SWITCHER) {
            float sp = Draw.hoverProgress("title_switch", Draw.hit(mx, my, profX + 54, profY + 26, profW - 64, 12));
            Draw.text(ctx, tr, "⇄ Switch account", x + 54, y + 29,
                    MenuKit.fa(Draw.lerpColor(Draw.DIM2, Draw.withAlpha(Draw.accent(), 0xFF), sp), e), false);
        } else {
            Draw.text(ctx, tr, "Tempest v" + version(), x + 54, y + 29, MenuKit.fa(Draw.DIM2, e), false);
        }
        ctx.fill(x + 10, y + 54, x + profW - 10, y + 55, MenuKit.fa(Draw.border(), e));
        int bx = x + 10, bw = (profW - 28) / 2;
        smallButton(ctx, tr, "title_cust", bx, y + 64, bw, 20, "Customize", mx, my, e);
        smallButton(ctx, tr, "title_friends", bx + bw + 8, y + 64, bw, 20,
                dev.tempest.util.Social.pendingCount() > 0 ? "Friends (" + dev.tempest.util.Social.pendingCount() + ")" : "Friends", mx, my, e);
        smallButton(ctx, tr, "title_hud", bx, y + 88, bw, 20, "Edit HUD", mx, my, e);
        smallButton(ctx, tr, "title_theme", bx + bw + 8, y + 88, bw, 20, "Theme", mx, my, e);
    }

    /** Account switcher hidden for now: Microsoft/Mojang need an approved app ID that players can't get for free. */
    private static final boolean ACCOUNT_SWITCHER = false;

    /** {x, y, w, h} of the small account chip shown top-right when the profile card doesn't fit. */
    private int[] accountChip(TextRenderer tr) {
        int w = Draw.textW(tr, playerName()) + 34;
        return new int[]{width - w - 8, 6, w, 18};
    }

    private void renderAccountChip(DrawContext ctx, TextRenderer tr, int mx, int my, float e) {
        if (e <= 0.01f) return;
        int[] b = accountChip(tr);
        float p = Draw.hoverProgress("title_acc_chip", Draw.hit(mx, my, b[0], b[1], b[2], b[3]));
        Draw.panel(ctx, b[0], b[1], b[2], b[3], 9, MenuKit.fa(Draw.lerpColor(0xB0121319, 0xD0202230, p), e),
                MenuKit.fa(Draw.lerpColor(0x26FFFFFF, Draw.withAlpha(Draw.accent(), 0xA0), p), e));
        java.util.UUID uuid = null;
        try { if (client != null && client.getSession() != null) uuid = client.getSession().getUuidOrNull(); } catch (Throwable ignored) { }
        net.minecraft.util.Identifier head = dev.tempest.util.PlayerHeads.get(uuid);
        if (head != null) ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, head, b[0] + 4, b[1] + 3, 0f, 0f, 12, 12, 32, 32, 32, 32, MenuKit.fa(0xFFFFFFFF, e));
        Draw.text(ctx, tr, playerName(), b[0] + 20, b[1] + 5, MenuKit.fa(Draw.lerpColor(0xFFC9CCD6, 0xFFFFFFFF, p), e), false);
        if (ACCOUNT_SWITCHER) Draw.text(ctx, tr, "⇄", b[0] + b[2] - 11, b[1] + 5, MenuKit.fa(Draw.withAlpha(Draw.accent(), 0xFF), e), false);
    }

    private void smallButton(DrawContext ctx, TextRenderer tr, String key, int x, int y, int w, int h, String label, int mx, int my, float e) {
        float p = Draw.hoverProgress("mk:" + key, Draw.hit(mx, my, x, y, w, h));
        Draw.panel(ctx, x, y, w, h, 6, MenuKit.fa(Draw.lerpColor(Draw.card(), Draw.cardHover(), p), e),
                MenuKit.fa(Draw.lerpColor(Draw.border(), Draw.withAlpha(Draw.accent(), 0xA0), p), e));
        Draw.text(ctx, tr, label, x + (w - Draw.textW(tr, label)) / 2, y + (h - 8) / 2, MenuKit.fa(Draw.lerpColor(Draw.DIM, Draw.TEXT, p), e), false);
    }

    private String playerName() {
        try {
            if (client != null && client.getSession() != null) return client.getSession().getUsername();
        } catch (Throwable ignored) { }
        return "Player";
    }

    private String updateLabel() {
        dev.tempest.util.Updater.State st = dev.tempest.util.Updater.state();
        if (st == dev.tempest.util.Updater.State.READY) return "Restart to finish updating";
        if (st == dev.tempest.util.Updater.State.DOWNLOADING) return "Downloading update\u2026 " + Math.round(dev.tempest.util.Updater.progress() * 100) + "%";
        if (dev.tempest.util.Updater.offer()) return "Update available \u00b7 v" + dev.tempest.util.Updater.latestVersion();
        return null;
    }

    private int pillW(TextRenderer tr, String label) { return Draw.textW(tr, label) + 30; }

    /** Small glowing pill above the version text while an update is offered / downloading / ready. */
    private String checkLabel() {
        return dev.tempest.util.Updater.state() == dev.tempest.util.Updater.State.CHECKING ? "Checking for updates\u2026" : "Check for updates";
    }

    private void updatePill(DrawContext ctx, TextRenderer tr, int mx, int my, float e, float time) {
        String label = updateLabel();
        if (label == null) {
            // no update known: a quiet "Check for updates" button in the same spot
            String cl = checkLabel();
            int w = Draw.textW(tr, cl) + 16, x = 8, y = height - 36;
            boolean hv = Draw.hit(mx, my, x, y, w, 16);
            Draw.panel(ctx, x, y, w, 16, 8, MenuKit.fa(hv ? Draw.cardHover() : Draw.card(), e), MenuKit.fa(hv ? Draw.withAlpha(Draw.accent(), 0xA0) : Draw.border(), e));
            Draw.text(ctx, tr, cl, x + 8, y + 4, MenuKit.fa(hv ? 0xFFFFFFFF : 0xFFB8BED0, e), false);
            return;
        }
        int w = pillW(tr, label), h = 16, x = 8, y = height - 36;
        boolean hv = Draw.hit(mx, my, x, y, w, h);
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.4f);
        int acc = Draw.accent();
        Draw.roundRect(ctx, x - 2, y - 2, w + 4, h + 4, 10, MenuKit.fa(Draw.withAlpha(acc, Math.round(0x20 + 0x30 * pulse)), e));
        Draw.panel(ctx, x, y, w, h, 8, MenuKit.fa(hv ? Draw.cardHover() : Draw.card(), e), MenuKit.fa(Draw.withAlpha(acc, hv ? 0xE0 : 0xA0), e));
        Draw.roundRect(ctx, x + 7, y + 5, 6, 6, 3, MenuKit.fa(0xFF000000 | acc, e));
        Draw.text(ctx, tr, label, x + 19, y + 4, MenuKit.fa(0xFFFFFFFF, e), false);
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer("tempest")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.6").toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();
        int[] ext = externalModsBox(textRenderer);
        if (ext != null && Draw.hit(mx, my, ext[0], ext[1], ext[2], ext[3])) { openModMenu(); return true; }
        String upd = updateLabel();
        if (upd != null && Draw.hit(mx, my, 8, height - 36, pillW(textRenderer, upd), 16)) {
            client.setScreen(new UpdateScreen(this));
            return true;
        }
        if (upd == null && Draw.hit(mx, my, 8, height - 36, Draw.textW(textRenderer, checkLabel()) + 16, 16)) {
            dev.tempest.util.Updater.checkNow();
            return true;
        }
        for (Btn b : buttons) {
            if (!Draw.hit(mx, my, b.x(), b.y(), b.w(), b.h())) continue;
            switch (b.id()) {
                case "single" -> client.setScreen(new TempestSinglePlayerScreen(this));
                case "multi" -> client.setScreen(new TempestMultiplayerScreen(this));
                case "mods" -> client.setScreen(new TempestMenuScreen(this));
                case "visuals", "promo" -> client.setScreen(ClickGuiScreen.openTo(dev.tempest.module.Category.VISUALS, this));
                case "options" -> client.setScreen(new OptionsScreen(this, client.options));
                case "quit" -> client.scheduleStop();
                default -> { }
            }
            return true;
        }
        if (!sideCards) {
            int[] ch = accountChip(textRenderer);
            if (ACCOUNT_SWITCHER && Draw.hit(mx, my, ch[0], ch[1], ch[2], ch[3])) { client.setScreen(new AccountsScreen(this)); return true; }
        }
        if (sideCards) {
            if (ACCOUNT_SWITCHER && Draw.hit(mx, my, profX + 54, profY + 26, profW - 64, 12)) { client.setScreen(new AccountsScreen(this)); return true; }
            List<FavoriteServer> favs = TempestConfig.get().favoriteServers;
            int rows = Math.min(favs.size(), 4);
            for (int i = 0; i < rows; i++) {
                if (Draw.hit(mx, my, favX + 6, favY + 32 + i * 26, favW - 12, 24)) {
                    QuickServersModule.join(this, favs.get(i));
                    return true;
                }
            }
            int bx = profX + 10, bw = (profW - 28) / 2;
            if (Draw.hit(mx, my, bx, profY + 64, bw, 20)) { client.setScreen(new TempestMenuScreen(this)); return true; }
            if (Draw.hit(mx, my, bx + bw + 8, profY + 64, bw, 20)) { client.setScreen(new SocialScreen(this)); return true; }
            if (Draw.hit(mx, my, bx, profY + 88, bw, 20)) { client.setScreen(new HudEditorScreen(this)); return true; }
            if (Draw.hit(mx, my, bx + bw + 8, profY + 88, bw, 20)) { client.setScreen(new ThemeScreen(this)); return true; }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean shouldPause() { return false; }
}
