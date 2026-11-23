package dev.tempest.gui;

import dev.tempest.util.CustomPack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Texture Painter: pick any item or block texture, paint it pixel by pixel, and save it into your own resource
 * pack ("Tempest Custom", util/CustomPack). Saving turns the pack on and reloads, so you see it in game straight away.
 *
 * <p>Tools: brush (B), eraser (E), fill (G), colour picker (I; right-click also picks). Ctrl+Z / Ctrl+Y undo and
 * redo, Ctrl+S saves. Mirror paints both halves at once. Animated textures (water, fire...) are painted one frame at a
 * time, or on every frame at once.</p>
 */
public class TexturePainterScreen extends Screen {
    private static final int BRUSH = 0, ERASER = 1, FILL = 2, PICKER = 3;
    private static final String[] TOOL_ICONS = {"brush", "eraser", "bucket", "eyedropper"};
    private static final String[] TOOL_NAMES = {"Brush (B)", "Eraser (E)", "Fill (G)", "Pick colour (I)"};
    private static final String[] CATS = {"Items", "Blocks", "Edited"};
    private static final int ROW_H = 20, LIST_W = 150, TOOLS_W = 104, HEAD_H = 30;
    private static final int[] PALETTE = {
            0xFF000000, 0xFF3F3F3F, 0xFF7F7F7F, 0xFFBFBFBF, 0xFFFFFFFF, 0xFF7A3B12, 0xFFC27B37, 0xFFFFD29C,
            0xFFD8282F, 0xFFFF8A1E, 0xFFFFE135, 0xFF3FBF3A, 0xFF1F8F8F, 0xFF2F6FE0, 0xFF8A3FE0, 0xFFE05AB8};

    private final Screen parent;
    private TextFieldWidget search;
    private int cat;
    private List<Identifier> all = new ArrayList<>();
    private final List<Identifier> shown = new ArrayList<>();
    private Set<Identifier> edited = Set.of();
    private final Map<Identifier, int[]> dims = new HashMap<>();
    private double listScroll, listTarget;

