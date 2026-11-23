package dev.tempest.util;

import dev.tempest.module.ModuleManager;
import dev.tempest.modules.EmotesModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emotes: short poses/animations played on a player model. Yours are sent over the live link so every Tempest
 * player near you sees them; theirs arrive the same way.
 *
 * <p>Posing happens after vanilla has set the model's angles (BipedEntityModelMixin), so armour, sleeves and the
 * hat follow. Angles are absolute (radians, model space: +Y down, front is -Z; arm roll > 0 lifts the right arm
 * out to the side, < 0 the left one) and blended in and out over a quarter second.</p>
 */
public final class Emotes {
    /** id (sent over the network), display name, length in seconds (0 = loops until you move). */
    public record Def(String id, String name, float length) { }

    public static final Def[] ALL = {
            new Def("wave", "Wave", 2.6f), new Def("clap", "Clap", 2.4f), new Def("dance", "Dance", 0f),
            new Def("dab", "Dab", 1.7f), new Def("salute", "Salute", 2.2f), new Def("facepalm", "Facepalm", 2.4f),
            new Def("cheer", "Cheer", 2.6f), new Def("shrug", "Shrug", 1.8f),
    };

    private static final float FADE = 0.25f, MAX_LOOP = 60f;

    private record Playing(Def def, long start, long stopAt) {
        Playing stopping(long now) { return stopAt != 0 ? this : new Playing(def, start, now); }
    }

    private static final Map<UUID, Playing> PLAYING = new ConcurrentHashMap<>();
    /** Try-on in the cosmetics screen: loops on your model only, nothing is sent. */
    private static volatile Playing preview;
    private static Perspective savedPerspective;
    private static Vec3d startPos;

    private Emotes() { }

    public static Def byId(String id) {
        if (id != null) for (Def d : ALL) if (d.id.equals(id)) return d;
        return null;
    }

    public static boolean wanted() {
        EmotesModule m = ModuleManager.get(EmotesModule.class);
        return m != null && m.isEnabled();
    }

