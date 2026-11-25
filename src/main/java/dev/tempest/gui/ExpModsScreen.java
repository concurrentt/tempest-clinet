package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * EXPERIMENTAL mod list (Exp Tempest), opened from the orbit menu. No boxed window: the mods sit straight on the
 * purple night as glass capsules in two columns. Each capsule has a round orb with the mod's icon (it fills with your
 * accent colour and glows when the mod is on), the name and a line about it, and a switch. A row of round category
 * buttons runs along the top. Click a capsule to turn the mod on or off; right-click it or press its gear for its
 * options (they open right here; where depends on the menu style).
 */
public class ExpModsScreen extends Screen {
    private static final Category[] CATS = Category.values();
    private static final int ROW_H = 36, GAP = 8, CHIP = 22;

    private final Screen parent;
    private final String startSearch;
    private Category filter;
    private TextFieldWidget search;
    private double scroll, scrollTarget;
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    /*
     * Options open inside this screen, and each style shows them its own way:
     * Orbit Ring - a card that zooms up in the middle; Radial Wheel - a sheet that slides up from the bottom;
     * anything else (Split Hub, Constellation) - a panel that slides in from the right edge.
     */
    private ExpSettings opts;
    private final String look = switch (ExpMenus.current()) {
        case "orbit" -> "zoom";
        case "radial" -> "sheet";
        default -> "side";
    };
    private static final float OPTS_SECS = 0.26f;

    public ExpModsScreen(Category filter, Screen parent, String search) {
        super(Text.literal("Mods"));
        this.filter = filter;
        this.parent = parent;
        this.startSearch = search == null ? "" : search;
    }

    // ---------------------------------------------------------------- layout
    private int contentW() { return Math.min(width - 40, 660); }
    private int x0() { return (width - contentW()) / 2; }
    private int cols() { return contentW() >= 440 ? 2 : 1; }
    private int rowW() { return (contentW() - GAP * (cols() - 1)) / cols(); }
    private int chipsY() { return 14; }
    private int titleY() { return chipsY() + CHIP + 16; }
    private int listY() { return titleY() + 38; }
    private int listBottom() { return height - 24; }
    private int searchW() { return Math.min(200, contentW() / 3); }
    private int searchX() { return x0() + contentW() - searchW(); }
    private int searchY() { return titleY() + 2; }

    /** Category chips: "All" then every category, centred. Index 0 = All. */
    private int chipCount() { return CATS.length + 1; }
    private int chipX(int i) {
        int total = chipCount() * CHIP + (chipCount() - 1) * 6;
        return width / 2 - total / 2 + i * (CHIP + 6);
    }
    private Category chipCat(int i) { return i == 0 ? null : CATS[i - 1]; }

    @Override
    protected void init() {
        String old = search != null ? search.getText() : startSearch;
        search = new TextFieldWidget(textRenderer, 0, 0, searchW() - 30, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setPlaceholder(Text.literal("Search mods"));
        search.setMaxLength(64);
        search.setText(old);
        search.setChangedListener(s -> scrollTarget = 0);
        search.setX(searchX() + 22);
        search.setY(searchY() + 6);
        addDrawableChild(search);
        if (!old.isEmpty()) {
            setFocused(search);
            search.setFocused(true);
            search.setCursorToEnd(false);
        }
    }

    private List<Module> visible() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<String> favs = TempestConfig.get().favoriteMods;
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (filter != null && m.getCategory() != filter) continue;
            if (!q.isEmpty() && !(m.getName() + " " + m.getDescription()).toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(m);
        }
        out.sort((a, b) -> {
            int c = Boolean.compare(b.alwaysOn(), a.alwaysOn());
            return c != 0 ? c : Boolean.compare(favs.contains(b.getId()), favs.contains(a.getId()));
        });
        return out;
    }

    private double maxScroll(int count) {
        int rows = (count + cols() - 1) / cols();
        return Math.max(0, rows * (ROW_H + GAP) - GAP - (listBottom() - listY()) + 6);
    }

    private static String title(Category c) {
        if (c == null) return "All mods";
        return switch (c) {
            case HUD -> "HUD";
            case PVP -> "Combat";
            case RENDER -> "Render";
            case VISUALS -> "Visuals";
            case QOL -> "Quality of life";
            case UTILITY -> "Utility";
            case CHAT -> "Chat";
            case CLIENT -> "Client";
        };
    }

    private float intro(float delay) {
        if (!dev.tempest.modules.ClientSettingsModule.animationsOn()) return 1f;
        return MenuKit.ease(((System.nanoTime() - openedAt) / 1e9f - delay) / 0.4f);
    }

