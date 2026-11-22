package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;

import java.util.List;
import java.util.Locale;

/** Shows the address of the server you are on (handy when recording). Hidden while Streamer Mode hides coordinates. */
public class ServerAddressModule extends HudModule {
    private final BoolSetting showLabel = add(new BoolSetting("label", "Show \"IP\" Label", true));
    private final BoolSetting lower = add(new BoolSetting("lower", "Lower Case", true));
    private final BoolSetting stripPort = add(new BoolSetting("strip_port", "Hide Default Port", true));

    public ServerAddressModule() { super("server_address", "Server Address", "Shows the IP of the server you're on.", Category.HUD, false, 6, 60); }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        String ip;
        if (preview) ip = "play.tempest.gg";
        else {
            ServerInfo info = c.getCurrentServerEntry();
            if (info != null) ip = info.address;
            else if (c.isInSingleplayer()) ip = "Singleplayer";
            else return;
            if (StreamerModeModule.hideCoords()) ip = "hidden";
        }
        if (stripPort.get() && ip.endsWith(":25565")) ip = ip.substring(0, ip.length() - 6);
        if (lower.get() && !ip.equals("Singleplayer")) ip = ip.toLowerCase(Locale.ROOT);
        drawLines(ctx, List.of(showLabel.get() ? "IP: " + ip : ip));
    }
}
