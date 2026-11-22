package dev.tempest.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Tempest singleplayer screen: a clean column with a back button, search, and one row per world
 * (mode icon, name, mode and last played). Hover a row for Play; double-click or Enter also plays.
 * Creating, editing and deleting worlds open the vanilla world screen.
 */
public class TempestSinglePlayerScreen extends Screen {
    private static final int ROW = 38, PITCH = 42, BAR_H = 28;

    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private final List<LevelSummary> worlds = new ArrayList<>();
    private boolean loading = true;
    private String error;
    private int selected = -1;
    private long lastClickMs;
    private double scroll, scrollTarget;
    private long lastFrame = System.nanoTime();
    private TextFieldWidget search;

    public TempestSinglePlayerScreen(Screen parent) {
        super(Text.literal("Singleplayer"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- layout
    private int colW() { return Math.min(480, width - 40); }
    private int colX() { return (width - colW()) / 2; }
    private int headY() { return Math.max(14, Math.round(height * 0.07f)); }
    private int listTop() { return headY() + 38; }
    private int barY() { return height - 16 - BAR_H; }
    private int listH() { return barY() - 10 - listTop(); }
    private int searchW() { return Math.min(170, colW() / 2 - 20); }
    private int searchX() { return colX() + colW() - searchW(); }
    private int playW() { return Math.min(170, colW() / 2 - 4); }
    private int editX() { return colX() + playW() + 8; }
    private int editW() { return Math.min(150, colW() - playW() - 8); }

    @Override
    protected void init() {
        String old = search != null ? search.getText() : "";
        search = new TextFieldWidget(textRenderer, searchX() + 24, headY() + 8, searchW() - 32, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setPlaceholder(Text.literal("Search worlds"));
        search.setMaxLength(32);
        search.setText(old);
        search.setChangedListener(s -> { scrollTarget = 0; selected = -1; });
        addDrawableChild(search);
        if (worlds.isEmpty()) loadWorlds();
    }

    /** Reads the saves folder off-thread, the same way the vanilla list does. */
    private void loadWorlds() {
        loading = true;
        error = null;
        try {
            var storage = client.getLevelStorage();
            storage.loadSummaries(storage.getLevelList()).whenComplete((list, err) -> client.execute(() -> {
                worlds.clear();
                if (err != null || list == null) error = "Could not read your worlds folder.";
                else {
                    worlds.addAll(list);
                    worlds.sort(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed());
                }
                loading = false;
            }));
        } catch (Exception e) {
            error = "Could not read your worlds folder.";
            loading = false;
        }
    }

    private List<LevelSummary> visible() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return worlds;
        List<LevelSummary> out = new ArrayList<>();
        for (LevelSummary w : worlds) {
            if (w.getDisplayName().toLowerCase(Locale.ROOT).contains(q) || w.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(w);
        }
        return out;
    }

    private double maxScroll() { return Math.max(0, visible().size() * PITCH - 4 - listH()); }

    /** {label, icon, rgb} for a world's game mode. */
    private static Object[] mode(LevelSummary w) {
        if (w.isHardcore()) return new Object[]{"Hardcore", "flame", 0xFF5470};
        String n = w.getGameMode().name().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "creative" -> new Object[]{"Creative", "sparkles", 0x6CB4FF};
            case "adventure" -> new Object[]{"Adventure", "compass", 0xFFB347};
            case "spectator" -> new Object[]{"Spectator", "eye", 0xB085FF};
            default -> new Object[]{"Survival", "heart", 0x5BE38A};
        };
    }

    // ---------------------------------------------------------------- render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        float e = MenuKit.ease(((now - openedAt) / 1_000_000_000f) / 0.4f);
        Draw.menuBackground(ctx, width, height);

        List<LevelSummary> list = visible();
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scroll += (scrollTarget - scroll) * (1.0 - Math.exp(-dt * 13.0));
        if (Math.abs(scrollTarget - scroll) < 0.08) scroll = scrollTarget;

        int x = colX(), w = colW(), hy = headY();
        String sub = loading ? "Loading..." : worlds.size() + (worlds.size() == 1 ? " world" : " worlds");
        MenuKit.titleBar(ctx, tr, x, hy, "Singleplayer", sub, Draw.hit(mx, my, x, hy, 24, 24), e);

        // search pill
        int sx = searchX(), sw = searchW();
        boolean focus = search.isFocused();
        Draw.panel(ctx, sx, hy, sw, 24, 12, MenuKit.fa(0xC81A1B20, e), MenuKit.fa(focus ? 0x90FFFFFF : 0x26FFFFFF, e));
        Icons.draw(ctx, "search", sx + 9, hy + 7, 10, MenuKit.fa(focus ? 0xFFFFFFFF : 0xFF8B90A0, e));
        search.setX(sx + 24);
        search.setY(hy + 8);

        // rows
        int lt = listTop(), lh = listH();
        ctx.enableScissor(x - 2, lt, x + w + 2, lt + lh);
        if (loading) emptyState(ctx, tr, "clock", "Reading your worlds...", null, e);
        else if (error != null) emptyState(ctx, tr, "info", error, null, e);
        else if (list.isEmpty()) {
            if (worlds.isEmpty()) emptyState(ctx, tr, "cube", "No worlds yet", "Use Create / Edit below to make one", e);
            else emptyState(ctx, tr, "search", "No worlds match", "Try a different search", e);
        }
        boolean inList = my >= lt && my < lt + lh;
        for (int i = 0; i < list.size(); i++) {
            int ry = lt + i * PITCH - (int) Math.round(scroll) + Math.round((1f - e) * (8 + i * 3));
            if (ry + ROW < lt || ry > lt + lh) continue;
            LevelSummary wd = list.get(i);
            boolean hv = inList && Draw.hit(mx, my, x, ry, w, ROW);
            boolean sel = i == selected;
            String key = "sp" + wd.getName();
            MenuKit.row(ctx, key, x, ry, w, ROW, hv, sel, e);
            Object[] md = mode(wd);
            MenuKit.iconTile(ctx, x + 7, ry + 6, 26, (String) md[1], (int) md[2], e);

            int right = x + w - 10;
            boolean showPlay = hv || sel;
            int textMax = w - 50 - (showPlay ? 74 : 10);
            Draw.text(ctx, tr, Draw.ellipsize(tr, wd.getDisplayName(), textMax), x + 42, ry + 9, MenuKit.fa(0xFFFFFFFF, e), false);
            String meta = md[0] + "  ·  " + MenuKit.ago(wd.getLastPlayed());
            Draw.text(ctx, tr, Draw.ellipsize(tr, meta, textMax), x + 42, ry + 22, MenuKit.fa(wd.isHardcore() ? 0xFFFF8A9C : 0xFF8B90A0, e), false);
            if (showPlay) {
                int pw = 62, px = right - pw, py = ry + (ROW - 22) / 2;
                MenuKit.flatButton(ctx, tr, key + "play", px, py, pw, 22, "play", "Play", MenuKit.GREEN, hv && Draw.hit(mx, my, px, py, pw, 22), true, e);
            }
        }
        MenuKit.listFades(ctx, x - 2, lt, w + 4, lh, scroll, maxScroll());
        ctx.disableScissor();
        Draw.scrollbar(ctx, x + w + 5, lt + 2, lh - 4, maxScroll(), scroll);

        // bottom bar
        int by = barY();
        boolean can = selected >= 0 && selected < list.size();
        MenuKit.flatButton(ctx, tr, "sp_play", x, by, playW(), BAR_H, "play", "Play Selected", MenuKit.GREEN,
                Draw.hit(mx, my, x, by, playW(), BAR_H), can, e);
        MenuKit.flatButton(ctx, tr, "sp_edit", editX(), by, editW(), BAR_H, "pencil", "Create / Edit", MenuKit.DARK,
                Draw.hit(mx, my, editX(), by, editW(), BAR_H), true, e);

        search.render(ctx, mx, my, delta);
    }

    private void emptyState(DrawContext ctx, TextRenderer tr, String icon, String line1, String line2, float e) {
        int cx = width / 2, cy = listTop() + Math.min(70, listH() / 3);
        Icons.draw(ctx, icon, cx - 12, cy - 30, 24, MenuKit.fa(0x70FFFFFF, e));
        Draw.centered(ctx, tr, line1, cx, cy + 2, MenuKit.fa(0xFFD0D3DC, e));
        if (line2 != null) Draw.centered(ctx, tr, line2, cx, cy + 16, MenuKit.fa(0xFF8B90A0, e));
    }

    // ---------------------------------------------------------------- actions
    private void play(LevelSummary w) {
        if (client == null) return;
        client.createIntegratedServerLoader().start(w.getName(), () -> client.setScreen(this));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() == 0 && Draw.hit(mx, my, colX(), headY(), 24, 24)) { close(); return true; }
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        List<LevelSummary> list = visible();
        int x = colX(), w = colW(), by = barY();
        if (Draw.hit(mx, my, x, by, playW(), BAR_H)) {
            if (selected >= 0 && selected < list.size()) play(list.get(selected));
            return true;
        }
        if (Draw.hit(mx, my, editX(), by, editW(), BAR_H)) {
            TempestScreens.openVanilla(client, new SelectWorldScreen(this));
            return true;
        }
        int lt = listTop();
        if (my >= lt && my < lt + listH()) {
            for (int i = 0; i < list.size(); i++) {
                int ry = lt + i * PITCH - (int) Math.round(scroll);
                if (!Draw.hit(mx, my, x, ry, w, ROW)) continue;
                int pw = 62, px = x + w - 10 - pw;
                if (Draw.hit(mx, my, px, ry + (ROW - 22) / 2, pw, 22)) { play(list.get(i)); return true; }
                long now = System.currentTimeMillis();
                if (selected == i && now - lastClickMs < 400) play(list.get(i));
                selected = i;
                lastClickMs = now;
                return true;
            }
        }
        boolean onSearch = Draw.hit(mx, my, searchX(), headY(), searchW(), 24);
        search.setFocused(onSearch);
        if (onSearch) setFocused(search);
        return onSearch;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - vertical * 34));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (super.keyPressed(input)) return true;
        List<LevelSummary> list = visible();
        int k = input.key();
        if ((k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) && selected >= 0 && selected < list.size()) { play(list.get(selected)); return true; }
        if (k == GLFW.GLFW_KEY_DOWN && !list.isEmpty()) { selected = Math.min(list.size() - 1, selected + 1); reveal(); return true; }
        if (k == GLFW.GLFW_KEY_UP && !list.isEmpty()) { selected = Math.max(0, selected - 1); reveal(); return true; }
        return false;
    }

    private void reveal() {
        double top = selected * PITCH, bottom = top + ROW;
        if (top < scrollTarget) scrollTarget = top;
        else if (bottom > scrollTarget + listH()) scrollTarget = bottom - listH();
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
