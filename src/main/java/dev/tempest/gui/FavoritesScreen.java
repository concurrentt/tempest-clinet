package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.modules.QuickServersModule;
import dev.tempest.util.FavoriteServer;
import dev.tempest.util.ServerPinger;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Pick favorites from your saved servers (or add by address), see ping and join. */
public class FavoritesScreen extends Screen {
    private static final int ROW = 26, HEAD = 40, FOOT = 40;
    private final Screen parent;
    private int pX, pY, pW, pH;
    private double scroll = 0, scrollTarget = 0;
    private long lastScrollFrame = System.nanoTime();
    private TextFieldWidget field;
    private final List<FavoriteServer> all = new ArrayList<>();

    public FavoritesScreen(Screen parent) {
        super(Text.literal("Favorite Servers"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pW = Math.min(width - 20, 400);
        pH = Math.min(height - 20, 300);
        pX = (width - pW) / 2;
        pY = (height - pH) / 2;

        all.clear();
        all.addAll(TempestConfig.get().favoriteServers);
        try {
            ServerList list = new ServerList(client);
            list.loadFile();
            for (int i = 0; i < list.size(); i++) {
                ServerInfo s = list.get(i);
                if (all.stream().noneMatch(f -> f.address.equalsIgnoreCase(s.address))) all.add(new FavoriteServer(s.name, s.address));
            }
        } catch (Exception ignored) { }
        for (FavoriteServer f : all) ServerPinger.ping(f.address);

        field = new TextFieldWidget(textRenderer, pX + 14, pY + pH - FOOT + 10, pW - 120, 20, Text.literal("Server address"));
        field.setMaxLength(128);
        field.setPlaceholder(Text.literal("Add by address, e.g. play.example.net"));
        addDrawableChild(field);
    }

    private int listTop() { return pY + HEAD + 4; }
    private int listH() { return pH - HEAD - FOOT - 8; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        Draw.window(ctx, tr, width, height, pX, pY, pW, pH, HEAD, "\u2605 Favorite Servers", "Starred servers appear on the main menu");

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastScrollFrame) / 1_000_000_000f);
        lastScrollFrame = now;
        double maxScroll = Math.max(0, all.size() * ROW - listH());
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * (1.0 - Math.exp(-dt * 13.0));
        if (Math.abs(scrollTarget - scroll) < 0.08) scroll = scrollTarget;
        ctx.enableScissor(pX + 1, listTop(), pX + pW - 1, listTop() + listH());
        for (int i = 0; i < all.size(); i++) {
            FavoriteServer f = all.get(i);
            int ry = listTop() + i * ROW - (int) Math.round(scroll);
            if (ry + ROW < listTop() || ry > listTop() + listH()) continue;
            int rx = pX + 12, rw = pW - 24;
            float hover = Draw.hoverProgress("favorites-row-" + i, Draw.hit(mx, my, rx, ry, rw, ROW - 3));
            Draw.panel(ctx, rx, ry, rw, ROW - 3, 6, Draw.lerpColor(Draw.card(), Draw.cardHover(), hover), Draw.lerpColor(Draw.border(), Draw.accent(), hover * 0.45f));
            boolean fav = QuickServersModule.isFavorite(f.address);
            Draw.text(ctx, tr, "\u2605", rx + 8, ry + 7, fav ? 0xFFFFD83A : Draw.DIM2, false);
            Draw.text(ctx, tr, Draw.trim(tr, f.name, rw - 190), rx + 22, ry + 7, Draw.TEXT, false);

            ServerPinger.Result r = ServerPinger.get(f.address);
            String st = r == null || (!r.done && r.pingMs < 0) ? "..." : (r.failed || r.pingMs < 0) ? "offline" : r.pingMs + "ms";
            if (r != null && r.online >= 0 && !r.failed) st = r.online + "/" + r.max + "  " + st;
            int col = r == null || r.pingMs < 0 ? Draw.DIM : r.pingMs < 80 ? 0xFF55FF55 : r.pingMs < 200 ? 0xFFFFD83A : 0xFFFF5555;
            Draw.text(ctx, tr, st, rx + rw - 62 - Draw.textW(tr, st), ry + 7, col, false);

            Draw.button(ctx, tr, rx + rw - 54, ry + 2, 46, ROW - 7, "Join", Draw.hit(mx, my, rx + rw - 54, ry + 2, 46, ROW - 7), Draw.accent() & 0xFFFFFF);
        }
        ctx.disableScissor();
        Draw.scrollbar(ctx, pX + pW - 7, listTop(), listH(), Math.max(0, all.size() * ROW - listH()), scroll);
        if (all.isEmpty()) Draw.centered(ctx, tr, "No servers saved yet", pX + pW / 2, listTop() + 40, Draw.DIM);

        ctx.fill(pX + 1, pY + pH - FOOT, pX + pW - 1, pY + pH - FOOT + 1, Draw.border());
        Draw.button(ctx, tr, pX + pW - 98, pY + pH - FOOT + 10, 84, 20, "Add", Draw.hit(mx, my, pX + pW - 98, pY + pH - FOOT + 10, 84, 20), Draw.accent() & 0xFFFFFF);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();
        if (Draw.hit(mx, my, pX + pW - 98, pY + pH - FOOT + 10, 84, 20)) {
            String a = field.getText().trim();
            if (!a.isEmpty() && !QuickServersModule.isFavorite(a)) {
                QuickServersModule.toggle(a, a);
                FavoriteServer n = new FavoriteServer(a, a);
                all.add(0, n);
                ServerPinger.ping(a);
                field.setText("");
            }
            return true;
        }
        if (my >= listTop() && my < listTop() + listH()) {
            for (int i = 0; i < all.size(); i++) {
                FavoriteServer f = all.get(i);
                int ry = listTop() + i * ROW - (int) Math.round(scroll);
                int rx = pX + 12, rw = pW - 24;
                if (Draw.hit(mx, my, rx + rw - 54, ry + 2, 46, ROW - 7)) {
                    QuickServersModule.join(this, f);
                    return true;
                }
                if (Draw.hit(mx, my, rx + 4, ry, 18, ROW - 3)) {
                    QuickServersModule.toggle(f.name, f.address);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = Math.max(0, Math.min(Math.max(0, all.size() * ROW - listH()), scrollTarget - vertical * 32));
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        TempestConfig.save();
        if (client != null) client.setScreen(parent);
    }
}
