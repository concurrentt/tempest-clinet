package dev.tempest.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a flat textured cosmetic sheet into a solid piece, the way Minecraft turns item sprites into 3D held items:
 * a front and a back face (the texture), plus side walls along the outline of the texture's solid pixels, each wall
 * coloured by the pixel it borders. Every face gets its real normal, so the piece is shaded like a 3D object.
 *
 * <p>The outline comes from a coarse grid over the texture (e.g. 64x64 cells for a 512px wing), read once per
 * texture, so a piece costs a few hundred quads at most. Sheets can be any four corners (hinged wing panels, leaning
 * ears, crown walls); the walls follow them.</p>
 */
final class Extrude {
    /** Outline of a texture: per edge {x0, y0, x1, y1, outX, outY, sampleU, sampleV}, all in 0..1 texture space. */
    record Mask(float[] edges, int count) {
        static final Mask NONE = new Mask(new float[0], 0);
    }

    private static final Map<String, Mask> CACHE = new ConcurrentHashMap<>();

    private Extrude() { }

    /** The outline of a texture on a cellsW x cellsH grid (a cell is solid when its centre is mostly opaque). */
    static Mask mask(Identifier tex, int cellsW, int cellsH) {
        return CACHE.computeIfAbsent(tex + "#" + cellsW + "x" + cellsH, k -> build(tex, cellsW, cellsH));
    }

