package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Real-time ping to the server you are playing on, measured on the game connection itself.
 *
 * Every 2 seconds it sends the same small ping packet that vanilla's F3 network graph uses, and times the
 * server's answer. The value shown is the median of the last 5 answers, so one slow packet can't spike it.
 * This works through proxies (it is answered by the server you are actually on) and needs nothing from the
 * server. If a server never answers, Tempest falls back to the tab list's latency.
 */
public final class PingTracker {
    private static final ArrayDeque<Long> SAMPLES = new ArrayDeque<>();
    private static long pendingSince;     // nanoTime the outstanding ping was sent, 0 = none
    private static long lastSent;
    private static long lastAnswer;

    private PingTracker() { }

    public static void tick(MinecraftClient c) {
        if (c.getNetworkHandler() == null || c.player == null || c.isInSingleplayer()) {
            reset();
            return;
        }
        long now = System.nanoTime();
        if (pendingSince != 0 && now - pendingSince > 10_000_000_000L) pendingSince = 0;   // lost: try again
        if (pendingSince == 0 && now - lastSent > 2_000_000_000L) {
            try {
                c.getNetworkHandler().sendPacket(new QueryPingC2SPacket(net.minecraft.util.Util.getMeasuringTimeMs()));
                pendingSince = now;
                lastSent = now;
            } catch (Throwable ignored) { }
        }
    }

    /** The server answered (ClientPlayNetworkHandlerMixin, onPingResult). Called once per answer. */
    public static synchronized void onResult() {
        if (pendingSince == 0) return;
        long ms = (System.nanoTime() - pendingSince) / 1_000_000L;
        pendingSince = 0;
        lastAnswer = System.nanoTime();
        SAMPLES.addLast(Math.max(1, ms));
        while (SAMPLES.size() > 5) SAMPLES.removeFirst();
    }

    /** Median of the recent answers in ms, or -1 if there is no fresh measurement. */
    public static synchronized int ping() {
        if (SAMPLES.isEmpty() || System.nanoTime() - lastAnswer > 8_000_000_000L) return -1;
        List<Long> l = new ArrayList<>(SAMPLES);
        Collections.sort(l);
        return (int) (long) l.get(l.size() / 2);
    }

    public static synchronized void reset() {
        SAMPLES.clear();
        pendingSince = 0;
    }
}