    // ---------------------------------------------------------------- render
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() {
        MenuBlur.restore();
        if (opts != null) opts.close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (opts != null && opts.gone(OPTS_SECS)) opts = null;
        if (opts == null) { renderMain(ctx, mx, my, delta); return; }
        renderMain(ctx, -9999, -9999, delta);
        TextRenderer tr = textRenderer;
        float p = MenuKit.ease(opts.progress(OPTS_SECS));
        boolean live = p >= 1f && !opts.closing();
        ctx.fill(0, 0, width, height, MenuKit.fa(0x90060410, p));
        int[] r = optsRect();
        switch (look) {
            case "zoom" -> {
                if (live) { opts.render(ctx, tr, r[0], r[1], r[2], r[3], mx, my, 1f, true); break; }
                float k = 0.85f + 0.15f * p;
                var m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(r[0] + r[2] / 2f, r[1] + r[3] / 2f);
                m.scale(k, k);
                opts.render(ctx, tr, -r[2] / 2, -r[3] / 2, r[2], r[3], mx, my, p, false);
                m.popMatrix();
            }
            case "sheet" -> opts.render(ctx, tr, r[0], r[1] + Math.round((1f - p) * (r[3] + 20)), r[2], r[3], mx, my, Math.max(0.01f, p), live);
            default -> opts.render(ctx, tr, r[0] + Math.round((1f - p) * (r[2] + 20)), r[1], r[2], r[3], mx, my, Math.max(0.01f, p), live);
        }
    }

    /** Where the options sit for this style: {x, y, w, h}. */
    private int[] optsRect() {
        return switch (look) {
            case "zoom" -> {
                int w = Math.min(300, width - 40), h = Math.min(300, height - 50);
                yield new int[]{width / 2 - w / 2, height / 2 - h / 2, w, h};
            }
            case "sheet" -> {
                int w = Math.min(contentW(), 440), h = Math.min(Math.round(height * 0.62f), 300);
                yield new int[]{width / 2 - w / 2, height - h + 10, w, h};    // runs off the bottom, like a sheet
            }
            default -> {
                int w = Math.min(240, Math.max(180, width / 3));
                yield new int[]{width - w - 8, 8, w, height - 16};
            }
        };
    }