    // the open texture
    private Identifier open;
    private int[] px, original;
    private int tw, th, frames = 1, frame;
    private boolean allFrames, dirtyTex, unsaved;
    private NativeImageBackedTexture texture;
    private Identifier texId;
    private static int texCounter;
    private final Deque<int[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    // tools
    private int tool = BRUSH, size = 1, color = 0xFFD8282F;
    private boolean mirror, grid = true;
    private float hue = 0f, sat = 0.8f, val = 0.85f;
    private int alpha = 255;
    private final List<Integer> recent = new ArrayList<>();
    private int painting = -1;       // mouse button while painting on the canvas
    private int lastPx = -1, lastPy = -1;
    private int dragSlider = 0;      // 1 = SV square, 2 = hue, 3 = alpha
    private String status = "";
    private long statusAt;

    private Identifier openOnInit;

    public TexturePainterScreen(Screen parent) {
        super(Text.literal("Texture Painter"));
        this.parent = parent;
        setHsv(color);
    }

    /** Opens straight onto one texture (the pencil button in Tempest Editor). */
    public TexturePainterScreen(Screen parent, Identifier texture) {
        this(parent);
        this.openOnInit = texture;
        if (texture != null && texture.getPath().startsWith("textures/block/")) cat = 1;
    }

    // ---------------------------------------------------------------- layout
    private int winW() { return Math.min(width - 16, 660); }
    private int winH() { return Math.min(height - 16, 380); }
    private int x0() { return (width - winW()) / 2; }
    private int y0() { return (height - winH()) / 2; }
    private int listX() { return x0() + 10; }
    private int listY() { return y0() + HEAD_H + 50; }
    private int listH() { return y0() + winH() - 10 - listY(); }
    private int toolsX() { return x0() + winW() - 10 - TOOLS_W; }
    private int canX() { return listX() + LIST_W + 12; }
    private int canW() { return toolsX() - 12 - canX(); }
    private int canY() { return y0() + HEAD_H + 8; }
    private int canH() { return winH() - HEAD_H - 8 - 46; }
    private int frameH() { return frames > 1 ? th / frames : th; }

    /** Size of one texture pixel on screen. */
    private int scale() {
        if (open == null) return 1;
        return Math.max(1, Math.min((canW() - 8) / tw, (canH() - 8) / frameH()));
    }
    private int imgX() { return canX() + (canW() - tw * scale()) / 2; }
    private int imgY() { return canY() + (canH() - frameH() * scale()) / 2; }

    @Override
    protected void init() {
        String old = search != null ? search.getText() : "";
        search = new TextFieldWidget(textRenderer, listX() + 20, y0() + HEAD_H + 34, LIST_W - 26, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setPlaceholder(Text.literal("Search textures..."));
        search.setMaxLength(48);
        search.setText(old);
        search.setChangedListener(s -> { listTarget = 0; filter(); });
        addDrawableChild(search);
        if (all.isEmpty()) loadList();
        filter();
        if (openOnInit != null) {
            Identifier t = openOnInit;
            openOnInit = null;
            openTexture(t);
        }
    }

    private void loadList() {
        MinecraftClient c = MinecraftClient.getInstance();
        all = new ArrayList<>();
        try {
            String root = cat == 1 ? "textures/block" : "textures/item";
            if (cat == 2) {
                all.addAll(CustomPack.editedTextures());
            } else {
                for (Identifier id : c.getResourceManager().findResources(root, i -> i.getPath().endsWith(".png")).keySet()) {
                    if (!all.contains(id)) all.add(id);
                }
            }
        } catch (Exception ignored) { }
        all.sort((a, b) -> shortName(a).compareTo(shortName(b)));
        edited = CustomPack.editedTextures();
    }

    private void filter() {
        shown.clear();
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (Identifier id : all) if (q.isEmpty() || id.getPath().contains(q)) shown.add(id);
    }

    private static String shortName(Identifier id) {
        String p = id.getPath();
        int s = p.lastIndexOf('/');
        p = p.substring(s + 1, p.length() - 4);
        return id.getNamespace().equals("minecraft") ? p : id.getNamespace() + ":" + p;
    }

    private static String pretty(Identifier id) {
        String s = shortName(id).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------------------------------------------------------- open / save
    private void openTexture(Identifier id) {
        if (unsaved && open != null) flash("Unsaved changes to " + pretty(open) + " were dropped");
        NativeImage img = CustomPack.read(id, false);
        if (img == null) { flash("Couldn't read that texture"); return; }
        try (img) {
            int w = img.getWidth(), h = img.getHeight();
            if (w > 512 || h > 8192) { flash("That texture is too big to paint here"); return; }
            int[] p = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) p[y * w + x] = img.getColorArgb(x, y);
            open = id;
            tw = w;
            th = h;
            px = p;
            original = p.clone();
        }
        frames = th > tw && th % tw == 0 ? th / tw : 1;
        frame = 0;
        undo.clear();
        redo.clear();
        unsaved = false;
        rebuildTexture();
    }

    private void rebuildTexture() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (texId != null) c.getTextureManager().destroyTexture(texId);
        NativeImage img = new NativeImage(tw, th, true);
        texture = new NativeImageBackedTexture(() -> "Tempest texture painter", img);
        texId = Identifier.of("tempest", "dynamic/painter_" + (texCounter++));
        c.getTextureManager().registerTexture(texId, texture);
        dirtyTex = true;
    }

    private void syncTexture() {
        if (!dirtyTex || texture == null || texture.getImage() == null) return;
        NativeImage img = texture.getImage();
        for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) img.setColorArgb(x, y, px[y * tw + x]);
        texture.upload();
        dirtyTex = false;
    }

    private void save() {
        if (open == null) return;
        try {
            CustomPack.save(open, px, tw, th);
            unsaved = false;
            edited = CustomPack.editedTextures();
            flash("Saved - reloading textures…");
            CustomPack.apply();
        } catch (Exception e) {
            flash("Couldn't save: " + e.getMessage());
        }
    }

    private void revert() {
        if (open == null) return;
        pushUndo();
        px = original.clone();
        dirtyTex = true;
        unsaved = true;
        flash("Back to how it was when you opened it");
    }

