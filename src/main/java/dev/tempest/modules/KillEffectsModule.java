package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Kill Effects: a glowing animated effect where the player you killed died, in your theme colour.
 *
 * Drawn by Tempest itself (no Minecraft particles) with additive blending, so everything glows: soft light
 * blobs, light beams, glowing rings and lightning ribbons with a white-hot core. The animation is computed
 * every frame from the time since the kill, so it is perfectly smooth at any frame rate. Cheap: a few hundred
 * quads for at most 1-2 seconds, at most 3 effects at once. Drawn from WorldRendererEffectsMixin.
 * Kills are detected in AutoGgModule (death of the player you just hit, or a kill message naming you).
 */
public final class KillEffectsModule extends Module {
    private static final String[] EFFECTS = {"Soul Spiral", "Shockwave", "Storm Strike", "Starburst", "Ascension", "Random"};
    private final ModeSetting effect = add(new ModeSetting("effect", "Effect", "Soul Spiral", EFFECTS));
    private final ModeSetting quality = add(new ModeSetting("quality", "Detail", "High", "High", "Medium", "Low"));
    private final BoolSetting sound = add(new BoolSetting("sound", "Sound", true));

    private static final List<Running> RUNNING = new ArrayList<>();
    private static final Map<String, Long> RECENT = new HashMap<>();
    private static final Random RNG = new Random();

