package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.KeySetting;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import dev.tempest.module.Setting;
import dev.tempest.modules.ClientSettingsModule;
import dev.tempest.util.Keys;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Floating mod menu: sidebar with sections, header with clock and search, two-column module list, per-mod settings. */
public class ClickGuiScreen extends Screen {
    private static final int PAD = 14, SIDE_W = 140, HEAD_H = 38, GAP = 10, CARD_H = 48, ROW_H = 28, PITCH = 32;
    /** Settings page: height of the module header card, where the first setting row starts, and the inset of buttons inside a row. */
    private static final int HDR_H = 52, ROWS_TOP = 86, BTN_Y = (ROW_H - 18) / 2;
    private static final int DESC = 0xFF7B87B0;
    private static final Set<String> RISKY = Set.of();
    private static int savedX = Integer.MIN_VALUE, savedY = Integer.MIN_VALUE;
    private static double savedScroll = 0, savedScrollTarget = 0;
    private static Category savedFilter = null;
    private static String savedOpenId = null;
    private int pX, pY, pW, pH;
    private TextFieldWidget search;
    private Category filter = null;
    private Module open = null;
    private double scroll = 0, scrollTarget = 0, scrollVel = 0;
    private Setting dragging = null;
    private KeySetting listening = null, prevListen = null;
    // color picker popup
    private ColorSetting picker = null;
    private float pickH, pickS, pickV;
    private int pickDrag = 0; // 1 = saturation/value square, 2 = hue bar
    private static final int PK_W = 164, PK_H = 168, SV_W = 112, SV_H = 96;

    private String tip = null;
    private int tipX, tipY;

    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = System.nanoTime();
    private float dt = 0.016f;
    private final Map<Object, Float> hov = new HashMap<>();
    private final Map<Object, Float> tog = new HashMap<>();

    private record Side(String label, Category cat, int kind, Item icon, int y) { } // kind 0 = category, 1 = action, 2 = section label

    /** Screen to go back to on Escape (the main menu or the Right Shift menu); null = back to the game. */
    private final Screen parent;

    public ClickGuiScreen() { this(null); }

    public ClickGuiScreen(Screen parent) {
        super(Text.literal("Tempest"));
        this.parent = parent;
    }

    @Override
    public void close() {
        if (client != null && parent != null) client.setScreen(parent);
        else super.close();
    }

    /** Right Shift: straight back to the game when in a world, otherwise back to where we came from. */
    private void closeAll() {
        if (client != null && client.world != null) client.setScreen(null);
        else close();
    }

    @Override
    protected void init() {
        float gs = ClientSettingsModule.guiScale();
        pW = Math.min(width - 32, Math.max(480, Math.round(780 * gs)));
        pH = Math.min(height - 32, Math.max(300, Math.round(460 * gs)));
        pX = (width - pW) / 2;
        pY = (height - pH) / 2;
        if (savedX != Integer.MIN_VALUE) {
            pX = Math.max(8, Math.min(savedX, width - pW - 8));
            pY = Math.max(8, Math.min(savedY, height - pH - 8));
        }
        filter = savedFilter;
        open = null;
        if (savedOpenId != null) {
            for (Module m : ModuleManager.all()) {
                if (m.getId().equals(savedOpenId)) { open = m; break; }
            }
            if (open == null) savedOpenId = null;
        }
        scroll = savedScroll;
        scrollVel = 0;
        scrollTarget = savedScrollTarget;
        String old = search != null ? search.getText() : "";
        search = new TextFieldWidget(textRenderer, 0, 0, searchW() - 78, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setPlaceholder(Text.literal("Search mods...  is:on  cat:pvp"));
        search.setMaxLength(64);
        search.setText(old);
        search.setChangedListener(s -> scrollTarget = 0);
        addDrawableChild(search);
    }

    // ---------- animation helpers ----------
    private float approach(Map<Object, Float> map, Object key, float target) {
        float cur = map.getOrDefault(key, target);
        if (!ClientSettingsModule.animationsOn()) {
            cur = target;
        } else {
            String mode = ClientSettingsModule.animationEasing();
            if ("Linear".equals(mode)) {
                float step = dt * 8f;
                if (Math.abs(target - cur) <= step) cur = target;
                else cur += Math.copySign(step, target - cur);
            } else if ("Snappy".equals(mode)) {
                cur += (target - cur) * (1f - (float) Math.exp(-dt * 24f));
            } else {
                cur += (target - cur) * (1f - (float) Math.exp(-dt * 14f));
            }
            if (Math.abs(target - cur) < 0.005f) cur = target;
        }
        map.put(key, cur);
        return cur;
    }

    private static Text bold(String s) { return Draw.bold(s); }

    private void drawPlayerHead(DrawContext ctx, int x, int y, int size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            String ini = playerName().substring(0, 1).toUpperCase(Locale.ROOT);
            Draw.text(ctx, textRenderer, bold(ini), x + (size - Draw.textW(textRenderer, bold(ini))) / 2,
                    y + (size - 8) / 2, 0xFFFFFFFF, false);
            return;
        }
        // 1.21.11 exposes the player's skin as SkinTextures/AssetInfo rather
        // than a raw Identifier. Use Mojang's skin drawer so the face and hat
        // layers are resolved through the current skin cache API.
        PlayerSkinDrawer.draw(ctx, mc.player.getSkin(), x, y, size);
    }

    private String playerName() {
        return client != null && client.player != null ? client.player.getName().getString() : "Player";
    }

    // ---------- layout ----------
    private int sideX() { return pX + PAD; }
    private int sideY() { return pY + PAD; }
    private int sideH() { return pH - 2 * PAD; }
    private int cx() { return pX + PAD + SIDE_W + GAP; }
    private int cw() { return pW - 2 * PAD - SIDE_W - GAP; }
    private int headY() { return pY + PAD; }
    private int cy() { return pY + PAD + HEAD_H + GAP; }
    private int ch() { return pH - 2 * PAD - HEAD_H - GAP; }
    private int searchW() { return Math.min(210, cw() - 190); }

    /** Search filters (with auto-complete): category:pvp, is:on, is:off, is:fav, is:hud; -word excludes. */
    private static java.util.Map<String, java.util.function.Supplier<List<String>>> searchFilters() {
        java.util.Map<String, java.util.function.Supplier<List<String>>> f = dev.tempest.util.SearchQuery.filters();
        f.put("category", () -> {
            List<String> l = new ArrayList<>();
            for (Category c : Category.values()) l.add(c.name().toLowerCase(Locale.ROOT));
            return l;
        });
        f.put("is", () -> List.of("on", "off", "fav", "hud"));
        return f;
    }