    private static Mask build(Identifier tex, int cw, int ch) {
        BufferedImage img;
        try {
            var res = MinecraftClient.getInstance().getResourceManager().getResource(tex);
            if (res.isEmpty()) return Mask.NONE;
            try (InputStream in = res.get().getInputStream()) { img = ImageIO.read(in); }
            if (img == null) return Mask.NONE;
        } catch (Exception e) {
            return Mask.NONE;
        }
        int iw = img.getWidth(), ih = img.getHeight();
        boolean[] solid = new boolean[cw * ch];
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                // a cell is solid if most of a 3x3 sample inside it is opaque
                int hits = 0;
                for (int sy = 0; sy < 3; sy++) for (int sx = 0; sx < 3; sx++) {
                    int px = Math.min(iw - 1, (int) ((x + (sx + 0.5f) / 3f) * iw / cw));
                    int py = Math.min(ih - 1, (int) ((y + (sy + 0.5f) / 3f) * ih / ch));
                    if ((img.getRGB(px, py) >>> 24) >= 100) hits++;
                }
                solid[y * cw + x] = hits >= 5;
            }
        }
        float[] e = new float[cw * ch * 4 * 8];
        int n = 0;
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                if (!solid[y * cw + x]) continue;
                float x0 = (float) x / cw, x1 = (float) (x + 1) / cw, y0 = (float) y / ch, y1 = (float) (y + 1) / ch;
                float su = (x + 0.5f) / cw, sv = (y + 0.5f) / ch;
                if (x == 0 || !solid[y * cw + x - 1]) n = put(e, n, x0, y0, x0, y1, -1, 0, su, sv);
                if (x == cw - 1 || !solid[y * cw + x + 1]) n = put(e, n, x1, y0, x1, y1, 1, 0, su, sv);
                if (y == 0 || !solid[(y - 1) * cw + x]) n = put(e, n, x0, y0, x1, y0, 0, -1, su, sv);
                if (y == ch - 1 || !solid[(y + 1) * cw + x]) n = put(e, n, x0, y1, x1, y1, 0, 1, su, sv);
            }
        }
        return new Mask(e, n);
    }

    private static int put(float[] e, int n, float x0, float y0, float x1, float y1, float ox, float oy, float su, float sv) {
        int o = n * 8;
        e[o] = x0; e[o + 1] = y0; e[o + 2] = x1; e[o + 3] = y1; e[o + 4] = ox; e[o + 5] = oy; e[o + 6] = su; e[o + 7] = sv;
        return n + 1;
    }

    /**
     * A solid sheet. Corners p00..p01 are where texture coords (u0,v0) (u1,v0) (u1,v1) (u0,v1) of the region land;
     * the sheet is {@code thick} deep, centred on that surface. Only the outline inside the region is walled, so
     * hinged panels that share a texture (wings) join up without walls at the hinges.
     */
    static void sheet(VertexConsumer vc, MatrixStack.Entry en, Mask m, float[] p00, float[] p10, float[] p11, float[] p01,
                      float u0, float v0, float u1, float v1, float thick, int argb, int light) {
        float[] du = sub(p10, p00), dv = sub(p01, p00);
        float[] n = norm(cross(du, dv));
        if (n == null) return;
        float h = thick / 2f;
        float[] f00 = add(p00, n, h), f10 = add(p10, n, h), f11 = add(p11, n, h), f01 = add(p01, n, h);
        float[] b00 = add(p00, n, -h), b10 = add(p10, n, -h), b11 = add(p11, n, -h), b01 = add(p01, n, -h);
        // front and back
        v(vc, en, f00, u0, v0, argb, light, n, 1);
        v(vc, en, f10, u1, v0, argb, light, n, 1);
        v(vc, en, f11, u1, v1, argb, light, n, 1);
        v(vc, en, f01, u0, v1, argb, light, n, 1);
        v(vc, en, b01, u0, v1, argb, light, n, -1);
        v(vc, en, b11, u1, v1, argb, light, n, -1);
        v(vc, en, b10, u1, v0, argb, light, n, -1);
        v(vc, en, b00, u0, v0, argb, light, n, -1);
        if (m == null || m.count() == 0 || thick <= 0f) return;
        // side walls along the outline inside this region
        float ru = u1 - u0, rv = v1 - v0, eps = 1e-4f;
        float[] nu = norm(du), nv = norm(dv);
        if (nu == null || nv == null) return;
        float[] e = m.edges();
        float[] a = new float[3], b = new float[3], wn = new float[3];
        for (int i = 0; i < m.count(); i++) {
            int o = i * 8;
            float ex0 = e[o], ey0 = e[o + 1], ex1 = e[o + 2], ey1 = e[o + 3];
            if (ex0 == ex1) {                                   // vertical edge (constant u)
                if (ex0 < Math.min(u0, u1) - eps || ex0 > Math.max(u0, u1) + eps) continue;
                if (Math.abs(ex0 - u0) < eps && e[o + 4] > 0 == u1 > u0) continue;   // belongs to the neighbouring panel
                if (Math.abs(ex0 - u1) < eps && e[o + 4] < 0 == u1 > u0) continue;
                ey0 = clamp(ey0, v0, v1); ey1 = clamp(ey1, v0, v1);
                if (Math.abs(ey1 - ey0) < eps) continue;
            } else {                                            // horizontal edge (constant v)
                if (ey0 < Math.min(v0, v1) - eps || ey0 > Math.max(v0, v1) + eps) continue;
                ex0 = clamp(ex0, u0, u1); ex1 = clamp(ex1, u0, u1);
                if (Math.abs(ex1 - ex0) < eps) continue;
            }
            bilerp(p00, p10, p11, p01, (ex0 - u0) / ru, (ey0 - v0) / rv, a);
            bilerp(p00, p10, p11, p01, (ex1 - u0) / ru, (ey1 - v0) / rv, b);
            float sx = Math.signum(ru) * e[o + 4], sy = Math.signum(rv) * e[o + 5];
            wn[0] = nu[0] * sx + nv[0] * sy; wn[1] = nu[1] * sx + nv[1] * sy; wn[2] = nu[2] * sx + nv[2] * sy;
            float[] wnn = norm(wn);
            if (wnn == null) continue;
            float su = e[o + 6], sv = e[o + 7];
            v(vc, en, add(a, n, h), su, sv, argb, light, wnn, 1);
            v(vc, en, add(b, n, h), su, sv, argb, light, wnn, 1);
            v(vc, en, add(b, n, -h), su, sv, argb, light, wnn, 1);
            v(vc, en, add(a, n, -h), su, sv, argb, light, wnn, 1);
        }
    }

    private static float clamp(float x, float a, float b) {
        float lo = Math.min(a, b), hi = Math.max(a, b);
        return Math.max(lo, Math.min(hi, x));
    }

    private static void bilerp(float[] p00, float[] p10, float[] p11, float[] p01, float s, float t, float[] out) {
        for (int i = 0; i < 3; i++)
            out[i] = (1 - s) * (1 - t) * p00[i] + s * (1 - t) * p10[i] + s * t * p11[i] + (1 - s) * t * p01[i];
    }

    static float[] p(float x, float y, float z) { return new float[]{x, y, z}; }

    private static float[] sub(float[] a, float[] b) { return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    private static float[] add(float[] a, float[] d, float k) { return new float[]{a[0] + d[0] * k, a[1] + d[1] * k, a[2] + d[2] * k}; }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float[] norm(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-7f ? null : new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static void v(VertexConsumer vc, MatrixStack.Entry en, float[] p, float u, float v, int argb, int light, float[] n, float s) {
        vc.vertex(en, p[0], p[1], p[2]).color(argb).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(en, n[0] * s, n[1] * s, n[2] * s);
    }

    /** Plain vertex with a normal (for the round hat parts). */
    static void vn(VertexConsumer vc, MatrixStack.Entry en, float x, float y, float z, float u, float v, int argb, int light,
                   float nx, float ny, float nz) {
        vc.vertex(en, x, y, z).color(argb).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(en, nx, ny, nz);
    }
}
