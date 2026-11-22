package dev.tempest.modules;

import dev.tempest.TempestConfig;
import dev.tempest.gui.FavoritesScreen;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import dev.tempest.util.Chat;
import dev.tempest.util.FavoriteServer;
import dev.tempest.util.ServerPinger;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Favorite servers with ping and one-click join on the main menu. */
public class QuickServersModule extends Module {
    private final NumberSetting rows = add(new NumberSetting("rows", "Servers Shown", 5, 1, 8, 1));

    @Override
    public boolean alwaysOn() { return true; }

    public QuickServersModule() {
        super("quick_servers", "Favorite Servers", "Favorite servers with ping and one-click join on the main menu.", Category.UTILITY, true);
        add(new ActionSetting("manage", "Manage Favorites", "Open", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            c.setScreen(new FavoritesScreen(c.currentScreen));
        }));
        add(new ActionSetting("add_current", "Favorite Current Server", "Add", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            ServerInfo i = c.getCurrentServerEntry();
            if (i == null) { Chat.info("You are not on a multiplayer server."); return; }
            toggle(i.name, i.address);
            Chat.info("Updated favorites for " + i.name + ".");
        }));
    }

    public static boolean isFavorite(String address) {
        for (FavoriteServer f : TempestConfig.get().favoriteServers) if (f.address.equalsIgnoreCase(address)) return true;
        return false;
    }

    public static void toggle(String name, String address) {
        List<FavoriteServer> l = TempestConfig.get().favoriteServers;
        if (!l.removeIf(f -> f.address.equalsIgnoreCase(address))) l.add(new FavoriteServer(name, address));
        TempestConfig.save();
    }

    public static void join(Screen from, FavoriteServer f) {
        MinecraftClient c = MinecraftClient.getInstance();
        ServerInfo info = new ServerInfo(f.name, f.address, ServerInfo.ServerType.OTHER);
        dev.tempest.util.ServerJoin.join(c, info, from);
    }

    public static void onScreenInit(MinecraftClient client, Screen screen, int w, int h) {
        QuickServersModule m = ModuleManager.get(QuickServersModule.class);
        if (m == null || !m.isEnabled()) return;

        if (screen instanceof MultiplayerScreen) {
            Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("\u2605 Favorites"),
                    b -> client.setScreen(new FavoritesScreen(screen))).dimensions(6, 6, 80, 20).build());
            return;
        }
        if (!(screen instanceof TitleScreen)) return;

        List<FavoriteServer> favs = TempestConfig.get().favoriteServers;
        int shown = Math.min(favs.size(), m.rows.getInt());
        int bw = 158, x = 6, y = 6;
        if (w / 2 - 100 < x + bw + 28) return; // window too narrow, would overlap the main buttons

        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("\u2605 Favorite Servers  (Manage)"),
                b -> client.setScreen(new FavoritesScreen(screen))).dimensions(x, y, bw + 22, 18).build());

        List<ButtonWidget> buttons = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            FavoriteServer f = favs.get(i);
            ServerPinger.ping(f.address);
            ButtonWidget b = ButtonWidget.builder(Text.literal(f.name), btn -> join(screen, f))
                    .dimensions(x, y + 22 + i * 22, bw + 22, 20).build();
            Screens.getButtons(screen).add(b);
            buttons.add(b);
        }

        ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
            for (int i = 0; i < buttons.size(); i++) {
                FavoriteServer f = favs.get(i);
                ServerPinger.ping(f.address); // refreshes at most every 20s
                ServerPinger.Result r = ServerPinger.get(f.address);
                String status;
                int col = 0xFFAAAAAA;
                if (r == null || (!r.done && r.pingMs < 0)) status = "...";
                else if (r.failed || r.pingMs < 0) { status = "offline"; col = 0xFFFF5555; }
                else {
                    status = r.pingMs + "ms";
                    col = r.pingMs < 80 ? 0xFF55FF55 : r.pingMs < 200 ? 0xFFFFD83A : 0xFFFF5555;
                }
                var tr = client.textRenderer;
                var btn = buttons.get(i);
                String players = r != null && r.online >= 0 ? "  " + r.online + "/" + r.max : "";
                ctx.drawText(tr, tr.trimToWidth(f.name, 70), btn.getX() + 6, btn.getY() + 6, 0xFFFFFFFF, true);
                String right = players + "  " + status;
                ctx.drawText(tr, right, btn.getX() + btn.getWidth() - 6 - tr.getWidth(right), btn.getY() + 6, col, true);
            }
        });
    }
}