    private List<String> suggestions = List.of();

    private List<Module> visible() {
        dev.tempest.util.SearchQuery q = dev.tempest.util.SearchQuery.parse(search.getText(), searchFilters());
        List<String> favMods = TempestConfig.get().favoriteMods;
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (filter != null && m.getCategory() != filter) continue;
            if (!q.isEmpty()) {
                java.util.Map<String, String> vals = new java.util.HashMap<>();
                vals.put("category", m.getCategory().name());
                vals.put("is", (m.isEnabled() ? "on" : "off") + (favMods.contains(m.getId()) ? "\nfav" : "")
                        + (m instanceof dev.tempest.module.HudModule ? "\nhud" : ""));
                if (!q.matches(m.getName() + " " + m.getDescription(), vals)) continue;
            }
            out.add(m);
        }
        // "always on" (locked) mods first, then starred mods; the rest keep their usual order
        List<String> favs = TempestConfig.get().favoriteMods;
        out.sort((a, b) -> {
            int c = Boolean.compare(b.alwaysOn(), a.alwaysOn());
            return c != 0 ? c : Boolean.compare(favs.contains(b.getId()), favs.contains(a.getId()));
        });
        return out;
    }

    /** Mod cards: three per row (two on very narrow windows), tall, icon on top. */
    private static final int MCARD_H = 128, OPT_H = 16, TOG_H = 18;
    private int cols() { return cw() >= 300 ? 3 : 2; }
    private int cardW() { int c = cols(); return (cw() - GAP * (c - 1)) / c; }
    private int cardX(int col) { return cx() + col * (cardW() + GAP); }
    private int cardY(int row) { return cy() + row * (MCARD_H + GAP) - (int) Math.floor(scroll); }
    // pieces of a card, relative to its top-left corner
    private int optY() { return 78; }
    private int togY() { return 78 + OPT_H + 4; }
    private int gearW() { return OPT_H + 6; }

    private int rx() { return cx(); }
    private int rw() { return cw(); }
    private int top() { return cy() - (int) Math.floor(scroll); }

    /**
     * Smooth scrolling: a critically damped spring toward the target (eases in and out, never wobbles), solved exactly
     * so it behaves the same at any frame rate.
     */
    private void stepScroll(float dt) {
        final double omega = 18.0;
        double x = scroll - scrollTarget, e = Math.exp(-omega * dt);
        double k = scrollVel + omega * x;
        scroll = scrollTarget + (x + k * dt) * e;
        scrollVel = (scrollVel - omega * k * dt) * e;
        double max = maxScroll();
        if (scroll < 0) { scroll = 0; scrollVel = Math.max(0, scrollVel); }
        if (scroll > max) { scroll = max; scrollVel = Math.min(0, scrollVel); }
        if (Math.abs(scroll - scrollTarget) < 0.02 && Math.abs(scrollVel) < 0.5) { scroll = scrollTarget; scrollVel = 0; }
    }

    /** The part of the scroll below one GUI pixel, rounded to whole screen pixels, so content glides 1 px at a time. */
    private float scrollFraction() {
        double frac = scroll - Math.floor(scroll);
        double gs = client != null ? client.getWindow().getScaleFactor() : 1.0;
        return (float) (Math.round(frac * gs) / gs);
    }
    private int rowY(int i) { return top() + ROWS_TOP + i * PITCH; }

    private double maxScroll() {
        int content;
        if (open != null) content = ROWS_TOP + open.getSettings().size() * PITCH + 34;
        else {
            int n = visible().size();
            int rows = (n + cols() - 1) / cols();
            content = rows * (MCARD_H + GAP);
        }
        return Math.max(0, content - ch());
    }

    private List<Side> side() {
        List<Side> out = new ArrayList<>();
        int pitch = sidePitch();
        int y = sideY() + 54;
        out.add(new Side("MODULES", null, 2, null, y));
        y += 16;
        out.add(new Side("All Mods", null, 0, Items.NETHER_STAR, y));
        y += pitch;
        for (Category c : Category.values()) {
            out.add(new Side(c.label, c, 0, null, y));
            y += pitch;
        }
        y += 4;
        out.add(new Side("GENERAL", null, 2, null, y));
        y += 16;
        out.add(new Side("Edit HUD", null, 1, Items.ITEM_FRAME, y));
        y += pitch;
        out.add(new Side("Configs", null, 1, Items.BOOK, y));
        y += pitch;
        out.add(new Side("Waypoints", null, 1, Items.RECOVERY_COMPASS, y));
        y += pitch;
        out.add(new Side("Friends", null, 1, Items.PLAYER_HEAD, y));
        y += pitch;
        out.add(new Side("Servers", null, 1, Items.ENDER_EYE, y));
        return out;
    }

    /** Distance between sidebar rows. Shrinks on short windows so every row (and the profile card) still fits. */
    private int sidePitch() {
        boolean profile = sideH() >= 330;
        // clickable rows (All + every category + 5 actions) plus the two section labels and a small gap
        int rows = 1 + Category.values().length + 5;
        int avail = sideH() - 54 - 2 * 16 - 4 - (profile ? 48 : 8);
        return Math.max(14, Math.min(24, avail / rows));
    }

    private int rowH() { return Math.min(sidePitch(), sideH() >= 400 ? 22 : 18); }

    // ---------- render ----------
    /** No vanilla blur: it re-renders the whole screen several times per frame, and this menu draws its own wash. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        tip = null;

        float anim = ClientSettingsModule.animationsOn() ? Math.min(1f, (System.currentTimeMillis() - openedAt) / 260f) : 1f;
        anim = 1f - (1f - anim) * (1f - anim) * (1f - anim);
        int baseY = pY;
        pY += (int) ((1f - anim) * 16);

        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        stepScroll(dt);

        TextRenderer tr = textRenderer;

        // Dim the game with a faint blue-black wash, then a soft accent glow so the window feels lit from behind.
        if (client != null && client.world == null) Draw.menuBackground(ctx, width, height);
        ctx.fillGradient(0, 0, width, height, (((int) (0x70 * anim)) << 24) | 0x05070D, (((int) (0x38 * anim)) << 24) | 0x05070D);
        TempestLoading.drawGlow(ctx, pX + pW / 2, pY + pH / 2, Math.round(Math.max(pW, pH) * 1.7f), Draw.accent(), 0.18f * anim);

        // window: shadow, panel, a faint sheen across the top and an accent hairline on the edge
        Draw.shadow(ctx, pX, pY, pW, pH, 11, 14);
        Draw.glass(ctx, pX, pY, pW, pH, 11, Draw.bg(), Draw.border());
        Draw.hairline(ctx, pX + 22, pY + 1, pW - 44, Draw.accent(), 0x70);

        renderSidebar(ctx, mx, my);
        renderHeader(ctx, mx, my);

        int cx = cx(), cy = cy(), cw = cw(), ch = ch();
        ctx.enableScissor(cx, cy, cx + cw, cy + ch);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0f, -scrollFraction());
        if (open == null) renderGrid(ctx, mx, my);
        else renderSettings(ctx, mx, my);
        ctx.getMatrices().popMatrix();

        // content fades out at the edges it can still scroll past
        int solid = Draw.solid();
        int fadeCol = Draw.bg() | 0xFF000000;
        float fadeTop = (float) Math.min(1.0, scroll / 24.0);
        float fadeBot = (float) Math.min(1.0, (maxScroll() - scroll) / 24.0);
        if (fadeTop > 0.01f) ctx.fillGradient(cx, cy, cx + cw, cy + 18, Draw.withAlpha(fadeCol, (int) (0x66 * fadeTop)), Draw.withAlpha(fadeCol, 0));
        if (fadeBot > 0.01f) ctx.fillGradient(cx, cy + ch - 18, cx + cw, cy + ch, Draw.withAlpha(fadeCol, 0), Draw.withAlpha(fadeCol, (int) (0x66 * fadeBot)));
        ctx.disableScissor();

        Draw.scrollbar(ctx, cx + cw + 5, cy + 2, ch - 4, maxScroll(), scroll);

        search.render(ctx, mx, my, delta);
        renderSuggestions(ctx, mx, my);

        // opening: the window's contents fade in from the panel colour
        if (anim < 1f) Draw.roundRect(ctx, pX + 1, pY + 1, pW - 2, pH - 2, 10, Draw.withAlpha(solid, (int) (0x90 * (1f - anim))));

        renderPicker(ctx, mx, my);

        if (tip != null && picker == null) {
            int tw = Draw.textW(tr, tip) + 14;
            int tx = Math.min(tipX, width - tw - 4), ty = tipY - 22;
            Draw.shadow(ctx, tx, ty, tw, 18, 5, 4);
            Draw.panel(ctx, tx, ty, tw, 18, 5, Draw.withAlpha(solid, 0xE8), Draw.withAlpha(Draw.accent(), 0xB0));
            Draw.text(ctx, tr, tip, tx + 7, ty + 5, Draw.TEXT, false);
        }
        pY = baseY;
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer("tempest")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.6");
    }

    private int searchX() { return cx() + cw() - searchW(); }
    private int searchY() { return headY() + 8; }

    private void renderHeader(DrawContext ctx, int mx, int my) {
        TextRenderer tr = textRenderer;
        int x = cx(), y = headY(), w = cw();
        // Header is a simple title strip, not another floating card.
        ctx.fill(x, y + HEAD_H - 1, x + w, y + HEAD_H, Draw.border());

        List<Module> vis = visible();
        int n = vis.size(), on = 0;
        for (Module m : vis) if (m.isEnabled()) on++;
        String title = filter == null ? "All modules" : filter.label;

        // a slim bar in the category colour leads the title
        Draw.roundRect(ctx, x, y + 6, 3, 24, 1, open != null ? Draw.catColor(open.getCategory()) : Draw.catColor(filter));
        Draw.text(ctx, tr, bold(title), x + 11, y + 7, Draw.TEXT, false);
        String sub = open != null ? "Settings" : n + (n == 1 ? " mod" : " mods") + "  ·  " + on + " on";
        Draw.text(ctx, tr, sub, x + 11, y + 20, Draw.DIM, false);

        int sw = searchW(), sx = searchX(), sy = searchY();
        boolean focus = search.isFocused();
        if (focus) Draw.roundRect(ctx, sx - 2, sy - 2, sw + 4, 26, 7, Draw.withAlpha(Draw.accent(), 0x30));
        Draw.panel(ctx, sx, sy, sw, 22, 8, Draw.side(), focus ? Draw.accent() : Draw.border());
        // magnifier
        int mc = focus ? Draw.accent() : Draw.DIM;
        Draw.circle(ctx, sx + 12, sy + 10, 4, mc);
        Draw.circle(ctx, sx + 12, sy + 10, 2, Draw.solid());
        ctx.fill(sx + 15, sy + 13, sx + 17, sy + 15, mc);
        search.setX(sx + 24);
        search.setY(sy + 7);
        if (search.getText().isEmpty()) {
            String chip = "Ctrl K";
            int cw2 = Draw.textW(tr, chip) + 10;
            Draw.roundRect(ctx, sx + sw - cw2 - 5, sy + 4, cw2, 14, 4, Draw.header());
            Draw.text(ctx, tr, chip, sx + sw - cw2, sy + 7, Draw.DIM2, false);
        }
    }

    private void renderSidebar(DrawContext ctx, int mx, int my) {
        TextRenderer tr = textRenderer;
        int sx = sideX(), sy = sideY(), sh = sideH();
        Draw.roundRect(ctx, sx, sy, SIDE_W, sh, 9, Draw.side());
        // faint accent wash at the top of the sidebar, behind the brand
        Draw.roundRectV(ctx, sx, sy, SIDE_W, 56, 7, Draw.withAlpha(Draw.accent(), 0x16), Draw.withAlpha(Draw.accent(), 0x00));

        // brand
        TempestLoading.drawLogo(ctx, sx + 10, sy + 10, 28);
        Draw.text(ctx, tr, bold("TEMPEST"), sx + 44, sy + 13, Draw.TEXT, false);
        Draw.text(ctx, tr, "Client v" + version(), sx + 44, sy + 26, Draw.DIM2, false);
        ctx.fill(sx + 12, sy + 48, sx + SIDE_W - 12, sy + 49, Draw.border());

        int rh = rowH();
        List<Side> list = side();
        for (int i = 0; i < list.size(); i++) {
            Side e = list.get(i);
            if (e.kind() == 2) {
                Draw.text(ctx, tr, e.label(), sx + 14, e.y() + 3, Draw.DIM2, false);
                continue;
            }
            boolean sel = e.kind() == 0 && filter == e.cat() && open == null;
            boolean over = Draw.hit(mx, my, sx + 6, e.y(), SIDE_W - 12, rh);
            float h = approach(hov, "side" + i, over ? 1f : 0f);
            float s = approach(tog, "side" + i, sel ? 1f : 0f);
            int accent = Draw.accent();
            if (s > 0.01f) {
                Draw.roundRect(ctx, sx + 6, e.y(), SIDE_W - 12, rh, 5, Draw.withAlpha(accent, (int) (0x2A * s)));
                Draw.roundRect(ctx, sx + 6, e.y() + 4, 2, rh - 8, 1, Draw.withAlpha(accent, (int) (0xFF * s)));
            } else if (h > 0.01f) {
                Draw.roundRect(ctx, sx + 6, e.y(), SIDE_W - 12, rh, 5, Draw.withAlpha(0xFFFFFF, (int) (0x0E * h)));
            }

            int mid = e.y() + rh / 2;
            int col = Draw.lerpColor(Draw.DIM, Draw.TEXT, Math.max(h * 0.8f, s));
            if (e.kind() != 0) {   // categories are text only; the shortcut rows keep their icons
                String ic = switch (e.label()) {
                    case "Edit HUD" -> "layout";
                    case "Configs" -> "layers";
                    case "Waypoints" -> "pin";
                    case "Friends" -> "people";
                    default -> "servers";
                };
                Icons.draw(ctx, ic, sx + 13, mid - 5, 11, Draw.lerpColor(Draw.DIM2, accent, h));
            }
            Draw.text(ctx, tr, e.label(), e.kind() == 0 ? sx + 16 : sx + 29, mid - 4, col, false);

            if (e.kind() == 0) {
                int on = 0;
                for (Module mm : ModuleManager.all()) if ((e.cat() == null || mm.getCategory() == e.cat()) && mm.isEnabled()) on++;
                if (on > 0) {
                    String cs = Integer.toString(on);
                    int pw = Draw.textW(tr, cs) + 9;
                    int px = sx + SIDE_W - 12 - pw;
                    Draw.roundRect(ctx, px, mid - 6, pw, 12, 6, sel ? Draw.withAlpha(accent, 0x38) : Draw.header());
                    Draw.text(ctx, tr, cs, px + 4, mid - 4, sel ? accent : Draw.DIM2, false);
                }
            } else {
                Draw.chevron(ctx, sx + SIDE_W - 17, mid, Draw.lerpColor(Draw.DIM2, accent, h));
            }
        }

        if (sh >= 330) {
            int px = sx + 8, py = sy + sh - 46, pw = SIDE_W - 16;
            Draw.panel(ctx, px, py, pw, 38, 8, Draw.card(), Draw.border());
            drawPlayerHead(ctx, px + 7, py + 7, 24);
            Draw.circle(ctx, px + 29, py + 29, 3, Draw.solid());
            Draw.circle(ctx, px + 29, py + 29, 2, 0xFF2ECC71);
            Draw.text(ctx, tr, Draw.trim(tr, playerName(), pw - 44), px + 38, py + 9, Draw.TEXT, false);
            Draw.text(ctx, tr, "RShift to close", px + 38, py + 21, Draw.DIM2, false);
        }
    }

    private KeySetting keyOf(Module m) {
        for (Setting s : m.getSettings()) if (s instanceof KeySetting k) return k;
        return null;
    }

    /** A key that is not bound shows as None / Unknown (or nothing) in vanilla. */
    private static boolean unbound(String label) {
        return label == null || label.isBlank() || label.equals("-")
                || label.equalsIgnoreCase("None") || label.equalsIgnoreCase("Unknown") || label.equalsIgnoreCase("Not bound");
    }

    /** Rounded icon tile with the module's item on it; tinted by category, stronger when the module is on. */
    private void drawTile(DrawContext ctx, Module m, int x, int y, int size, float on) {
        int cc = Draw.catColor(m.getCategory());
        Draw.roundRect(ctx, x, y, size, size, 7, Draw.withAlpha(cc, Math.round(0x1E + 0x2C * on)));
        int g = Math.round(size * 0.58f);
        Icons.draw(ctx, Icons.forModule(m.getId()), x + (size - g) / 2, y + (size - g) / 2, g, 0xFFFFFFFF);
    }

    private static final int ON_GREEN = 0xFF2FA866, OFF_RED = 0xFFC93756;

    private void renderGrid(DrawContext ctx, int mx, int my) {
        TextRenderer tr = textRenderer;
        List<Module> list = visible();
        int cx = cx(), cy = cy(), cw = cw(), ch = ch();
        if (list.isEmpty()) {
            Draw.centered(ctx, tr, "No mods found", cx + cw / 2, cy + ch / 2 - 10, Draw.DIM);
            Draw.centered(ctx, tr, "Try a different search", cx + cw / 2, cy + ch / 2 + 4, Draw.DIM2);
            return;
        }
        int cols = cols(), cardW = cardW();
        boolean inContent = my >= cy && my < cy + ch && mx >= cx && mx < cx + cw && picker == null;
        List<String> favs = TempestConfig.get().favoriteMods;
        // Only visit cards that intersect the viewport.
        int pitch = MCARD_H + GAP;
        int firstRow = Math.max(0, (int) Math.floor((scroll - MCARD_H) / pitch) + 1);
        int lastRow = Math.min((list.size() + cols - 1) / cols - 1, (int) Math.floor((scroll + ch) / pitch));
        for (int row = firstRow; row <= lastRow; row++) {
            int start = row * cols;
            int end = Math.min(list.size(), start + cols);
            for (int i = start; i < end; i++) {
                Module m = list.get(i);
                int x = cardX(i % cols), y = cardY(i / cols);
                boolean on = m.isEnabled();
                boolean hv = inContent && Draw.hit(mx, my, x, y, cardW, MCARD_H);
                float h = approach(hov, m, hv ? 1f : 0f);
                float t = approach(tog, m, on ? 1f : 0f);

                // card: soft translucent grey, a touch lighter on hover
                int fill = Draw.lerpColor(Draw.withAlpha(0x2C2F37, 0x8C), Draw.withAlpha(0x3A3E48, 0xA6), h);
                int border = Draw.lerpColor(Draw.withAlpha(0xFFFFFF, 0x1C), Draw.withAlpha(0xFFFFFF, 0x3A), h);
                Draw.panel(ctx, x, y, cardW, MCARD_H, 7, fill, border);

                // favourite star, top-left
                boolean fav = favs.contains(m.getId());
                boolean overStar = inContent && Draw.hit(mx, my, x + 3, y + 3, 14, 14);
                if (fav || h > 0.02f) {
                    int sc = fav ? 0xFFFFD34D : Draw.withAlpha(0xFFFFFF, overStar ? 0xE0 : (int) (0x70 * h));
                    Icons.draw(ctx, fav ? "star_filled" : "star_outline", x + 5, y + 5, 10, sc);
                }

                // keybind chip, top-right (only when one is bound)
                KeySetting ks = keyOf(m);
                if (ks != null && !unbound(ks.label())) {
                    String kl = Draw.trim(tr, ks.label(), 40);
                    int kw = Draw.textW(tr, kl) + 8;
                    Draw.roundRect(ctx, x + cardW - kw - 5, y + 5, kw, 12, 4, Draw.withAlpha(0x000000, 0x50));
                    Draw.text(ctx, tr, kl, x + cardW - kw - 1, y + 7, Draw.DIM, false);
                }

                // icon and name
                int icon = 34;
                int iconCol = Draw.lerpColor(0xFFD8DCE6, 0xFFFFFFFF, Math.max(t, h));
                Icons.draw(ctx, Icons.forModule(m.getId()), x + (cardW - icon) / 2, y + 14, icon, iconCol);
                String nm = Draw.ellipsize(tr, m.getName(), cardW - 12);
                int nameCol = Draw.lerpColor(0xFF9EA3B0, 0xFFFFFFFF, Math.max(t, h * 0.6f));
                Draw.text(ctx, tr, nm, x + (cardW - Draw.textW(tr, nm)) / 2, y + 58, nameCol, false);
                if (inContent && Draw.hit(mx, my, x, y + 10, cardW, 60)) { tip = m.getDescription(); tipX = mx; tipY = y + 12; }

                // OPTIONS bar with a gear segment
                int bx = x + 6, bw = cardW - 12, oy = y + optY(), gw = gearW();
                boolean overOpt = inContent && Draw.hit(mx, my, bx, oy, bw - gw - 2, OPT_H);
                boolean overGear = inContent && Draw.hit(mx, my, bx + bw - gw, oy, gw, OPT_H);
                float po = approach(hov, m.getId() + "#opt", overOpt ? 1f : 0f);
                float pg = approach(hov, m.getId() + "#gear", overGear ? 1f : 0f);
                Draw.roundRect(ctx, bx, oy, bw - gw - 2, OPT_H, 3, Draw.lerpColor(Draw.withAlpha(0x15161B, 0xA0), Draw.withAlpha(0x4A4E5A, 0xC0), po));
                Draw.roundRect(ctx, bx + bw - gw, oy, gw, OPT_H, 3, Draw.lerpColor(Draw.withAlpha(0x15161B, 0xA0), Draw.withAlpha(0x4A4E5A, 0xC0), pg));
                Draw.spaced(ctx, tr, "OPTIONS", bx + (bw - gw - 2) / 2, oy + (OPT_H - 8) / 2, 0xFFFFFFFF, true, 2);
                Icons.draw(ctx, "gear", bx + bw - gw + (gw - 10) / 2, oy + (OPT_H - 10) / 2, 10, 0xFFFFFFFF);

                // ENABLED / DISABLED bar
                int ty = y + togY();
                boolean overTog = inContent && Draw.hit(mx, my, bx, ty, bw, TOG_H);
                float pt = approach(hov, m.getId() + "#tog", overTog ? 1f : 0f);
                if (m.alwaysOn()) {
                    Draw.roundRect(ctx, bx, ty, bw, TOG_H, 3, Draw.withAlpha(0x15161B, 0xA0));
                    Draw.spaced(ctx, tr, "ALWAYS ON", bx + bw / 2, ty + (TOG_H - 8) / 2, 0xFF9EA3B0, true, 2);
                } else {
                    int base = Draw.lerpColor(OFF_RED, ON_GREEN, t);
                    int col = Draw.lerpColor(Draw.withAlpha(base, 0xD8), Draw.lerpColor(base, 0xFFFFFFFF, 0.12f), pt);
                    Draw.roundRect(ctx, bx, ty, bw, TOG_H, 3, col);
                    Draw.hairline(ctx, bx + 3, ty, bw - 6, 0xFFFFFF, 0x30);
                    Draw.spaced(ctx, tr, on ? "ENABLED" : "DISABLED", bx + bw / 2, ty + (TOG_H - 8) / 2, 0xFFFFFFFF, true, 2);
                }
            }
        }
    }

    private void renderSettings(DrawContext ctx, int mx, int my) {
        TextRenderer tr = textRenderer;
        int rx = rx(), rw = rw(), top = top();
        boolean inContent = my >= cy() && my < cy() + ch();

        // back pill
        float bh = approach(hov, "back", inContent && Draw.hit(mx, my, rx, top, 56, 18) ? 1f : 0f);
        int backCol = Draw.lerpColor(Draw.DIM, Draw.TEXT, bh);
        if (bh > 0.01f) Draw.roundRect(ctx, rx - 3, top - 1, 52, 20, 6, Draw.withAlpha(0xFFFFFF, (int) (0x10 * bh)));
        Draw.chevron(ctx, rx + 5, top + 9, backCol, true);
        Draw.text(ctx, tr, "Back", rx + 14, top + 5, backCol, false);
        Draw.text(ctx, tr, (filter == null ? "All Mods" : filter.label) + "  /  " + open.getName(), rx + 56, top + 5, Draw.DIM2, false);

        // header card
        int hy = top + 26;
        boolean on = open.isEnabled();
        float t = approach(tog, open, on ? 1f : 0f);
        Draw.panel(ctx, rx, hy, rw, HDR_H, 8, Draw.lerpColor(Draw.card(), Draw.lerpColor(Draw.card(), Draw.accent(), 0.07f), t), Draw.lerpColor(Draw.border(), Draw.accent(), t * 0.5f));
        if (t > 0.01f) Draw.roundRectV(ctx, rx + 1, hy + 1, rw - 2, 26, 7, Draw.withAlpha(Draw.accent(), (int) (0x1E * t)), Draw.withAlpha(Draw.accent(), 0));
        drawTile(ctx, open, rx + 9, hy + (HDR_H - 34) / 2, 34, t);
        Draw.text(ctx, tr, bold(open.getName()), rx + 54, hy + 13, Draw.TEXT, false);
        Draw.text(ctx, tr, Draw.ellipsize(tr, open.getDescription(), rw - 54 - 64), rx + 54, hy + 28, Draw.DIM, false);
        if (!open.alwaysOn()) Draw.toggle(ctx, rx + rw - 42, hy + (HDR_H - 14) / 2, t);

        List<Setting> list = open.getSettings();
        if (list.isEmpty()) {
            Draw.centered(ctx, tr, "No settings for this mod", rx + rw / 2, rowY(0) + 8, Draw.DIM2);
        }
        // Settings are virtualized too: skip rows that cannot be seen this frame.
        int firstSetting = Math.max(0, (int) Math.floor((scroll - ROWS_TOP - ROW_H) / PITCH) + 1);
        int lastSetting = Math.min(list.size() - 1,
                (int) Math.floor((scroll + ch() - ROWS_TOP) / PITCH));
        for (int i = firstSetting; i <= lastSetting; i++) {
            Setting s = list.get(i);
            int ry = rowY(i);
            boolean rowHover = inContent && Draw.hit(mx, my, rx, ry, rw, ROW_H);
            float rh = approach(hov, s, rowHover ? 1f : 0f);
            Draw.surface(ctx, rx, ry, rw, ROW_H, 6, Draw.lerpColor(Draw.card(), Draw.cardHover(), rh * 0.6f));
            Draw.text(ctx, tr, s.name, rx + 14, ry + (ROW_H - 8) / 2, Draw.lerpColor(0xFFC4CBE4, Draw.TEXT, rh), false);

            int mid = ry + ROW_H / 2;
            if (s instanceof BoolSetting b) {
                Draw.toggle(ctx, rx + rw - 42, mid - 7, approach(tog, s, b.get() ? 1f : 0f));
            } else if (s instanceof NumberSetting n) {
                int tx = rx + rw - 150, ty = mid - 2;
                Draw.roundRect(ctx, tx, ty, 100, 4, 2, Draw.off());
                int fw = (int) (100 * n.fraction());
                if (fw > 0) Draw.roundRect(ctx, tx, ty, Math.max(4, fw), 4, 2, Draw.accent());
                if (dragging == s || rh > 0.5f) Draw.circle(ctx, tx + fw, mid, 8, Draw.withAlpha(Draw.accent(), dragging == s ? 0x44 : 0x26));
                Draw.circle(ctx, tx + fw, mid, 6, Draw.accent());
                Draw.circle(ctx, tx + fw, mid, 4, 0xFFFFFFFF);
                String v = n.display();
                Draw.text(ctx, tr, v, rx + rw - 12 - Draw.textW(tr, v), mid - 4, Draw.accent(), false);
            } else if (s instanceof ModeSetting mo) {
                int bx = rx + rw - 122, byy = ry + BTN_Y;
                Draw.roundRect(ctx, bx, byy, 112, 18, 6, Draw.header());
                boolean lh = inContent && Draw.hit(mx, my, bx, byy, 22, 18), rhv = inContent && Draw.hit(mx, my, bx + 90, byy, 22, 18);
                Draw.text(ctx, tr, "<", bx + 8, byy + 5, lh ? Draw.accent() : Draw.DIM, false);
                Draw.text(ctx, tr, ">", bx + 112 - 14, byy + 5, rhv ? Draw.accent() : Draw.DIM, false);
                String v = Draw.trim(tr, mo.get(), 64);
                Draw.text(ctx, tr, v, bx + (112 - Draw.textW(tr, v)) / 2, byy + 5, Draw.TEXT, false);
            } else if (s instanceof KeySetting ks) {
                int bx = rx + rw - 122, byy = ry + BTN_Y;
                boolean listen = listening == ks;
                boolean hvr = inContent && Draw.hit(mx, my, bx, byy, 112, 18);
                Draw.panel(ctx, bx, byy, 112, 18, 6, listen ? Draw.withAlpha(Draw.accent(), 0x40) : Draw.header(),
                        listen ? Draw.accent() : hvr ? Draw.off() : Draw.border());
                String t2 = Draw.trim(tr, listen ? "Press a key..." : ks.label(), 98);
                Draw.text(ctx, tr, t2, bx + (112 - Draw.textW(tr, t2)) / 2, byy + 5, listen ? Draw.accent() : Draw.TEXT, false);
            } else if (s instanceof ActionSetting as) {
                int bw = 70, bx = rx + rw - bw - 10, byy = ry + BTN_Y;
                float ah = approach(hov, as, inContent && Draw.hit(mx, my, bx, byy, bw, 18) ? 1f : 0f);
                Draw.roundRect(ctx, bx, byy, bw, 18, 6, Draw.lerpColor(Draw.off(), Draw.accent(), ah));
                Draw.text(ctx, tr, as.label, bx + (bw - Draw.textW(tr, as.label)) / 2, byy + 5, Draw.TEXT, false);
            } else if (s instanceof ColorSetting c) {
                boolean inPalette = false;
                for (int k = 0; k < ColorSetting.PALETTE.length; k++) {
                    int ccx = rx + rw - 18 - (ColorSetting.PALETTE.length - 1 - k) * 16;
                    int col = ColorSetting.PALETTE[k];
                    if (col == c.get()) { Draw.circle(ctx, ccx, mid, 8, 0xFFFFFFFF); inPalette = true; }
                    Draw.circle(ctx, ccx, mid, 6, col);
                }
                // custom color tile (rainbow) opens the picker; shows the current color when it is not a palette color
                int kx = customX(rx, rw), ky = mid;
                if (!inPalette) Draw.circle(ctx, kx, ky, 8, 0xFFFFFFFF);
                for (int dy = -6; dy <= 6; dy++) {
                    int half = (int) Math.round(Math.sqrt(Math.max(0, 36 - dy * dy + 0.25)));
                    for (int dx = -half; dx <= half; dx++) {
                        ctx.fill(kx + dx, ky + dy, kx + dx + 1, ky + dy + 1, inPalette ? Draw.hsvToRgb((dx + 6) / 13f, 0.85f, 1f) : (dx < 0 ? c.get() : Draw.hsvToRgb((dx + 6) / 13f, 0.85f, 1f)));
                    }
                }
            }
        }
        if (open.keyHint != null) {
            Draw.text(ctx, tr, Text.literal(Draw.trim(tr, open.keyHint, rw)).formatted(Formatting.ITALIC),
                    rx + 2, rowY(list.size()) + 4, Draw.DIM2, false);
        }
    }

    private int customX(int rx, int rw) { return rx + rw - 18 - ColorSetting.PALETTE.length * 16; }

    private int pkX() { return pX + (pW - PK_W) / 2; }
    private int pkY() { return pY + (pH - PK_H) / 2; }

    private void openPicker(ColorSetting c) {
        picker = c;
        float[] hsv = Draw.rgbToHsv(c.get());
        pickH = hsv[0];
        pickS = hsv[1];
        pickV = hsv[2];
    }

    private void applyPick() { if (picker != null) picker.set(Draw.hsvToRgb(pickH, pickS, pickV)); }

    private void renderPicker(DrawContext ctx, int mx, int my) {
        if (picker == null) return;
        TextRenderer tr = textRenderer;
        ctx.fill(pX, pY, pX + pW, pY + pH, 0xA0000000);
        int x = pkX(), y = pkY();
        Draw.shadow(ctx, x, y, PK_W, PK_H, 8, 10);
        Draw.shadow(ctx, x, y, PK_W, PK_H, 8, 8);
        Draw.panel(ctx, x, y, PK_W, PK_H, 9, Draw.withAlpha(Draw.solid(), 0xF0), Draw.withAlpha(Draw.accent(), 0x70));
        Draw.text(ctx, tr, bold(picker.name), x + 12, y + 10, Draw.TEXT, false);

        int sx = x + 12, sy = y + 28;
        int hueC = Draw.hsvToRgb(pickH, 1f, 1f);
        for (int i = 0; i < SV_W; i++) {
            int top = Draw.hsvToRgb(pickH, i / (float) (SV_W - 1), 1f);
            ctx.fillGradient(sx + i, sy, sx + i + 1, sy + SV_H, top, 0xFF000000);
        }
        Draw.outline(ctx, sx - 1, sy - 1, SV_W + 2, SV_H + 2, Draw.border());
        int px = sx + Math.round(pickS * (SV_W - 1)), py = sy + Math.round((1f - pickV) * (SV_H - 1));
        Draw.outline(ctx, px - 3, py - 3, 7, 7, 0xFF000000);
        Draw.outline(ctx, px - 2, py - 2, 5, 5, 0xFFFFFFFF);

        int hx = sx + SV_W + 10;
        for (int i = 0; i < SV_H; i++) ctx.fill(hx, sy + i, hx + 14, sy + i + 1, Draw.hsvToRgb(i / (float) (SV_H - 1), 1f, 1f));
        Draw.outline(ctx, hx - 1, sy - 1, 16, SV_H + 2, Draw.border());
        int hy = sy + Math.round(pickH * (SV_H - 1));
        ctx.fill(hx - 3, hy - 1, hx + 17, hy + 2, 0xFF000000);
        ctx.fill(hx - 2, hy, hx + 16, hy + 1, 0xFFFFFFFF);

        int cur = picker.get();
        Draw.roundRect(ctx, x + 12, y + PK_H - 34, 22, 22, 6, cur);
        Draw.outline(ctx, x + 12, y + PK_H - 34, 22, 22, Draw.border());
        Draw.text(ctx, tr, String.format("#%06X", cur & 0xFFFFFF), x + 40, y + PK_H - 27, Draw.TEXT, false);
        boolean dh = Draw.hit(mx, my, x + PK_W - 62, y + PK_H - 34, 50, 22);
        Draw.roundRect(ctx, x + PK_W - 62, y + PK_H - 34, 50, 22, 5, Draw.lerpColor(Draw.accent(), 0xFFFFFFFF, dh ? 0.15f : 0f));
        Draw.text(ctx, tr, "Done", x + PK_W - 62 + (50 - Draw.textW(tr, "Done")) / 2, y + PK_H - 27, 0xFFFFFFFF, false);
    }

    private boolean clickPicker(double mx, double my) {
        int x = pkX(), y = pkY();
        if (!Draw.hit(mx, my, x, y, PK_W, PK_H)) { picker = null; return true; }
        int sx = x + 12, sy = y + 28, hx = sx + SV_W + 10;
        if (Draw.hit(mx, my, sx, sy, SV_W, SV_H)) { pickDrag = 1; dragPicker(mx, my); }
        else if (Draw.hit(mx, my, hx - 2, sy, 18, SV_H)) { pickDrag = 2; dragPicker(mx, my); }
        else if (Draw.hit(mx, my, x + PK_W - 62, y + PK_H - 34, 50, 22)) picker = null;
        return true;
    }

    private void dragPicker(double mx, double my) {
        int x = pkX(), y = pkY(), sx = x + 12, sy = y + 28;
        if (pickDrag == 1) {
            pickS = (float) Math.max(0, Math.min(1, (mx - sx) / (SV_W - 1.0)));
            pickV = 1f - (float) Math.max(0, Math.min(1, (my - sy) / (SV_H - 1.0)));
        } else if (pickDrag == 2) {
            pickH = (float) Math.max(0, Math.min(0.999, (my - sy) / (SV_H - 1.0)));
        }
        applyPick();
    }

    // ---------- input ----------
    private void renderSuggestions(DrawContext ctx, int mx, int my) {
        suggestions = search.isFocused() ? dev.tempest.util.SearchQuery.suggest(search.getText(), searchFilters(), 6) : List.of();
        if (suggestions.isEmpty()) return;
        int x = searchX(), y = searchY() + 25, w = searchW();
        Draw.panel(ctx, x, y, w, suggestions.size() * 13 + 6, 6, Draw.solid(), Draw.withAlpha(Draw.accent(), 0x90));
        for (int i = 0; i < suggestions.size(); i++) {
            int ry = y + 3 + i * 13;
            boolean hv = Draw.hit(mx, my, x, ry, w, 13);
            if (hv || i == 0) Draw.roundRect(ctx, x + 2, ry, w - 4, 13, 3, Draw.withAlpha(Draw.accent(), hv ? 0x50 : 0x28));
            Draw.text(ctx, textRenderer, suggestions.get(i), x + 8, ry + 3, 0xFFFFFFFF, false);
            if (i == 0) Draw.text(ctx, textRenderer, "Tab", x + w - 26, ry + 3, Draw.DIM2, false);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!suggestions.isEmpty()) {
            int x = searchX(), y = searchY() + 25;
            for (int i = 0; i < suggestions.size(); i++) {
                if (Draw.hit(click.x(), click.y(), x, y + 3 + i * 13, searchW(), 13)) {
                    search.setText(dev.tempest.util.SearchQuery.complete(search.getText(), suggestions.get(i)));
                    return true;
                }
            }
        }
        if (picker != null) return clickPicker(click.x(), click.y());
        if (super.mouseClicked(click, doubled)) return true;
        prevListen = listening;
        listening = null;
        double mx = click.x(), my = click.y();
        int btn = click.button();
        if (!Draw.hit(mx, my, pX, pY, pW, pH)) return false;

        // search pill
        if (Draw.hit(mx, my, searchX(), searchY(), searchW(), 22)) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        search.setFocused(false);
        setFocused(null);

        int rh = rowH();
        for (Side e : side()) {
            if (e.kind() == 2 || !Draw.hit(mx, my, sideX() + 6, e.y(), SIDE_W - 12, rh)) continue;
            if (e.kind() == 0) {
                filter = e.cat();
                open = null;
                scrollTarget = 0;
            } else if (client != null) {
                switch (e.label()) {
                    case "Edit HUD" -> client.setScreen(new HudEditorScreen(this));
                    case "Configs" -> client.setScreen(new ConfigsScreen(this));
                    case "Waypoints" -> client.setScreen(new WaypointScreen(this));
                    case "Friends" -> client.setScreen(new SocialScreen(this));
                    default -> client.setScreen(new FavoritesScreen(this));
                }
            }
            return true;
        }

        if (mx < cx() || my < cy()) return false;
        return open == null ? clickGrid(mx, my, btn) : clickSettings(mx, my, btn);
    }

    private boolean clickGrid(double mx, double my, int btn) {
        List<Module> list = visible();
        int cols = cols(), cardW = cardW();
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int x = cardX(i % cols), y = cardY(i / cols);
            if (!Draw.hit(mx, my, x, y, cardW, MCARD_H)) continue;
            int bx = x + 6, bw = cardW - 12, gw = gearW();
            if (btn == 0 && Draw.hit(mx, my, x + 3, y + 3, 14, 14)) {
                List<String> favs = TempestConfig.get().favoriteMods;
                if (!favs.remove(m.getId())) favs.add(m.getId());
                TempestConfig.save();
                return true;
            }
            if (btn == 0 && Draw.hit(mx, my, bx + bw - gw, y + optY(), gw, OPT_H)) {
                // gear: HUD mods go straight to the layout editor, everything else to its settings
                if (m instanceof dev.tempest.module.HudModule && client != null) client.setScreen(new HudEditorScreen(this));
                else openSettings(m);
                return true;
            }
            if (btn == 1 || Draw.hit(mx, my, bx, y + optY(), bw - gw - 2, OPT_H)) {
                openSettings(m);
                return true;
            }
            if (btn == 0 && Draw.hit(mx, my, bx, y + togY(), bw, TOG_H)) {
                if (!m.alwaysOn()) m.toggle();
                return true;
            }
            // clicking anywhere else on the card (icon / name) toggles too, like before
            if (btn == 0 && !m.alwaysOn()) m.toggle();
            return true;
        }
        return false;
    }

    private void openSettings(Module m) {
        open = m;
        scrollTarget = 0;
        scroll = 0;
        scrollVel = 0;
    }

    /** Opens the mod menu on a given category (used by the main menu). */
    public static ClickGuiScreen openTo(Category c, Screen parent) {
        savedFilter = c;
        savedOpenId = null;
        savedScroll = 0;
        savedScrollTarget = 0;
        return new ClickGuiScreen(parent);
    }

    private boolean clickSettings(double mx, double my, int btn) {
        int rx = rx(), rw = rw(), top = top();
        if (Draw.hit(mx, my, rx, top, 56, 18)) {
            open = null;
            scrollTarget = 0;
            scroll = 0;
            scrollVel = 0;
            return true;
        }
        if (Draw.hit(mx, my, rx + rw - 52, top + 26, 52, HDR_H)) {
            open.toggle();
            return true;
        }
        List<Setting> list = open.getSettings();
        for (int i = 0; i < list.size(); i++) {
            Setting s = list.get(i);
            int ry = rowY(i);
            if (!Draw.hit(mx, my, rx, ry, rw, ROW_H)) continue;

            if (s instanceof BoolSetting b) {
                b.toggle();
            } else if (s instanceof NumberSetting n) {
                int tx = rx + rw - 150;
                if (mx >= tx - 8 && mx <= tx + 108) {
                    dragging = n;
                    n.setFraction((mx - tx) / 100.0);
                }
            } else if (s instanceof ModeSetting mo) {
                int bx = rx + rw - 122;
                if (mx < bx + 56) mo.cycle(-1); else mo.cycle(1);
            } else if (s instanceof KeySetting ks) {
                if (btn == 1) { ks.assign(-1); listening = null; }
                else listening = (prevListen == ks) ? null : ks;
            } else if (s instanceof ActionSetting as) {
                int bw = 70, bx = rx + rw - bw - 10;
                if (Draw.hit(mx, my, bx, ry + BTN_Y, bw, 18)) as.run();
            } else if (s instanceof ColorSetting c) {
                for (int k = 0; k < ColorSetting.PALETTE.length; k++) {
                    int ccx = rx + rw - 18 - (ColorSetting.PALETTE.length - 1 - k) * 16, ccy = ry + ROW_H / 2;
                    if (Draw.hit(mx, my, ccx - 7, ccy - 7, 14, 14)) c.set(ColorSetting.PALETTE[k]);
                }
                if (Draw.hit(mx, my, customX(rx, rw) - 7, ry + ROW_H / 2 - 7, 14, 14)) openPicker(c);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (picker != null && pickDrag != 0) { dragPicker(click.x(), click.y()); return true; }
        if (dragging instanceof NumberSetting n && open != null) {
            int tx = rx() + rw() - 150;
            n.setFraction((click.x() - tx) / 100.0);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        pickDrag = 0;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (picker != null) return true;
        // Ignore wheel events over the sidebar/header and use a responsive, frame-rate
        // independent scroll step. This also prevents accidental menu movement elsewhere.
        if (mouseX < cx() || mouseX > cx() + cw() || mouseY < cy() || mouseY > cy() + ch())
            return false;
        double step = 46.0;
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - vertical * step));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_TAB && search.isFocused() && !suggestions.isEmpty()) {
            search.setText(dev.tempest.util.SearchQuery.complete(search.getText(), suggestions.get(0)));
            search.setCursorToEnd(false);
            return true;
        }
        if (picker == null && input.key() == GLFW.GLFW_KEY_K && Keys.ctrl()) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (picker != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER) picker = null;
            return true;
        }
        if (listening != null) {
            listening.assign(input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key());
            listening = null;
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT && !search.isFocused()) {
            closeAll();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        savedX = pX;
        savedY = pY;
        savedScroll = scroll;
        savedScrollTarget = scrollTarget;
        savedFilter = filter;
        savedOpenId = open == null ? null : open.getId();
        TempestConfig.save();
    }
}
