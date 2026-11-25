package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

public class ReconnectModule extends Module {
    private static ServerInfo lastServer;

    private final dev.tempest.module.BoolSetting auto = add(new dev.tempest.module.BoolSetting("auto", "Auto Reconnect When Full", true));
    private final dev.tempest.module.BoolSetting anyReason = add(new dev.tempest.module.BoolSetting("any", "Auto Reconnect On Any Kick", false));
    private final dev.tempest.module.NumberSetting delay = add(new dev.tempest.module.NumberSetting("delay", "Retry Every (s)", 5, 2, 60, 1));

    /** Auto reconnect: when (ms) to try again, 0 = not waiting; tries so far for this server. */
    private static long retryAt;
    private static int tries;
    private static Screen waitingOn;
    private static ButtonWidget cancelBtn;

    public ReconnectModule() { super("reconnect", "Reconnect Button", "Reconnect button on the disconnect screen, and auto retry when a server is full.", Category.UTILITY, true); }

    private static final java.util.regex.Pattern FULL = java.util.regex.Pattern.compile(
            "full|queue|try again|restarting|starting up|whitelist is being|too many|server is busy|maintenance", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static void connect(MinecraftClient client, ServerInfo info) {
        retryAt = 0;
        waitingOn = null;
        ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), client, ServerAddress.parse(info.address), info, false, null);
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (retryAt == 0) return;
        if (c.currentScreen != waitingOn || lastServer == null) { retryAt = 0; waitingOn = null; return; }   // left the screen: stop
        if (cancelBtn != null && status() != null) cancelBtn.setMessage(Text.literal(status() + " - Cancel"));
        if (System.currentTimeMillis() >= retryAt) { tries++; connect(c, lastServer); }
    }

    public static void remember(ServerInfo info) {
        if (info != null) lastServer = info;
        tries = 0;                                    // got in: stop counting
        retryAt = 0;
    }

    /** "Retrying in 3s (try 4)" for the disconnect screen (drawn by onScreenRender). */
    public static String status() {
        if (retryAt == 0) return null;
        long s = Math.max(0, (retryAt - System.currentTimeMillis() + 999) / 1000);
        return "Server full - retrying in " + s + "s" + (tries > 0 ? " (try " + (tries + 1) + ")" : "");
    }

    public static void onScreenInit(MinecraftClient client, Screen screen, int w, int h) {
        if (!(screen instanceof DisconnectedScreen) || lastServer == null || !ModuleManager.enabled(ReconnectModule.class)) return;
        ServerInfo info = lastServer;
        ReconnectModule m = ModuleManager.get(ReconnectModule.class);
        String why = screen.getNarratedTitle() == null ? "" : screen.getNarratedTitle().getString();
        boolean retry = m != null && (m.anyReason.get() || (m.auto.get() && FULL.matcher(why).find()));
        if (retry) {
            waitingOn = screen;
            retryAt = System.currentTimeMillis() + Math.round(m.delay.get() * 1000);
            cancelBtn = ButtonWidget.builder(Text.literal(status() + " - Cancel"), b -> {
                retryAt = 0; waitingOn = null; tries = 0;
                b.active = false;
                b.setMessage(Text.literal("Auto reconnect stopped"));
            }).dimensions(w / 2 - 100, h - 54, 200, 20).build();
            Screens.getButtons(screen).add(cancelBtn);
        } else {
            tries = 0;
        }
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Reconnect"), b -> { tries = 0; connect(client, info); })
                .dimensions(w / 2 - 100, h - 30, 200, 20).build());
    }
}