    public KillEffectsModule() {
        super("kill_effects", "Kill Effects", "Glowing effects in your theme colour where your opponent dies.", Category.PVP, false);
        add(new ActionSetting("preview", "Preview", "Play", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player == null) return;
            Vec3d look = c.player.getRotationVec(1f);
            Vec3d at = c.player.getEntityPos().add(look.x * 4, 0, look.z * 4);
            start(at, 1.8);
        }));
    }

    private static KillEffectsModule on() {
        KillEffectsModule m = ModuleManager.get(KillEffectsModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** A kill was detected. Same victim twice within 3 s (death packet + chat line) plays once. */
    public static void onKill(Entity victim, String key) {
        KillEffectsModule m = on();
        if (m == null) return;
        long now = System.currentTimeMillis();
        String k = key != null ? key.toLowerCase() : victim != null ? victim.getUuidAsString() : "?";
        Long last = RECENT.get(k);
        if (last != null && now - last < 3000) return;
        RECENT.put(k, now);
        if (RECENT.size() > 64) RECENT.clear();
        MinecraftClient c = MinecraftClient.getInstance();
        if (victim == null || c.player == null || victim.squaredDistanceTo(c.player) > 128 * 128) return;
        start(victim.getEntityPos(), victim.getHeight());
    }

    private static void start(Vec3d pos, double height) {
        KillEffectsModule m = ModuleManager.get(KillEffectsModule.class);
        if (m == null) return;
        synchronized (RUNNING) {
            if (RUNNING.size() >= 3) RUNNING.remove(0);
            int kind = m.effect.index();
            if (kind >= EFFECTS.length - 1) kind = RNG.nextInt(EFFECTS.length - 1);
            RUNNING.add(new Running(kind, pos, Math.max(1.0, height), RNG.nextLong()));
            if (m.sound.get()) playSound(kind);
        }
    }

    private static void playSound(int kind) {
        SoundEvent s;
        float pitch;
        switch (kind) {
            case 1 -> { s = SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST; pitch = 0.7f; }
            case 2 -> { s = SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER; pitch = 1.4f; }
            case 3 -> { s = SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE; pitch = 1.0f; }
            case 4 -> { s = SoundEvents.BLOCK_BEACON_ACTIVATE; pitch = 1.6f; }
            default -> { s = SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME; pitch = 0.8f; }
        }
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.world != null && c.player != null) {
                c.world.playSound(c.player, c.player.getX(), c.player.getY(), c.player.getZ(), s,
                        net.minecraft.sound.SoundCategory.PLAYERS, 0.55f, pitch);
            }
        } catch (Throwable ignored) { }
    }

    /** Client tick: drop finished effects, clear on world change. */
    public static void tick(MinecraftClient c) {
        synchronized (RUNNING) {
            if (c.world == null) { RUNNING.clear(); return; }
            RUNNING.removeIf(Running::finished);
        }
    }

    // ------------------------------------------------------------------ drawing
    /** Called while the world's entities are submitted (camera at the origin of {@code matrices}). */
    public static void render(MatrixStack matrices, OrderedRenderCommandQueue queue) {
        List<Running> list;
        synchronized (RUNNING) {
            if (RUNNING.isEmpty()) return;
            list = new ArrayList<>(RUNNING);
        }
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.gameRenderer == null) return;
        Vec3d cam = c.gameRenderer.getCamera().getCameraPos();
        KillEffectsModule m = ModuleManager.get(KillEffectsModule.class);
        float detail = m == null ? 1f : switch (m.quality.get()) { case "Low" -> 0.45f; case "Medium" -> 0.7f; default -> 1f; };
        Palette pal = Palette.of(Draw.accent() & 0xFFFFFF);
        try {
            queue.submitCustom(matrices, RenderLayers.lightning(), (entry, vc) -> {
                G g = new G(vc, entry);
                for (Running r : list) r.draw(g, cam, pal, detail);
            });
        } catch (Throwable ignored) { }
    }

    /** Three tones from the theme colour: a bright tint, the accent itself and a deeper shade, plus white-hot. */
    private record Palette(int tint, int accent, int deep, int hot) {
        static Palette of(int accent) {
            float[] hsv = Draw.rgbToHsv(accent);
            int tint = Draw.hsvToRgb((hsv[0] + 0.05f) % 1f, hsv[1] * 0.55f, 1f) & 0xFFFFFF;
            int deep = Draw.hsvToRgb((hsv[0] + 0.94f) % 1f, Math.min(1f, hsv[1] * 1.1f + 0.1f), Math.max(0.55f, hsv[2] * 0.8f)) & 0xFFFFFF;
            int hot = Draw.lerpColor(0xFF000000 | tint, 0xFFFFFFFF, 0.7f) & 0xFFFFFF;
            return new Palette(tint, accent, deep, hot);
        }
    }

    /** Small additive-geometry toolkit. Positions are relative to the camera (which sits at the origin). */
    private static final class G {
        final VertexConsumer vc;
        final MatrixStack.Entry e;

        G(VertexConsumer vc, MatrixStack.Entry e) { this.vc = vc; this.e = e; }

        static int argb(int rgb, float a) { return (Math.round(Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF); }

        void v(Vector3f p, int col) { vc.vertex(e, p.x, p.y, p.z).color(col); }

        /** Quad drawn with both windings so it shows from either side. */
        void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int ca, int cb, int cc, int cd) {
            v(a, ca); v(b, cb); v(c, cc); v(d, cd);
            v(d, cd); v(c, cc); v(b, cb); v(a, ca);
        }

        /** Two directions spanning the plane that faces the camera at p. */
        static Vector3f[] facing(Vector3f p) {
            Vector3f side = new Vector3f(p).cross(0, 1, 0);
            if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0);
            side.normalize();
            Vector3f up = new Vector3f(side).cross(p).normalize();
            return new Vector3f[]{side, up};
        }

        /**
         * A soft light facing the camera. Bright centre, quick fall-off, long faint tail (two rings), which reads as
         * real glow rather than a flat disc.
         */
        void glow(Vector3f p, float r, int rgb, float a, int segs) {
            if (a <= 0.003f || r <= 0f) return;
            Vector3f[] f = facing(p);
            int c0 = argb(rgb, a), c1 = argb(rgb, a * 0.38f), c2 = argb(rgb, 0f);
            float r1 = r * 0.32f;
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                float x0 = (float) Math.cos(a0), y0 = (float) Math.sin(a0), x1 = (float) Math.cos(a1), y1 = (float) Math.sin(a1);
                Vector3f m0 = at(p, f, x0 * r1, y0 * r1), m1 = at(p, f, x1 * r1, y1 * r1);
                Vector3f o0 = at(p, f, x0 * r, y0 * r), o1 = at(p, f, x1 * r, y1 * r);
                quad(p, p, m1, m0, c0, c0, c1, c1);
                quad(m0, m1, o1, o0, c1, c1, c2, c2);
            }
        }

        private static Vector3f at(Vector3f p, Vector3f[] f, float x, float y) {
            return new Vector3f(p).add(f[0].x * x + f[1].x * y, f[0].y * x + f[1].y * y, f[0].z * x + f[1].z * y);
        }

        /** A pool of light lying flat on the ground (same soft fall-off as {@link #glow}). */
        void disc(Vector3f c, float r, int rgb, float a, int segs) {
            if (a <= 0.003f || r <= 0f) return;
            int c0 = argb(rgb, a), c1 = argb(rgb, a * 0.4f), c2 = argb(rgb, 0f);
            float r1 = r * 0.4f;
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                float x0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0), x1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
                Vector3f m0 = new Vector3f(c).add(x0 * r1, 0, z0 * r1), m1 = new Vector3f(c).add(x1 * r1, 0, z1 * r1);
                Vector3f o0 = new Vector3f(c).add(x0 * r, 0, z0 * r), o1 = new Vector3f(c).add(x1 * r, 0, z1 * r);
                quad(c, c, m1, m0, c0, c0, c1, c1);
                quad(m0, m1, o1, o0, c1, c1, c2, c2);
            }
        }

        /** A light ribbon from a to b facing the camera: glowing centre line, soft edges. */
        void ribbon(Vector3f a, Vector3f b, float width, int rgb, float alphaA, float alphaB) {
            Vector3f dir = new Vector3f(b).sub(a);
            Vector3f side = new Vector3f(dir).cross(a);
            if (side.lengthSquared() < 1e-8f) return;
            side.normalize().mul(width * 0.5f);
            int ca = argb(rgb, alphaA), cb = argb(rgb, alphaB), z = argb(rgb, 0f);
            Vector3f al = new Vector3f(a).sub(side), ar = new Vector3f(a).add(side);
            Vector3f bl = new Vector3f(b).sub(side), br = new Vector3f(b).add(side);
            quad(al, a, b, bl, z, ca, cb, z);
            quad(a, ar, br, b, ca, z, z, cb);
        }

        /** Lens-flare star: long thin horizontal streak, shorter vertical one, both facing the camera. */
        void flare(Vector3f p, float len, float width, int rgb, float a) {
            if (a <= 0.003f) return;
            Vector3f[] f = facing(p);
            for (int k = 0; k < 2; k++) {
                Vector3f d = new Vector3f(f[k]).mul(k == 0 ? len : len * 0.55f);
                ribbon(p, new Vector3f(p).add(d), width, rgb, a, 0f);
                ribbon(p, new Vector3f(p).sub(d), width, rgb, a, 0f);
            }
        }

        /** A glowing horizontal ring (annulus) around centre c: bright in the middle of its width. */
        void ring(Vector3f c, float radius, float width, int rgb, float a, int segs) {
            if (a <= 0.003f || radius <= 0f) return;
            int mid = argb(rgb, a), z = argb(rgb, 0f);
            float ri = Math.max(0f, radius - width / 2), ro = radius + width / 2;
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                Vector3f i0 = new Vector3f(c.x + c0 * ri, c.y, c.z + s0 * ri), i1 = new Vector3f(c.x + c1 * ri, c.y, c.z + s1 * ri);
                Vector3f m0 = new Vector3f(c.x + c0 * radius, c.y, c.z + s0 * radius), m1 = new Vector3f(c.x + c1 * radius, c.y, c.z + s1 * radius);
                Vector3f o0 = new Vector3f(c.x + c0 * ro, c.y, c.z + s0 * ro), o1 = new Vector3f(c.x + c1 * ro, c.y, c.z + s1 * ro);
                quad(i0, i1, m1, m0, z, z, mid, mid);
                quad(m0, m1, o1, o0, mid, mid, z, z);
            }
        }

        /** A vertical beam of light standing on base, facing the camera, fading towards the top and the sides. */
        void beam(Vector3f base, float height, float width, int rgb, float a) {
            if (a <= 0.003f) return;
            Vector3f side = new Vector3f(-base.z, 0, base.x);
            if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0);
            side.normalize().mul(width / 2);
            Vector3f top = new Vector3f(base).add(0, height, 0);
            int c = argb(rgb, a), ct = argb(rgb, 0f), z = argb(rgb, 0f);
            quad(new Vector3f(base).sub(side), base, top, new Vector3f(top).sub(side), z, c, ct, z);
            quad(base, new Vector3f(base).add(side), new Vector3f(top).add(side), top, c, z, z, ct);
        }

        /** A wedge of light from a point (god ray), facing the camera as best it can. */
        void shaft(Vector3f from, Vector3f dir, float len, float width, int rgb, float a) {
            if (a <= 0.003f) return;
            Vector3f to = new Vector3f(from).add(new Vector3f(dir).mul(len));
            Vector3f side = new Vector3f(dir).cross(from);
            if (side.lengthSquared() < 1e-8f) return;
            side.normalize().mul(width * 0.5f);
            int c = argb(rgb, a), z = argb(rgb, 0f);
            quad(from, from, new Vector3f(to).add(side), to, c, c, z, z);
            quad(from, from, to, new Vector3f(to).sub(side), c, c, z, z);
        }
    }

    /**
     * Sparks with real motion: launched with drag and gravity (solved exactly, so they look the same at any frame
     * rate), drawn as a motion streak with a bright head, cooling from white-hot to the theme colour, and they come
     * to rest on the ground.
     */
    private static final class Embers {
        final float[] vx, vy, vz, life, size;
        final int[] col;
        final float delay, gravity, drag;
        final Vector3f origin;   // relative to the feet

        Embers(Random rnd, int n, Vector3f origin, float speed, float up, float spread, float gravity, float drag,
               float delay, float lifeMin, float lifeMax, int[] colours) {
            this.origin = origin;
            this.delay = delay;
            this.gravity = gravity;
            this.drag = drag;
            vx = new float[n]; vy = new float[n]; vz = new float[n]; life = new float[n]; size = new float[n]; col = new int[n];
            for (int i = 0; i < n; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                float sp = speed * (0.45f + rnd.nextFloat() * 0.75f);
                float h = spread * (0.4f + rnd.nextFloat() * 0.6f);
                vx[i] = (float) Math.cos(ang) * sp * h;
                vz[i] = (float) Math.sin(ang) * sp * h;
                vy[i] = sp * (up + (rnd.nextFloat() - 0.3f) * 0.6f);
                life[i] = lifeMin + rnd.nextFloat() * (lifeMax - lifeMin);
                size[i] = 0.05f + rnd.nextFloat() * 0.06f;
                col[i] = colours[rnd.nextInt(colours.length)];
            }
        }

        /** Position at time t after launch (relative to the feet); y never goes below the ground. */
        private Vector3f pos(int i, float t) {
            float e = (float) Math.exp(-drag * t), f = (1f - e) / drag;
            float y = (vy[i] + gravity / drag) * f - gravity * t / drag;
            Vector3f p = new Vector3f(origin).add(vx[i] * f, y, vz[i] * f);
            if (p.y < 0.03f) p.y = 0.03f;
            return p;
        }

        void draw(G g, Vector3f feet, float s, float out, float detail) {
            float t = s - delay;
            if (t <= 0f) return;
            int n = Math.max(4, Math.round(vx.length * detail));
            for (int i = 0; i < n; i++) {
                if (t > life[i]) continue;
                float k = t / life[i];
                float a = (1f - k * k) * out;
                Vector3f head = pos(i, t).add(feet), tail = pos(i, Math.max(0f, t - 0.05f)).add(feet);
                int c = Draw.lerpColor(0xFFFFFFFF, 0xFF000000 | col[i], Math.min(1f, k * 2.2f)) & 0xFFFFFF;
                g.ribbon(tail, head, size[i] * 0.9f, c, 0f, 0.9f * a);
                g.glow(head, size[i] * 2.2f, col[i], 0.55f * a, 6);
                g.glow(head, size[i] * 0.7f, 0xFFFFFF, 0.9f * a, 5);
            }
        }
    }

    // ------------------------------------------------------------------ the effects
    private static final class Running {
        final int kind;
        final Vec3d at;
        final float h;
        final long start = System.nanoTime();
        final Random rnd;
        final long seed;
        List<Vector3f> bolt;          // Storm Strike path (relative to the feet); null entries split branches
        int boltStrike = -1;
        Vector3f[] dirs;              // Starburst directions
        Embers embers, embers2;

        Running(int kind, Vec3d at, double h, long seed) {
            this.kind = kind;
            this.at = at;
            this.h = (float) h;
            this.seed = seed;
            this.rnd = new Random(seed);
        }

        float seconds() { return (System.nanoTime() - start) / 1e9f; }

        float duration() {
            return switch (kind) { case 1 -> 2.0f; case 2 -> 2.0f; case 3 -> 2.2f; case 4 -> 2.8f; default -> 2.4f; };
        }

        boolean finished() { return seconds() > duration(); }

        private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

        private static float easeOut(float t) { t = clamp(t); return 1f - (1f - t) * (1f - t) * (1f - t); }

        private static float easeInOut(float t) { t = clamp(t); return t * t * (3f - 2f * t); }

        /** 0 -> 1 -> 0 bump between a and b. */
        private static float pulse(float s, float a, float b) {
            if (s <= a || s >= b) return 0f;
            return (float) Math.sin(Math.PI * (s - a) / (b - a));
        }

        void draw(G g, Vec3d cam, Palette p, float detail) {
            float s = seconds();
            if (s > duration()) return;
            Vector3f o = new Vector3f((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z));
            float out = clamp((duration() - s) / 0.6f);              // everything fades out at the end
            int segs = detail >= 1f ? 14 : detail >= 0.7f ? 10 : 8;
            // shared grounding: a pool of light under the body that blooms and fades
            Vector3f ground = new Vector3f(o).add(0, 0.04f, 0);
            g.disc(ground, 1.4f + 1.2f * easeOut(s / 0.5f), p.accent(), 0.42f * clamp(1f - s / 1.4f), segs + 6);
            g.disc(ground, 0.7f, p.hot(), 0.35f * clamp(1f - s / 0.5f), segs);
            switch (kind) {
                case 1 -> shockwave(g, o, s, out, p, segs, detail);
                case 2 -> storm(g, o, s, out, p, segs, detail);
                case 3 -> starburst(g, o, s, out, p, segs, detail);
                case 4 -> ascension(g, o, s, out, p, segs, detail);
                default -> spiral(g, o, s, out, p, segs, detail);
            }
        }

        /** Two glowing strands wind up around the body, a soul wisp rises out of it and bursts into a ring and sparks. */
        private void spiral(G g, Vector3f o, float s, float out, Palette p, int segs, float detail) {
            if (embers == null) embers = new Embers(rnd, 34, new Vector3f(0, h + 0.9f, 0), 4.2f, 0.5f, 1f, 7f, 1.8f,
                    1.15f, 0.8f, 1.3f, new int[]{p.tint(), p.accent(), p.deep()});
            float k = easeInOut(s / 1.1f);
            int n = Math.max(10, Math.round(36 * detail));
            for (int strand = 0; strand < 2; strand++) {
                Vector3f prev = null;
                for (int j = 0; j <= n; j++) {
                    float u = j / (float) n;
                    if (u > k) break;
                    float y = 0.05f + u * (h + 0.8f);
                    float rad = 0.9f - 0.55f * u;
                    double ang = u * Math.PI * 5 + strand * Math.PI + s * 2.4;
                    float trail = clamp(1f - (k - u) * 1.4f) * out * clamp(1f - (s - 1.3f) / 0.5f);
                    Vector3f q = new Vector3f(o).add((float) Math.cos(ang) * rad, y, (float) Math.sin(ang) * rad);
                    if (prev != null) {
                        int col = strand == 0 ? p.accent() : p.deep();
                        g.ribbon(prev, q, 0.7f, col, 0.22f * trail, 0.22f * trail);      // haze
                        g.ribbon(prev, q, 0.26f, p.tint(), 0.55f * trail, 0.55f * trail);
                        g.ribbon(prev, q, 0.08f, 0xFFFFFF, 0.95f * trail, 0.95f * trail); // core
                        if (j % 3 == 0) g.glow(q, 0.22f, col, 0.35f * trail, 8);
                    }
                    prev = q;
                }
                if (prev != null && k < 1f) {                                            // bright comet head
                    g.glow(prev, 0.6f, p.tint(), 0.75f * out, segs);
                    g.glow(prev, 0.16f, 0xFFFFFF, out, 8);
                }
            }
            // opening: a burst of light at the feet as the strands start
            Vector3f feet = new Vector3f(o).add(0, 0.1f, 0);
            g.glow(new Vector3f(o).add(0, 0.5f, 0), 1.8f * easeOut(s / 0.15f), p.tint(), 0.7f * clamp(1f - s / 0.45f), segs + 4);
            g.ring(feet, 0.3f + 1.9f * easeOut(s / 0.55f), 0.3f, p.accent(), 0.85f * clamp(1f - s / 0.7f), 48);
            g.ring(feet, 0.2f + 1.2f * easeOut(s / 0.45f), 0.1f, p.hot(), 0.8f * clamp(1f - s / 0.5f), 40);
            // the soul: a soft wisp rising through the body
            float soul = pulse(s, 0.15f, 1.25f);
            if (soul > 0f) {
                float rise = easeInOut((s - 0.15f) / 1.1f);
                Vector3f c = new Vector3f(o).add((float) Math.sin(s * 9) * 0.06f, 0.4f + rise * (h + 0.5f), 0);
                for (int i = 0; i < 5; i++) {
                    Vector3f q = new Vector3f(c).sub(0, i * 0.16f, 0).add((float) Math.sin(s * 7 + i) * 0.05f * i, 0, 0);
                    g.glow(q, 0.6f - i * 0.08f, i == 0 ? p.tint() : p.accent(), 0.8f * soul * (1f - i * 0.17f), segs);
                }
                g.glow(c, 0.22f, 0xFFFFFF, soul, 8);
            }
            if (s > 1.1f) {
                float b = s - 1.1f;
                Vector3f top = new Vector3f(o).add(0, h + 0.9f, 0);
                g.glow(top, 1.6f * easeOut(b / 0.25f), p.tint(), 0.75f * clamp(1f - b / 0.5f), segs + 4);
                g.flare(top, 2.2f * easeOut(b / 0.15f), 0.09f, p.hot(), 0.9f * clamp(1f - b / 0.45f));
                g.ring(top, 0.3f + 2.0f * easeOut(b / 0.7f), 0.3f, p.accent(), 0.9f * out, 48);
                g.ring(top, 0.2f + 1.3f * easeOut(b / 0.5f), 0.12f, p.hot(), 0.8f * clamp(1f - b / 0.6f), 40);
            }
            embers.draw(g, o, s, out, detail);
        }

        /** A white-hot pillar, a shock dome, three rings racing out and debris sparks skidding across the ground. */
        private void shockwave(G g, Vector3f o, float s, float out, Palette p, int segs, float detail) {
            if (embers == null) embers = new Embers(rnd, 40, new Vector3f(0, 0.3f, 0), 7.5f, 0.35f, 1.3f, 14f, 2.6f,
                    0.02f, 0.6f, 1.2f, new int[]{p.hot(), p.tint(), p.accent()});
            float pillar = clamp(1f - s / 0.5f);
            g.beam(o, 4.2f, 1.3f, p.accent(), 0.7f * pillar);
            g.beam(o, 3.6f, 0.45f, p.tint(), 0.9f * pillar);
            g.beam(o, 3.0f, 0.12f, 0xFFFFFF, pillar);
            Vector3f chest = new Vector3f(o).add(0, 0.7f, 0);
            g.glow(chest, 2.2f * easeOut(s / 0.2f), p.accent(), 0.65f * clamp(1f - s / 0.6f), segs + 4);
            g.flare(chest, 3.0f * easeOut(s / 0.12f), 0.1f, p.hot(), clamp(1f - s / 0.4f));
            // shock dome: stacked latitude rings of a growing hemisphere
            float dome = easeOut(s / 0.7f), domeA = 0.55f * clamp(1f - s / 0.8f);
            int lat = Math.max(3, Math.round(6 * detail));
            for (int i = 0; i < lat; i++) {
                double phi = (i + 0.5) / lat * Math.PI / 2;
                float r = (0.4f + 3.0f * dome) * (float) Math.cos(phi);
                float y = (0.4f + 3.0f * dome) * (float) Math.sin(phi);
                g.ring(new Vector3f(o).add(0, y, 0), r, 0.12f, i % 2 == 0 ? p.tint() : p.accent(), domeA * (1f - i / (float) lat * 0.6f), 40);
            }
            Vector3f ground = new Vector3f(o).add(0, 0.08f, 0);
            g.ring(ground, 0.3f + 5.0f * easeOut(s / 1.2f), 0.7f, p.accent(), 0.95f * out, 64);
            if (s > 0.12f) g.ring(ground, 0.3f + 3.6f * easeOut((s - 0.12f) / 1.1f), 0.32f, p.hot(), 0.9f * out, 56);
            if (s > 0.28f) g.ring(ground, 0.3f + 2.4f * easeOut((s - 0.28f) / 1.0f), 0.22f, p.deep(), 0.8f * out, 48);
            embers.draw(g, o, s, out, detail);
        }

        /** A jagged bolt that strikes twice (re-forming in between), electric arcs crawling over the ground, sparks. */
        private void storm(G g, Vector3f o, float s, float out, Palette p, int segs, float detail) {
            if (embers == null) embers = new Embers(rnd, 36, new Vector3f(0, 0.15f, 0), 6f, 0.9f, 1f, 12f, 2.2f,
                    0.05f, 0.5f, 1.1f, new int[]{p.hot(), p.tint(), p.accent()});
            int strikeNo = s < 0.32f ? 0 : s < 0.62f ? 1 : -1;
            if (strikeNo >= 0 && strikeNo != boltStrike) { bolt = buildBolt(new Random(seed + strikeNo * 31L)); boltStrike = strikeNo; }
            float local = strikeNo == 0 ? s : s - 0.32f;
            float strike = strikeNo < 0 ? 0f : (0.6f + 0.4f * (float) Math.abs(Math.sin(s * 53))) * clamp(1f - (local - 0.16f) / 0.14f);
            if (strike > 0f && bolt != null) {
                for (int i = 0; i + 1 < bolt.size(); i++) {
                    Vector3f a = bolt.get(i), b = bolt.get(i + 1);
                    if (a == null || b == null) continue;
                    Vector3f pa = new Vector3f(o).add(a), pb = new Vector3f(o).add(b);
                    g.ribbon(pa, pb, 1.4f, p.deep(), 0.22f * strike, 0.22f * strike);    // wide haze
                    g.ribbon(pa, pb, 0.5f, p.accent(), 0.5f * strike, 0.5f * strike);
                    g.ribbon(pa, pb, 0.16f, p.tint(), 0.9f * strike, 0.9f * strike);
                    g.ribbon(pa, pb, 0.05f, 0xFFFFFF, strike, strike);                 // white-hot core
                    g.glow(pb, 0.75f, p.deep(), 0.2f * strike, 8);                        // joints fill the bends
                    g.glow(pb, 0.3f, p.tint(), 0.45f * strike, 8);
                    g.glow(pb, 0.07f, 0xFFFFFF, 0.9f * strike, 5);
                }
                Vector3f hit = new Vector3f(o).add(0, 0.3f, 0);
                g.flare(hit, 2.6f, 0.1f, p.hot(), strike);
            }
            Vector3f chest = new Vector3f(o).add(0, 0.6f, 0);
            g.glow(chest, 2.8f * easeOut(s / 0.12f), p.tint(), 0.8f * clamp(1f - s / 0.45f), segs + 4);
            if (s > 0.32f) g.glow(chest, 2.2f * easeOut((s - 0.32f) / 0.1f), p.accent(), 0.6f * clamp(1f - (s - 0.32f) / 0.4f), segs + 4);
            // electric arcs crawling outward over the ground
            int arcs = Math.max(3, Math.round(7 * detail));
            float arcA = clamp(1f - (s - 0.2f) / 0.9f) * out;
            if (arcA > 0f) {
                Random r = new Random(seed ^ ((long) (s * 18) * 7919L));
                for (int i = 0; i < arcs; i++) {
                    double ang = (i + 0.5) / arcs * Math.PI * 2 + seed % 7;
                    float len = 0.6f + 2.4f * easeOut(s / 0.7f);
                    Vector3f prev = new Vector3f(o).add(0, 0.06f, 0);
                    int pieces = 5;
                    for (int j = 1; j <= pieces; j++) {
                        float d = len * j / pieces;
                        double a2 = ang + (r.nextFloat() - 0.5f) * 0.5f;
                        Vector3f q = new Vector3f(o).add((float) Math.cos(a2) * d, 0.06f + r.nextFloat() * 0.12f, (float) Math.sin(a2) * d);
                        g.ribbon(prev, q, 0.18f, p.accent(), 0.5f * arcA, 0.3f * arcA);
                        g.ribbon(prev, q, 0.05f, p.hot(), 0.9f * arcA, 0.5f * arcA);
                        prev = q;
                    }
                }
            }
            Vector3f ground = new Vector3f(o).add(0, 0.1f, 0);
            g.ring(ground, 0.4f + 3.2f * easeOut(s / 0.9f), 0.5f, p.accent(), 0.85f * out, 56);
            embers.draw(g, o, s, out, detail);
        }

        private List<Vector3f> buildBolt(Random rnd) {
            List<Vector3f> path = new ArrayList<>();
            Vector3f top = new Vector3f((rnd.nextFloat() - 0.5f) * 2.5f, 16, (rnd.nextFloat() - 0.5f) * 2.5f);
            Vector3f bottom = new Vector3f(0, 0.1f, 0);
            int segs = 20;
            List<Vector3f> forks = new ArrayList<>();
            for (int i = 0; i <= segs; i++) {
                float k = i / (float) segs;
                Vector3f base = new Vector3f(top).lerp(bottom, k);
                float jag = i == 0 || i == segs ? 0 : 0.7f * (1 - k * 0.55f);
                Vector3f q = base.add((rnd.nextFloat() - 0.5f) * 2 * jag, 0, (rnd.nextFloat() - 0.5f) * 2 * jag);
                path.add(q);
                if (i == segs / 4 || i == segs / 2 || i == (segs * 2) / 3) forks.add(new Vector3f(q));
            }
            for (Vector3f from : forks) {
                path.add(null);
                Vector3f q = from;
                path.add(new Vector3f(q));
                float dx = rnd.nextBoolean() ? 1 : -1, dz = rnd.nextFloat() - 0.5f;
                int steps = 3 + rnd.nextInt(4);
                for (int i = 0; i < steps; i++) {
                    q = new Vector3f(q).add(dx * (0.3f + rnd.nextFloat() * 0.35f), -0.6f - rnd.nextFloat() * 0.5f, dz * 0.6f + (rnd.nextFloat() - 0.5f) * 0.4f);
                    path.add(q);
                }
            }
            return path;
        }

        /** A burst of glowing streaks with a lens flare, twinkling sparkles, then glitter drifting down. */
        private void starburst(G g, Vector3f o, float s, float out, Palette p, int segs, float detail) {
            if (embers == null) embers = new Embers(rnd, 46, new Vector3f(0, h * 0.6f, 0), 3.2f, 0.7f, 1.2f, 3.2f, 1.6f,
                    0.25f, 1.2f, 1.9f, new int[]{p.tint(), p.accent(), p.deep(), p.hot()});
            int count = Math.max(16, Math.round(48 * detail));
            if (dirs == null || dirs.length != count) {
                dirs = new Vector3f[count];
                double golden = Math.PI * (3 - Math.sqrt(5));
                for (int i = 0; i < count; i++) {
                    float y = 1 - (i + 0.5f) * 2f / count, r = (float) Math.sqrt(1 - y * y);
                    double a = i * golden;
                    dirs[i] = new Vector3f((float) Math.cos(a) * r, y, (float) Math.sin(a) * r);
                }
            }
            Vector3f center = new Vector3f(o).add(0, h * 0.6f, 0);
            float k = easeOut(s / 0.9f);
            float r1 = 0.2f + 3.4f * k, r0 = r1 * (0.35f + 0.4f * k);
            float drop = k * k * 0.6f;
            float streakA = clamp(1f - (s - 0.5f) / 0.6f) * out;
            for (int i = 0; i < dirs.length; i++) {
                Vector3f d = dirs[i];
                Vector3f head = new Vector3f(center).add(new Vector3f(d).mul(r1)).sub(0, drop, 0);
                Vector3f tail = new Vector3f(center).add(new Vector3f(d).mul(r0)).sub(0, drop * 0.5f, 0);
                int col = i % 3 == 0 ? p.tint() : i % 3 == 1 ? p.accent() : p.deep();
                g.ribbon(tail, head, 0.2f, col, 0f, 0.55f * streakA);
                g.ribbon(tail, head, 0.06f, p.hot(), 0f, 0.95f * streakA);
                float tw = 0.6f + 0.4f * (float) Math.sin(s * 30 + i * 1.7);
                g.glow(head, 0.16f, col, 0.7f * streakA * tw, 6);
            }
            g.glow(center, 2.0f * (1f - k) + 0.3f, p.tint(), 0.85f * clamp(1f - s / 0.55f), segs + 4);
            g.glow(center, 0.6f, 0xFFFFFF, clamp(1f - s / 0.3f), segs);
            g.flare(center, 3.4f * easeOut(s / 0.1f), 0.1f, p.hot(), clamp(1f - s / 0.5f));
            g.ring(center, 0.3f + 2.6f * easeOut(s / 0.6f), 0.18f, p.tint(), 0.8f * clamp(1f - s / 0.7f), 48);
            embers.draw(g, o, s, out, detail);
        }

        /** A column of light with turning god rays, a golden halo rising, spiralling sparks and feathers drifting down. */
        private void ascension(G g, Vector3f o, float s, float out, Palette p, int segs, float detail) {
            if (embers == null) embers = new Embers(rnd, 24, new Vector3f(0, h + 1.2f, 0), 1.6f, 0.4f, 1.2f, 1.1f, 1.4f,
                    0.9f, 1.4f, 1.9f, new int[]{p.hot(), 0xFFF1C27D, p.tint()});
            float grow = easeOut(s / 0.5f);
            float rise = s * 1.2f;
            g.beam(o, (6.5f + rise) * grow, 1.6f, p.accent(), 0.45f * out);
            g.beam(o, (5.5f + rise) * grow, 0.6f, p.tint(), 0.65f * out);
            g.beam(o, (4.5f + rise) * grow, 0.14f, 0xFFFFFF, 0.85f * out);
            // god rays turning slowly around the chest
            Vector3f chest = new Vector3f(o).add(0, h * 0.7f, 0);
            int rays = Math.max(4, Math.round(9 * detail));
            float rayA = 0.35f * out * clamp(s / 0.3f);
            for (int i = 0; i < rays; i++) {
                double a = i * Math.PI * 2 / rays + s * 0.6;
                Vector3f dir = new Vector3f((float) Math.cos(a), 0.25f + 0.2f * (float) Math.sin(s * 2 + i), (float) Math.sin(a)).normalize();
                g.shaft(chest, dir, 2.2f + 0.4f * (float) Math.sin(s * 3 + i), 0.7f, i % 2 == 0 ? p.tint() : p.accent(), rayA);
            }
            Vector3f halo = new Vector3f(o).add(0, h + 0.35f + rise * 0.7f, 0);
            g.ring(halo, 0.48f, 0.2f, 0xF5C26B, 0.95f * out, 40);
            g.ring(halo, 0.48f, 0.06f, 0xFFF6DC, out, 40);
            g.glow(halo, 0.9f, 0xF5C26B, 0.3f * out, segs);
            int n = Math.max(6, Math.round(18 * detail));
            for (int i = 0; i < n; i++) {
                float u = (i / (float) n + s * 0.35f) % 1f;
                double ang = i * 2.4 + s * 2.2;
                float y = u * (h + 3.2f);
                float a = (float) Math.sin(Math.PI * u) * out;
                float rad = 0.55f + 0.15f * (float) Math.sin(s * 3 + i);
                Vector3f q = new Vector3f(o).add((float) Math.cos(ang) * rad, y, (float) Math.sin(ang) * rad);
                g.glow(q, 0.16f, i % 2 == 0 ? p.accent() : p.tint(), 0.7f * a, segs);
                g.glow(q, 0.05f, 0xFFFFFF, 0.9f * a, 5);
            }
            // feathers: soft light flakes swaying down from the halo
            int feathers = Math.max(4, Math.round(10 * detail));
            for (int i = 0; i < feathers; i++) {
                float t0 = 0.5f + i * 0.12f, t = s - t0;
                if (t <= 0f) continue;
                float life = 1.8f;
                if (t > life) continue;
                float a = (float) Math.sin(Math.PI * t / life) * out;
                double ang = i * 2.1;
                float sway = (float) Math.sin(t * 3.2 + i) * 0.35f;
                Vector3f q = new Vector3f(halo).add((float) Math.cos(ang) * (0.6f + t * 0.35f) + sway, -t * 0.9f, (float) Math.sin(ang) * (0.6f + t * 0.35f));
                Vector3f q2 = new Vector3f(q).add(0.12f * (float) Math.cos(t * 4 + i), 0.18f, 0.12f * (float) Math.sin(t * 4 + i));
                g.ribbon(q, q2, 0.12f, 0xFFF1D8, 0.8f * a, 0.15f * a);
                g.glow(q, 0.12f, 0xF5C26B, 0.4f * a, 6);
            }
            if (s > 1.9f) {
                float b = s - 1.9f;
                g.glow(halo, 2.0f * easeOut(b / 0.35f), p.tint(), 0.6f * clamp(1f - b / 0.6f), segs + 4);
                g.flare(halo, 2.4f * easeOut(b / 0.15f), 0.08f, 0xFFF6DC, 0.9f * clamp(1f - b / 0.5f));
            }
            embers.draw(g, o, s, out, detail);
        }
    }
}
