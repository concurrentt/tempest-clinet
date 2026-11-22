package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Projects world positions onto the 2D HUD using the real camera (so it also works in third person).
 * Tempest draws hitboxes as a HUD overlay instead of in the world pass, which keeps it independent of
 * Minecraft's render internals.
 */
public final class Projection {
    /** Vertical FOV in degrees, updated every frame by GameRendererMixin. */
    public static float fov = 70f;

    private static final double NEAR = 0.05;

    /** Line thickness in GUI pixels used by line() and box(). */
    public int width1 = 1;
    public Projection thickness(int t) { width1 = Math.max(1, Math.min(6, t)); return this; }

    private final double cx, cy, cz;
    private final double fx, fy, fz, rx, rz, ux, uy, uz;
    private final int width, height;
    private final double tv, th;

    private Projection(Camera cam, Vec3d p, int w, int h) {
        cx = p.x;
        cy = p.y;
        cz = p.z;
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
        ux = -Math.sin(yaw) * Math.sin(pitch);
        uy = Math.cos(pitch);
        uz = Math.cos(yaw) * Math.sin(pitch);
        width = w;
        height = h;
        tv = Math.tan(Math.toRadians(fov) / 2.0);
        th = tv * w / (double) h;
    }

    public static Projection of(MinecraftClient c, DrawContext ctx, float tickDelta) {
        return new Projection(c.gameRenderer.getCamera(), c.player.getCameraPosVec(tickDelta), ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
    }

    // camera space: x = right, y = up, z = forward
    private double[] cam(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return new double[]{dx * rx + dz * rz, dx * ux + dy * uy + dz * uz, dx * fx + dy * fy + dz * fz};
    }

    private int[] screen(double[] c) {
        return new int[]{
                (int) Math.round(width / 2.0 + c[0] / c[2] / th * (width / 2.0)),
                (int) Math.round(height / 2.0 - c[1] / c[2] / tv * (height / 2.0))
        };
    }

    /** @return {x, y} in scaled GUI pixels, or null if the point is behind the camera. */
    public int[] project(Vec3d p) {
        double[] c = cam(p.x, p.y, p.z);
        return c[2] < NEAR ? null : screen(c);
    }

    /** Draws a 3D line segment, clipped against the camera's near plane. */
    public void line(DrawContext ctx, Vec3d a, Vec3d b, int color) {
        double[] ca = cam(a.x, a.y, a.z), cb = cam(b.x, b.y, b.z);
        if (ca[2] < NEAR && cb[2] < NEAR) return;
        if (ca[2] < NEAR) ca = clip(ca, cb);
        else if (cb[2] < NEAR) cb = clip(cb, ca);
        int[] sa = screen(ca), sb = screen(cb);
        int t = width1;
        boolean horiz = Math.abs(sb[0] - sa[0]) >= Math.abs(sb[1] - sa[1]);
        for (int i = 0; i < t; i++) {
            int o = i - (t - 1) / 2;
            if (horiz) drawLine(ctx, sa[0], sa[1] + o, sb[0], sb[1] + o, color);
            else drawLine(ctx, sa[0] + o, sa[1], sb[0] + o, sb[1], color);
        }
    }

    private static double[] clip(double[] behind, double[] front) {
        double t = (NEAR - behind[2]) / (front[2] - behind[2]);
        return new double[]{
                behind[0] + (front[0] - behind[0]) * t,
                behind[1] + (front[1] - behind[1]) * t,
                NEAR
        };
    }

    /** Wireframe box. */
    public void box(DrawContext ctx, Box b, int color) {
        double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
        Vec3d[] v = {
                new Vec3d(x0, y0, z0), new Vec3d(x1, y0, z0), new Vec3d(x1, y0, z1), new Vec3d(x0, y0, z1),
                new Vec3d(x0, y1, z0), new Vec3d(x1, y1, z0), new Vec3d(x1, y1, z1), new Vec3d(x0, y1, z1)
        };
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) line(ctx, v[e[0]], v[e[1]], color);
    }

    /** 1px line, drawn as horizontal/vertical runs so it needs few fill calls. */
    public static void drawLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0, dy = y1 - y0;
        if (Math.abs(dx) > 6000 || Math.abs(dy) > 6000) return;
        if (Math.abs(dx) >= Math.abs(dy)) {
            if (dx == 0) { ctx.fill(x0, y0, x0 + 1, y0 + 1, color); return; }
            int step = dx > 0 ? 1 : -1;
            int runStart = x0, runY = y0;
            for (int i = 0, x = x0; i <= Math.abs(dx); i++, x += step) {
                int y = (int) Math.round(y0 + dy * (i / (double) Math.abs(dx)));
                if (y != runY) {
                    ctx.fill(Math.min(runStart, x - step), runY, Math.max(runStart, x - step) + 1, runY + 1, color);
                    runStart = x;
                    runY = y;
                }
            }
            ctx.fill(Math.min(runStart, x1), runY, Math.max(runStart, x1) + 1, runY + 1, color);
        } else {
            int step = dy > 0 ? 1 : -1;
            int runStart = y0, runX = x0;
            for (int i = 0, y = y0; i <= Math.abs(dy); i++, y += step) {
                int x = (int) Math.round(x0 + dx * (i / (double) Math.abs(dy)));
                if (x != runX) {
                    ctx.fill(runX, Math.min(runStart, y - step), runX + 1, Math.max(runStart, y - step) + 1, color);
                    runStart = y;
                    runX = x;
                }
            }
            ctx.fill(runX, Math.min(runStart, y1), runX + 1, Math.max(runStart, y1) + 1, color);
        }
    }
}
