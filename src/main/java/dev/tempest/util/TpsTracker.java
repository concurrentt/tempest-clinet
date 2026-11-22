package dev.tempest.util;

/** Estimates server TPS from the spacing of time-update packets (sent every 20 ticks). */
public final class TpsTracker {
    private static long last = 0;
    private static double tps = -1;

    private TpsTracker() { }

    public static void onUpdate() {
        long now = System.nanoTime();
        if (last != 0) {
            double dt = (now - last) / 1_000_000_000.0;
            if (dt > 0 && dt < 10) {
                double v = Math.min(20.0, 20.0 / dt);
                tps = tps < 0 ? v : tps * 0.7 + v * 0.3;
            }
        }
        last = now;
    }

    /** @return estimated TPS or -1 if unknown/stale */
    public static double tps() {
        if (last == 0 || (System.nanoTime() - last) / 1_000_000_000.0 > 8) return -1;
        return tps;
    }
}
