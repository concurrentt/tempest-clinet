package dev.tempest.gui;

import dev.tempest.modules.QuickServersModule;
import dev.tempest.util.ServerIcons;
import dev.tempest.util.ServerPinger;
import net.minecraft.util.Identifier;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Tempest multiplayer screen: saved servers with live ping and players. Hover a row for Join, favourite and
 * remove (remove asks for a second click). The bar at the bottom adds a server or connects straight to it.
 */
public class TempestMultiplayerScreen extends Screen {
    private static final int ROW = 38, PITCH = 42, BAR_H = 28, BTN = 24;

    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private final List<ServerInfo> servers = new ArrayList<>();
    private ServerList saved;
    private int selected = -1;
    private long lastClickMs;
    private double scroll, scrollTarget;
    private long lastFrame = System.nanoTime();
    private TextFieldWidget address;
    private String armedDelete;          // address whose remove button was clicked once
    private long armedAt;

    public TempestMultiplayerScreen(Screen parent) {
        super(Text.literal("Multiplayer"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- layout
    private int colW() { return Math.min(520, width - 40); }
    private int colX() { return (width - colW()) / 2; }
    private int headY() { return Math.max(14, Math.round(height * 0.07f)); }
    private int listTop() { return headY() + 38; }
    private int barY() { return height - 16 - BAR_H; }
    private int listH() { return barY() - 10 - listTop(); }
    private int addW() { return 64; }
    private int connectW() { return 88; }
    private int fieldW() { return colW() - addW() - connectW() - 16; }
    /** header buttons, right to left: favourites, refresh, classic list */
    private int hbX(int i) { return colX() + colW() - BTN - i * (BTN + 6); }

    @Override
    protected void init() {
        reload();
        String old = address != null ? address.getText() : "";
        address = new TextFieldWidget(textRenderer, colX() + 28, barY() + 10, fieldW() - 36, 12, Text.literal("Server address"));
        address.setDrawsBackground(false);
        address.setPlaceholder(Text.literal("Server address, e.g. play.example.net"));
        address.setMaxLength(128);
        address.setText(old);
        addDrawableChild(address);
    }

    private void reload() {
        servers.clear();
        try {
            saved = new ServerList(client);
            saved.loadFile();
            for (int i = 0; i < saved.size(); i++) servers.add(saved.get(i));
        } catch (Exception ignored) { }
        for (ServerInfo s : servers) ServerPinger.ping(s.address);
        if (selected >= servers.size()) selected = -1;
    }

    private double maxScroll() { return Math.max(0, servers.size() * PITCH - 4 - listH()); }

    private static int pingColor(ServerPinger.Result r) {
        if (r == null || (!r.done && r.pingMs < 0)) return 0xFF8B90A0;
        if (r.failed || r.pingMs < 0) return 0xFFFF5C6C;
        return r.pingMs < 80 ? 0xFF5BE38A : r.pingMs < 200 ? 0xFFFFD34D : 0xFFFF7A5C;
    }

    private static int nameColor(String name) {
        int h = name == null ? 0 : name.hashCode();
        return Draw.hsvToRgb(((h >>> 3) % 360) / 360f, 0.45f, 0.95f) & 0xFFFFFF;
    }

    // ---------------------------------------------------------------- render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        float e = MenuKit.ease(((now - openedAt) / 1_000_000_000f) / 0.4f);
        if (armedDelete != null && System.currentTimeMillis() - armedAt > 2500) armedDelete = null;
        Draw.menuBackground(ctx, width, height);

        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scroll += (scrollTarget - scroll) * (1.0 - Math.exp(-dt * 13.0));
        if (Math.abs(scrollTarget - scroll) < 0.08) scroll = scrollTarget;

        int x = colX(), w = colW(), hy = headY();
        MenuKit.titleBar(ctx, tr, x, hy, "Multiplayer", servers.size() + (servers.size() == 1 ? " server" : " servers"),
                Draw.hit(mx, my, x, hy, 24, 24), e);
        String tip = null;
        String[][] hb = {{"star_filled", "Favorites"}, {"refresh", "Refresh"}, {"list", "Classic server list"}};
        for (int i = 0; i < hb.length; i++) {
            boolean hv = Draw.hit(mx, my, hbX(i), hy, BTN, BTN);
            MenuKit.iconButton(ctx, "mp_h" + i, hbX(i), hy, BTN, hb[i][0], hv, i == 0 ? 0xFFFFD34D : 0xFFE4E6EC, e);
            if (hv) tip = hb[i][1];
        }

        // rows
        int lt = listTop(), lh = listH();
        boolean inList = my >= lt && my < lt + lh;
        ctx.enableScissor(x - 2, lt, x + w + 2, lt + lh);
        if (servers.isEmpty()) {
            int cx = width / 2, cy = lt + Math.min(70, lh / 3);
            Icons.draw(ctx, "servers", cx - 12, cy - 30, 24, MenuKit.fa(0x70FFFFFF, e));
            Draw.centered(ctx, tr, "No saved servers", cx, cy + 2, MenuKit.fa(0xFFD0D3DC, e));
            Draw.centered(ctx, tr, "Type an address below and press Add", cx, cy + 16, MenuKit.fa(0xFF8B90A0, e));
        }
        for (int i = 0; i < servers.size(); i++) {
            int ry = lt + i * PITCH - (int) Math.round(scroll) + Math.round((1f - e) * (8 + i * 3));
            if (ry + ROW < lt || ry > lt + lh) continue;
            ServerInfo s = servers.get(i);
            ServerPinger.ping(s.address); // refreshes at most every 20s
            ServerPinger.Result r = ServerPinger.get(s.address);
            boolean hv = inList && Draw.hit(mx, my, x, ry, w, ROW);
            boolean sel = i == selected;
            String key = "mp" + s.address;
            MenuKit.row(ctx, key, x, ry, w, ROW, hv, sel, e);
            byte[] png = r != null && r.favicon != null ? r.favicon : s.getFavicon() != null ? s.getFavicon() : r != null ? r.webIcon : null;
            if (r != null && r.favicon != null && !java.util.Arrays.equals(r.favicon, s.getFavicon())) s.setFavicon(r.favicon);   // remembered in servers.dat
            Identifier icon = ServerIcons.get(s.address, png);
            if (icon != null) MenuKit.serverIcon(ctx, icon, x + 7, ry + 6, 26, e);
            else MenuKit.iconTile(ctx, x + 7, ry + 6, 26, "globe", nameColor(s.name), e);

            boolean fav = QuickServersModule.isFavorite(s.address);
            int right = x + w - 10;
            int infoW;
            if (hv || sel) {
                // [Join] [star] [trash]
                int tx = right - 18, sx = tx - 22, jx = sx - 6 - 56;
                int by = ry + (ROW - 22) / 2;
                MenuKit.flatButton(ctx, tr, key + "join", jx, by, 56, 22, "play", "Join", MenuKit.GREEN, hv && Draw.hit(mx, my, jx, by, 56, 22), true, e);
                boolean overStar = hv && Draw.hit(mx, my, sx, ry + 10, 18, 18);
                Icons.draw(ctx, fav ? "star_filled" : "star_outline", sx + 2, ry + 12, 14,
                        MenuKit.fa(fav ? 0xFFFFD34D : (overStar ? 0xFFFFFFFF : 0xFF8B90A0), e));
                boolean overTrash = hv && Draw.hit(mx, my, tx, ry + 10, 18, 18);
                boolean armed = s.address.equals(armedDelete);
                Icons.draw(ctx, "trash", tx + 2, ry + 12, 14, MenuKit.fa(armed ? 0xFFFF5C6C : (overTrash ? 0xFFFFFFFF : 0xFF8B90A0), e));
                if (overStar) tip = fav ? "Remove from favorites" : "Add to favorites";
                if (overTrash) tip = armed ? "Click again to remove" : "Remove server";
                infoW = right - jx + 8;
            } else {
                int col = pingColor(r);
                String ping = r == null || (!r.done && r.pingMs < 0) ? "..." : (r.failed || r.pingMs < 0) ? "offline" : r.pingMs + " ms";
                int pw = Draw.textW(tr, ping);
                Draw.text(ctx, tr, ping, right - pw, ry + 9, MenuKit.fa(col, e), false);
                Icons.draw(ctx, "signal", right - pw - 14, ry + 8, 10, MenuKit.fa(col, e));
                String pl = r != null && r.online >= 0 && !r.failed ? r.online + " / " + r.max : "";
                if (!pl.isEmpty()) Draw.text(ctx, tr, pl, right - Draw.textW(tr, pl), ry + 22, MenuKit.fa(0xFF8B90A0, e), false);
                infoW = Math.max(pw + 16, Draw.textW(tr, pl)) + 12;
            }
            int textMax = w - 50 - infoW;
            String nm = Draw.ellipsize(tr, s.name, textMax - (fav ? 14 : 0));
            Draw.text(ctx, tr, nm, x + 42, ry + 9, MenuKit.fa(0xFFFFFFFF, e), false);
            if (fav && !(hv || sel)) Icons.draw(ctx, "star_filled", x + 46 + Draw.textW(tr, nm), ry + 9, 8, MenuKit.fa(0xFFFFD34D, e));
            Draw.text(ctx, tr, Draw.ellipsize(tr, s.address, textMax), x + 42, ry + 22, MenuKit.fa(0xFF8B90A0, e), false);
        }
        MenuKit.listFades(ctx, x - 2, lt, w + 4, lh, scroll, maxScroll());
        ctx.disableScissor();
        Draw.scrollbar(ctx, x + w + 5, lt + 2, lh - 4, maxScroll(), scroll);

        // bottom bar: [ address ............ ] [+ Add] [> Connect]
        int by = barY();
        boolean focus = address.isFocused();
        boolean hasText = !address.getText().trim().isEmpty();
        Draw.panel(ctx, x, by, fieldW(), BAR_H, 6, MenuKit.fa(0xD81A1B20, e), MenuKit.fa(focus ? 0x90FFFFFF : 0x26FFFFFF, e));
        Icons.draw(ctx, "globe", x + 10, by + 9, 10, MenuKit.fa(focus ? 0xFFFFFFFF : 0xFF8B90A0, e));
        address.setX(x + 28);
        address.setY(by + 10);
        int ax = x + fieldW() + 8, cx = ax + addW() + 8;
        MenuKit.flatButton(ctx, tr, "mp_add", ax, by, addW(), BAR_H, "plus", "Add", MenuKit.DARK, Draw.hit(mx, my, ax, by, addW(), BAR_H), hasText, e);
        MenuKit.flatButton(ctx, tr, "mp_connect", cx, by, connectW(), BAR_H, "play", "Connect", MenuKit.GREEN, Draw.hit(mx, my, cx, by, connectW(), BAR_H), hasText, e);

        address.render(ctx, mx, my, delta);

        if (tip != null) {
            int tw = Draw.textW(tr, tip) + 12;
            int tx = Math.min(mx + 8, width - tw - 4), ty = my - 20;
            Draw.panel(ctx, tx, ty, tw, 16, 4, 0xF0101116, 0x40FFFFFF);
            Draw.text(ctx, tr, tip, tx + 6, ty + 4, 0xFFFFFFFF, false);
        }
    }

    // ---------------------------------------------------------------- actions
    private void join(ServerInfo info) {
        if (client == null) return;
        dev.tempest.util.ServerJoin.join(client, info, this);
    }

    private void addTyped() {
        String a = address.getText().trim();
        if (a.isEmpty()) return;
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).address.equalsIgnoreCase(a)) { address.setText(""); selected = i; return; }
        }
        try {
            saved.add(new ServerInfo(a, a, ServerInfo.ServerType.OTHER), false);
            saved.saveFile();
        } catch (Exception ignored) { }
        address.setText("");
        reload();
        selected = servers.size() - 1;
        scrollTarget = maxScroll();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int x = colX(), w = colW(), hy = headY();
        if (click.button() == 0) {
            if (Draw.hit(mx, my, x, hy, 24, 24)) { close(); return true; }
            if (Draw.hit(mx, my, hbX(0), hy, BTN, BTN)) { client.setScreen(new FavoritesScreen(this)); return true; }
            if (Draw.hit(mx, my, hbX(1), hy, BTN, BTN)) { for (ServerInfo s : servers) ServerPinger.refresh(s.address); return true; }
            if (Draw.hit(mx, my, hbX(2), hy, BTN, BTN)) { TempestScreens.openVanilla(client, new MultiplayerScreen(this)); return true; }
        }
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;

