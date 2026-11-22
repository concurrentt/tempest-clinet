package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Joins a server from anywhere in Tempest. If you are already in a world (joining from the mod menu's
 * Favorite Servers in game), it leaves that world properly first, the way the pause menu's Disconnect does.
 * Connecting on top of a live world used to leave the old world and connection half-alive, which is what
 * glitched the screen.
 */
public final class ServerJoin {
    private ServerJoin() { }

    public static void join(MinecraftClient c, ServerInfo info, Screen parent) {
        if (c == null || info == null) return;
        if (c.currentScreen instanceof ConnectScreen) return;          // already connecting
        Screen back = parent;
        if (c.world != null) {
            boolean singleplayer = c.isInSingleplayer();
            try {
                if (c.getNetworkHandler() != null) c.getNetworkHandler().getConnection().disconnect(Text.literal("Switching server"));
            } catch (Throwable ignored) { }
            if (singleplayer) c.disconnectWithSavingScreen();
            else c.disconnectWithProgressScreen();
            back = new MultiplayerScreen(new TitleScreen());               // in-game screens are gone now
        }
        if (back == null) back = new MultiplayerScreen(new TitleScreen());
        ConnectScreen.connect(back, c, ServerAddress.parse(info.address), info, false, null);
    }
}