    private void removeFromPack() {
        if (open == null || !CustomPack.edited(open)) return;
        try {
            CustomPack.remove(open);
            edited = CustomPack.editedTextures();
            NativeImage img = CustomPack.read(open, true);
            if (img != null) {
                try (img) {
                    if (img.getWidth() == tw && img.getHeight() == th) {
                        pushUndo();
                        for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) px[y * tw + x] = img.getColorArgb(x, y);
                        original = px.clone();
                        dirtyTex = true;
                    }
                }
            }
            unsaved = false;
            flash("Removed from your pack - reloading…");
            CustomPack.apply();
        } catch (Exception e) {
            flash("Couldn't remove: " + e.getMessage());
        }
    }

    private void flash(String s) { status = s; statusAt = System.currentTimeMillis(); }

    // ---------------------------------------------------------------- painting
    private void pushUndo() {
        undo.push(px.clone());
        while (undo.size() > 60) undo.removeLast();
        redo.clear();
    }

    private void doUndo() {
        if (undo.isEmpty()) return;
        redo.push(px);
        px = undo.pop();
        dirtyTex = true;
        unsaved = true;
    }

    private void doRedo() {
        if (redo.isEmpty()) return;
        undo.push(px);
        px = redo.pop();
        dirtyTex = true;
        unsaved = true;
    }

    private void set(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= tw || y >= frameH()) return;
        int fh = frameH();
        if (allFrames && frames > 1) {
            for (int f = 0; f < frames; f++) px[(f * fh + y) * tw + x] = argb;
        } else {
            px[(frame * fh + y) * tw + x] = argb;
        }
    }

    private int get(int x, int y) { return px[(frame * frameH() + y) * tw + x]; }

    private void dab(int x, int y, int argb) {
        int o = (size - 1) / 2;
        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                set(x - o + dx, y - o + dy, argb);
                if (mirror) set(tw - 1 - (x - o + dx), y - o + dy, argb);
            }
        }
        dirtyTex = true;
        unsaved = true;
    }

    private void line(int x0, int y0, int x1, int y1, int argb) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            dab(x0, y0, argb);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void fill(int x, int y, int argb) {
        int target = get(x, y);
        if (target == argb) return;
        int fh = frameH();
        boolean[] seen = new boolean[tw * fh];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{x, y});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int cx = p[0], cy = p[1];
            if (cx < 0 || cy < 0 || cx >= tw || cy >= fh || seen[cy * tw + cx] || get(cx, cy) != target) continue;
            seen[cy * tw + cx] = true;
            set(cx, cy, argb);
            if (mirror) set(tw - 1 - cx, cy, argb);
            stack.push(new int[]{cx + 1, cy});
            stack.push(new int[]{cx - 1, cy});
            stack.push(new int[]{cx, cy + 1});
            stack.push(new int[]{cx, cy - 1});
        }
        dirtyTex = true;
        unsaved = true;
    }

    private void pick(int x, int y) {
        int c = get(x, y);
        if ((c >>> 24) == 0) { tool = ERASER; return; }
        setColor(c);
    }

    private void setColor(int argb) {
        color = argb;
        setHsv(argb);
    }

    private void setHsv(int argb) {
        float[] hsv = Draw.rgbToHsv(argb & 0xFFFFFF);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        alpha = (argb >>> 24) & 255;
    }

    private void colorFromHsv() {
        color = (alpha << 24) | (Draw.hsvToRgb(hue, sat, val) & 0xFFFFFF);
    }

    private void remember(int argb) {
        recent.remove((Integer) argb);
        recent.add(0, argb);
        while (recent.size() > 8) recent.remove(recent.size() - 1);
    }

    /** Texture pixel under the mouse, or null. */
    private int[] pixelAt(double mx, double my) {
        if (open == null) return null;
        int s = scale();
        int x = (int) Math.floor((mx - imgX()) / s), y = (int) Math.floor((my - imgY()) / s);
        if (x < 0 || y < 0 || x >= tw || y >= frameH()) return null;
        return new int[]{x, y};
    }

    private void strokeAt(double mx, double my, boolean first) {
        int[] p = pixelAt(mx, my);
        if (p == null) return;
        boolean secondary = painting == 1;
        int t = secondary ? PICKER : tool;
        switch (t) {
            case PICKER -> pick(p[0], p[1]);
            case FILL -> { if (first) { pushUndo(); fill(p[0], p[1], color); remember(color); } }
            default -> {
                int c = t == ERASER ? 0 : color;
                if (first) { pushUndo(); dab(p[0], p[1], c); if (t == BRUSH) remember(color); }
                else if (lastPx >= 0) line(lastPx, lastPy, p[0], p[1], c);
            }
        }
        lastPx = p[0];
        lastPy = p[1];
    }

    // ---------------------------------------------------------------- render
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        listScroll += (listTarget - listScroll) * 0.35;
        if (client != null && client.world == null) Draw.menuBackground(ctx, width, height);
        else ctx.fillGradient(0, 0, width, height, 0x90000000, 0x70000000);
        int x = x0(), y = y0(), w = winW(), h = winH();
        Draw.shadow(ctx, x, y, w, h, 10, 12);
        Draw.glass(ctx, x, y, w, h, 10, 0xE40F1117, 0x40FFFFFF);
        MenuKit.titleBar(ctx, tr, x + 10, y + 6, "Texture Painter", null, Draw.hit(mx, my, x + 10, y + 6, 24, 24), 1f);
        String packLine = CustomPack.enabled() ? "Saving into \"" + CustomPack.NAME + "\" (on)" : "Saves into the \"" + CustomPack.NAME + "\" pack";
        Draw.text(ctx, tr, packLine, x + w - 12 - Draw.textW(tr, packLine), y + 14, 0xFF8B90A0, false);

        drawList(ctx, tr, mx, my);
        drawCanvas(ctx, tr, mx, my);
        drawTools(ctx, tr, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void drawList(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int lx = listX(), top = y0() + HEAD_H + 8, cw = (LIST_W - 4) / 3;
        for (int i = 0; i < CATS.length; i++) {
            int cx = lx + i * (cw + 2);
            boolean sel = i == cat, hv = Draw.hit(mx, my, cx, top, cw, 18);
            Draw.panel(ctx, cx, top, cw, 18, 9, sel ? Draw.withAlpha(Draw.accent(), 0xD0) : hv ? 0x8A3A3C44 : 0x5A26272C, sel ? 0x60FFFFFF : 0x30FFFFFF);
            Draw.centered(ctx, tr, CATS[i], cx + cw / 2, top + 5, 0xFFFFFFFF);
        }
        int sy = top + 22;
        Draw.panel(ctx, lx, sy, LIST_W, 18, 6, search.isFocused() ? 0xE0202330 : 0xC0181A22, search.isFocused() ? Draw.withAlpha(Draw.accent(), 0xA0) : 0x30FFFFFF);
        Icons.draw(ctx, "search", lx + 5, sy + 4, 10, 0xA0FFFFFF);
        search.setX(lx + 20);
        search.setY(sy + 5);

        int ly = listY(), lh = listH();
        Draw.roundRect(ctx, lx, ly, LIST_W, lh, 6, 0x70101218);
        ctx.enableScissor(lx, ly, lx + LIST_W, ly + lh);
        int first = Math.max(0, (int) (listScroll / ROW_H));
        for (int i = first; i < shown.size(); i++) {
            int ry = ly + 2 + i * ROW_H - (int) Math.round(listScroll);
            if (ry > ly + lh) break;
            Identifier id = shown.get(i);
            boolean hv = Draw.hit(mx, my, lx, ry, LIST_W, ROW_H) && my >= ly && my < ly + lh, sel = id.equals(open);
            if (sel || hv) Draw.roundRect(ctx, lx + 2, ry, LIST_W - 4, ROW_H - 2, 5, sel ? Draw.withAlpha(Draw.accent(), 0x70) : 0x40FFFFFF);
            thumb(ctx, id, lx + 5, ry + 1, 16);
            String name = Draw.ellipsize(tr, pretty(id), LIST_W - 36);
            Draw.text(ctx, tr, name, lx + 26, ry + 5, 0xFFE4E6EC, false);
            if (edited.contains(id)) Draw.circle(ctx, lx + LIST_W - 9, ry + 9, 2, 0xFF6AF09A);
        }
        ctx.disableScissor();
        if (shown.isEmpty()) {
            String msg = cat == 2 ? "Nothing painted yet" : "No textures match";
            Draw.centered(ctx, tr, msg, lx + LIST_W / 2, ly + 14, 0xFF8B90A0);
        }
        MenuKit.listFades(ctx, lx, ly, LIST_W, lh, listScroll, maxListScroll());
    }

    private double maxListScroll() { return Math.max(0, shown.size() * ROW_H + 4 - listH()); }

    /** Small preview of a game texture (first frame of animated ones). */
    private void thumb(DrawContext ctx, Identifier id, int x, int y, int s) {
        int[] d = dims.computeIfAbsent(id, TexturePainterScreen::readSize);
        if (d[0] <= 0) return;
        int fw = d[0], fh = d[1] > d[0] && d[1] % d[0] == 0 ? d[0] : d[1];
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, s, s, fw, fh, d[0], d[1], 0xFFFFFFFF);
    }

    /** Width and height from the PNG header (no full decode). */
    private static int[] readSize(Identifier id) {
        try {
            var r = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (r.isEmpty()) return new int[]{0, 0};
            try (InputStream in = r.get().getInputStream()) {
                byte[] b = in.readNBytes(24);
                if (b.length < 24) return new int[]{0, 0};
                int w = ((b[16] & 255) << 24) | ((b[17] & 255) << 16) | ((b[18] & 255) << 8) | (b[19] & 255);
                int h = ((b[20] & 255) << 24) | ((b[21] & 255) << 16) | ((b[22] & 255) << 8) | (b[23] & 255);
                return new int[]{w, h};
            }
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    private void drawCanvas(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int cx = canX(), cy = canY(), cw = canW(), ch = canH();
        Draw.roundRect(ctx, cx, cy, cw, ch, 8, 0x80090A0E);
        if (open == null) {
            Icons.draw(ctx, "brush", cx + cw / 2 - 20, cy + ch / 2 - 34, 40, 0x90FFFFFF);
            Draw.centered(ctx, tr, "Pick a texture on the left", cx + cw / 2, cy + ch / 2 + 14, 0xFFB0B4C4);
            Draw.centered(ctx, tr, "to start painting", cx + cw / 2, cy + ch / 2 + 26, 0xFF8B90A0);
        } else {
            syncTexture();
            int s = scale(), ix = imgX(), iy = imgY(), fh = frameH();
            // checkerboard so transparent pixels are visible
            int cs = Math.max(4, s / 2 * 2);
            ctx.enableScissor(ix, iy, ix + tw * s, iy + fh * s);
            for (int yy = 0; yy * cs < fh * s; yy++) {
                for (int xx = 0; xx * cs < tw * s; xx++) {
                    ctx.fill(ix + xx * cs, iy + yy * cs, ix + (xx + 1) * cs, iy + (yy + 1) * cs, ((xx + yy) & 1) == 0 ? 0xFF2A2C34 : 0xFF3A3D47);
                }
            }
            ctx.disableScissor();
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, ix, iy, 0f, (float) (frame * fh), tw * s, fh * s, tw, fh, tw, th, 0xFFFFFFFF);
            if (grid && s >= 6) {
                for (int gx = 1; gx < tw; gx++) ctx.fill(ix + gx * s, iy, ix + gx * s + 1, iy + fh * s, 0x22FFFFFF);
                for (int gy = 1; gy < fh; gy++) ctx.fill(ix, iy + gy * s, ix + tw * s, iy + gy * s + 1, 0x22FFFFFF);
            }
            if (mirror) ctx.fill(ix + tw * s / 2, iy - 3, ix + tw * s / 2 + 1, iy + fh * s + 3, Draw.withAlpha(Draw.accent(), 0x90));
            Draw.ring(ctx, ix - 1, iy - 1, tw * s + 2, fh * s + 2, 1, 0x40FFFFFF);
            int[] hp = pixelAt(mx, my);
            if (hp != null) {
                int o = tool == BRUSH || tool == ERASER ? (size - 1) / 2 : 0, sz = tool == BRUSH || tool == ERASER ? size : 1;
                Draw.ring(ctx, ix + (hp[0] - o) * s, iy + (hp[1] - o) * s, sz * s, sz * s, 0, 0xC0FFFFFF);
            }
            // title + frame controls
            String t = pretty(open) + "  " + tw + "x" + fh + (unsaved ? "  • unsaved" : "");
            Draw.text(ctx, tr, Draw.ellipsize(tr, t, cw - 8), cx + 6, cy + 5, unsaved ? 0xFFFFD27A : 0xFFB0B4C4, false);
            if (frames > 1) {
                String f = "Frame " + (frame + 1) + "/" + frames;
                int fx = cx + cw - 100, fy = cy + ch - 18;
                small(ctx, tr, fx, fy, 14, "<", mx, my);
                Draw.centered(ctx, tr, f, fx + 40, fy + 3, 0xFFE4E6EC);
                small(ctx, tr, fx + 66, fy, 14, ">", mx, my);
                checkbox(ctx, tr, cx + 6, fy + 2, "Paint every frame", allFrames);
            }
        }
        // footer buttons under the canvas
        int by = cy + ch + 8;
        boolean can = open != null;
        MenuKit.flatButton(ctx, tr, "tp_save", cx, by, 110, 22, "play", "Save & Apply", MenuKit.GREEN, Draw.hit(mx, my, cx, by, 110, 22), can, 1f);
        MenuKit.flatButton(ctx, tr, "tp_revert", cx + 116, by, 70, 22, "refresh", "Revert", MenuKit.OUTLINE, Draw.hit(mx, my, cx + 116, by, 70, 22), can && (unsaved || !undo.isEmpty()), 1f);
        boolean inPack = can && edited.contains(open);
        MenuKit.flatButton(ctx, tr, "tp_remove", cx + 192, by, 104, 22, "trash", "Use original", MenuKit.OUTLINE, Draw.hit(mx, my, cx + 192, by, 104, 22), inPack, 1f);
        if (System.currentTimeMillis() - statusAt < 6000 && !status.isEmpty()) {
            Draw.text(ctx, tr, Draw.ellipsize(tr, status, cw), cx, by + 28, 0xFFB8F5C8, false);
        }
    }

    private void small(DrawContext ctx, TextRenderer tr, int x, int y, int s, String label, int mx, int my) {
        boolean hv = Draw.hit(mx, my, x, y, s, s);
        Draw.panel(ctx, x, y, s, s, 5, hv ? 0xEC2A2C33 : 0xD81A1B20, hv ? 0x70FFFFFF : 0x30FFFFFF);
        Draw.centered(ctx, tr, label, x + s / 2 + 1, y + (s - 8) / 2 + 1, 0xFFFFFFFF);
    }

    private void checkbox(DrawContext ctx, TextRenderer tr, int x, int y, String label, boolean on) {
        Draw.panel(ctx, x, y, 10, 10, 3, on ? Draw.accent() : 0xC0181A22, 0x60FFFFFF);
        Draw.text(ctx, tr, label, x + 14, y + 1, 0xFFC9CCD6, false);
    }

    // tools column positions
    private int toolY() { return y0() + HEAD_H + 8; }
    private int svY() { return toolY() + 104; }
    private static final int SV_H = 56;
    private int hueY() { return svY() + SV_H + 5; }
    private int alphaY() { return hueY() + 12; }
    private int palY() { return alphaY() + 14; }

    private void drawTools(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int tx = toolsX(), ty = toolY();
        for (int i = 0; i < 4; i++) {
            int bx = tx + i * 26, sel = i == tool ? 1 : 0;
            boolean hv = Draw.hit(mx, my, bx, ty, 24, 24);
            Draw.panel(ctx, bx, ty, 24, 24, 6, sel == 1 ? Draw.withAlpha(Draw.accent(), 0xC0) : hv ? 0xEC2A2C33 : 0xD81A1B20, sel == 1 ? 0x80FFFFFF : 0x30FFFFFF);
            Icons.draw(ctx, TOOL_ICONS[i], bx + 5, ty + 5, 14, 0xFFFFFFFF);
            if (hv) tooltip(ctx, tr, TOOL_NAMES[i], mx, my);
        }
        int ry = ty + 28;
        String[][] row = {{"undo", "Undo (Ctrl+Z)"}, {"redo", "Redo (Ctrl+Y)"}, {"swap", "Mirror"}, {"grid", "Grid"}};
        for (int i = 0; i < 4; i++) {
            int bx = tx + i * 26;
            boolean hv = Draw.hit(mx, my, bx, ry, 24, 20);
            boolean on = (i == 2 && mirror) || (i == 3 && grid);
            boolean enabled = i == 0 ? !undo.isEmpty() : i == 1 ? !redo.isEmpty() : true;
            Draw.panel(ctx, bx, ry, 24, 20, 6, on ? Draw.withAlpha(Draw.accent(), 0xA0) : hv && enabled ? 0xEC2A2C33 : 0xD81A1B20, 0x30FFFFFF);
            Icons.draw(ctx, row[i][0], bx + 6, ry + 4, 12, enabled ? 0xFFFFFFFF : 0x50FFFFFF);
            if (hv) tooltip(ctx, tr, row[i][1], mx, my);
        }
        // brush size
        int sy = ry + 26;
        Draw.text(ctx, tr, "Size", tx, sy + 3, 0xFF9DA2B3, false);
        for (int s = 1; s <= 3; s++) {
            int bx = tx + 30 + (s - 1) * 24;
            boolean hv = Draw.hit(mx, my, bx, sy, 20, 14);
            Draw.panel(ctx, bx, sy, 20, 14, 5, s == size ? Draw.withAlpha(Draw.accent(), 0xC0) : hv ? 0xEC2A2C33 : 0xD81A1B20, 0x30FFFFFF);
            Draw.centered(ctx, tr, Integer.toString(s), bx + 10, sy + 3, 0xFFFFFFFF);
        }
        // current colour
        int cy = sy + 20;
        int cs = 6;
        for (int yy = 0; yy < 4; yy++) for (int xx = 0; xx < 4; xx++)
            ctx.fill(tx + xx * cs, cy + yy * cs, tx + (xx + 1) * cs, cy + (yy + 1) * cs, ((xx + yy) & 1) == 0 ? 0xFF2A2C34 : 0xFF3A3D47);
        ctx.fill(tx, cy, tx + 24, cy + 24, color);
        Draw.ring(ctx, tx - 1, cy - 1, 26, 26, 2, 0x60FFFFFF);
        String hex = String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        Draw.text(ctx, tr, hex, tx + 30, cy + 3, 0xFFE4E6EC, false);
        Draw.text(ctx, tr, "Alpha " + Math.round(alpha / 2.55f) + "%", tx + 30, cy + 14, 0xFF9DA2B3, false);

        // saturation / value square
        int sx = tx, sw = TOOLS_W, svy = svY();
        int hueRgb = Draw.hsvToRgb(hue, 1f, 1f) & 0xFFFFFF;
        for (int i = 0; i < sw; i++) {
            float s = i / (float) (sw - 1);
            int top = 0xFF000000 | Draw.lerpColor(0xFFFFFFFF, 0xFF000000 | hueRgb, s);
            ctx.fillGradient(sx + i, svy, sx + i + 1, svy + SV_H, top, 0xFF000000);
        }
        int kx = sx + Math.round(sat * (sw - 1)), ky = svy + Math.round((1 - val) * (SV_H - 1));
        Draw.ring(ctx, kx - 3, ky - 3, 7, 7, 3, 0xFFFFFFFF);
        // hue bar
        int hy = hueY();
        for (int i = 0; i < sw; i++) ctx.fill(sx + i, hy, sx + i + 1, hy + 8, 0xFF000000 | Draw.hsvToRgb(i / (float) sw, 1f, 1f));
        int hx = sx + Math.round(hue * (sw - 1));
        ctx.fill(hx - 1, hy - 2, hx + 2, hy + 10, 0xFFFFFFFF);
        // alpha bar
        int ay = alphaY(), rgb = color & 0xFFFFFF;
        for (int i = 0; i < sw; i += 4) ctx.fill(sx + i, ay, sx + i + 4, ay + 8, ((i / 4) & 1) == 0 ? 0xFF2A2C34 : 0xFF3A3D47);
        for (int i = 0; i < sw; i++) ctx.fill(sx + i, ay, sx + i + 1, ay + 8, (Math.round(255f * i / (sw - 1)) << 24) | rgb);
        int axp = sx + Math.round(alpha / 255f * (sw - 1));
        ctx.fill(axp - 1, ay - 2, axp + 2, ay + 10, 0xFFFFFFFF);
        // palette + recent colours
        int py = palY();
        for (int i = 0; i < PALETTE.length; i++) {
            int px0 = sx + (i % 8) * 13, py0 = py + (i / 8) * 13;
            ctx.fill(px0, py0, px0 + 12, py0 + 12, PALETTE[i]);
        }
        if (!recent.isEmpty()) {
            Draw.text(ctx, tr, "Recent", sx, py + 30, 0xFF9DA2B3, false);
            for (int i = 0; i < recent.size(); i++) ctx.fill(sx + i * 13, py + 41, sx + i * 13 + 12, py + 53, recent.get(i) | 0xFF000000);
        }
    }

    private void tooltip(DrawContext ctx, TextRenderer tr, String s, int mx, int my) {
        int w = Draw.textW(tr, s) + 8;
        int x = Math.min(mx + 8, width - w - 2), y = my - 16;
        Draw.panel(ctx, x, y, w, 14, 4, 0xF0101218, 0x50FFFFFF);
        Draw.text(ctx, tr, s, x + 4, y + 3, 0xFFFFFFFF, false);
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();
        if (Draw.hit(mx, my, x0() + 10, y0() + 6, 24, 24)) { close(); return true; }
        if (super.mouseClicked(click, doubled)) return true;   // search field
        if (search.isFocused() && !Draw.hit(mx, my, listX(), y0() + HEAD_H + 30, LIST_W, 18)) { search.setFocused(false); setFocused(null); }

        // categories
        int lx = listX(), top = y0() + HEAD_H + 8, cw = (LIST_W - 4) / 3;
        for (int i = 0; i < CATS.length; i++) {
            if (Draw.hit(mx, my, lx + i * (cw + 2), top, cw, 18)) {
                if (cat != i) { cat = i; listTarget = listScroll = 0; loadList(); filter(); }
                return true;
            }
        }
        if (Draw.hit(mx, my, lx, top + 22, LIST_W, 18)) { setFocused(search); search.setFocused(true); return true; }
        // list rows
        if (Draw.hit(mx, my, lx, listY(), LIST_W, listH())) {
            int i = (int) ((my - listY() - 2 + listScroll) / ROW_H);
            if (i >= 0 && i < shown.size()) openTexture(shown.get(i));
            return true;
        }
        // canvas
        if (open != null && pixelAt(mx, my) != null) {
            painting = btn;
            lastPx = lastPy = -1;
            strokeAt(mx, my, true);
            return true;
        }
        if (open != null && frames > 1) {
            int fx = canX() + canW() - 100, fy = canY() + canH() - 18;
            if (Draw.hit(mx, my, fx, fy, 14, 14)) { frame = (frame + frames - 1) % frames; return true; }
            if (Draw.hit(mx, my, fx + 66, fy, 14, 14)) { frame = (frame + 1) % frames; return true; }
            if (Draw.hit(mx, my, canX() + 6, fy + 2, 110, 10)) { allFrames = !allFrames; return true; }
        }
        // footer
        int cx = canX(), by = canY() + canH() + 8;
        if (open != null && Draw.hit(mx, my, cx, by, 110, 22)) { save(); return true; }
        if (open != null && Draw.hit(mx, my, cx + 116, by, 70, 22)) { revert(); return true; }
        if (open != null && Draw.hit(mx, my, cx + 192, by, 104, 22)) { removeFromPack(); return true; }
        return clickTools(mx, my);
    }

    private boolean clickTools(double mx, double my) {
        int tx = toolsX(), ty = toolY();
        for (int i = 0; i < 4; i++) if (Draw.hit(mx, my, tx + i * 26, ty, 24, 24)) { tool = i; return true; }
        int ry = ty + 28;
        if (Draw.hit(mx, my, tx, ry, 24, 20)) { doUndo(); return true; }
        if (Draw.hit(mx, my, tx + 26, ry, 24, 20)) { doRedo(); return true; }
        if (Draw.hit(mx, my, tx + 52, ry, 24, 20)) { mirror = !mirror; return true; }
        if (Draw.hit(mx, my, tx + 78, ry, 24, 20)) { grid = !grid; return true; }
        int sy = ry + 26;
        for (int s = 1; s <= 3; s++) if (Draw.hit(mx, my, tx + 30 + (s - 1) * 24, sy, 20, 14)) { size = s; return true; }
        if (Draw.hit(mx, my, tx, svY(), TOOLS_W, SV_H)) { dragSlider = 1; dragColor(mx, my); return true; }
        if (Draw.hit(mx, my, tx, hueY() - 2, TOOLS_W, 12)) { dragSlider = 2; dragColor(mx, my); return true; }
        if (Draw.hit(mx, my, tx, alphaY() - 2, TOOLS_W, 12)) { dragSlider = 3; dragColor(mx, my); return true; }
        int py = palY();
        for (int i = 0; i < PALETTE.length; i++) {
            if (Draw.hit(mx, my, tx + (i % 8) * 13, py + (i / 8) * 13, 12, 12)) { setColor(PALETTE[i]); pickBrush(); return true; }
        }
        for (int i = 0; i < recent.size(); i++) {
            if (Draw.hit(mx, my, tx + i * 13, py + 41, 12, 12)) { setColor(recent.get(i)); pickBrush(); return true; }
        }
        return false;
    }

    private void pickBrush() { if (tool == ERASER || tool == PICKER) tool = BRUSH; }

    private void dragColor(double mx, double my) {
        float fx = (float) Math.max(0, Math.min(1, (mx - toolsX()) / (TOOLS_W - 1)));
        switch (dragSlider) {
            case 1 -> { sat = fx; val = 1f - (float) Math.max(0, Math.min(1, (my - svY()) / (SV_H - 1))); }
            case 2 -> hue = Math.min(0.999f, fx);
            case 3 -> alpha = Math.round(fx * 255);
            default -> { return; }
        }
        colorFromHsv();
        pickBrush();
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragSlider != 0) { dragColor(click.x(), click.y()); return true; }
        if (painting >= 0) { strokeAt(click.x(), click.y(), false); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        painting = -1;
        dragSlider = 0;
        lastPx = lastPy = -1;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Draw.hit(mx, my, listX(), listY(), LIST_W, listH())) {
            listTarget = Math.max(0, Math.min(maxListScroll(), listTarget - vertical * ROW_H * 2));
            return true;
        }
        if (open != null && frames > 1 && Draw.hit(mx, my, canX(), canY(), canW(), canH())) {
            frame = Math.floorMod(frame - (int) Math.signum(vertical), frames);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        boolean ctrl = dev.tempest.util.Keys.ctrl();
        if (search.isFocused()) {
            if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER) { search.setFocused(false); setFocused(null); return true; }
            return super.keyPressed(input);
        }
        if (ctrl && k == GLFW.GLFW_KEY_Z) { doUndo(); return true; }
        if (ctrl && k == GLFW.GLFW_KEY_Y) { doRedo(); return true; }
        if (ctrl && k == GLFW.GLFW_KEY_S) { save(); return true; }
        if (ctrl && k == GLFW.GLFW_KEY_F) { setFocused(search); search.setFocused(true); return true; }
        switch (k) {
            case GLFW.GLFW_KEY_B -> { tool = BRUSH; return true; }
            case GLFW.GLFW_KEY_E -> { tool = ERASER; return true; }
            case GLFW.GLFW_KEY_G -> { tool = FILL; return true; }
            case GLFW.GLFW_KEY_I -> { tool = PICKER; return true; }
            case GLFW.GLFW_KEY_M -> { mirror = !mirror; return true; }
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3 -> { size = k - GLFW.GLFW_KEY_0; return true; }
            default -> { }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        if (texId != null && client != null) client.getTextureManager().destroyTexture(texId);
        texId = null;
        texture = null;
        super.removed();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
