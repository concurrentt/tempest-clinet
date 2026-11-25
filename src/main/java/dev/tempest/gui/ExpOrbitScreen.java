package dev.tempest.gui;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.util.ConfigManager;
import dev.tempest.util.PlayerHeads;
import dev.tempest.util.Social;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * EXPERIMENTAL Right Shift menu (Exp Tempest): every section sits on a turning ring of round buttons around the
 * Tempest logo, which floats in the middle as a slowly turning 3D badge. The button in front is the selected one;
 * its name, a line about it and a count show underneath. Scroll, drag or use the left/right arrow keys to turn the ring, click a
 * side button to bring it to the front, click the front one (or press Enter) to open it, or just start typing to
 * search every mod.
 */
public class ExpOrbitScreen extends Screen {
    private static final Identifier LOGO = Identifier.of("tempest", "textures/gui/loading/logo.png");
    private static final int LOGO_TEX = 512;

    /** One button on the ring. */
    private record Item(String label, String line, String icon, boolean head, Supplier<String> info, Supplier<Screen> open) { }

    private final Screen parent;
    private final List<Item> items = new ArrayList<>();
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();
    private static int savedIndex = 1;          // remembers the last section (starts on Combat)

    private float pos;                          // current ring position (item index, fractional while turning)
    private float target;                       // where the ring is heading
    private boolean dragging;
    private double dragStartX;
    private float dragStartPos;
    private boolean dragMoved;

    public ExpOrbitScreen(Screen parent) {
        super(Text.literal("Tempest Client"));
        this.parent = parent;
        build();
        pos = target = Math.floorMod(savedIndex, items.size());
    }

    // ---------------------------------------------------------------- the sections
    private void build() {
        cat(Category.HUD, "HUD", "Everything on your screen, placed your way");
        cat(Category.PVP, "Combat", "Precision and awareness in every fight");
        cat(Category.RENDER, "Render", "See the world more clearly");
        cat(Category.VISUALS, "Visuals", "Skies, colours and effects");
        items.add(new Item("Profile", "Your capes, cosmetics and emotes", "person", true,
                () -> "Capes, wings, hats and emotes", () -> new CosmeticsScreen(this, CosmeticsScreen.CAPES)));
        items.add(new Item("Social", "Friends, messages and invites", "people", false, ExpOrbitScreen::socialInfo,
                () -> new SocialScreen(this)));
        items.add(new Item("Configs", "Save and switch your setups", "layers", false, () -> {
            int n = ConfigManager.list().size();
            return n + (n == 1 ? " saved config" : " saved configs");
        }, () -> new ConfigsScreen(this)));
        items.add(new Item("Edit HUD", "Drag your HUD into place", "layout", false, () -> "Opens the HUD editor",
                () -> new HudEditorScreen(this)));
        items.add(new Item("Editor", "Textures, packs and painter", "brush", false, () -> "Opens the Tempest Editor",
                () -> new TempestEditorScreen(this)));
        cat(Category.QOL, "Quality of life", "Small things that save you time");
        cat(Category.UTILITY, "Utility", "Tools for every session");
        cat(Category.CHAT, "Chat", "Tabs, mentions and messages");
        cat(Category.CLIENT, "Client", "How Tempest looks and behaves");
    }

    private void cat(Category c, String label, String line) {
        items.add(new Item(label, line, Icons.forCategory(c), false, () -> {
            int total = 0, on = 0;
            for (Module m : ModuleManager.all()) {
                if (m.getCategory() != c) continue;
                total++;
                if (m.isEnabled()) on++;
            }
            return total + (total == 1 ? " module" : " modules") + "   " + on + " active";
        }, () -> new ExpModsScreen(c, this, "")));
    }

    private static String socialInfo() {
        int online = 0;
        for (Social.Friend f : Social.friends()) if (f.online()) online++;
        int req = Social.pendingCount();
        String s = online + " online";
        if (req > 0) s += "   " + req + (req == 1 ? " request" : " requests");
        return s;
    }

