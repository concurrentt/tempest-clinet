package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.util.ServerPinger;
import dev.tempest.util.TpsTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PingModule extends HudModule {
    private final BoolSetting ping = add(new BoolSetting("ping", "Ping", true));
    private final BoolSetting tps = add(new BoolSetting("tps", "TPS (estimated)", true));

    public PingModule() { super("ping", "Ping & TPS", "Latency and estimated server TPS.", Category.HUD, false, 6, 300); }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        List<String> lines = new ArrayList<>();
        if (ping.get()) {
            int raw = preview ? 42 : currentPing(c);
            if (!preview && c.isInSingleplayer()) {
                lines.add("Ping: local");
            } else {
                double smoothed = raw < 0 ? -1 : smoothMetric("ping", raw, 9f);
                lines.add("Ping: " + (smoothed < 0 ? "..." : Math.max(0, (int) Math.round(smoothed)) + "ms"));
            }
        }
        if (tps.get()) {
            double raw = preview ? 19.9 : TpsTracker.tps();
            double t = raw < 0 ? -1 : smoothMetric("tps", raw, 5f);
            lines.add("TPS: " + (t < 0 ? "?" : String.format(Locale.ROOT, "%.1f", t)));
        }
        if (lines.isEmpty()) return;
        drawLines(ctx, lines);
    }

    /**
     * Your ping to the current server: measured live on the game connection (util/PingTracker, median of the last
     * 5); if the server doesn't answer those, the tab list's latency; if that is 0 too, a status ping.
     */
    private static int currentPing(MinecraftClient c) {
        if (c.player == null || c.getNetworkHandler() == null) return -1;
        int live = dev.tempest.util.PingTracker.ping();          // measured on the game connection: most accurate
        if (live > 0) return live;
        PlayerListEntry e = c.getNetworkHandler().getPlayerListEntry(c.player.getUuid());
        if (e != null && e.getLatency() > 0) return e.getLatency();
        if (c.getCurrentServerEntry() == null) return -1;
        String address = c.getCurrentServerEntry().address;
        ServerPinger.ping(address, 5_000);
        ServerPinger.Result r = ServerPinger.get(address);
        return r != null && r.pingMs > 0 ? r.pingMs : -1;
    }
}
