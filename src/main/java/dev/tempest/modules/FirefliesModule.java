package dev.tempest.modules;

import dev.tempest.gui.TempestLoading;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import dev.tempest.util.SkyView;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Random;

/** Little glowing bugs that drift and blink in the air around you. Client-side only. */
public class FirefliesModule extends Module {
    private final NumberSetting count = add(new NumberSetting("count", "Amount", 36, 8, 120, 1));
    private final NumberSetting radius = add(new NumberSetting("radius", "Radius (blocks)", 12, 5, 28, 1));
    private final ColorSetting color = add(new ColorSetting("color", "Glow Color", 0xFFD8FF6A));
    private final BoolSetting nightOnly = add(new BoolSetting("night_only", "Night Only", true));

    private static final int MAX = 120;
    private final double[][] pos = new double[MAX][];
    private final float[] phase = new float[MAX], seen = new float[MAX];
    private final boolean[] open = new boolean[MAX];
    private final Random rnd = new Random();
    private int checkCursor;

    public FirefliesModule() {
        super("fireflies", "Fireflies", "Glowing fireflies drifting and blinking around you.", Category.VISUALS, false);
    }

    public static void render(DrawContext ctx) {
        FirefliesModule m = ModuleManager.get(FirefliesModule.class);
        if (m != null && m.isEnabled()) m.draw(ctx);
    }

    /** Places a firefly at a random air block near the player, low to the ground. */
    private void respawn(int i, Vec3d around, double rad) {
        var w = SkyView.mc.world;
        for (int tries = 0; tries < 8; tries++) {
            double x = around.x + (rnd.nextDouble() * 2 - 1) * rad;
            double z = around.z + (rnd.nextDouble() * 2 - 1) * rad;
            double y = around.y - 1.0 + rnd.nextDouble() * 4.0;
            if (w.getBlockState(BlockPos.ofFloored(x, y, z)).isAir()) {
                pos[i] = new double[]{x, y, z};
                phase[i] = rnd.nextFloat() * 50f;
                seen[i] = 0f;
                open[i] = false;
                return;
            }
        }
        pos[i] = null;
    }

    private void draw(DrawContext ctx) {
        float fade = nightOnly.get() ? SkyView.realNight() : 1f;
        fade *= 0.4f + 0.6f * SkyView.clear();
        if (fade < 0.02f) return;
        var mc = SkyView.mc;
        Vec3d feet = mc.player.getLerpedPos(SkyView.tickDelta);
        double rad = radius.get();
        int n = Math.min(MAX, count.getInt());
        float t = SkyView.time, dt = SkyView.dt;
        int rgb = color.get() & 0xFFFFFF;

        // a few line-of-sight checks per frame so fireflies hide behind walls
        for (int k = 0; k < 6 && n > 0; k++) {
            int i = checkCursor = (checkCursor + 1) % n;
            if (pos[i] == null) continue;
            Vec3d p = new Vec3d(pos[i][0], pos[i][1], pos[i][2]);
            HitResult hit = mc.world.raycast(new RaycastContext(SkyView.cam, p,
                    RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, mc.player));
            open[i] = hit == null || hit.getType() == HitResult.Type.MISS;
        }

        for (int i = 0; i < n; i++) {
            double[] q = pos[i];
            if (q == null || Math.abs(q[0] - feet.x) > rad * 1.3 || Math.abs(q[2] - feet.z) > rad * 1.3 || Math.abs(q[1] - feet.y) > 8) {
                respawn(i, feet, rad);
                continue;
            }
            float ph = phase[i] + t;
            // lazy wandering drift
            q[0] += Math.sin(ph * 0.41 + i) * 0.35 * dt;
            q[1] += Math.sin(ph * 0.63 + i * 2.1) * 0.18 * dt;
            q[2] += Math.cos(ph * 0.37 + i * 1.3) * 0.35 * dt;

            seen[i] += ((open[i] ? 1f : 0f) - seen[i]) * Math.min(1f, dt * 6f);
            if (seen[i] < 0.03f) continue;
            int[] s = SkyView.projectWorld(new Vec3d(q[0], q[1], q[2]));
            if (s == null) continue;
            double dist = SkyView.cam.distanceTo(new Vec3d(q[0], q[1], q[2]));
            // blink: mostly lit, with slow pulses and the odd dark moment
            float blink = 0.55f + 0.45f * (float) Math.sin(ph * 2.2);
            blink *= SkyView.wave(ph * 0.7f) > 0.3f ? 1f : 0.25f;
            float al = blink * fade * seen[i];
            int glow = (int) Math.max(6, Math.min(60, 160 / Math.max(0.8, dist)));
            TempestLoading.drawGlow(ctx, s[0], s[1], glow, rgb, 0.55f * al);
            SkyView.dot(ctx, s[0], s[1], dist < 4 ? 2 : 1, SkyView.argb(0xFFFFF0, al));
        }
    }

    @Override
    public void onDisable() { java.util.Arrays.fill(pos, null); }
}