    // ---------------------------------------------------------------- ring maths
    private int n() { return items.size(); }
    private int front() { return Math.floorMod(Math.round(pos), n()); }
    /** Signed distance (in items) from the front, wrapped to -n/2..n/2. */
    private float rel(int i) {
        float r = (i - pos) % n();
        if (r > n() / 2f) r -= n();
        if (r < -n() / 2f) r += n();
        return r;
    }
    private int cx() { return width / 2; }
    private int ringY() { return Math.round(height * 0.40f); }
    private float radius() { return Math.min(width * 0.36f, 230f); }
    private int frontSize() { return Math.max(40, Math.min(66, Math.round(height * 0.16f))); }
    private static final int BTN_W = 104, BTN_H = 22;
    private int titleY() { return ringY() + frontSize() / 2 + 16; }
    private int pillY() { return titleY() + 42; }
    private int btnY() { return pillY() + 16 + 8; }

    /** Screen place of item i: x, y, size, depth (1 = front, -1 = back). */
    private float[] place(int i) {
        float a = rel(i) * (float) (Math.PI * 2 / n());
        float depth = (float) Math.cos(a);
        float x = cx() + radius() * (float) Math.sin(a);
        float d01 = (depth + 1f) / 2f;
        float y = ringY() - (1f - d01) * frontSize() * 0.95f;
        float size = frontSize() * (0.42f + 0.58f * d01 * d01);
        return new float[]{x, y, size, depth};
    }

    // ---------------------------------------------------------------- render
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() { MenuBlur.restore(); }

