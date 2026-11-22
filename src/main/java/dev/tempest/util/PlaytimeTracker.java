package dev.tempest.util;

import dev.tempest.TempestConfig;
import net.minecraft.client.MinecraftClient;

/** Counts time spent in a world: this session, in total, and per server. Saved to the config every minute. */
public final class PlaytimeTracker {
    private static long last = 0, sessionMs = 0, sinceSave = 0;
    private static String key = "singleplayer";

    private PlaytimeTracker() { }

    public static void onJoin(MinecraftClient c) {
        sessionMs = 0;
        key = c.getCurrentServerEntry() != null ? c.getCurrentServerEntry().address : "singleplayer";
    }

    public static void onLeave() { TempestConfig.save(); }

    public static void resetSession() { sessionMs = 0; }

    public static void tick(MinecraftClient c) {
        long now = System.nanoTime();
        long dt = last == 0 ? 0 : (now - last) / 1_000_000L;
        last = now;
        if (c.world == null || c.player == null || dt <= 0 || dt > 2000) return;
        sessionMs += dt;
        TempestConfig cfg = TempestConfig.get();
        cfg.playtimeMs += dt;
        cfg.playtimeServers.merge(key, dt, Long::sum);
        sinceSave += dt;
        if (sinceSave > 60_000) { sinceSave = 0; TempestConfig.save(); }
    }

    public static long session() { return sessionMs; }
    public static long total() { return TempestConfig.get().playtimeMs; }
    public static long server() { return TempestConfig.get().playtimeServers.getOrDefault(key, 0L); }
    public static String serverName() { return key; }

    /** 1:02:03 for short times. */
    public static String clock(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    /** 52h 10m for long times. */
    public static String hours(long ms) {
        long m = ms / 60_000;
        return (m / 60) + "h " + String.format("%02d", m % 60) + "m";
    }
}
