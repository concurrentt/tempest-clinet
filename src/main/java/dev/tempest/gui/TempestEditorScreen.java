package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.modules.ParticleFilterModule;
import dev.tempest.util.CustomPack;
import dev.tempest.util.PackLooks;
import dev.tempest.util.PackLooks.Category;
import dev.tempest.util.PackLooks.Img;
import dev.tempest.util.PackLooks.Look;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tempest Editor: a pack editor. The hub shows the groups (PvP, Blocks, Gear, HUD, Misc, Effects, Sounds) and
 * shortcuts; a group page has its categories down the side and a grid of looks for the chosen one: Vanilla, Custom
 * (pick a colour, or drop a screenshot to copy its colour), twelve colour styles, and the same textures from any pack
 * in your resourcepacks folder. Pick looks, then Apply writes them into the "Tempest Custom" pack and reloads.
 */
public class TempestEditorScreen extends Screen {
    private static final int CARD_W = 62, CARD_H = 72, GAP = 6, SIDE_W = 150;

    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private int group = -1;                 // -1 = hub
    private Category cat;
    private final Map<String, Look> pending = new LinkedHashMap<>();
    private double scroll, scrollTarget;
    private String status = "";
    private long statusAt, deleteArmedAt;

    // previews for the open category
    private final Map<String, Identifier> previews = new HashMap<>();
    private final Map<String, int[]> previewSize = new HashMap<>();
    private List<ResourcePackProfile> packs = new ArrayList<>();
    private static int texCounter;

    // custom colour picker
    private boolean picker;
    private float ph, ps = 0.8f, pv = 0.9f;
    private int pickDrag;

    public TempestEditorScreen(Screen parent) {
        super(Text.literal("Tempest Editor"));
        this.parent = parent;
        for (var e : PackLooks.applied().entrySet()) pending.put(e.getKey(), e.getValue());
        if (PackLooks.repairGuiOnce()) flash("Fixed your inventory look: it's solid now (reloading textures\u2026)");
    }

    private Look pendingLook(Category c) { return pending.getOrDefault(c.id(), Look.VANILLA); }

    private boolean dirty() {
        for (Category c : PackLooks.CATEGORIES) if (!PackLooks.same(pendingLook(c), PackLooks.appliedLook(c.id()))) return true;
        return false;
    }

    private boolean groupChanged(int g) {
        for (Category c : PackLooks.inGroup(PackLooks.GROUPS[g])) {
            Look l = PackLooks.appliedLook(c.id());
            if (!l.look().equals("Vanilla") || l.glintOff()) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- layout
    private int winW() { return Math.min(width - 16, 720); }
    private int winH() { return Math.min(height - 16, 400); }
    private int x0() { return (width - winW()) / 2; }
    private int y0() { return (height - winH()) / 2; }
    private int mainX() { return x0() + 10 + SIDE_W + 10; }
    private int mainW() { return x0() + winW() - 10 - mainX(); }
    private int gridY() { return y0() + 36 + (cat != null && cat.isSound() ? 22 : 42); }
    private int gridH() { return y0() + winH() - 40 - gridY(); }
    private int cols() { return Math.max(1, (mainW() + GAP) / (CARD_W + GAP)); }

    // ---------------------------------------------------------------- the looks offered for a category
    private record Option(String key, String name, String tag, int tagColor) { }

    private List<Option> options() {
        List<Option> out = new ArrayList<>();
        out.add(new Option("Vanilla", "Vanilla", "VANILLA", 0xFF9DA2B3));
        if (cat.isSound()) {
            for (PackLooks.SoundOption s : PackLooks.SOUNDS) out.add(new Option(s.name(), s.name(), "SOUND", 0xFF6FD3FF));
            return out;
        }
        out.add(new Option("Custom", "Custom", "PICK", 0xFFB48CFF));
        for (PackLooks.Style s : PackLooks.STYLES) out.add(new Option(s.name(), s.name(), "STYLE", 0xFFFFC94A));
        for (ResourcePackProfile p : packs) out.add(new Option("pack:" + p.getId(), p.getDisplayName().getString(), "PACK", 0xFF5EE0E0));
        return out;
    }

    private void openCategory(Category c) {
        clearPreviews();
        cat = c;
        picker = false;
        scroll = scrollTarget = 0;
        packs = c.isSound() ? new ArrayList<>() : PackLooks.packsWith(c);
        if (!c.isSound()) {
            Img base = PackLooks.base(c.previewId());
            if (base != null) buildPreviews(base.crop(c.region(), regionRef(c)));
        }
    }

    /** Width of the vanilla texture a category's preview region is measured on (HD packs get it scaled up). */
    private static int regionRef(Category c) { return c.region() == null ? 0 : c.id().equals("gui") ? 256 : 64; }

    private void buildPreviews(Img base) {
        register("Vanilla", base);
        for (PackLooks.Style s : PackLooks.STYLES) register(s.name(), recolored(base, s));
        register("Custom", recolored(base, PackLooks.custom(pendingLook(cat).color())));
        for (ResourcePackProfile p : packs) {
            Img img = PackLooks.solidify(cat, cat.previewId(), PackLooks.fromPack(p.getId(), cat.previewId()));
            if (img != null) register("pack:" + p.getId(), img.crop(cat.region(), regionRef(cat)));
        }
    }

    private static Img recolored(Img base, PackLooks.Style s) {
        int[] px = base.px().clone();
        for (int i = 0; i < px.length; i++) px[i] = PackLooks.recolor(px[i], s);
        return new Img(base.w(), base.h(), px);
    }

    private void register(String key, Img img) {
        MinecraftClient c = MinecraftClient.getInstance();
        Identifier old = previews.remove(key);
        if (old != null) c.getTextureManager().destroyTexture(old);
        if (img == null || img.w() <= 0) return;
        NativeImage n = new NativeImage(img.w(), img.h(), true);
        for (int y = 0; y < img.h(); y++) for (int x = 0; x < img.w(); x++) n.setColorArgb(x, y, img.px()[y * img.w() + x]);
        Identifier id = Identifier.of("tempest", "dynamic/editor_" + (texCounter++));
        c.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "Tempest editor preview", n));
        previews.put(key, id);
        previewSize.put(key, new int[]{img.w(), img.h()});
    }

