package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Per-frame state for the HUD-layer visuals (Fireflies). The sky itself no longer uses this: it is drawn in
 * the world by render/SkyRenderer, so there is no screen projection or sky ray casting here any more.
 */
public final class SkyView {
    public static MinecraftClient mc;
    public static Projection pr;
    public static Vec3d cam;
    public static float tickDelta, dt, time;
    private static float night, clear;
    private static long lastNs = System.nanoTime();
    private static final long T0 = System.nanoTime();

    private SkyView() { }

    /** Prepares a frame. @return false when there is nothing to draw (no world, not the Overworld, under water). */
    public static boolean begin(DrawContext ctx, float td) {
        mc = MinecraftClient.getInstance();
        long now = System.nanoTime();
        dt = Math.min(0.1f, Math.max(0f, (now - lastNs) / 1_000_000_000f));
        lastNs = now;
        time = (now - T0) / 1_000_000_000f;
        if (mc == null || mc.world == null || mc.player == null) return false;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return false;
        if (mc.player.isSubmergedInWater() || mc.player.isInLava()) return false;
        tickDelta = td;
        pr = Projection.of(mc, ctx, td);
        cam = mc.player.getCameraPosVec(td);
        long t = Math.floorMod(mc.world.getTimeOfDay(), 24000L);
        float n;
        if (t < 12000) n = 0f;
        else if (t < 13800) n = (t - 12000) / 1800f;
        else if (t < 22200) n = 1f;
        else n = 1f - (t - 22200) / 1800f;
        night = n * n * (3f - 2f * n);
        clear = 1f - 0.85f * mc.world.getRainGradient(td);
        return true;
    }

    /** 0 during the day, 1 at night. */
    public static float realNight() { return night; }

    /** 1 in clear weather, lower in rain. */
    public static float clear() { return clear; }

    /** Screen position of a world position, or null when it is behind the camera. */
    public static int[] projectWorld(Vec3d pos) { return pr.project(pos); }

    public static void dot(DrawContext ctx, int x, int y, int size, int argb) {
        if (((argb >>> 24) & 255) == 0) return;
        int h = size / 2;
        ctx.fill(x - h, y - h, x - h + size, y - h + size, argb);
    }

    public static int argb(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static float wave(float x) {
        return 0.5f + 0.25f * (float) Math.sin(x) + 0.15f * (float) Math.sin(x * 2.37f + 1.3f) + 0.10f * (float) Math.sin(x * 5.11f + 4.1f);
    }
}