    private void renderMain(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        TextRenderer tr = textRenderer;
        float t = MenuKit.time();
        float e = intro(0f);
        ExpOrbitScreen.paintBackground(ctx, width, height, width / 2, height / 3, t, e);

        List<Module> mods = visible();
        scrollTarget = Math.max(0, Math.min(maxScroll(mods.size()), scrollTarget));
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 14f));

        // back button + category chips
        int bx = x0(), by = chipsY();
        boolean bh = Draw.hit(mx, my, bx, by, CHIP, CHIP);
        orb(ctx, bx, by, CHIP, "back", Draw.hoverProgress("expm_back", bh) * 0.4f, false, e);
        for (int i = 0; i < chipCount(); i++) {
            Category c = chipCat(i);
            int cx = chipX(i);
            boolean sel = c == filter;
            boolean hv = Draw.hit(mx, my, cx, by, CHIP, CHIP);
            float p = Draw.hoverProgress("expm_chip" + i, sel);
            orb(ctx, cx, by, CHIP, Icons.forCategory(c), Math.max(p, Draw.hoverProgress("expm_chiph" + i, hv) * 0.35f), sel, e);
            if (hv && !sel) {
                String l = title(c);
                int lw = Draw.textW(tr, l) + 10;
                Draw.panel(ctx, cx + CHIP / 2 - lw / 2, by + CHIP + 4, lw, 13, 6, MenuKit.fa(0xF01A1530, e), MenuKit.fa(0x40FFFFFF, e));
                Draw.text(ctx, tr, l, cx + CHIP / 2 - lw / 2 + 5, by + CHIP + 7, MenuKit.fa(0xFFFFFFFF, e), false);
            }
        }

        // title + counts
        int on = 0;
        for (Module m : mods) if (m.isEnabled()) on++;
        float et = intro(0.06f);
        MenuKit.scaledLeft(ctx, tr, Draw.bold(title(filter)), x0(), titleY() + Math.round((1f - et) * 6), 2.2f, MenuKit.fa(0xFFF3F0FF, et), false);
        String counts = mods.size() + (mods.size() == 1 ? " mod" : " mods") + "   " + on + " on";
        Draw.text(ctx, tr, counts, x0() + 1, titleY() + 22, MenuKit.fa(0xB8D6CFF2, et), false);

        // search capsule
        int sx = searchX(), sy = searchY(), sw = searchW(), sh = 20;
        boolean sf = search.isFocused();
        float sp = Draw.hoverProgress("expm_search", sf || Draw.hit(mx, my, sx, sy, sw, sh));
        Draw.panel(ctx, sx, sy, sw, sh, sh / 2, MenuKit.fa(Draw.lerpColor(0x70161228, 0xA0221A3C, sp), e),
                MenuKit.fa(Draw.lerpColor(0x30FFFFFF, Draw.withAlpha(Draw.accent(), 0xC0), sp), e));
        Icons.draw(ctx, "search", sx + 7, sy + 5, 10, MenuKit.fa(0xB0FFFFFF, e));

        // the mods
        int top = listY(), bottom = listBottom();
        ctx.enableScissor(x0() - 12, top - 4, x0() + contentW() + 12, bottom);
        int hoveredIdx = -1;
        for (int i = 0; i < mods.size(); i++) {
            int col = i % cols(), row = i / cols();
            int rx = x0() + col * (rowW() + GAP);
            int ry = top + row * (ROW_H + GAP) - (int) Math.round(scroll);
            if (ry + ROW_H < top - 4 || ry > bottom) continue;
            boolean hv = my >= top && my < bottom && Draw.hit(mx, my, rx, ry, rowW(), ROW_H);
            if (hv) hoveredIdx = i;
            float stagger = intro(0.08f + Math.min(12, i) * 0.025f);
            row(ctx, tr, mods.get(i), rx, ry + Math.round((1f - stagger) * 8), hv, mx, my, stagger);
        }
        if (mods.isEmpty()) {
            String msg = "No mods match \"" + search.getText() + "\"";
            Draw.text(ctx, tr, msg, width / 2 - Draw.textW(tr, msg) / 2, top + 30, MenuKit.fa(0xB0FFFFFF, e), false);
        }
        ctx.disableScissor();

        String hint = "Click to turn on or off   Right-click or the gear for options   Esc to go back";
        Draw.text(ctx, tr, hint, width / 2 - Draw.textW(tr, hint) / 2, height - 14, MenuKit.fa(0x80FFFFFF, intro(0.3f)), false);
        super.render(ctx, mx, my, delta);
    }

    /** A round glass button with an icon (chips, back). {@code p} = how selected/hovered it looks. */
    private static void orb(DrawContext ctx, int x, int y, int size, String icon, float p, boolean sel, float e) {
        int r = size / 2;
        if (sel) TempestLoading.drawGlow(ctx, x + r, y + r, size * 3, Draw.accent(), 0.35f * e);
        int fill = Draw.lerpColor(0xD0221C38, Draw.accent() | 0xFF000000, p);
        Draw.panel(ctx, x, y, size, size, r, MenuKit.fa(fill, e), MenuKit.fa(Draw.lerpColor(0x38FFFFFF, 0x90FFFFFF, p), e));
        int is = Math.round(size * 0.5f);
        Icons.draw(ctx, icon, x + r - is / 2, y + r - is / 2, is, MenuKit.fa(Draw.lerpColor(0xC8E4E0F5, 0xFFFFFFFF, p), e));
    }

    private int gearX(int rx) { return rx + rowW() - 34 - 22; }
    private int switchX(int rx) { return rx + rowW() - 38; }

    private void row(DrawContext ctx, TextRenderer tr, Module m, int x, int y, boolean hv, int mx, int my, float a) {
        int w = rowW(), h = ROW_H, r = h / 2;
        boolean on = m.isEnabled();
        float p = Draw.hoverProgress("expm_on_" + m.getId(), on);
        float hp = Draw.hoverProgress("expm_row_" + m.getId(), hv);

        if (p > 0.02f) TempestLoading.drawGlow(ctx, x + r, y + r, h * 3, Draw.accent(), 0.22f * p * a);
        int fill = Draw.lerpColor(Draw.lerpColor(0x90161228, 0xB0201A38, hp), Draw.lerpColor(0xA0241A4A, 0xC02E2260, hp), p);
        int border = Draw.lerpColor(Draw.lerpColor(0x28FFFFFF, 0x50FFFFFF, hp), Draw.withAlpha(Draw.accent(), 0x90), p);
        Draw.panel(ctx, x, y, w, h, r, MenuKit.fa(fill, a), MenuKit.fa(border, a));

        // the orb: dark when off, filled with the accent when on
        int os = h - 8, ox = x + 4, oy = y + 4, or = os / 2;
        int orbFill = Draw.lerpColor(0xFF2A2342, Draw.lerpColor(Draw.accent() | 0xFF000000, 0xFFB09BFF, 0.2f), p);
        Draw.panel(ctx, ox, oy, os, os, or, MenuKit.fa(orbFill, a), MenuKit.fa(Draw.lerpColor(0x30FFFFFF, 0x90FFFFFF, p), a));
        Draw.circle(ctx, ox + or, oy + Math.round(or * 0.7f), Math.round(or * 0.6f), MenuKit.fa(Draw.withAlpha(0xFFFFFF, 0x10 + Math.round(0x10 * p)), a));
        int is = Math.round(os * 0.52f);
        Icons.draw(ctx, Icons.forModule(m.getId()), ox + or - is / 2, oy + or - is / 2, is, MenuKit.fa(Draw.lerpColor(0xB0D8D2F0, 0xFFFFFFFF, p), a));

        // name + line
        int tx = ox + os + 8;
        int textW = (m.getSettings().isEmpty() ? switchX(x) : gearX(x)) - tx - 6;
        Draw.text(ctx, tr, Draw.ellipsize(tr, m.getName(), textW), tx, y + 8, MenuKit.fa(Draw.lerpColor(0xFFD8D4EA, 0xFFFFFFFF, p), a), false);
        String d = m.getDescription() == null ? "" : m.getDescription();
        Draw.text(ctx, tr, Draw.ellipsize(tr, d, textW), tx, y + 20, MenuKit.fa(0x9AC8C2E6, a), false);

        // gear (options) + switch
        if (!m.getSettings().isEmpty()) {
            int gx = gearX(x), gy = y + (h - 18) / 2;
            boolean gh = Draw.hit(mx, my, gx, gy, 18, 18);
            float gp = Draw.hoverProgress("expm_gear_" + m.getId(), gh);
            if (gp > 0.02f) Draw.circle(ctx, gx + 9, gy + 9, 9, MenuKit.fa(Draw.withAlpha(0xFFFFFF, Math.round(0x22 * gp)), a));
            Icons.draw(ctx, "gear", gx + 4, gy + 4, 10, MenuKit.fa(Draw.lerpColor(0x90FFFFFF, 0xFFFFFFFF, Math.max(gp, hp * 0.5f)), a));
        }
        if (m.alwaysOn()) {
            Draw.text(ctx, tr, "Always on", switchX(x) - 16, y + (h - 8) / 2, MenuKit.fa(0x90FFFFFF, a), false);
        } else {
            // Draw.toggle has no alpha: fade it in with the row by skipping it until the row is mostly visible
            if (a > 0.6f) Draw.toggle(ctx, switchX(x), y + (h - 14) / 2, p);
        }
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (opts != null) {
            if (opts.closing() || opts.progress(OPTS_SECS) < 1f) return true;
            if (!opts.mouseClicked(mx, my, click.button()) || opts.closeRequested) opts.startClosing();
            return true;
        }
        int by = chipsY();
        if (Draw.hit(mx, my, x0(), by, CHIP, CHIP)) { close(); return true; }
        for (int i = 0; i < chipCount(); i++) {
            if (Draw.hit(mx, my, chipX(i), by, CHIP, CHIP)) {
                filter = chipCat(i);
                scrollTarget = scroll = 0;
                return true;
            }
        }
        if (my >= listY() && my < listBottom()) {
            List<Module> mods = visible();
            for (int i = 0; i < mods.size(); i++) {
                int rx = x0() + (i % cols()) * (rowW() + GAP);
                int ry = listY() + (i / cols()) * (ROW_H + GAP) - (int) Math.round(scroll);
                if (!Draw.hit(mx, my, rx, ry, rowW(), ROW_H)) continue;
                Module m = mods.get(i);
                boolean gear = !m.getSettings().isEmpty() && Draw.hit(mx, my, gearX(rx), ry + (ROW_H - 18) / 2, 18, 18);
                if (gear || click.button() == 1) openOptions(m);
                else if (click.button() == 0) m.toggle();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    /** A mod's options open in the normal settings page (it has every kind of setting); Esc brings you back here. */
    private void openOptions(Module m) {
        if (search != null) search.setFocused(false);
        setFocused(null);
        opts = new ExpSettings(m, false);
    }

    /** The old way: the normal settings page (kept for reference). */
    @SuppressWarnings("unused")
    private void openClickGui(Module m) {
        if (client == null) return;
        ClickGuiScreen gui = ClickGuiScreen.openTo(m.getCategory(), this);
        client.setScreen(gui);
        try {
            java.lang.reflect.Field f = ClickGuiScreen.class.getDeclaredField("open");
            f.setAccessible(true);
            f.set(gui, m);
        } catch (ReflectiveOperationException ignored) { }      // still opens the list on that category
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (opts != null) { opts.mouseScrolled(mouseX, mouseY, vertical); return true; }
        scrollTarget -= vertical * 40;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (opts != null) {
            if (opts.keyPressed(input.key())) return true;
            if (input.key() == 256) { opts.startClosing(); return true; }       // Esc
        }
        if (input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT && !search.isFocused()) {
            if (client != null) client.setScreen(null);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (opts != null && opts.mouseDragged(click.x(), click.y())) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (opts != null) opts.mouseReleased();
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        if (opts != null && !opts.closing()) { opts.startClosing(); return; }
        if (client != null) client.setScreen(parent);
    }
}
