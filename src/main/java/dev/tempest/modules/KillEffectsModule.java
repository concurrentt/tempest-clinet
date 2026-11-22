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
        int accent = Draw.accent() & 0xFFFFFF;
        int light = Draw.lerpColor(0xFF000000 | accent, 0xFFFFFFFF, 0.55f) & 0xFFFFFF;
        try {
            queue.submitCustom(matrices, RenderLayers.lightning(), (entry, vc) -> {
                G g = new G(vc, entry);
                for (Running r : list) r.draw(g, cam, accent, light, detail);
            });
        } catch (Throwable ignored) { }
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

        /** A soft round light facing the camera: bright in the middle, fading to nothing at radius r. */
        void glow(Vector3f p, float r, int rgb, float a, int segs) {
            if (a <= 0.003f || r <= 0f) return;
            Vector3f side = new Vector3f(p).cross(0, 1, 0);
            if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0);
            side.normalize();
            Vector3f up = new Vector3f(side).cross(p).normalize();
            int center = argb(rgb, a), edge = argb(rgb, 0f);
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                Vector3f e0 = new Vector3f(p).add(new Vector3f(side).mul((float) Math.cos(a0) * r)).add(new Vector3f(up).mul((float) Math.sin(a0) * r));
                Vector3f e1 = new Vector3f(p).add(new Vector3f(side).mul((float) Math.cos(a1) * r)).add(new Vector3f(up).mul((float) Math.sin(a1) * r));
                quad(p, p, e1, e0, center, center, edge, edge);
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
    }

    // ------------------------------------------------------------------ the effects
    private static final class Running {
        final int kind;
        final Vec3d at;
        final float h;
        final long start = System.nanoTime();
        final Random rnd;
        List<Vector3f> bolt;          // Storm Strike path (relative to the feet); null entries split branches
        Vector3f[] dirs;              // Starburst directions

        Running(int kind, Vec3d at, double h, long seed) {
            this.kind = kind;
            this.at = at;
            this.h = (float) h;
            this.rnd = new Random(seed);
        }

        float seconds() { return (System.nanoTime() - start) / 1e9f; }

        float duration() {
            return switch (kind) { case 1 -> 1.4f; case 2 -> 1.2f; case 3 -> 1.3f; case 4 -> 2.2f; default -> 1.8f; };
        }

        boolean finished() { return seconds() > duration(); }

        private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

        private static float easeOut(float t) { t = clamp(t); return 1f - (1f - t) * (1f - t) * (1f - t); }

        void draw(G g, Vec3d cam, int accent, int light, float detail) {
            float s = seconds();
            if (s > duration()) return;
            Vector3f o = new Vector3f((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z));
            float out = clamp((duration() - s) / 0.45f);              // everything fades out at the end
            int segs = detail >= 1f ? 12 : detail >= 0.7f ? 9 : 7;
            switch (kind) {
                case 1 -> shockwave(g, o, s, out, accent, light, segs);
                case 2 -> storm(g, o, s, out, accent, light, segs, detail);
                case 3 -> starburst(g, o, s, out, accent, light, segs, detail);
                case 4 -> ascension(g, o, s, out, accent, light, segs, detail);
                default -> spiral(g, o, s, out, accent, light, segs, detail);
            }
        }

        /** Two glowing strands wind up around the body, then a ring of light bursts at the top. */
        private void spiral(G g, Vector3f o, float s, float out, int accent, int light, int segs, float detail) {
            float k = clamp(s / 1.0f);
            int n = Math.round(30 * detail);
            for (int strand = 0; strand < 2; strand++) {
                for (int j = 0; j <= n; j++) {
                    float u = j / (float) n;
                    if (u > k) break;
                    float y = 0.05f + u * (h + 0.8f);
                    float rad = 0.95f - 0.6f * u;
                    double ang = u * Math.PI * 5 + strand * Math.PI + s * 2.2;
                    float trail = clamp(1f - (k - u) * 1.6f);
                    Vector3f p = new Vector3f(o).add((float) Math.cos(ang) * rad, y, (float) Math.sin(ang) * rad);
                    g.glow(p, 0.16f, strand == 0 ? accent : light, 0.55f * trail * out, segs);
                    g.glow(p, 0.05f, 0xFFFFFF, 0.9f * trail * out, 6);
                }
            }
            if (s > 1.0f) {
                float b = s - 1.0f;
                Vector3f top = new Vector3f(o).add(0, h + 0.85f, 0);
                g.glow(top, 1.2f * easeOut(b / 0.3f), light, 0.7f * clamp(1f - b / 0.5f), 16);
                g.ring(top, 0.3f + 1.6f * easeOut(b / 0.6f), 0.25f, accent, 0.9f * out, 40);
            }
        }

        /** A white-hot pillar, then two glowing rings race out across the ground. */
        private void shockwave(G g, Vector3f o, float s, float out, int accent, int light, int segs) {
            float pillar = clamp(1f - s / 0.45f);
            g.beam(o, 3.2f, 0.9f, light, 0.9f * pillar);
            g.beam(o, 2.6f, 0.25f, 0xFFFFFF, pillar);
            Vector3f ground = new Vector3f(o).add(0, 0.08f, 0);
            g.glow(new Vector3f(o).add(0, 0.6f, 0), 1.6f * easeOut(s / 0.25f), accent, 0.6f * clamp(1f - s / 0.6f), 16);
            g.ring(ground, 0.3f + 4.2f * easeOut(s / 1.0f), 0.55f, accent, 0.95f * out, 56);
            if (s > 0.18f) g.ring(ground, 0.3f + 3.0f * easeOut((s - 0.18f) / 1.0f), 0.3f, light, 0.9f * out, 48);
        }

        /** A jagged themed bolt with a white-hot core and a branch, then a flash and a ring on the ground. */
        private void storm(G g, Vector3f o, float s, float out, int accent, int light, int segs, float detail) {
            if (bolt == null) bolt = buildBolt();
            float strike = s < 0.5f ? (0.65f + 0.35f * (float) Math.abs(Math.sin(s * 47))) * clamp(1f - (s - 0.25f) / 0.25f) : 0f;
            if (strike > 0f) {
                for (int i = 0; i + 1 < bolt.size(); i++) {
                    Vector3f a = bolt.get(i), b = bolt.get(i + 1);
                    if (a == null || b == null) continue;
                    Vector3f pa = new Vector3f(o).add(a), pb = new Vector3f(o).add(b);
                    g.ribbon(pa, pb, 0.9f, accent, 0.35f * strike, 0.35f * strike);     // outer glow
                    g.ribbon(pa, pb, 0.28f, light, 0.8f * strike, 0.8f * strike);
                    g.ribbon(pa, pb, 0.08f, 0xFFFFFF, strike, strike);                  // core
                }
            }
            Vector3f ground = new Vector3f(o).add(0, 0.1f, 0);
            g.glow(new Vector3f(o).add(0, 0.5f, 0), 2.2f * easeOut(s / 0.15f), light, 0.8f * clamp(1f - s / 0.5f), 16);
            g.ring(ground, 0.4f + 2.6f * easeOut(s / 0.8f), 0.45f, accent, 0.9f * out, 48);
        }

        private List<Vector3f> buildBolt() {
            List<Vector3f> path = new ArrayList<>();
            Vector3f top = new Vector3f((rnd.nextFloat() - 0.5f) * 2, 14, (rnd.nextFloat() - 0.5f) * 2);
            Vector3f bottom = new Vector3f(0, 0.1f, 0);
            int segs = 16;
            Vector3f branchFrom = null;
            for (int i = 0; i <= segs; i++) {
                float k = i / (float) segs;
                Vector3f base = new Vector3f(top).lerp(bottom, k);
                float jag = i == 0 || i == segs ? 0 : 0.6f * (1 - k * 0.5f);
                Vector3f q = base.add((rnd.nextFloat() - 0.5f) * 2 * jag, 0, (rnd.nextFloat() - 0.5f) * 2 * jag);
                path.add(q);
                if (i == segs / 3) branchFrom = new Vector3f(q);
            }
            if (branchFrom != null) {
                path.add(null);
                Vector3f q = branchFrom;
                path.add(new Vector3f(q));
                float dx = rnd.nextBoolean() ? 1 : -1, dz = rnd.nextFloat() - 0.5f;
                for (int i = 0; i < 5; i++) {
                    q = new Vector3f(q).add(dx * (0.35f + rnd.nextFloat() * 0.3f), -0.75f - rnd.nextFloat() * 0.4f, dz * 0.6f + (rnd.nextFloat() - 0.5f) * 0.4f);
                    path.add(q);
                }
            }
            return path;
        }

        /** A burst of glowing streaks from the chest, with a bright flash at the centre. */
        private void starburst(G g, Vector3f o, float s, float out, int accent, int light, int segs, float detail) {
            int count = Math.round(44 * detail);
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
            float k = easeOut(s / 1.0f);
            float r1 = 0.2f + 3.0f * k, r0 = r1 * 0.55f;
            float drop = k * k * 0.5f;
            for (int i = 0; i < dirs.length; i++) {
                Vector3f d = dirs[i];
                Vector3f head = new Vector3f(center).add(new Vector3f(d).mul(r1)).sub(0, drop, 0);
                Vector3f tail = new Vector3f(center).add(new Vector3f(d).mul(r0)).sub(0, drop * 0.5f, 0);
                int col = i % 3 == 0 ? light : accent;
                g.ribbon(tail, head, 0.12f, col, 0f, 0.85f * out);
                g.glow(head, 0.12f, 0xFFFFFF, 0.8f * out, 6);
            }
            g.glow(center, 1.5f * (1f - k) + 0.2f, light, 0.85f * clamp(1f - s / 0.5f), 16);
            g.glow(center, 0.5f, 0xFFFFFF, clamp(1f - s / 0.3f), 12);
        }

        /** A column of light rises from the body, a halo floats up, sparks spiral with it, then a soft burst. */
        private void ascension(G g, Vector3f o, float s, float out, int accent, int light, int segs, float detail) {
            float rise = s * 1.3f;
            g.beam(o, 5.5f + rise, 1.1f, accent, 0.55f * out);
            g.beam(o, 4.5f + rise, 0.35f, 0xFFFFFF, 0.7f * out);
            Vector3f halo = new Vector3f(o).add(0, h + 0.35f + rise * 0.8f, 0);
            g.ring(halo, 0.45f, 0.16f, 0xFFE9A0, 0.95f * out, 32);
            g.glow(halo, 0.6f, light, 0.35f * out, 12);
            int n = Math.round(16 * detail);
            for (int i = 0; i < n; i++) {
                float u = (i / (float) n + s * 0.35f) % 1f;
                double ang = i * 2.4 + s * 2.0;
                float y = u * (h + 3.0f);
                float a = (float) Math.sin(Math.PI * u) * out;
                Vector3f p = new Vector3f(o).add((float) Math.cos(ang) * 0.6f, y, (float) Math.sin(ang) * 0.6f);
                g.glow(p, 0.13f, i % 2 == 0 ? accent : light, 0.7f * a, segs);
            }
            if (s > 1.6f) {
                float b = s - 1.6f;
                g.glow(halo, 1.6f * easeOut(b / 0.4f), light, 0.6f * clamp(1f - b / 0.6f), 16);
            }
        }
    }
}