    private float intro(float delay) {
        if (!dev.tempest.modules.ClientSettingsModule.animationsOn()) return 1f;
        return MenuKit.ease(((System.nanoTime() - openedAt) / 1e9f - delay) / 0.45f);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (!dragging) pos += (target - pos) * (1f - (float) Math.exp(-dt * 11f));
        if (!dragging && Math.abs(target - pos) < 0.001f) pos = target;

        TextRenderer tr = textRenderer;
        float t = MenuKit.time();
        float e = intro(0f);

        background(ctx, t, e);

        // ring: back half first, then the logo, then the front half (so the logo sits inside the ring)
        Integer[] order = new Integer[n()];
        for (int i = 0; i < n(); i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(place(a)[3], place(b)[3]));
        int hovered = hoveredItem(mx, my);
        boolean logoDone = false;
        for (int i : order) {
            float[] p = place(i);
            if (!logoDone && p[3] > -0.05f) { logo(ctx, t, e); logoDone = true; }
            item(ctx, tr, i, p, i == hovered, e);
        }
        if (!logoDone) logo(ctx, t, e);

        // selected section: big name, its line, a count pill and the open button
        Item sel = items.get(front());
        float et = intro(0.12f);
        int lift = Math.round((1f - et) * 8);
        int ty = titleY() + lift;
        MenuKit.scaledCentered(ctx, tr, Draw.bold(sel.label()), cx(), ty, 2.6f, MenuKit.fa(0xFFF3F0FF, et), false);
        MenuKit.scaledCentered(ctx, tr, Draw.uiText(sel.line()), cx(), ty + 27, 1.1f, MenuKit.fa(0xD0D6CFF2, et), false);

        String info = sel.info().get();
        int pw = Draw.textW(tr, info) + 26, ph = 16, px = cx() - pw / 2, py = pillY() + lift;
        Draw.panel(ctx, px, py, pw, ph, ph / 2, MenuKit.fa(0x44FFFFFF, et), MenuKit.fa(0x38FFFFFF, et));
        Draw.circle(ctx, px + 9, py + ph / 2, 2, MenuKit.fa(Draw.withAlpha(Draw.accent(), 0xE0), et));
        Draw.text(ctx, tr, info, px + 16, py + 4, MenuKit.fa(0xFFE9E6FA, et), false);

        int bw = BTN_W, bh = BTN_H, bx = cx() - bw / 2, by = btnY() + lift;
        boolean bh0 = Draw.hit(mx, my, bx, by, bw, bh);
        float hp = Draw.hoverProgress("exp_open", bh0);
        Draw.shadow(ctx, bx, by, bw, bh, 9, 4 + Math.round(3 * hp));
        Draw.panel(ctx, bx, by, bw, bh, 9, MenuKit.fa(Draw.lerpColor(0xF0231C3E, 0xF0362A62, hp), et),
                MenuKit.fa(Draw.lerpColor(0x40FFFFFF, Draw.withAlpha(Draw.accent(), 0xC0), hp), et));
        MenuKit.scaledCentered(ctx, tr, Draw.bold("Open"), cx(), by + (bh - 10) / 2f, 1.25f, MenuKit.fa(0xFFFFFFFF, et), false);

        float eh = intro(0.3f);
        String hint = "Scroll or drag to turn   Enter to open   Type to search";
        Draw.text(ctx, tr, hint, cx() - Draw.textW(tr, hint) / 2, height - 16, MenuKit.fa(0x88FFFFFF, eh), false);
        String exp = "Exp Tempest";
        Draw.text(ctx, tr, exp, 8, 8, MenuKit.fa(0x70FFFFFF, eh), false);

        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    /** Purple night: blurred world (or the menu wallpaper), a violet wash, stars and slow falling sparkles. */
    private void background(DrawContext ctx, float t, float e) {
        paintBackground(ctx, width, height, cx(), ringY(), t, e);
        // a pool of light under the ring, squashed into an ellipse like a floor
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx(), ringY() + frontSize() * 0.4f);
        m.scale(1f, 0.22f);
        TempestLoading.drawGlow(ctx, 0, 0, Math.round(radius() * 2.9f), Draw.accent(), 0.34f * e);
        TempestLoading.drawGlow(ctx, 0, 0, Math.round(radius() * 1.4f), 0xB9A8FF, 0.22f * e);
        m.popMatrix();
        // the ring's track: a dotted orbit, brighter in front, with a glint running round it
        int dots = 160;
        float glint = (t * 0.11f) % 1f;
        for (int i = 0; i < dots; i++) {
            float f = i / (float) dots;
            float a = (float) (f * Math.PI * 2);
            float d01 = ((float) Math.cos(a) + 1f) / 2f;
            int x = Math.round(cx() + radius() * (float) Math.sin(a));
            int y = Math.round(ringY() - (1f - d01) * frontSize() * 0.95f);
            float dd = Math.abs(((f - glint) % 1f + 1.5f) % 1f - 0.5f);
            float gl = Math.max(0f, 1f - dd * 16f);
            int al = Math.min(255, Math.round((0x14 + 0x46 * d01 + 0xC0 * gl * gl) * e));
            ctx.fill(x, y, x + 1, y + 1, al << 24 | 0xD8CCFF);
            if (gl > 0.6f) ctx.fill(x - 1, y, x + 2, y + 1, Math.round(al * 0.5f) << 24 | 0xD8CCFF);
        }
    }