    /** The emote you're playing right now, or null. */
    public static Def current() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return null;
        Playing p = PLAYING.get(c.player.getUuid());
        return p == null || p.stopAt != 0 ? null : p.def;
    }

    // ------------------------------------------------------------------ you
    public static void play(String id) {
        MinecraftClient c = MinecraftClient.getInstance();
        Def d = byId(id);
        if (d == null || c.player == null || !wanted()) return;
        if (c.player.isSleeping() || c.player.hasVehicle() || c.player.isGliding() || c.player.isSwimming()) {
            Chat.info("You can't emote right now.");
            return;
        }
        long now = System.currentTimeMillis();
        PLAYING.put(c.player.getUuid(), new Playing(d, now, 0));
        startPos = c.player.getEntityPos();
        EmotesModule m = ModuleManager.get(EmotesModule.class);
        if (m != null && m.thirdPerson.get() && c.options.getPerspective() == Perspective.FIRST_PERSON) {
            if (savedPerspective == null) savedPerspective = c.options.getPerspective();
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        }
        TempestLive.sendEmote(d.id);
    }

    public static void stop() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return;
        Playing p = PLAYING.get(c.player.getUuid());
        if (p == null || p.stopAt != 0) return;
        PLAYING.put(c.player.getUuid(), p.stopping(System.currentTimeMillis()));
        TempestLive.sendEmote(null);
        restoreCamera(c);
    }

    private static void restoreCamera(MinecraftClient c) {
        if (savedPerspective != null) {
            if (c.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) c.options.setPerspective(savedPerspective);
            savedPerspective = null;
        }
    }

    /** Every client tick: ends finished emotes, cancels yours when you move, drops stale ones. */
    public static void tick(MinecraftClient c) {
        long now = System.currentTimeMillis();
        if (c.player == null || c.world == null) {
            PLAYING.clear();
            savedPerspective = null;
            return;
        }
        UUID me = c.player.getUuid();
        Playing mine = PLAYING.get(me);
        if (mine != null && mine.stopAt == 0) {
            float t = (now - mine.start) / 1000f;
            boolean over = mine.def.length > 0 ? t >= mine.def.length : t >= MAX_LOOP;
            if (over || (t > 0.15f && moving(c))) stop();
        }
        PLAYING.entrySet().removeIf(e -> {
            Playing p = e.getValue();
            float t = (now - p.start) / 1000f;
            if (p.stopAt != 0) return now - p.stopAt > FADE * 1000 + 50;
            if (e.getKey().equals(me)) return false;
            return p.def.length > 0 ? t > p.def.length + 1f : t > MAX_LOOP + 5f;
        });
        if (mine != null && !PLAYING.containsKey(me)) restoreCamera(c);
    }

    private static boolean moving(MinecraftClient c) {
        GameOptions o = c.options;
        if (o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed()
                || o.jumpKey.isPressed() || o.sneakKey.isPressed() || o.attackKey.isPressed() || o.useKey.isPressed()) return true;
        if (startPos != null && c.player.getEntityPos().squaredDistanceTo(startPos) > 0.25 * 0.25) return true;
        return c.player.hurtTime > 0;
    }

    /** Show an emote on your own model in menus (looping), or null to stop. Not sent to anyone. */
    public static void preview(String id) {
        Def d = byId(id);
        if (d == null) { preview = null; return; }
        Playing p = preview;
        if (p == null || p.def != d) preview = new Playing(d, System.currentTimeMillis(), 0);
    }

    // ------------------------------------------------------------------ others
    public static void onRemote(UUID id, String emote) {
        if (id == null) return;
        MinecraftClient c = MinecraftClient.getInstance();
        EmotesModule m = ModuleManager.get(EmotesModule.class);
        if (m == null || !m.isEnabled() || !m.seeOthers.get()) return;
        if (c.player != null && id.equals(c.player.getUuid())) return;
        long now = System.currentTimeMillis();
        Def d = byId(emote);
        if (d == null) PLAYING.computeIfPresent(id, (k, p) -> p.stopping(now));
        else PLAYING.put(id, new Playing(d, now, 0));
    }

    public static void clearRemote() {
        MinecraftClient c = MinecraftClient.getInstance();
        UUID me = c.player == null ? null : c.player.getUuid();
        PLAYING.keySet().removeIf(k -> !k.equals(me));
    }

    // ------------------------------------------------------------------ posing
    /** Called after vanilla posed a player model (entity id from the render state). */
    public static void pose(int entityId, BipedEntityModel<?> m) {
        Playing pv = preview;
        if (PLAYING.isEmpty() && pv == null) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        long now = System.currentTimeMillis();
        Playing pl;
        float t;
        if (pv != null && c.player != null && entityId == c.player.getId()) {
            pl = pv;
            float cycle = (pv.def.length > 0 ? pv.def.length : 3f) + 0.5f;
            t = ((now - pv.start) / 1000f) % cycle;
            if (pv.def.length <= 0) {          // loops: no fade between cycles
                apply(pv.def.id, (now - pv.start) / 1000f, smooth((now - pv.start) / 1000f / FADE), m);
                return;
            }
            if (t > pv.def.length) return;
        } else {
            Entity e = c.world.getEntityById(entityId);
            if (!(e instanceof PlayerEntity p)) return;
            pl = PLAYING.get(p.getUuid());
            if (pl == null) return;
            t = (now - pl.start) / 1000f;
        }
        float w = smooth(t / FADE);
        if (pl.def.length > 0) w *= smooth((pl.def.length - t) / FADE);
        if (pl.stopAt != 0) w *= 1f - smooth((now - pl.stopAt) / 1000f / FADE);
        if (w <= 0.001f) return;
        apply(pl.def.id, t, w, m);
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3 - 2 * x);
    }

    private static void set(ModelPart part, float pitch, float yaw, float roll, float w) {
        part.pitch += (pitch - part.pitch) * w;
        part.yaw += (yaw - part.yaw) * w;
        part.roll += (roll - part.roll) * w;
    }

    private static void lift(BipedEntityModel<?> m, float dy, float w) {
        float d = dy * w;
        m.head.originY += d; m.body.originY += d; m.rightArm.originY += d; m.leftArm.originY += d;
        m.rightLeg.originY += d; m.leftLeg.originY += d;
    }

    private static final float PI = (float) Math.PI;

    private static void apply(String id, float t, float w, BipedEntityModel<?> m) {
        switch (id) {
            case "wave" -> {
                set(m.rightArm, -0.15f, 0f, 2.55f + 0.38f * (float) Math.sin(t * 11f), w);
                set(m.head, -0.12f, -0.15f, 0.06f * (float) Math.sin(t * 5.5f), w);
            }
            case "clap" -> {
                float close = 0.5f + 0.5f * (float) Math.cos(t * 13f);   // 1 = hands together
                float yaw = 0.08f + 0.44f * close;
                set(m.rightArm, -1.30f, -yaw, 0f, w);
                set(m.leftArm, -1.30f, yaw, 0f, w);
                set(m.head, 0.08f, 0f, 0f, w);
            }
            case "dance" -> {
                float ph = t * 7.2f, s = (float) Math.sin(ph);
                set(m.rightArm, -0.25f, 0f, 1.3f + 1.15f * s, w);
                set(m.leftArm, -0.25f, 0f, -(1.3f - 1.15f * s), w);
                set(m.rightLeg, 0.35f * s, 0f, 0.06f, w);
                set(m.leftLeg, -0.35f * s, 0f, -0.06f, w);
                set(m.head, 0.05f, 0.25f * (float) Math.sin(ph * 0.5f), 0.18f * s, w);
                set(m.body, 0f, 0.12f * (float) Math.sin(ph * 0.5f), 0.07f * s, w);
                lift(m, 0.8f * Math.abs((float) Math.cos(ph)), w);
            }
            case "dab" -> {
                float hit = smooth(t / 0.18f);
                set(m.rightArm, -0.25f, 0f, 0.4f + 1.85f * hit, w);
                set(m.leftArm, -1.85f * hit, 0.80f * hit, 0f, w);
                set(m.head, 0.60f * hit, -0.45f * hit, 0.10f * hit, w);
            }
            case "salute" -> {
                float snap = smooth(t / 0.2f);
                set(m.rightArm, -2.35f * snap, -0.62f * snap, 0.15f * snap, w);
                set(m.head, -0.05f, 0f, 0f, w);
                set(m.body, 0f, 0f, 0f, w);
            }
            case "facepalm" -> {
                float reach = smooth(t / 0.35f);
                set(m.rightArm, -2.05f * reach, -0.52f * reach, 0f, w);
                set(m.head, 0.42f * reach, 0.18f * (float) Math.sin(t * 6f) * reach, 0f, w);
            }
            case "cheer" -> {
                float pump = (float) Math.sin(t * 12f);
                set(m.rightArm, 0f, 0f, 2.65f + 0.22f * pump, w);
                set(m.leftArm, 0f, 0f, -(2.65f + 0.22f * (float) Math.sin(t * 12f + 0.7f)), w);
                set(m.head, -0.30f, 0f, 0.05f * pump, w);
                lift(m, 0.6f * Math.abs((float) Math.sin(t * 6f)), w);
            }
            case "shrug" -> {
                float up = smooth(t / 0.25f);
                set(m.rightArm, -0.55f * up, 0.35f * up, 0.45f * up, w);
                set(m.leftArm, -0.55f * up, -0.35f * up, -0.45f * up, w);
                set(m.head, 0.08f, 0f, 0.22f * up, w);
                m.rightArm.originY -= 1.2f * up * w;
                m.leftArm.originY -= 1.2f * up * w;
            }
            default -> { }
        }
    }
}
