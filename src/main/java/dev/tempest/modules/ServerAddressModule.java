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
    private final BoolSetting icon = add(new BoolSetting("icon", "Show Server Icon", true));
    private String askedFor;

    public ServerAddressModule() { super("server_address", "Server Address", "Shows the IP of the server you're on.", Category.HUD, false, 6, 60); }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        String ip;
        net.minecraft.util.Identifier tex = null;
        if (preview) ip = "play.tempest.gg";
        else {
            ServerInfo info = c.getCurrentServerEntry();
            if (info != null) {
                ip = info.address;
                if (icon.get() && !StreamerModeModule.hideCoords()) tex = serverIcon(info);
            }
            else if (c.isInSingleplayer()) ip = "Singleplayer";
            else return;
            if (StreamerModeModule.hideCoords()) ip = "hidden";
        }
        if (stripPort.get() && ip.endsWith(":25565")) ip = ip.substring(0, ip.length() - 6);
        if (lower.get() && !ip.equals("Singleplayer")) ip = ip.toLowerCase(Locale.ROOT);
        String line = showLabel.get() ? "IP: " + ip : ip;
        if (tex != null) drawLines(ctx, List.of(line), null, List.of(tex));
        else drawLines(ctx, List.of(line));
    }

    /** The server's icon: from the join itself, else from a server-list ping (started once per server). */
    private net.minecraft.util.Identifier serverIcon(ServerInfo info) {
        byte[] png = info.getFavicon();
        if (png == null) {
            dev.tempest.util.ServerPinger.Result r = dev.tempest.util.ServerPinger.get(info.address);
            if (r != null) png = r.icon();
            if (png == null && !info.address.equals(askedFor)) {
                askedFor = info.address;
                dev.tempest.util.ServerPinger.ping(info.address, 300_000);
            }
        }
        return png == null ? null : dev.tempest.util.ServerIcons.get(info.address, png);
    }
}
