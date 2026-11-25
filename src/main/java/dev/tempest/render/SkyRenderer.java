package dev.tempest.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.tempest.modules.CustomSkyModule;
import dev.tempest.render.SkyScenes.Scene;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Draws the current Custom Sky scene inside Minecraft's sky pass (called right after the sky dome, see
 * mixin/SkyRenderingMixin).
 *
 * <p>Everything is real geometry about {@link #R} blocks from the camera, in world directions, drawn with the
 * same camera transform as the vanilla sky. So it stays put when you walk, bob or turn, and because the sky
 * is drawn before the terrain without writing depth, blocks cover it exactly. Each object is a handful of
 * textured quads (textures/sky/*.png, made by tools/make_sky_textures.py). tools/preview_sky.py renders the
 * same scenes offline with the same maths.</p>
 *
 * <p>Directions: azimuth 0 = north (-Z), 90 = east (+X); elevation 0 = horizon, 90 = straight up.</p>
 */
public final class SkyRenderer {
    private static final float R = 100f;
    private static final int LIGHT = 0xF000F0;

    private static final Identifier GLOW = tex("glow"), STAR = tex("star"), STAR_BRIGHT = tex("star_bright"),
            HORIZON = tex("horizon"), MOON = tex("moon"), STREAK = tex("streak"), NEBULA = tex("nebula"),
            DISK = tex("disk"), DISK_CORE = tex("disk_core"), LENS = tex("lens"), SHADOW = tex("shadow"),
            PHOTON = tex("photon_ring"), AURORA = tex("aurora"), MILKY = tex("milky_way");

    private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();
    private static final Map<String, Identifier> PLANETS = new HashMap<>();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Matrix4f mat = new Matrix4f();
    private static final long T0 = System.nanoTime();
    private static long lastNs = System.nanoTime();
    private static boolean failed;
    private static String lastScene;

    private SkyRenderer() { }

    private static Identifier tex(String name) { return Identifier.of("tempest", "textures/sky/" + name + ".png"); }

    private static RenderLayer layer(Identifier id) { return LAYERS.computeIfAbsent(id, RenderLayers::textSeeThrough); }

    // ======================================================================== entry point
    public static void render() {
        if (failed) return;
        CustomSkyModule m = CustomSkyModule.active();
        if (m == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isSubmergedInWater() || mc.player.isInLava()) return;
        try {
            draw(m, mc);
        } catch (Throwable t) {
            // Never take the game down for a sky; switch the drawing off for this session and say why once.
            failed = true;
            System.err.println("[Tempest] Custom Sky drawing turned off: " + t);
        }
    }

    private static void draw(CustomSkyModule m, MinecraftClient mc) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        float time = (now - T0) / 1e9f;

        // The sky pass normally has the camera rotation on the model-view stack already. If it is not there,
        // apply the camera rotation ourselves (the same matrix the game builds for the world).
        Matrix4f mv = RenderSystem.getModelViewMatrix();
        if (mv == null || mv.equals(IDENTITY, 1e-5f)) {
            Quaternionf q = new Quaternionf(mc.gameRenderer.getCamera().getRotation()).conjugate();
            mat.identity().rotation(q);
        } else {
            mat.identity();
        }

        Scene sc = m.current();
        if (!sc.name.equals(lastScene)) { lastScene = sc.name; METEORS.clear(); }
        long tod = Math.floorMod(mc.world.getTimeOfDay(), 24000L);
        float night = nightFactor(tod);
        float clear = 1f - 0.85f * mc.world.getRainGradient(mc.getRenderTickCounter().getTickProgress(false));
        float a = (m.showAt.is("Night Only") ? night : 1f) * clear * m.brightness.getFloat();
        if (a < 0.01f) return;
        float rot = m.rotation.getFloat();

        // far to near
        if (sc.glow != null) horizonGlow(sc.glow, a);
        if (sc.milkyWay != null) milkyWay(sc.milkyWay, rot, a);
        for (int i = 0; i < sc.nebulae.size(); i++) nebula(sc.nebulae.get(i), i, rot, a, time);
        if (sc.stars != null) stars(sc.stars, a, time);
        for (SkyScenes.Aurora au : sc.auroras) aurora(au, rot, a, time);
        for (SkyScenes.Planet p : sc.planets) planet(p, rot, a);
        for (SkyScenes.Moon mo : sc.moons) moon(mo, rot, a);
        if (sc.blackHole != null) blackHole(sc.blackHole, rot, a, time);
        if (m.meteors.get() && sc.meteors != null) shootingStars(sc.meteors, rot, a, dt);
        else METEORS.clear();
    }

    private static float nightFactor(long t) {
        float n;
        if (t < 12000) n = 0f;
        else if (t < 13800) n = (t - 12000) / 1800f;
        else if (t < 22200) n = 1f;
        else n = 1f - (t - 22200) / 1800f;
        return n * n * (3f - 2f * n);
    }

    private static float clamp01(float v) { return v < 0 ? 0 : Math.min(1, v); }

    // ======================================================================== geometry helpers
    private static BufferBuilder begin(Identifier tex) {
        RenderLayer l = layer(tex);
        return Tessellator.getInstance().begin(l.getDrawMode(), l.getVertexFormat());
    }

    private static void end(BufferBuilder b, Identifier tex) {
        BuiltBuffer built = b.endNullable();
        if (built != null) layer(tex).draw(built);
    }

    private static void vtx(BufferBuilder b, float x, float y, float z, float u, float v, int argb) {
        b.vertex(mat, x, y, z).color(argb).texture(u, v).light(LIGHT);
    }

    /**
     * One quad, wound so it faces the camera (works whether the layer culls back faces or not).
     * p = {x0,y0,z0, x1,y1,z1, x2,y2,z2, x3,y3,z3}, uv = {u0,v0 .. u3,v3}; one colour per corner.
     */
    private static void quad(BufferBuilder b, float[] p, float[] uv, int c0, int c1, int c2, int c3) {
        float ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
        float bx = p[6] - p[0], by = p[7] - p[1], bz = p[8] - p[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        boolean facing = -(nx * p[0] + ny * p[1] + nz * p[2]) >= 0; // normal points back at the camera (origin)
        if (facing) {
            vtx(b, p[0], p[1], p[2], uv[0], uv[1], c0);
            vtx(b, p[3], p[4], p[5], uv[2], uv[3], c1);
            vtx(b, p[6], p[7], p[8], uv[4], uv[5], c2);
            vtx(b, p[9], p[10], p[11], uv[6], uv[7], c3);
        } else {
            vtx(b, p[0], p[1], p[2], uv[0], uv[1], c0);
            vtx(b, p[9], p[10], p[11], uv[6], uv[7], c3);
            vtx(b, p[6], p[7], p[8], uv[4], uv[5], c2);
            vtx(b, p[3], p[4], p[5], uv[2], uv[3], c1);
        }
    }

    private static final float[] FULL_UV = {0, 1, 1, 1, 1, 0, 0, 0};

    /** Unit direction for azimuth/elevation in degrees. */
    private static float[] dir(double az, double el) {
        double a = Math.toRadians(az), e = Math.toRadians(el), ce = Math.cos(e);
        return new float[]{(float) (Math.sin(a) * ce), (float) Math.sin(e), (float) (-Math.cos(a) * ce)};
    }

    /** {right, up} tangent vectors on the sky at direction d (right is screen-right when facing d). */
    private static float[][] basis(float[] d) {
        float ux = 0, uy = 1, uz = 0;
        if (Math.abs(d[1]) > 0.98f) { uy = 0; uz = -1; }
        float rx = d[1] * uz - d[2] * uy, ry = d[2] * ux - d[0] * uz, rz = d[0] * uy - d[1] * ux;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl; ry /= rl; rz /= rl;
        float vx = ry * d[2] - rz * d[1], vy = rz * d[0] - rx * d[2], vz = rx * d[1] - ry * d[0];
        return new float[][]{{rx, ry, rz}, {vx, vy, vz}};
    }

    /** A square sprite of half-size {@code half} (blocks at distance R) centred on direction d, aligned to the sky. */
    private static void sprite(BufferBuilder b, float[] d, float half, int argb) {
        float[][] bs = basis(d);
        sprite(b, d, bs[0], bs[1], half, half, argb, FULL_UV);
    }

    private static void sprite(BufferBuilder b, float[] d, float[] r, float[] u, float hw, float hh, int argb, float[] uv) {
        float cx = d[0] * R, cy = d[1] * R, cz = d[2] * R;
        float[] p = {
                cx - r[0] * hw - u[0] * hh, cy - r[1] * hw - u[1] * hh, cz - r[2] * hw - u[2] * hh,
                cx + r[0] * hw - u[0] * hh, cy + r[1] * hw - u[1] * hh, cz + r[2] * hw - u[2] * hh,
                cx + r[0] * hw + u[0] * hh, cy + r[1] * hw + u[1] * hh, cz + r[2] * hw + u[2] * hh,
                cx - r[0] * hw + u[0] * hh, cy - r[1] * hw + u[1] * hh, cz - r[2] * hw + u[2] * hh};
        quad(b, p, uv, argb, argb, argb, argb);
    }

    /** Blocks at distance R covered by an angle of {@code deg} degrees. */
    private static float span(double deg) { return (float) (R * Math.tan(Math.toRadians(deg))); }

    private static int argb(int rgb, float a) {
        int al = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static float wave(float x) {
        return 0.5f + 0.25f * (float) Math.sin(x) + 0.15f * (float) Math.sin(x * 2.37f + 1.3f) + 0.10f * (float) Math.sin(x * 5.11f + 4.1f);
    }

    // ======================================================================== horizon glow
    /** A ring of light low around the horizon (from 6 degrees below to 26 above), fading upward. */
    private static void horizonGlow(SkyScenes.Glow g, float a) {
        final int segs = 48;
        int c = argb(SkyScenes.rgb(g.color), clamp01(g.alpha * a));
        BufferBuilder b = begin(HORIZON);
        for (int i = 0; i < segs; i++) {
            double a0 = i * 360.0 / segs, a1 = (i + 1) * 360.0 / segs;
            float[] b0 = dir(a0, -6), b1 = dir(a1, -6), t1 = dir(a1, 26), t0 = dir(a0, 26);
            float[] p = {b0[0] * R, b0[1] * R, b0[2] * R, b1[0] * R, b1[1] * R, b1[2] * R,
                    t1[0] * R, t1[1] * R, t1[2] * R, t0[0] * R, t0[1] * R, t0[2] * R};
            quad(b, p, new float[]{0, 1, 1, 1, 1, 0, 0, 0}, c, c, c, c);
        }
        end(b, HORIZON);
    }

    // ======================================================================== stars
    private static float[][] starDir = new float[0][];
    private static float[][][] starBasis = new float[0][][];
    private static float[] starMag = new float[0], starPhase = new float[0];
    private static int[] starTint = new int[0];
    private static int starsBuiltFor = -1;

    private static void stars(SkyScenes.Stars st, float a, float time) {
        int n = Math.max(0, Math.min(6000, st.count));
        if (n != starsBuiltFor) {
            starsBuiltFor = n;
            Random r = new Random(90210);
            starDir = new float[n][];
            starBasis = new float[n][][];
            starMag = new float[n];
            starPhase = new float[n];
            starTint = new int[n];
            for (int i = 0; i < n; i++) {
                double z = -0.1 + r.nextDouble() * 1.1;          // mostly above the horizon
                double ang = r.nextDouble() * Math.PI * 2, s = Math.sqrt(Math.max(0, 1 - z * z));
                starDir[i] = new float[]{(float) (Math.cos(ang) * s), (float) z, (float) (Math.sin(ang) * s)};
                starBasis[i] = basis(starDir[i]);
                float mm = r.nextFloat();
                starMag[i] = mm * mm * mm * mm;
                starPhase[i] = r.nextFloat() * 6.28f;
                // colours of real stars by temperature (blackbody): orange giants, yellow-white suns, white, blue-white
                float c = r.nextFloat();
                starTint[i] = c < 0.16f ? 0xFFC48A : c < 0.42f ? 0xFFE6C8 : c < 0.70f ? 0xFFF6EC : c < 0.90f ? 0xF2F4FF : 0xC4D4FF;
            }
        }
        int tint = SkyScenes.rgb(st.tint);
        float bright = st.brightness * a;
        // two passes (the tessellator has one buffer): ordinary stars, then the brightest few with halo and spikes
        for (int pass = 0; pass < 2; pass++) {
            Identifier tex = pass == 0 ? STAR : STAR_BRIGHT;
            BufferBuilder b = begin(tex);
            for (int i = 0; i < n; i++) {
                boolean isBig = starMag[i] > 0.82f;
                if (isBig != (pass == 1)) continue;
                // real twinkling is quick and slight overhead, stronger low down (more air in the way)
                float low = 1f - clamp01(starDir[i][1] / 0.5f);
                float amp = 0.08f + 0.30f * low * low;
                float twk = 1f - amp * (0.5f + 0.5f * (float) Math.sin(time * (2.3f + starMag[i] * 4.0f) + starPhase[i])
                        * (float) Math.sin(time * 0.71f + starPhase[i] * 3f));
                float al = clamp01((0.30f + 0.70f * starMag[i]) * twk * bright * extinction(starDir[i][1]));
                float half = isBig ? span(0.9 + 0.5 * starMag[i]) : span(0.16 + 0.30 * starMag[i]);
                sprite(b, starDir[i], starBasis[i][0], starBasis[i][1], half, half, argb(mul(starTint[i], tint), al), FULL_UV);
            }
            end(b, tex);
        }
    }

    /**
     * How much light from a point at height y (sin of its elevation) gets through the atmosphere: nearly all
     * overhead, a quarter at the horizon, nothing a little below it.
     */
    private static float extinction(float y) {
        if (y < -0.05f) return 0f;
        float e = clamp01((y + 0.05f) / 0.40f);
        return 0.25f * clamp01((y + 0.05f) / 0.05f) + 0.75f * e * (2f - e);
    }

    /** Multiplies two RGB colours channel by channel. */
    private static int mul(int a, int b) {
        int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255;
        int g = ((a >> 8) & 255) * ((b >> 8) & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    // ======================================================================== milky way
    private static void milkyWay(SkyScenes.MilkyWay mw, float rot, float a) {
        // The band follows a great circle that crosses the horizon at the chosen compass angle, tilted 60 degrees.
        double ang = Math.toRadians(mw.angle + rot);
        float[] u0 = {(float) Math.sin(ang), 0, (float) -Math.cos(ang)};
        float[] v0 = norm(new float[]{(float) (Math.cos(ang) * 0.5), (float) Math.sin(Math.toRadians(60)), (float) (Math.sin(ang) * 0.5)});
        float[] n = norm(cross(u0, v0));
        v0 = norm(cross(n, u0));
        float halfW = (float) Math.tan(Math.toRadians(mw.width));
        final int segs = 72;
        BufferBuilder b = begin(MILKY);
        int tintRgb = SkyScenes.rgb(mw.tint);
        float base = clamp01(0.85f * mw.alpha * a);
        for (int i = 0; i < segs; i++) {
            double p0 = i * Math.PI * 2 / segs, p1 = (i + 1) * Math.PI * 2 / segs;
            float[] c0 = add(scale(u0, (float) Math.cos(p0)), scale(v0, (float) Math.sin(p0)));
            float[] c1 = add(scale(u0, (float) Math.cos(p1)), scale(v0, (float) Math.sin(p1)));
            float[] a0 = norm(add(c0, scale(n, halfW))), b0 = norm(add(c0, scale(n, -halfW)));
            float[] a1 = norm(add(c1, scale(n, halfW))), b1 = norm(add(c1, scale(n, -halfW)));
            float uA = i / (float) segs, uB = (i + 1) / (float) segs;
            float[] p = {
                    b0[0] * R, b0[1] * R, b0[2] * R, b1[0] * R, b1[1] * R, b1[2] * R,
                    a1[0] * R, a1[1] * R, a1[2] * R, a0[0] * R, a0[1] * R, a0[2] * R};
            // fades and warms toward the horizon, like starlight through more air
            quad(b, p, new float[]{uA, 1, uB, 1, uB, 0, uA, 0},
                    lowSky(tintRgb, base, b0[1]), lowSky(tintRgb, base, b1[1]),
                    lowSky(tintRgb, base, a1[1]), lowSky(tintRgb, base, a0[1]));
        }
        end(b, MILKY);

        if (mw.alpha > 1.0f) {
            // a bright galactic core: warm dust glowing along the band around its middle
            float k = mw.alpha - 0.6f;
            BufferBuilder g = begin(GLOW);
            for (int j = 0; j < 7; j++) {
                double ph = Math.PI / 2 + (j - 3) * Math.toRadians(9);
                float[] cd = norm(add(scale(u0, (float) Math.cos(ph)), scale(v0, (float) Math.sin(ph))));
                float fall = (float) Math.exp(-Math.pow((j - 3) / 2.2, 2));
                sprite(g, cd, span(mw.width * (1.1 + 0.9 * fall)), argb(j % 2 == 0 ? 0xFFD9B0 : 0xFF9A7A, clamp01(0.22f * k * fall * a)));
            }
            sprite(g, v0, span(mw.width * 0.9), argb(0xFFF1DC, clamp01(0.35f * k * a)));
            end(g, GLOW);
        }
    }

    /** A colour as seen at height y: dimmer and redder near the horizon. */
    private static int lowSky(int rgb, float alpha, float y) {
        float ext = extinction(y);
        int warm = lerp(0xFFC8A0, 0xFFFFFF, clamp01(ext * 1.6f - 0.3f));
        return argb(mul(rgb, warm), alpha * ext);
    }

    // ======================================================================== nebula
    /** Five soft puffs per cloud, at fixed offsets so the layout is always the same. */
    private static void nebula(SkyScenes.Nebula nb, int i, float rot, float a, float time) {
        BufferBuilder b = begin(NEBULA);
        int rgb = SkyScenes.rgb(nb.color);
        for (int k = 0; k < 5; k++) {
            double oaz = Math.cos(k * 2.4 + i) * 0.45 * nb.size;
            double oel = Math.sin(k * 1.7 + i) * 0.25 * nb.size;
            double psz = nb.size * (0.55 + 0.15 * (k % 3));
            float breathe = 0.75f + 0.25f * (float) Math.sin(time * 0.25 + k + i);
            float[] d = dir(nb.az + rot + oaz, nb.el + oel);
            float[][] bs = basis(d);
            float half = span(psz * 0.5);
            sprite(b, d, bs[0], bs[1], half, half, argb(rgb, clamp01(nb.alpha * breathe * a)),
                    k % 2 == 1 ? new float[]{1, 1, 0, 1, 0, 0, 1, 0} : FULL_UV);
        }
        end(b, NEBULA);
    }

    // ======================================================================== aurora
    private static void aurora(SkyScenes.Aurora au, float rot, float a, float time) {
        float t = time * au.speed;
        final int cols = 96, segs = 6;
        float[][][] grid = new float[cols + 1][segs + 1][];
        for (int i = 0; i <= cols; i++) {
            float u = i / (float) cols;
            double az = au.az + rot + (u - 0.5) * au.span;
            double base = au.base + 4.5 * Math.sin(u * 7.0 + t * 0.21) + 2.0 * Math.sin(u * 17.0 - t * 0.37);
            double h = au.height * (0.62 + 0.38 * wave(u * 9f + t * 0.17f));
            for (int s = 0; s <= segs; s++) {
                float f = s / (float) segs;
                double fold = 2.6 * f * Math.sin(t * 0.55 + u * 23.0 + f * 2.2);
                float[] d = dir(az + fold, base + h * f);
                grid[i][s] = new float[]{d[0] * R, d[1] * R, d[2] * R};
            }
        }
        int[] c = {SkyScenes.rgb(au.colors[0]), SkyScenes.rgb(au.colors[Math.min(1, au.colors.length - 1)]),
                SkyScenes.rgb(au.colors[Math.min(2, au.colors.length - 1)])};
        BufferBuilder b = begin(AURORA);
        for (int i = 0; i < cols; i++) {
            float u0 = i / (float) cols, u1 = (i + 1) / (float) cols;
            float brA = clamp01(brightness(u0, t) * au.alpha * a), brB = clamp01(brightness(u1, t) * au.alpha * a);
            for (int s = 0; s < segs; s++) {
                float f0 = s / (float) segs, f1 = (s + 1) / (float) segs;
                float[] p00 = grid[i][s], p10 = grid[i + 1][s], p11 = grid[i + 1][s + 1], p01 = grid[i][s + 1];
                float[] p = {p00[0], p00[1], p00[2], p10[0], p10[1], p10[2], p11[0], p11[1], p11[2], p01[0], p01[1], p01[2]};
                float[] uv = {u0, 1 - f0, u1, 1 - f0, u1, 1 - f1, u0, 1 - f1};
                quad(b, p, uv, argb(tint(c, f0), brA), argb(tint(c, f0), brB), argb(tint(c, f1), brB), argb(tint(c, f1), brA));
            }
        }
        end(b, AURORA);
    }

    private static float brightness(float u, float time) {
        float ray = wave(u * 31f - time * 0.9f);
        ray = Math.min(1f, 0.45f + 0.75f * ray * ray * ray * 2f);
        return 0.95f * ray * (float) Math.sin(Math.PI * u);
    }

    private static int tint(int[] c, float f) {
        if (f < 0.4f) return lerp(c[0], c[1], f / 0.4f);
        return lerp(c[1], c[2], (f - 0.4f) / 0.6f);
    }

    private static int lerp(int a, int b, float t) {
        t = clamp01(t);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ======================================================================== planets and moons
    private static void planet(SkyScenes.Planet p, float rot, float a) {
        Identifier tex = PLANETS.computeIfAbsent(p.texture == null ? "planet_saturn" : p.texture, SkyRenderer::tex);
        float[] d = dir(p.az + rot, p.el);
        // the planet's disc fills 0.42 of the texture's half-width; the rest is ring
        float half = span(p.size * 0.5) / 0.42f;
        BufferBuilder g = begin(GLOW);
        sprite(g, d, half * 1.6f, argb(SkyScenes.rgb(p.glow), clamp01(0.22f * a)));
        end(g, GLOW);
        BufferBuilder b = begin(tex);
        sprite(b, d, half, argb(0xFFFFFF, clamp01(a)));
        end(b, tex);
    }

    private static void moon(SkyScenes.Moon mo, float rot, float a) {
        float[] d = dir(mo.az + rot, mo.el);
        float half = span(mo.size * 0.5) / 0.9f;          // the moon fills 0.9 of its texture
        BufferBuilder g = begin(GLOW);
        sprite(g, d, half * 4f, argb(SkyScenes.rgb(mo.glow), clamp01(mo.glowAlpha * a)));
        end(g, GLOW);
        BufferBuilder b = begin(MOON);
        sprite(b, d, half, argb(SkyScenes.rgb(mo.tint), clamp01(a)));
        end(b, MOON);
    }

    // ======================================================================== black hole
    private static void blackHole(SkyScenes.BlackHole bh, float rot, float a, float time) {
        int[] col = {SkyScenes.rgb(bh.colors[0]), SkyScenes.rgb(bh.colors[Math.min(1, bh.colors.length - 1)]),
                SkyScenes.rgb(bh.colors[Math.min(2, bh.colors.length - 1)])};
        a = clamp01(a);
        float[] d = dir(bh.az + rot, bh.el);
        float[][] bs = basis(d);
        float[] r = bs[0], u = bs[1];
        float rs = span(bh.size * 0.5);                       // shadow radius
        float spin = (float) (time * 0.35 * bh.spin);
        double tilt = Math.toRadians(bh.tilt);
        // disk plane: "right" and a second axis that points mostly toward the camera, tipped down a little,
        // so we look slightly onto the top of the disk and its near side passes in front of the shadow
        float[] toward = {-d[0], -d[1], -d[2]};
        float[] a2 = add(scale(u, (float) -Math.sin(tilt)), scale(toward, (float) Math.cos(tilt)));

        // 1) halo of light and a darkening ring that swallows the stars around it
        BufferBuilder g = begin(GLOW);
        sprite(g, d, rs * 9f, argb(col[1], 0.30f * a));
        sprite(g, d, rs * 5f, argb(col[0], 0.16f * a));
        sprite(g, d, rs * 3.4f, argb(0x000000, 0.55f * a));
        end(g, GLOW);

        // 2) far half of the disk
        disk(DISK, d, r, a2, rs, spin, false, col[1], a);
        disk(DISK_CORE, d, r, a2, rs, spin, false, col[0], 0.8f * a);

        // 3) light bent over the top of the shadow
        BufferBuilder l = begin(LENS);
        sprite(l, d, r, u, rs * 2.18f, rs * 2.18f, argb(col[0], 0.95f * a), FULL_UV);
        end(l, LENS);

        // 4) shadow, then the thin photon ring at its edge
        BufferBuilder s = begin(SHADOW);
        sprite(s, d, r, u, rs / 0.97f, rs / 0.97f, argb(0x000000, a), FULL_UV);
        end(s, SHADOW);
        BufferBuilder p = begin(PHOTON);
        sprite(p, d, r, u, rs * 1.18f, rs * 1.18f, argb(col[0], 0.9f * a), FULL_UV);
        end(p, PHOTON);

        // 5) near half of the disk, crossing in front of the shadow
        disk(DISK, d, r, a2, rs, spin, true, col[1], a);
        disk(DISK_CORE, d, r, a2, rs, spin, true, col[0], 0.8f * a);
    }

    /**
     * The accretion disk as a ring of quads in its own (tilted) plane, so it can spin by rotating its texture
     * coordinates, and be split into the half behind the shadow and the half in front of it.
     */
    private static void disk(Identifier tex, float[] d, float[] r, float[] a2, float rs, float spin, boolean near, int rgb, float alpha) {
        final int segs = 48;
        float outer = rs * 4.2f, inner = outer * 0.30f;
        float cx = d[0] * R, cy = d[1] * R, cz = d[2] * R;
        BufferBuilder b = begin(tex);
        for (int i = 0; i < segs; i++) {
            double t0 = i * Math.PI * 2 / segs, t1 = (i + 1) * Math.PI * 2 / segs;
            double mid = (t0 + t1) / 2;
            if ((Math.sin(mid) > 0) != near) continue;      // y > 0 along a2 is the side nearer the camera
            float doppler0 = (float) (0.55 + 0.45 * -Math.cos(t0)), doppler1 = (float) (0.55 + 0.45 * -Math.cos(t1));
            float[] q = new float[12];
            float[] uv = new float[8];
            double[][] pts = {{t0, inner}, {t1, inner}, {t1, outer}, {t0, outer}};
            for (int k = 0; k < 4; k++) {
                double th = pts[k][0], rad = pts[k][1];
                float x = (float) (Math.cos(th) * rad), y = (float) (Math.sin(th) * rad);
                q[k * 3] = cx + r[0] * x + a2[0] * y;
                q[k * 3 + 1] = cy + r[1] * x + a2[1] * y;
                q[k * 3 + 2] = cz + r[2] * x + a2[2] * y;
                double ts = th + spin, rt = rad / outer;            // texture rotates with the spin
                uv[k * 2] = (float) (0.5 + 0.5 * Math.cos(ts) * rt);
                uv[k * 2 + 1] = (float) (0.5 + 0.5 * Math.sin(ts) * rt);
            }
            int c0 = argb(rgb, alpha * doppler0), c1 = argb(rgb, alpha * doppler1);
            quad(b, q, uv, c0, c1, c1, c0);
        }
        end(b, tex);
    }

    // ======================================================================== shooting stars
    private static final class Meteor { double az, el, vaz, vel; float age, life; }
    private static final List<Meteor> METEORS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static float spawnTimer;

    private static void shootingStars(SkyScenes.Meteors mt, float rot, float a, float dt) {
        spawnTimer -= dt;
        if (spawnTimer <= 0f && mt.perMinute > 0) {
            Meteor s = new Meteor();
            if (mt.shower) {
                // everything fans out from one radiant point
                double ang = RNG.nextDouble() * Math.PI * 2, off = 4 + RNG.nextDouble() * 24;
                s.az = mt.radiantAz + rot + Math.cos(ang) * off;
                s.el = mt.radiantEl + Math.sin(ang) * off * 0.7;
                double sp = 26 + RNG.nextDouble() * 24;
                s.vaz = Math.cos(ang) * sp;
                s.vel = Math.sin(ang) * sp * 0.7;
            } else {
                s.az = RNG.nextDouble() * 360;
                s.el = 25 + RNG.nextDouble() * 45;
                double ang = Math.toRadians(200 + RNG.nextDouble() * 140), sp = 22 + RNG.nextDouble() * 30;
                s.vaz = Math.cos(ang) * sp;
                s.vel = Math.sin(ang) * sp * 0.6;
            }
            s.life = 0.6f + RNG.nextFloat() * 0.9f;
            METEORS.add(s);
            float perSec = mt.perMinute / 60f;
            spawnTimer = (float) (-Math.log(1.0 - RNG.nextDouble() * 0.999) / perSec);
        }
        METEORS.removeIf(s -> (s.age += dt) > s.life);
        if (METEORS.isEmpty()) return;
        // two passes (the tessellator has one buffer): streaks, then a soft glow on each head
        for (int pass = 0; pass < 2; pass++) {
            Identifier tex = pass == 0 ? STREAK : GLOW;
            BufferBuilder b = begin(tex);
            for (Meteor s : METEORS) {
                float k = s.age / s.life;
                float bright = clamp01((float) Math.sin(Math.PI * Math.min(1f, k * 1.15f)) * a);
                float[] head = dir(s.az + s.vaz * s.age, s.el + s.vel * s.age);
                if (pass == 1) { sprite(b, head, span(0.9), argb(0xCFE0FF, 0.6f * bright)); continue; }
                float ts = Math.max(0f, s.age - 0.3f);
                float[] tail = dir(s.az + s.vaz * ts, s.el + s.vel * ts);
                float[] side = norm(cross(sub(head, tail), head));
                float w = span(0.14);
                float[] p = {
                        tail[0] * R - side[0] * w, tail[1] * R - side[1] * w, tail[2] * R - side[2] * w,
                        head[0] * R - side[0] * w, head[1] * R - side[1] * w, head[2] * R - side[2] * w,
                        head[0] * R + side[0] * w, head[1] * R + side[1] * w, head[2] * R + side[2] * w,
                        tail[0] * R + side[0] * w, tail[1] * R + side[1] * w, tail[2] * R + side[2] * w};
                int c = argb(0xE8F0FF, bright);
                quad(b, p, new float[]{0, 1, 1, 1, 1, 0, 0, 0}, c, c, c, c);
            }
            end(b, tex);
        }
    }

    // ======================================================================== tiny vector maths
    private static float[] add(float[] a, float[] b) { return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    private static float[] sub(float[] a, float[] b) { return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static float[] scale(float[] a, float s) { return new float[]{a[0] * s, a[1] * s, a[2] * s}; }
    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
    private static float[] norm(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-6f ? a : new float[]{a[0] / l, a[1] / l, a[2] / l};
    }
}
