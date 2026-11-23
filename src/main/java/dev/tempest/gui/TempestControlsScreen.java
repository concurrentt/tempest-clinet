package dev.tempest.gui;

import dev.tempest.util.SearchQuery;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tempest key binds (replaces Options -> Controls -> Key Binds; "Classic" opens the vanilla one).
 * Like the Controlling mod: a search bar, a Conflicts view, and a Free Keys view. Searches understand filters with
 * auto-complete (Tab): {@code key:r}, {@code category:movement}, {@code is:conflict}, {@code is:unbound},
 * {@code is:changed}, {@code -word} to exclude.
 */
public class TempestControlsScreen extends Screen {
    private static final int HEAD = 40, ROW = 20, PAD = 14;
    private final Screen parent;
    private TextFieldWidget search;
    private KeyBinding binding;          // waiting for a key
    private int view;                    // 0 all, 1 conflicts, 2 free keys
    private double scroll, scrollTarget;
    private List<Object> rows = new ArrayList<>();   // String = category header, KeyBinding = row
    private String lastQuery = null;
    private int lastView = -1;
    private List<String> suggestions = List.of();

    public TempestControlsScreen(Screen parent) {
        super(Text.literal("Key Binds"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- layout
    private int pW() { return Math.min(width - 24, 560); }
    private int pH() { return height - 24; }
    private int pX() { return (width - pW()) / 2; }
    private int pY() { return 12; }
    private int listX() { return pX() + PAD; }
    private int listW() { return pW() - 2 * PAD; }
    private int listY() { return pY() + HEAD + 40; }
    private int listH() { return pH() - HEAD - 40 - 34; }
    private int btnY() { return pY() + HEAD + 10; }

    @Override
    protected void init() {
        String old = search != null ? search.getText() : "";
        search = new TextFieldWidget(textRenderer, listX() + 22, btnY() + 6, 210, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setMaxLength(64);
        search.setPlaceholder(Text.literal("Search keys...  try key:r  cat:movement  is:conflict"));
        search.setText(old);
        search.setChangedListener(s -> scrollTarget = 0);
        addDrawableChild(search);
        setInitialFocus(search);
    }

    // ---------------------------------------------------------------- data
    private KeyBinding[] all() { return client.options.allKeys; }

    private static String name(KeyBinding k) {
        String id = k.getId();
        if (Language.getInstance().hasTranslation(id)) return Text.translatable(id).getString();
        String tail = id.substring(id.lastIndexOf('.') + 1).replace('_', ' ');
        return tail.isEmpty() ? id : Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
    }

    /** "Movement", "Gameplay", "Tempest" ... from the category record's identifier (found by reflection, so it works whatever it is called). */
    private static final Map<Object, String> CATEGORY_NAMES = new LinkedHashMap<>();

    private static String category(KeyBinding k) {
        Object cat = k.getCategory();
        String cached = CATEGORY_NAMES.get(cat);
        if (cached != null) return cached;
        String label = String.valueOf(cat);
        try {
            if (cat != null && cat.getClass().isRecord()) {
                for (RecordComponent rc : cat.getClass().getRecordComponents()) {
                    java.lang.reflect.Method acc = rc.getAccessor();
                    acc.setAccessible(true);
                    Object v = acc.invoke(cat);
                    if (v instanceof Identifier id) {
                        String key = "key.category." + id.getNamespace() + "." + id.getPath();
                        label = Language.getInstance().hasTranslation(key) ? Text.translatable(key).getString()
                                : Character.toUpperCase(id.getPath().charAt(0)) + id.getPath().substring(1).replace('_', ' ');
                        break;
                    }
                }
            }
        } catch (Throwable ignored) { }
        CATEGORY_NAMES.put(cat, label);
        return label;
    }

    private boolean conflicts(KeyBinding k) {
        if (k.isUnbound()) return false;
        for (KeyBinding o : all()) if (o != k && !o.isUnbound() && k.equals(o)) return true;
        return false;
    }

    private Map<String, Supplier<List<String>>> filters() {
        Map<String, Supplier<List<String>>> f = SearchQuery.filters();
        f.put("category", () -> {
            LinkedHashSet<String> s = new LinkedHashSet<>();
            for (KeyBinding k : all()) s.add(category(k));
            return new ArrayList<>(s);
        });
        f.put("key", () -> {
            LinkedHashSet<String> s = new LinkedHashSet<>();
            for (KeyBinding k : all()) if (!k.isUnbound()) s.add(k.getBoundKeyLocalizedText().getString());
            return new ArrayList<>(s);
        });
        f.put("is", () -> List.of("conflict", "unbound", "changed", "default"));
        return f;
    }

    private void rebuild() {
        String q = search.getText();
        if (q.equals(lastQuery) && view == lastView) return;
        lastQuery = q;
        lastView = view;
        SearchQuery query = SearchQuery.parse(q, filters());
        List<Object> out = new ArrayList<>();
        String lastCat = null;
        List<KeyBinding> sorted = new ArrayList<>(List.of(all()));
        // keep the game's own order inside each category, categories in first-seen order
        LinkedHashMap<String, List<KeyBinding>> byCat = new LinkedHashMap<>();
        for (KeyBinding k : sorted) byCat.computeIfAbsent(category(k), c -> new ArrayList<>()).add(k);
        for (Map.Entry<String, List<KeyBinding>> e : byCat.entrySet()) {
            for (KeyBinding k : e.getValue()) {
                boolean conflict = conflicts(k);
                if (view == 1 && !conflict) continue;
                Map<String, String> vals = new LinkedHashMap<>();
                vals.put("category", e.getKey());
                vals.put("key", k.isUnbound() ? "" : k.getBoundKeyLocalizedText().getString());
                vals.put("is", (conflict ? "conflict\n" : "") + (k.isUnbound() ? "unbound\n" : "") + (k.isDefault() ? "default" : "changed"));
                if (!query.matches(name(k) + " " + e.getKey(), vals)) continue;
                if (!e.getKey().equals(lastCat)) { out.add(e.getKey()); lastCat = e.getKey(); }
                out.add(k);
            }
        }
        rows = out;
    }

    /** Keys nobody uses, for the Free Keys view. */
    private List<String> freeKeys() {
        List<String> out = new ArrayList<>();
        int[] codes = new int[26 + 10 + 12];
        int n = 0;
        for (int c = GLFW.GLFW_KEY_A; c <= GLFW.GLFW_KEY_Z; c++) codes[n++] = c;
        for (int c = GLFW.GLFW_KEY_0; c <= GLFW.GLFW_KEY_9; c++) codes[n++] = c;
        for (int c = GLFW.GLFW_KEY_F1; c <= GLFW.GLFW_KEY_F12; c++) codes[n++] = c;
        for (int code : codes) {
            InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(code);
            boolean used = false;
            for (KeyBinding k : all()) if (!k.isUnbound() && k.getBoundKeyTranslationKey().equals(key.getTranslationKey())) { used = true; break; }
            if (!used) out.add(key.getLocalizedText().getString());
        }
        return out;
    }

    // ---------------------------------------------------------------- render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        rebuild();
        Draw.window(ctx, tr, width, height, pX(), pY(), pW(), pH(), HEAD, "KEY BINDS", binding != null
                ? "Press a key for \"" + name(binding) + "\"  (Esc = unbind)" : "Search, find conflicts and free keys");

        // search box
        int sx = listX(), sy = btnY(), sw = 240;
        boolean focus = search.isFocused();
        Draw.panel(ctx, sx, sy, sw, 22, 8, Draw.side(), focus ? Draw.accent() : Draw.border());
        Icons.draw(ctx, "search", sx + 7, sy + 6, 10, focus ? Draw.accent() : Draw.DIM);
        search.setX(sx + 22);
        search.setY(sy + 7);
        search.setWidth(sw - 28);

        // view toggles + actions
        String[] labels = {"All", "Conflicts", "Free Keys"};
        int bx = sx + sw + 8;
        for (int i = 0; i < labels.length; i++) {
            int w = Draw.textW(tr, labels[i]) + 16;
            boolean sel = view == i, hv = Draw.hit(mx, my, bx, sy + 2, w, 18);
            int count = i == 1 ? countConflicts() : -1;
            String lab = labels[i] + (count > 0 ? " " + count : "");
            w = Draw.textW(tr, lab) + 16;
            Draw.panel(ctx, bx, sy + 2, w, 18, 9, sel ? Draw.withAlpha(Draw.accent(), 0xC0) : hv ? Draw.cardHover() : Draw.card(),
                    i == 1 && count > 0 && !sel ? 0xC0E04A5F : sel ? 0x60FFFFFF : Draw.border());
            Draw.text(ctx, tr, lab, bx + 8, sy + 7, sel ? 0xFFFFFFFF : Draw.TEXT, false);
            bx += w + 6;
        }
        int rx = pX() + pW() - PAD - 60;
        Draw.ghost(ctx, tr, rx, pY() + 11, 60, 18, "Done", Draw.hit(mx, my, rx, pY() + 11, 60, 18));
        Draw.ghost(ctx, tr, rx - 66, pY() + 11, 60, 18, "Classic", Draw.hit(mx, my, rx - 66, pY() + 11, 60, 18));
        Draw.ghost(ctx, tr, rx - 132, pY() + 11, 60, 18, "Reset All", Draw.hit(mx, my, rx - 132, pY() + 11, 60, 18));

        // list
        int lx = listX(), ly = listY(), lw = listW(), lh = listH();
        ctx.enableScissor(lx, ly, lx + lw, ly + lh);
        if (view == 2) renderFreeKeys(ctx, tr, lx, ly, lw);
        else {
            scroll += (scrollTarget - scroll) * 0.35;
            int y = ly - (int) Math.round(scroll);
            for (Object r : rows) {
                if (y + ROW >= ly && y <= ly + lh) {
                    if (r instanceof String cat) {
                        Draw.text(ctx, tr, Draw.bold(cat.toUpperCase(Locale.ROOT)), lx + 2, y + 8, Draw.accent(), false);
                        ctx.fill(lx + Draw.textW(tr, Draw.bold(cat.toUpperCase(Locale.ROOT))) + 8, y + 12, lx + lw, y + 13, Draw.border());
                    } else if (r instanceof KeyBinding k) {
                        renderRow(ctx, tr, k, lx, y, lw, mx, my);
                    }
                }
                y += ROW + 2;
            }
            if (rows.isEmpty()) Draw.centered(ctx, tr, view == 1 ? "No conflicts - every key does one thing" : "Nothing matches", lx + lw / 2, ly + 30, Draw.DIM);
        }
        ctx.disableScissor();

        Draw.text(ctx, tr, "Click a key to change it.  Right click resets it.  Tab completes a filter.", lx, pY() + pH() - 20, Draw.DIM2, false);

        super.render(ctx, mx, my, delta);
        renderSuggestions(ctx, tr, sx, sy + 24, mx, my);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    private int countConflicts() {
        int n = 0;
        for (KeyBinding k : all()) if (conflicts(k)) n++;
        return n;
    }

    private void renderRow(DrawContext ctx, TextRenderer tr, KeyBinding k, int x, int y, int w, int mx, int my) {
        boolean hv = Draw.hit(mx, my, x, y, w, ROW);
        if (hv) Draw.roundRect(ctx, x, y, w, ROW, 5, Draw.withAlpha(0xFFFFFF, 0x0C));
        Draw.text(ctx, tr, name(k), x + 8, y + 6, Draw.TEXT, false);
        boolean conflict = conflicts(k);
        int kw = 110, kx = x + w - kw - 26;
        boolean editing = k == binding;
        boolean khv = Draw.hit(mx, my, kx, y + 2, kw, ROW - 4);
        String label = editing ? "> press a key <" : k.isUnbound() ? "Not bound" : k.getBoundKeyLocalizedText().getString();
        int fill = editing ? Draw.withAlpha(Draw.accent(), 0x80) : khv ? Draw.cardHover() : Draw.card();
        int border = editing ? Draw.accent() : conflict ? 0xFFE04A5F : Draw.border();
        Draw.panel(ctx, kx, y + 2, kw, ROW - 4, 5, fill, border);
        int col = editing ? 0xFFFFFFFF : conflict ? 0xFFFF6B7D : k.isUnbound() ? Draw.DIM2 : 0xFFFFFFFF;
        String shown = Draw.ellipsize(tr, label, kw - 8);
        Draw.text(ctx, tr, shown, kx + (kw - Draw.textW(tr, shown)) / 2, y + 6, col, false);
        if (conflict && !editing) Draw.text(ctx, tr, "!", kx - 9, y + 6, 0xFFFF6B7D, false);
        // reset button
        if (!k.isDefault()) {
            int rx = x + w - 20;
            boolean rhv = Draw.hit(mx, my, rx, y + 2, 16, ROW - 4);
            Draw.panel(ctx, rx, y + 2, 16, ROW - 4, 5, rhv ? Draw.cardHover() : Draw.card(), Draw.border());
            Icons.draw(ctx, "refresh", rx + 3, y + 5, 10, rhv ? Draw.accent() : Draw.DIM);
        }
    }

    private void renderFreeKeys(DrawContext ctx, TextRenderer tr, int x, int y, int w) {
        List<String> free = freeKeys();
        Draw.text(ctx, tr, free.size() + " common keys are free to bind:", x + 2, y + 4, Draw.DIM, false);
        int cx = x, cy = y + 20;
        for (String k : free) {
            int cw = Math.max(26, Draw.textW(tr, k) + 12);
            if (cx + cw > x + w) { cx = x; cy += 24; }
            Draw.panel(ctx, cx, cy, cw, 20, 5, Draw.card(), Draw.withAlpha(Draw.accent(), 0x70));
            Draw.text(ctx, tr, k, cx + (cw - Draw.textW(tr, k)) / 2, cy + 6, 0xFFFFFFFF, false);
            cx += cw + 4;
        }
    }

    private void renderSuggestions(DrawContext ctx, TextRenderer tr, int x, int y, int mx, int my) {
        suggestions = search.isFocused() ? SearchQuery.suggest(search.getText(), filters(), 6) : List.of();
        if (suggestions.isEmpty()) return;
        int w = 200, h = suggestions.size() * 13 + 6;
        Draw.panel(ctx, x, y, w, h, 6, Draw.solid(), Draw.withAlpha(Draw.accent(), 0x90));
        for (int i = 0; i < suggestions.size(); i++) {
            int ry = y + 3 + i * 13;
            boolean hv = Draw.hit(mx, my, x, ry, w, 13);
            if (hv || i == 0) Draw.roundRect(ctx, x + 2, ry, w - 4, 13, 3, Draw.withAlpha(Draw.accent(), hv ? 0x50 : 0x28));
            Draw.text(ctx, tr, suggestions.get(i), x + 8, ry + 3, 0xFFFFFFFF, false);
            if (i == 0) Draw.text(ctx, tr, "Tab", x + w - 26, ry + 3, Draw.DIM2, false);
        }
    }

    // ---------------------------------------------------------------- input
    private void bind(KeyBinding k, InputUtil.Key key) {
        k.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        client.options.write();
        lastQuery = null;   // conflicts may have changed
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (binding != null) {               // a mouse button as the key
            bind(binding, InputUtil.Type.MOUSE.createFromCode(click.button()));
            binding = null;
            return true;
        }
        // suggestions
        if (!suggestions.isEmpty()) {
            int x = listX(), y = btnY() + 24;
            for (int i = 0; i < suggestions.size(); i++) {
                if (Draw.hit(mx, my, x, y + 3 + i * 13, 200, 13)) { search.setText(SearchQuery.complete(search.getText(), suggestions.get(i))); return true; }
            }
        }
        int rx = pX() + pW() - PAD - 60;
        if (Draw.hit(mx, my, rx, pY() + 11, 60, 18)) { close(); return true; }
        if (Draw.hit(mx, my, rx - 66, pY() + 11, 60, 18)) { TempestScreens.openVanilla(client, new KeybindsScreen(this, client.options)); return true; }
        if (Draw.hit(mx, my, rx - 132, pY() + 11, 60, 18)) {
            for (KeyBinding k : all()) k.setBoundKey(k.getDefaultKey());
            KeyBinding.updateKeysByCode();
            client.options.write();
            lastQuery = null;
            return true;
        }
        // view buttons
        String[] labels = {"All", "Conflicts", "Free Keys"};
        int bx = listX() + 240 + 8, sy = btnY();
        for (int i = 0; i < labels.length; i++) {
            int count = i == 1 ? countConflicts() : -1;
            int w = Draw.textW(textRenderer, labels[i] + (count > 0 ? " " + count : "")) + 16;
            if (Draw.hit(mx, my, bx, sy + 2, w, 18)) { view = i; scrollTarget = 0; return true; }
            bx += w + 6;
        }
        // rows
        if (view != 2 && Draw.hit(mx, my, listX(), listY(), listW(), listH())) {
            int y = listY() - (int) Math.round(scroll);
            for (Object r : rows) {
                if (r instanceof KeyBinding k && my >= y && my < y + ROW) {
                    int lx = listX(), lw = listW();
                    int kw = 110, kx = lx + lw - kw - 26;
                    if (!k.isDefault() && Draw.hit(mx, my, lx + lw - 20, y + 2, 16, ROW - 4)) { bind(k, k.getDefaultKey()); return true; }
                    if (Draw.hit(mx, my, kx, y + 2, kw, ROW - 4)) {
                        if (click.button() == 1) bind(k, k.getDefaultKey());
                        else startBinding(k);
                        return true;
                    }
                }
                y += ROW + 2;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    /**
     * Waits for a key for this bind. The search box gives up keyboard focus first: a key press also produces a
     * typed character, which would otherwise land in the search bar instead of only setting the bind.
     */
    private void startBinding(KeyBinding k) {
        binding = k;
        search.setFocused(false);
        setFocused(null);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (binding != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) bind(binding, InputUtil.UNKNOWN_KEY);
            else if (input.key() == GLFW.GLFW_KEY_UNKNOWN) bind(binding, InputUtil.Type.SCANCODE.createFromCode(input.scancode()));
            else bind(binding, InputUtil.Type.KEYSYM.createFromCode(input.key()));
            binding = null;
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_TAB && search.isFocused() && !suggestions.isEmpty()) {
            search.setText(SearchQuery.complete(search.getText(), suggestions.get(0)));
            search.setCursorToEnd(false);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int content = rows.size() * (ROW + 2);
        double max = Math.max(0, content - listH());
        scrollTarget = Math.max(0, Math.min(max, scrollTarget - vertical * 30));
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        client.options.write();
        client.setScreen(parent);
    }
}