    /** The Exp background, shared with the Exp mod list. */
    static void paintBackground(DrawContext ctx, int width, int height, int glowX, int glowY, float t, float e) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.world == null) Draw.menuBackground(ctx, width, height);
        MenuBlur.apply(ctx);                                   // blur the game, or the wallpaper on the title screen
        ctx.fillGradient(0, 0, width, height, MenuKit.fa(0xC81A0E4A, e), MenuKit.fa(0xB83A2458, e));
        // slow drifting nebula clouds in a few hues, so the night has depth instead of one flat wash
        int big = Math.max(width, height);
        for (int k = 0; k < 5; k++) {
            float ph = t * (0.035f + hash(k * 11 + 1) * 0.03f) + k * 1.9f;
            int nx = Math.round(width * (0.15f + 0.7f * hash(k * 11 + 2)) + (float) Math.sin(ph) * width * 0.08f);
            int ny = Math.round(height * (0.15f + 0.7f * hash(k * 11 + 3)) + (float) Math.cos(ph * 1.3f) * height * 0.06f);
            int col = k % 3 == 0 ? 0x6A3CFF : k % 3 == 1 ? 0xC04BD8 : 0x2E7BE0;
            float breathe = 0.75f + 0.25f * (float) Math.sin(t * 0.4f + k * 2.1f);
            TempestLoading.drawGlow(ctx, nx, ny, Math.round(big * (0.55f + 0.35f * hash(k * 11 + 4))), col, 0.16f * breathe * e);
        }
        TempestLoading.drawGlow(ctx, glowX, glowY, Math.round(big * 1.2f), 0x7A4DFF, 0.26f * e);
        // darker corners pull the eye to the middle
        vignette(ctx, width, height, e);

        // stars: fixed spots that twinkle
        for (int k = 0; k < 70; k++) {
            float sx = hash(k * 3 + 1) * width, sy = hash(k * 3 + 2) * height;
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (0.8f + hash(k * 3 + 3) * 1.6f) + k));
            int a = Math.round(0xA0 * tw * e);
            ctx.fill(Math.round(sx), Math.round(sy), Math.round(sx) + 1, Math.round(sy) + 1, (a << 24) | 0xFFFFFF);
        }
        // sparkles: little 4-point stars drifting down and swaying
        for (int k = 0; k < 26; k++) {
            float speed = 8f + hash(k * 7 + 1) * 14f;
            float sy = ((hash(k * 7 + 2) * (height + 30)) + t * speed) % (height + 30) - 15;
            float sx = hash(k * 7 + 3) * width + (float) Math.sin(t * (0.5f + hash(k * 7 + 4)) + k) * 6f;
            float tw = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(t * 2.3f + k * 1.7f));
            int col = k % 3 == 0 ? 0xC8B8FF : k % 3 == 1 ? 0xFFFFFF : 0x9EE6D2;
            sparkle(ctx, Math.round(sx), Math.round(sy), hash(k * 7 + 5) < 0.4f ? 2 : 1, Draw.withAlpha(col, Math.round(0xE0 * tw * e)));
        }
    }

    /** Soft dark edges: four gradients along the sides. */
    static void vignette(DrawContext ctx, int width, int height, float e) {
        int vw = Math.max(40, width / 6), vh = Math.max(30, height / 5);
        ctx.fillGradient(0, 0, width, vh, MenuKit.fa(0x70050312, e), 0x00050312);
        ctx.fillGradient(0, height - vh, width, height, 0x00050312, MenuKit.fa(0x80050312, e));
        for (int i = 0; i < vw; i++) {
            int a = Math.round(0x60 * (1f - i / (float) vw) * (1f - i / (float) vw) * e);
            ctx.fill(i, 0, i + 1, height, a << 24 | 0x050312);
            ctx.fill(width - 1 - i, 0, width - i, height, a << 24 | 0x050312);
        }
    }

    private static void sparkle(DrawContext ctx, int x, int y, int r, int col) {
        ctx.fill(x - r - 1, y, x + r + 2, y + 1, col);
        ctx.fill(x, y - r - 1, x + 1, y + r + 2, col);
        if (r > 1) ctx.fill(x - 1, y - 1, x + 2, y + 2, Draw.withAlpha(col, ((col >>> 24) & 255) / 2));
    }

    private static float hash(int i) {
        int h = i * 0x27D4EB2D;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /**
     * The Tempest logo as a solid badge: a stack of darker violet copies behind it gives it thickness, and it turns
     * gently left and right while it floats, so you see its edge move like a real object.
     */
    private void logo(DrawContext ctx, float t, float e) {
        int size = Math.round(frontSize() * 1.95f);
        float bob = (float) Math.sin(t * 1.1f) * 3f;
        float cy = ringY() - frontSize() * 1.3f + bob;
        float yaw = (float) Math.sin(t * 0.55f) * 0.55f;                       // radians, gentle sway
        float sx = (float) Math.cos(yaw);
        float breathe = 0.5f + 0.5f * (float) Math.sin(t * 1.4f);
        TempestLoading.drawGlow(ctx, cx(), Math.round(cy), Math.round(size * (2.0f + 0.2f * breathe)), Draw.accent(), 0.30f * e);

        int layers = 9;
        float depthPx = size * 0.055f;
        for (int l = layers; l >= 0; l--) {
            float off = (float) Math.sin(yaw) * depthPx * l / layers;
            int col;
            if (l == 0) col = 0xFFFFFFFF;
            else {
                float k = l / (float) layers;
                int rgb = Draw.lerpColor(0xFF7C66E8, 0xFF2A1A66, k) & 0xFFFFFF;
                col = Draw.withAlpha(rgb, 0xFF);
            }
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx() + off, cy + l * 0.35f);
            ctx.getMatrices().scale(sx, 1f);
            int a = Math.round(255 * e);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, -size / 2, -size / 2, 0f, 0f, size, size,
                    LOGO_TEX, LOGO_TEX, LOGO_TEX, LOGO_TEX, (a << 24) | (col & 0xFFFFFF));
            ctx.getMatrices().popMatrix();
        }
        // a quick double flicker on the bolt every few seconds
        float flash = flash(t);
        if (flash > 0.01f) TempestLoading.drawGlow(ctx, cx(), Math.round(cy + size * 0.08f), Math.round(size * 1.1f), 0xB9A8FF, 0.55f * flash * e);
    }

    private static float flash(float t) {
        float p = t % 6.5f;
        float a = 0f;
        if (p > 4.0f && p < 4.14f) a = 1f - (p - 4.0f) / 0.14f;
        if (p > 4.24f && p < 4.6f) a = Math.max(a, 0.7f * (1f - (p - 4.24f) / 0.36f));
        return a;
    }

    private void item(DrawContext ctx, TextRenderer tr, int i, float[] p, boolean hovered, float e) {
        Item it = items.get(i);
        float d01 = (p[3] + 1f) / 2f;
        float alpha = e * (0.18f + 0.82f * d01 * d01);
        boolean front = i == front() && Math.abs(rel(i)) < 0.5f;
        float sel = Math.max(0f, 1f - Math.abs(rel(i)) * 1.6f);              // 1 at the front, fades as it turns away
        float hp = Draw.hoverProgress("exp_item" + i, hovered);
        int size = Math.round(p[2] * (1f + 0.06f * hp));
        int x = Math.round(p[0]) - size / 2, y = Math.round(p[1]) - size / 2;
        int r = size / 2;

        if (sel > 0.05f) TempestLoading.drawGlow(ctx, x + r, y + r, Math.round(size * 2.4f), Draw.accent(), 0.45f * sel * alpha);
        Draw.shadow(ctx, x, y, size, size, r, Math.max(2, Math.round(size * 0.12f)));
        int dark = Draw.lerpColor(0xF0221C38, 0xF02C2448, hp);
        int fill = Draw.lerpColor(dark, Draw.lerpColor(Draw.accent() | 0xFF000000, 0xFFB09BFF, 0.25f), sel);
        int border = Draw.lerpColor(0x40FFFFFF, 0xA0FFFFFF, Math.max(sel, hp * 0.6f));
        Draw.panel(ctx, x, y, size, size, r, MenuKit.fa(fill, alpha), MenuKit.fa(border, alpha));
        // soft highlight on the upper half, like the light catching a glass ball
        Draw.circle(ctx, x + r, y + Math.round(r * 0.72f), Math.round(r * 0.62f), MenuKit.fa(Draw.withAlpha(0xFFFFFF, Math.round(0x10 + 0x12 * sel)), alpha));

        if (it.head()) {
            UUID id = client != null && client.getSession() != null ? client.getSession().getUuidOrNull() : null;
            Identifier head = id != null ? PlayerHeads.get(id) : null;
            int hs = Math.round(size * 0.62f);
            if (head != null) {
                Draw.circle(ctx, x + r, y + r, hs / 2 + 2, MenuKit.fa(0xFF15121F, alpha));
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, x + r - hs / 2, y + r - hs / 2, 0f, 0f, hs, hs, 32, 32, 32, 32,
                        (Math.round(255 * alpha) << 24) | 0xFFFFFF);
            } else Icons.draw(ctx, it.icon(), x + r - hs / 2, y + r - hs / 2, hs, MenuKit.fa(0xFFFFFFFF, alpha));
        } else {
            int is = Math.round(size * 0.46f);
            Icons.draw(ctx, it.icon(), x + r - is / 2, y + r - is / 2, is, MenuKit.fa(Draw.lerpColor(0xD8E4E0F5, 0xFFFFFFFF, sel), alpha));
        }
        // red badge on Social while friend requests wait
        if ("Social".equals(it.label()) && Social.pendingCount() > 0) {
            String b = "+" + Math.min(99, Social.pendingCount());
            int bw = Draw.textW(tr, b) + 8;
            Draw.panel(ctx, x + size - bw + 2, y - 2, bw, 11, 5, MenuKit.fa(0xFFE5484D, alpha), MenuKit.fa(0xFF15121F, alpha));
            Draw.text(ctx, tr, b, x + size - bw + 6, y, MenuKit.fa(0xFFFFFFFF, alpha), false);
        }
        // name under the side buttons (the front one has the big title instead)
        if (!front && p[3] > 0.15f) {
            float la = alpha * MenuKit.clamp01((p[3] - 0.15f) / 0.5f);
            MenuKit.scaledCentered(ctx, tr, Draw.uiText(it.label()), x + r, y + size + 5, 1f, MenuKit.fa(0xC0E2DCFF, la), false);
        }
    }

    private int hoveredItem(double mx, double my) {
        int best = -1;
        float bestDepth = -9f;
        for (int i = 0; i < n(); i++) {
            float[] p = place(i);
            if (p[3] < -0.2f) continue;
            float dx = (float) mx - p[0], dy = (float) my - p[1];
            if (dx * dx + dy * dy <= (p[2] / 2f) * (p[2] / 2f) && p[3] > bestDepth) { best = i; bestDepth = p[3]; }
        }
        return best;
    }

    // ---------------------------------------------------------------- input
    private void turnTo(float t) { target = t; savedIndex = Math.floorMod(Math.round(t), n()); }

    private void openFront() {
        if (client == null) return;
        Item it = items.get(front());
        savedIndex = front();
        client.setScreen(it.open().get());
    }

    /** Turns the shortest way round to item i. */
    private void bringToFront(int i) {
        float r = rel(i);
        turnTo(Math.round(pos + r));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        double mx = click.x(), my = click.y();
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        if (Draw.hit(mx, my, cx() - BTN_W / 2, btnY(), BTN_W, BTN_H)) { openFront(); return true; }
        int h = hoveredItem(mx, my);
        if (h >= 0) {
            if (h == front() && Math.abs(rel(h)) < 0.5f) openFront();
            else bringToFront(h);
            return true;
        }
        dragging = true;
        dragMoved = false;
        dragStartX = mx;
        dragStartPos = pos;
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging) {
            float step = radius() * (float) (Math.PI * 2 / n());           // pixels per item at the front
            pos = dragStartPos - (float) ((click.x() - dragStartX) / step);
            target = pos;
            dragMoved = true;
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
            turnTo(Math.round(pos));
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double v = vertical != 0 ? vertical : -horizontal;
        if (v != 0) turnTo(Math.round(target) + (v > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_LEFT) { turnTo(Math.round(target) - 1); return true; }
        if (k == GLFW.GLFW_KEY_RIGHT) { turnTo(Math.round(target) + 1); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER || k == GLFW.GLFW_KEY_SPACE) { openFront(); return true; }
        if (k == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(input);
    }

    /** Typing a letter or number opens the mod list with that text already in its search box. */
    @Override
    public boolean charTyped(CharInput input) {
        if (input.isValidChar() && client != null) {
            String s = input.asString();
            if (!s.isBlank()) {
                openSearch(s);
                return true;
            }
        }
        return super.charTyped(input);
    }

    /** Opens the Exp mod list on "All" with {@code text} already in its search box. */
    private void openSearch(String text) {
        client.setScreen(new ExpModsScreen(null, this, text));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
