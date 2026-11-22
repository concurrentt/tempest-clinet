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

    public ReconnectModule() { super("reconnect", "Reconnect Button", "Adds a Reconnect button to the disconnect screen.", Category.UTILITY, true); }

    public static void remember(ServerInfo info) { if (info != null) lastServer = info; }

    public static void onScreenInit(MinecraftClient client, Screen screen, int w, int h) {
        if (!(screen instanceof DisconnectedScreen) || lastServer == null || !ModuleManager.enabled(ReconnectModule.class)) return;
        ServerInfo info = lastServer;
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Reconnect"), b ->
                ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), client, ServerAddress.parse(info.address), info, false, null))
                .dimensions(w / 2 - 100, h - 30, 200, 20).build());
    }
}