    private void refreshCustomPreview() {
        if (cat == null || cat.isSound()) return;
        Img base = PackLooks.base(cat.previewId());
        if (base != null) register("Custom", recolored(base.crop(cat.region(), regionRef(cat)), PackLooks.custom(pendingLook(cat).color())));
    }

    private void clearPreviews() {
        MinecraftClient c = MinecraftClient.getInstance();
        for (Identifier id : previews.values()) c.getTextureManager().destroyTexture(id);
        previews.clear();
        previewSize.clear();
    }

    private void flash(String s) { status = s; statusAt = System.currentTimeMillis(); }

    @Override
    protected void init() {
        // coming back from the painter or a reload: the preview textures were freed
        if (cat != null && previews.isEmpty()) {
            Category c = cat;
            openCategory(c);
        }
    }

    // ---------------------------------------------------------------- render
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        scroll += (scrollTarget - scroll) * 0.35;
        float e = MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.25f);
        if (client != null && client.world == null) Draw.menuBackground(ctx, width, height);
        else ctx.fillGradient(0, 0, width, height, MenuKit.fa(0x88000000, e), MenuKit.fa(0x68000000, e));
        if (group < 0) renderHub(ctx, textRenderer, mx, my, e);
        else renderGroup(ctx, textRenderer, mx, my);
        super.render(ctx, mx, my, delta);
    }

    // ----- hub
    private static final int TILE = 84, TILE_GAP = 8;
    private static final String[][] SHORTCUTS = {{"short_configs", "Configs"}, {"short_packs", "My Packs"}, {"short_cosmetics", "Cosmetics"}, {"short_painter", "Painter"}};

    /** Full-colour icons for this page (textures/gui/editor_icons.png, made by tools/make_editor_icons.py). */
    private static final Identifier HUB_ICONS = Identifier.of("tempest", "textures/gui/editor_icons.png");
    private static final String[] HUB_NAMES = {"group_pvp", "group_blocks", "group_gear", "group_hud", "group_misc", "group_effects",
            "group_sounds", "short_configs", "short_packs", "short_cosmetics", "short_painter"};
    /** Each group's colour (tile glow on hover). */
    private static final int[] GROUP_GLOW = {0xFF5C6C, 0x7BD651, 0x5EE6FF, 0xFFD34D, 0xB06CFF, 0xFFB347, 0x5AB4FF};

    private static void hubIcon(DrawContext ctx, String name, int x, int y, int size, float alpha) {
        int i = java.util.Arrays.asList(HUB_NAMES).indexOf(name);
        if (i < 0) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, HUB_ICONS, x, y, (i % 8) * 64f, (i / 8) * 64f, size, size, 64, 64, 512, 128,
                MenuKit.fa(0xFFFFFFFF, alpha));
    }

    private int tilesX() { return width / 2 - (PackLooks.GROUPS.length * (TILE + TILE_GAP) - TILE_GAP) / 2; }
    private int tilesY() { return height / 2 - TILE / 2 - 14; }
    private int tileSize() { return Math.min(TILE, (width - 24 - (PackLooks.GROUPS.length - 1) * TILE_GAP) / PackLooks.GROUPS.length); }

    private void renderHub(DrawContext ctx, TextRenderer tr, int mx, int my, float e) {
        int ts = tileSize(), n = PackLooks.GROUPS.length;
        int tx0 = width / 2 - (n * (ts + TILE_GAP) - TILE_GAP) / 2, ty = tilesY();
        TempestLoading.drawGlow(ctx, width / 2, ty + ts / 2, Math.round(width * 0.9f), Draw.accent(), 0.14f * e);
        MenuKit.scaledCentered(ctx, tr, Draw.bold("Tempest Editor"), width / 2f, ty - 46, 2.6f, MenuKit.fa(0xFFFFFFFF, e), true);
        Draw.centered(ctx, tr, "Pack Editor", width / 2, ty - 18, MenuKit.fa(0xFFB0B4C4, e));

        for (int i = 0; i < n; i++) {
            int x = tx0 + i * (ts + TILE_GAP);
            boolean hv = Draw.hit(mx, my, x, ty, ts, ts);
            float p = Draw.hoverProgress("ed_tile" + i, hv);
            int yy = ty - Math.round(3 * p);
            if (p > 0.02f) Draw.shadow(ctx, x, yy, ts, ts, 8, Math.round(6 * p));
            Draw.panel(ctx, x, yy, ts, ts, 8, MenuKit.fa(Draw.lerpColor(0x90181A22, 0xC0262A38, p), e),
                    MenuKit.fa(Draw.lerpColor(0x40FFFFFF, Draw.withAlpha(Draw.accent(), 0xC0), p), e));
            if (p > 0.02f) TempestLoading.drawGlow(ctx, x + ts / 2, yy + ts / 2 - 8, Math.round(ts * 1.3f), GROUP_GLOW[i], 0.35f * p * e);
            int is = Math.round(ts * 0.46f + 4 * p);
            hubIcon(ctx, HUB_NAMES[i], x + (ts - is) / 2, yy + ts / 2 - 8 - is / 2, is, e);
            String label = PackLooks.GROUPS[i];
            Draw.centered(ctx, tr, label, x + ts / 2, yy + ts - 22, MenuKit.fa(0xFFFFFFFF, e));
            ctx.fill(x + ts / 2 - 8, yy + ts - 11, x + ts / 2 + 8, yy + ts - 10, MenuKit.fa(Draw.lerpColor(0x50FFFFFF, Draw.accent(), p), e));
            if (groupChanged(i)) Draw.circle(ctx, x + ts - 8, yy + 8, 2, MenuKit.fa(Draw.accent(), e));
        }

        int bw = 62, bh = 32, by = ty + ts + 18;
        int bx0 = width / 2 - (SHORTCUTS.length * (bw + 6) - 6) / 2;
        for (int i = 0; i < SHORTCUTS.length; i++) {
            int x = bx0 + i * (bw + 6);
            boolean hv = Draw.hit(mx, my, x, by, bw, bh);
            float p = Draw.hoverProgress("ed_short" + i, hv);
            Draw.panel(ctx, x, by, bw, bh, 6, MenuKit.fa(Draw.lerpColor(0x90181A22, 0xC0262A38, p), e), MenuKit.fa(Draw.lerpColor(0x40FFFFFF, 0x90FFFFFF, p), e));
            hubIcon(ctx, SHORTCUTS[i][0], x + bw / 2 - 7, by + 3, 14, e);
            Draw.centered(ctx, tr, SHORTCUTS[i][1], x + bw / 2, by + 19, MenuKit.fa(0xFFE4E6EC, e));
        }
        boolean recent = System.currentTimeMillis() - statusAt < 8000 && !status.isEmpty();
        String info = recent ? status : dirty() ? "You have looks that aren't applied yet" : "Looks are saved into the \"" + CustomPack.NAME + "\" pack";
        int iw = Draw.textW(tr, info) + 20, iy = by + bh + 12;
        Draw.panel(ctx, width / 2 - iw / 2, iy, iw, 16, 4, MenuKit.fa(0xA0101218, e), MenuKit.fa(0x40FFFFFF, e));
        Draw.centered(ctx, tr, info, width / 2, iy + 4, MenuKit.fa(dirty() ? 0xFFFFD27A : 0xFFD0D3DC, e));
        Draw.text(ctx, tr, "Esc to close", 8, height - 14, 0x80FFFFFF, false);
    }

    /** Draws an item centred on (cx, cy) at a scale. */
    private static void item(DrawContext ctx, Item it, int cx, int cy, float scale) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, cy);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawItem(new ItemStack(it), -8, -8);
        ctx.getMatrices().popMatrix();
    }

    // ----- group page
    private void renderGroup(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int x = x0(), y = y0(), w = winW(), h = winH();
        Draw.shadow(ctx, x, y, w, h, 10, 12);
        Draw.glass(ctx, x, y, w, h, 10, 0xE40F1117, 0x40FFFFFF);
        int gc = 0xFF000000 | GROUP_GLOW[Math.min(group, GROUP_GLOW.length - 1)];
        // header: a soft wash of the group's colour fading down from the top edge
        Draw.roundRectV(ctx, x + 1, y + 1, w - 2, 34, 9, Draw.withAlpha(gc, 0x2C), Draw.withAlpha(gc, 0x00));
        TempestLoading.drawGlow(ctx, x + 72, y + 16, 46, GROUP_GLOW[Math.min(group, GROUP_GLOW.length - 1)], 0.28f);
        boolean backHv = Draw.hit(mx, my, x + 10, y + 7, 46, 18);
        Draw.panel(ctx, x + 10, y + 7, 46, 18, 5, backHv ? 0xEC2A2C33 : 0xD81A1B20, 0x40FFFFFF);
        Draw.centered(ctx, tr, "< Back", x + 33, y + 12, 0xFFE4E6EC);
        hubIcon(ctx, HUB_NAMES[group], x + 63, y + 7, 18, 1f);
        Draw.text(ctx, tr, Draw.bold(PackLooks.GROUPS[group]), x + 84, y + 7, 0xFFFFFFFF, false);
        Draw.text(ctx, tr, "Pick a look, then press Apply", x + 84, y + 17, 0xFF8B90A0, false);
        boolean dirty = dirty();
        String st = dirty ? "● Not applied yet" : "✔ Everything applied";
        int stW = Draw.textW(tr, st) + 14, stC = dirty ? 0xFFFFD27A : 0xFF6AF09A;
        Draw.panel(ctx, x + w - 10 - stW, y + 8, stW, 16, 8, Draw.withAlpha(stC, 0x1E), Draw.withAlpha(stC, 0x60));
        Draw.text(ctx, tr, st, x + w - 10 - stW + 7, y + 12, stC, false);
        Draw.hairline(ctx, x + 10, y + 30, w - 20, gc & 0xFFFFFF, 0x50);

        // sidebar
        List<Category> cats = PackLooks.inGroup(PackLooks.GROUPS[group]);
        Draw.panel(ctx, x + 7, y + 33, SIDE_W + 6, h - 33 - 42, 7, 0x50090A0F, 0x18FFFFFF);
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            int ry = y + 36 + i * 22;
            boolean sel = c == cat, hv = Draw.hit(mx, my, x + 10, ry, SIDE_W, 20);
            float sp = Draw.hoverProgress("ed_side" + group + "_" + i, sel || hv);
            if (sp > 0.01f) Draw.panel(ctx, x + 10, ry, SIDE_W, 20, 5,
                    sel ? Draw.withAlpha(Draw.lerpColor(0xFF262A38, gc, 0.18f), 0xD0) : Draw.withAlpha(0xFF262A38, Math.round(0x60 * sp)),
                    sel ? Draw.withAlpha(gc, 0x70) : Draw.withAlpha(0xFFFFFFFF, Math.round(0x30 * sp)));
            if (sel) Draw.roundRect(ctx, x + 10, ry + 4, 2, 12, 1, gc);
            item(ctx, c.icon(), x + 21, ry + 10, 0.75f);
            Draw.text(ctx, tr, Draw.ellipsize(tr, c.name(), SIDE_W - 34), x + 32, ry + 6, sel ? 0xFFFFFFFF : 0xFFC9CCD6, false);
            Look l = PackLooks.appliedLook(c.id());
            if (!l.look().equals("Vanilla") || l.glintOff()) Draw.circle(ctx, x + SIDE_W + 3, ry + 10, 2, Draw.accent());
        }
        if (cat == null) return;

        // title row
        int mx0 = mainX(), mw = mainW(), ty = y + 37;
        Look look = pendingLook(cat);
        Draw.text(ctx, tr, Draw.bold(cat.name()), mx0, ty + 4, 0xFFFFFFFF, false);
        String sel = "selected: " + displayName(look.look());
        Draw.text(ctx, tr, sel, mx0 + Draw.textW(tr, Draw.bold(cat.name())) + 10, ty + 4, Draw.withAlpha(Draw.accent(), 0xFF), false);
        int rx = mx0 + mw;
        if (!cat.isSound()) {
            rx -= 18;
            boolean hv = Draw.hit(mx, my, rx, ty, 18, 16);
            Draw.panel(ctx, rx, ty, 18, 16, 4, hv ? 0xEC2A2C33 : 0xD81A1B20, 0x40FFFFFF);
            Icons.draw(ctx, "pencil", rx + 4, ty + 3, 10, 0xFFFFD27A);
        }
        String tg = toggleLabel();
        if (tg != null) {
            boolean on = toggleOn();
            rx -= 34;
            Draw.toggle(ctx, rx, ty + 1, Draw.hoverProgress("ed_tg_" + cat.id(), on));
            Draw.text(ctx, tr, tg, rx - 6 - Draw.textW(tr, tg), ty + 4, 0xFFE4E6EC, false);
        }
        // drop zone
        if (!cat.isSound()) {
            int dy = ty + 20;
            Draw.panel(ctx, mx0, dy, mw, 16, 4, 0x60181A22, Draw.withAlpha(Draw.accent(), 0x60));
            Draw.text(ctx, tr, Draw.ellipsize(tr, "↓ Drop a screenshot here to copy its colour onto " + cat.name(), mw - 70), mx0 + 6, dy + 4, 0xFFC9CCD6, false);
            boolean hv = Draw.hit(mx, my, mx0 + mw - 58, dy + 1, 56, 14);
            Draw.panel(ctx, mx0 + mw - 58, dy + 1, 56, 14, 4, hv ? 0xEC2A2C33 : 0xD81A1B20, 0x40FFFFFF);
            Draw.centered(ctx, tr, "Choose…", mx0 + mw - 30, dy + 4, 0xFFE4E6EC);
        }

        // grid
        List<Option> opts = options();
        int gy = gridY(), gh = gridH(), gx = mx0;
        ctx.enableScissor(mx0 - 2, gy - 2, mx0 + mw + 2, gy + gh + 2);
        for (int i = 0; i < opts.size(); i++) {
            Option o = opts.get(i);
            int cx = gx + (i % cols()) * (CARD_W + GAP), cy = gy + (i / cols()) * (CARD_H + GAP) - (int) Math.round(scroll);
            if (cy + CARD_H < gy - 4 || cy > gy + gh + 4) continue;
            boolean selected = look.look().equals(o.key), hv = Draw.hit(mx, my, cx, cy, CARD_W, CARD_H) && my >= gy && my < gy + gh;
            float p = Draw.hoverProgress("ed_card" + cat.id() + i, hv);
            cy -= Math.round(p * 1.5f);   // hovered cards lift a little
            if (selected) Draw.ring(ctx, cx - 2, cy - 2, CARD_W + 4, CARD_H + 4, 8, Draw.withAlpha(gc, 0x55));
            Draw.roundRectV(ctx, cx, cy, CARD_W, CARD_H, 6, Draw.lerpColor(0xB02A2D38, 0xE03A3E4D, p), Draw.lerpColor(0xB01A1C24, 0xE0262935, p));
            Draw.ring(ctx, cx, cy, CARD_W, CARD_H, 6,
                    selected ? Draw.withAlpha(gc, 0xF0) : Draw.lerpColor(0x30FFFFFF, 0x80FFFFFF, p));
            Draw.roundRect(ctx, cx + (CARD_W - 46) / 2, cy + 2, 46, 46, 5, 0x50000000);
            drawThumb(ctx, o, cx + (CARD_W - 40) / 2, cy + 5, 40);
            Draw.centered(ctx, tr, Draw.ellipsize(tr, o.name, CARD_W - 4), cx + CARD_W / 2, cy + 48, 0xFFFFFFFF);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx + CARD_W / 2f, cy + 60);
            ctx.getMatrices().scale(0.8f, 0.8f);
            Draw.spaced(ctx, tr, o.tag, 0, 0, o.tagColor, true, 1);
            ctx.getMatrices().popMatrix();
            if (selected) {
                Draw.circle(ctx, cx + CARD_W - 7, cy + 7, 6, 0x60000000);
                Draw.circle(ctx, cx + CARD_W - 7, cy + 7, 5, 0xFF2FA866);
                Draw.centered(ctx, tr, "✔", cx + CARD_W - 7, cy + 3, 0xFFFFFFFF);
            }
        }
        ctx.disableScissor();
        MenuKit.listFades(ctx, mx0, gy, mw, gh, scroll, maxScroll());

        // bottom bar
        int by = y + h - 30;
        Draw.hairline(ctx, x + 10, by - 6, w - 20, 0xFFFFFF, 0x28);
        String[] labels = {"Packs Folder", "Delete", "Reset", "Close"};
        int bx = x + w - 10 - 86;
        boolean canApply = dirty;
        MenuKit.flatButton(ctx, tr, "ed_apply", bx, by, 86, 22, null, "✔ Apply", MenuKit.GREEN, Draw.hit(mx, my, bx, by, 86, 22), canApply, 1f);
        for (int i = labels.length - 1; i >= 0; i--) {
            String lb = i == 1 && System.currentTimeMillis() - deleteArmedAt < 4000 ? "Sure? Click again" : labels[i];
            int bw2 = Draw.textW(tr, lb) + 16;
            bx -= bw2 + 6;
            MenuKit.flatButton(ctx, tr, "ed_btn" + i, bx, by, bw2, 22, null, lb, MenuKit.OUTLINE, Draw.hit(mx, my, bx, by, bw2, 22), true, 1f);
        }
        if (System.currentTimeMillis() - statusAt < 6000 && !status.isEmpty()) {
            Draw.text(ctx, tr, Draw.ellipsize(tr, status, bx - x - 24), x + 14, by + 7, 0xFFB8F5C8, false);
        }

        if (picker) drawPicker(ctx, tr, mx, my);
    }

    private String displayName(String key) {
        if (key.startsWith("pack:")) {
            for (ResourcePackProfile p : packs) if (("pack:" + p.getId()).equals(key)) return p.getDisplayName().getString();
            return key.substring(10);
        }
        return key;
    }

    private void drawThumb(DrawContext ctx, Option o, int x, int y, int s) {
        if (cat.isSound()) {
            Icons.draw(ctx, o.key.equals("Muted") ? "power" : "note", x + s / 2 - 12, y + s / 2 - 12, 24, o.key.equals("Vanilla") ? 0xB0FFFFFF : 0xFFFFFFFF);
            return;
        }
        Identifier id = previews.get(o.key);
        int[] d = previewSize.get(o.key);
        if (id == null || d == null) { Icons.draw(ctx, "layers", x + s / 2 - 10, y + s / 2 - 10, 20, 0x70FFFFFF); return; }
        float k = Math.min(s / (float) d[0], s / (float) d[1]);
        int w = Math.max(1, Math.round(d[0] * k)), h = Math.max(1, Math.round(d[1] * k));
        int cs = 4;
        for (int yy = 0; yy * cs < h; yy++) for (int xx = 0; xx * cs < w; xx++)
            ctx.fill(x + (s - w) / 2 + xx * cs, y + (s - h) / 2 + yy * cs, x + (s - w) / 2 + Math.min(w, (xx + 1) * cs),
                    y + (s - h) / 2 + Math.min(h, (yy + 1) * cs), ((xx + yy) & 1) == 0 ? 0x30FFFFFF : 0x18FFFFFF);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x + (s - w) / 2, y + (s - h) / 2, 0f, 0f, w, h, d[0], d[1], d[0], d[1], 0xFFFFFFFF);
        if (o.key.equals("Custom")) Draw.roundRect(ctx, x + s - 9, y + s - 9, 9, 9, 3, 0xFF000000 | pendingLook(cat).color());
    }

    private double maxScroll() {
        int rows = (options().size() + cols() - 1) / cols();
        return Math.max(0, rows * (CARD_H + GAP) - GAP - gridH());
    }

    // ----- toggles
    private String toggleLabel() {
        if (cat == null || cat.toggle() == null) return null;
        return switch (cat.toggle()) {
            case "explosions" -> "Explosion Particles";
            case "glint" -> "Enchant Glint";
            case "invert" -> "Invert colours";
            default -> null;
        };
    }

    private boolean toggleOn() {
        return switch (cat.toggle()) {
            case "explosions" -> ParticleFilterModule.explosionsShown();
            case "glint" -> !pendingLook(cat).glintOff();
            case "invert" -> PackLooks.invertCrosshair();
            default -> false;
        };
    }

    private void flipToggle() {
        switch (cat.toggle()) {
            case "explosions" -> { ParticleFilterModule.setExplosionsShown(!ParticleFilterModule.explosionsShown()); TempestConfig.save(); }
            case "glint" -> pending.put(cat.id(), pendingLook(cat).withGlintOff(!pendingLook(cat).glintOff()));
            case "invert" -> PackLooks.setInvertCrosshair(!PackLooks.invertCrosshair());
            default -> { }
        }
    }

    // ----- custom colour picker
    private int pkX() { return mainX() + mainW() / 2 - 80; }
    private int pkY() { return gridY() + 10; }

    private void drawPicker(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int x = pkX(), y = pkY(), w = 160, h = 128;
        Draw.shadow(ctx, x, y, w, h, 8, 10);
        Draw.panel(ctx, x, y, w, h, 8, 0xF8121419, 0x60FFFFFF);
        Draw.text(ctx, tr, "Custom colour", x + 8, y + 7, 0xFFFFFFFF, false);
        int sx = x + 8, sy = y + 20, sw = w - 16, sh = 60;
        int hueRgb = Draw.hsvToRgb(ph, 1f, 1f) & 0xFFFFFF;
        for (int i = 0; i < sw; i++) {
            int top = 0xFF000000 | Draw.lerpColor(0xFFFFFFFF, 0xFF000000 | hueRgb, i / (float) (sw - 1));
            ctx.fillGradient(sx + i, sy, sx + i + 1, sy + sh, top, 0xFF000000);
        }
        Draw.ring(ctx, sx + Math.round(ps * (sw - 1)) - 3, sy + Math.round((1 - pv) * (sh - 1)) - 3, 7, 7, 3, 0xFFFFFFFF);
        int hy = sy + sh + 5;
        for (int i = 0; i < sw; i++) ctx.fill(sx + i, hy, sx + i + 1, hy + 8, 0xFF000000 | Draw.hsvToRgb(i / (float) sw, 1f, 1f));
        int hx = sx + Math.round(ph * (sw - 1));
        ctx.fill(hx - 1, hy - 2, hx + 2, hy + 10, 0xFFFFFFFF);
        int col = 0xFF000000 | Draw.hsvToRgb(ph, ps, pv);
        Draw.roundRect(ctx, x + 8, y + h - 22, 16, 14, 3, col);
        Draw.text(ctx, tr, String.format(Locale.ROOT, "#%06X", col & 0xFFFFFF), x + 28, y + h - 19, 0xFFE4E6EC, false);
        MenuKit.flatButton(ctx, tr, "ed_pk_ok", x + w - 50, y + h - 24, 42, 18, null, "Done", MenuKit.GREEN, Draw.hit(mx, my, x + w - 50, y + h - 24, 42, 18), true, 1f);
    }

    private boolean pickerClick(double mx, double my) {
        int x = pkX(), y = pkY(), w = 160, h = 128;
        if (!Draw.hit(mx, my, x, y, w, h)) { picker = false; return true; }
        if (Draw.hit(mx, my, x + w - 50, y + h - 24, 42, 18)) { picker = false; return true; }
        if (Draw.hit(mx, my, x + 8, y + 20, w - 16, 60)) { pickDrag = 1; pickerDrag(mx, my); return true; }
        if (Draw.hit(mx, my, x + 8, y + 83, w - 16, 12)) { pickDrag = 2; pickerDrag(mx, my); return true; }
        return true;
    }

    private void pickerDrag(double mx, double my) {
        int x = pkX() + 8, y = pkY() + 20, sw = 144;
        float fx = (float) Math.max(0, Math.min(1, (mx - x) / (sw - 1)));
        if (pickDrag == 1) { ps = fx; pv = 1f - (float) Math.max(0, Math.min(1, (my - y) / 59.0)); }
        else if (pickDrag == 2) ph = Math.min(0.999f, fx);
        else return;
        setCustom(Draw.hsvToRgb(ph, ps, pv) & 0xFFFFFF);
    }

    private void setCustom(int rgb) {
        pending.put(cat.id(), pendingLook(cat).withColor(rgb));
        refreshCustomPreview();
    }

    private void openPicker() {
        float[] hsv = Draw.rgbToHsv(pendingLook(cat).color());
        ph = hsv[0]; ps = hsv[1]; pv = hsv[2];
        picker = true;
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (group < 0) return clickHub(mx, my) || super.mouseClicked(click, doubled);
        if (picker && cat != null) return pickerClick(mx, my);
        int x = x0(), y = y0(), w = winW(), h = winH();
        if (Draw.hit(mx, my, x + 10, y + 7, 46, 18)) { clearPreviews(); cat = null; group = -1; return true; }
        List<Category> cats = PackLooks.inGroup(PackLooks.GROUPS[group]);
        for (int i = 0; i < cats.size(); i++) {
            if (Draw.hit(mx, my, x + 10, y + 36 + i * 22, SIDE_W, 20)) { openCategory(cats.get(i)); return true; }
        }
        // bottom bar
        int by = y + h - 30, bx = x + w - 10 - 86;
        if (Draw.hit(mx, my, bx, by, 86, 22)) { if (dirty()) flash(PackLooks.apply(pending)); return true; }
        String[] labels = {"Packs Folder", "Delete", "Reset", "Close"};
        for (int i = labels.length - 1; i >= 0; i--) {
            String lb = i == 1 && System.currentTimeMillis() - deleteArmedAt < 4000 ? "Sure? Click again" : labels[i];
            int bw2 = Draw.textW(textRenderer, lb) + 16;
            bx -= bw2 + 6;
            if (!Draw.hit(mx, my, bx, by, bw2, 22)) continue;
            switch (i) {
                case 0 -> net.minecraft.util.Util.getOperatingSystem().open(CustomPack.dir().getParent().toFile());
                case 1 -> {
                    if (System.currentTimeMillis() - deleteArmedAt < 4000) {
                        deleteArmedAt = 0;
                        try { PackLooks.deleteAll(); pending.clear(); flash("Deleted everything Tempest added - reloading…"); }
                        catch (Exception ex) { flash("Couldn't delete: " + ex.getMessage()); }
                    } else { deleteArmedAt = System.currentTimeMillis(); flash("Deletes every look and painting in \"" + CustomPack.NAME + "\""); }
                }
                case 2 -> { for (Category c : cats) pending.put(c.id(), Look.VANILLA); flash("Everything in " + PackLooks.GROUPS[group] + " set to Vanilla - press Apply"); }
                default -> close();
            }
            return true;
        }
        if (cat == null) return super.mouseClicked(click, doubled);
        int mx0 = mainX(), mw = mainW(), ty = y + 37;
        int rx = mx0 + mw;
        if (!cat.isSound()) {
            rx -= 18;
            if (Draw.hit(mx, my, rx, ty, 18, 16)) {
                if (client != null) client.setScreen(new TexturePainterScreen(this, cat.previewId()));
                return true;
            }
        }
        if (toggleLabel() != null) {
            rx -= 34;
            if (Draw.hit(mx, my, rx - 6 - Draw.textW(textRenderer, toggleLabel()), ty, Draw.textW(textRenderer, toggleLabel()) + 40, 16)) { flipToggle(); return true; }
        }
        if (!cat.isSound() && Draw.hit(mx, my, mx0 + mw - 58, ty + 21, 56, 14)) { chooseFile(); return true; }
        // cards
        if (Draw.hit(mx, my, mx0, gridY(), mw, gridH())) {
            List<Option> opts = options();
            for (int i = 0; i < opts.size(); i++) {
                int cx = mx0 + (i % cols()) * (CARD_W + GAP), cy = gridY() + (i / cols()) * (CARD_H + GAP) - (int) Math.round(scroll);
                if (!Draw.hit(mx, my, cx, cy, CARD_W, CARD_H)) continue;
                Option o = opts.get(i);
                if (o.key.equals("Custom")) { pending.put(cat.id(), pendingLook(cat).withLook("Custom")); openPicker(); }
                else pending.put(cat.id(), pendingLook(cat).withLook(o.key));
                if (cat.isSound()) previewSound(o.key);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean clickHub(double mx, double my) {
        int ts = tileSize(), n = PackLooks.GROUPS.length;
        int tx0 = width / 2 - (n * (ts + TILE_GAP) - TILE_GAP) / 2, ty = tilesY();
        for (int i = 0; i < n; i++) {
            if (Draw.hit(mx, my, tx0 + i * (ts + TILE_GAP), ty, ts, ts)) {
                group = i;
                openCategory(PackLooks.inGroup(PackLooks.GROUPS[i]).get(0));
                return true;
            }
        }
        int bw = 62, bh = 32, by = ty + ts + 18;
        int bx0 = width / 2 - (SHORTCUTS.length * (bw + 6) - 6) / 2;
        for (int i = 0; i < SHORTCUTS.length; i++) {
            if (!Draw.hit(mx, my, bx0 + i * (bw + 6), by, bw, bh) || client == null) continue;
            switch (i) {
                case 0 -> client.setScreen(new ConfigsScreen(this));
                case 1 -> {
                    var mgr = client.getResourcePackManager();
                    List<String> before = new ArrayList<>(mgr.getEnabledIds());
                    client.setScreen(new net.minecraft.client.gui.screen.pack.PackScreen(mgr, m -> {
                        client.options.refreshResourcePacks(m);
                        client.setScreen(this);
                        if (!before.equals(new ArrayList<>(m.getEnabledIds()))) client.reloadResources();
                    }, client.getResourcePackDir(), Text.translatable("resourcePack.title")));
                }
                case 2 -> client.setScreen(new CosmeticsScreen(this, CosmeticsScreen.CAPES));
                default -> client.setScreen(new TexturePainterScreen(this));
            }
            return true;
        }
        return false;
    }

    private void previewSound(String key) {
        if (client == null) return;
        try {
            if (key.equals("Vanilla")) {
                dev.tempest.util.Keys.sound(SoundEvent.of(Identifier.ofVanilla(cat.events()[0])), 1f, 1f);
                return;
            }
            PackLooks.SoundOption o = PackLooks.soundOption(key);
            if (o == null || o.file() == null) return;
            SoundEvent ev = switch (key) {
                case "Orb" -> net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
                case "Ding" -> net.minecraft.sound.SoundEvents.ENTITY_ARROW_HIT_PLAYER;
                case "Pling" -> net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
                case "Bell" -> net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
                case "Hat" -> net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_HAT.value();
                case "Pop" -> net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP;
                case "Click" -> net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value();
                default -> net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP;
            };
            dev.tempest.util.Keys.sound(ev, 1f, 1f);   // plays on your player (only in a world)
        } catch (Exception ignored) { }
    }

    // ----- screenshots
    @Override
    public void onFilesDropped(List<Path> paths) {
        if (cat == null || cat.isSound() || paths.isEmpty()) return;
        useImage(paths.get(0));
    }

    private void useImage(Path p) {
        try {
            int rgb = PackLooks.dominantColor(p);
            setCustom(rgb);
            flash(String.format(Locale.ROOT, "Copied #%06X from %s - press Apply", rgb, p.getFileName()));
        } catch (Exception e) {
            flash("Couldn't read that picture");
        }
    }

    private void chooseFile() {
        Category target = cat;
        Thread t = new Thread(() -> {
            String f = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Pick a screenshot", null, null, "Images", false);
            if (f == null) return;
            MinecraftClient.getInstance().execute(() -> { if (cat == target) useImage(Path.of(f)); });
        }, "Tempest file picker");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (picker && pickDrag != 0) { pickerDrag(click.x(), click.y()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        pickDrag = 0;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (group >= 0 && cat != null && Draw.hit(mx, my, mainX(), gridY(), mainW(), gridH())) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - vertical * 40));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (picker) { picker = false; return true; }
            if (group >= 0) { clearPreviews(); cat = null; group = -1; return true; }
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        clearPreviews();
        super.removed();
    }

    @Override
    public void close() {
        clearPreviews();
        if (client != null) client.setScreen(parent);
    }
}