        int by = barY(), ax = x + fieldW() + 8, cx = ax + addW() + 8;
        if (Draw.hit(mx, my, ax, by, addW(), BAR_H)) { addTyped(); return true; }
        if (Draw.hit(mx, my, cx, by, connectW(), BAR_H)) {
            String a = address.getText().trim();
            if (!a.isEmpty()) join(new ServerInfo(a, a, ServerInfo.ServerType.OTHER));
            return true;
        }
        if (Draw.hit(mx, my, x, by, fieldW(), BAR_H)) { setFocused(address); address.setFocused(true); return true; }

        int lt = listTop();
        if (my >= lt && my < lt + listH()) {
            for (int i = 0; i < servers.size(); i++) {
                int ry = lt + i * PITCH - (int) Math.round(scroll);
                if (!Draw.hit(mx, my, x, ry, w, ROW)) continue;
                ServerInfo s = servers.get(i);
                int right = x + w - 10, tx = right - 18, sx = tx - 22, jx = sx - 6 - 56;
                if (Draw.hit(mx, my, tx, ry + 10, 18, 18)) {
                    if (s.address.equals(armedDelete)) {
                        try { saved.remove(s); saved.saveFile(); } catch (Exception ignored) { }
                        armedDelete = null;
                        reload();
                    } else {
                        armedDelete = s.address;
                        armedAt = System.currentTimeMillis();
                    }
                    return true;
                }
                if (Draw.hit(mx, my, sx, ry + 10, 18, 18)) { QuickServersModule.toggle(s.name, s.address); return true; }
                if (Draw.hit(mx, my, jx, ry + (ROW - 22) / 2, 56, 22)) { join(s); return true; }
                long now = System.currentTimeMillis();
                if (selected == i && now - lastClickMs < 400) join(s);
                selected = i;
                lastClickMs = now;
                return true;
            }
        }
        address.setFocused(false);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - vertical * 34));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if ((k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER)) {
            if (address.isFocused() && !address.getText().trim().isEmpty()) { addTyped(); return true; }
            if (selected >= 0 && selected < servers.size()) { join(servers.get(selected)); return true; }
        }
        if (super.keyPressed(input)) return true;
        if (k == GLFW.GLFW_KEY_DOWN && !servers.isEmpty()) { selected = Math.min(servers.size() - 1, selected + 1); return true; }
        if (k == GLFW.GLFW_KEY_UP && !servers.isEmpty()) { selected = Math.max(0, selected - 1); return true; }
        return false;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
