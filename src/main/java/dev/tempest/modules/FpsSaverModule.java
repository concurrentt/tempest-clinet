package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * FPS Saver: skips drawing things you can't see or that are too far away to matter.
 * <ul>
 *   <li>Mobs, items and other entities hidden behind solid blocks aren't drawn (players too, if you turn it on).
 *       Checked with a few cheap rays through the block grid every few frames per entity; glass, leaves and
 *       anything that isn't a full solid block count as see-through, so nothing visible ever disappears.</li>
 *   <li>Entities (not players) further away than "Entity Distance" aren't drawn.</li>
 *   <li>Chests, signs, banners, heads and other block entities further than "Block Entity Distance" aren't drawn.</li>
 *   <li>Particles further than "Particle Distance" aren't created at all.</li>
 * </ul>
 * Only changes what your game draws - nothing is sent to the server. Hooked in mixin/EntityRendererCullMixin,
 * mixin/BlockEntityDistanceMixin and mixin/ParticleManagerMixin.
 */
public class FpsSaverModule extends Module {
    private final BoolSetting hideBehindWalls = add(new BoolSetting("hide_behind_walls", "Hide Mobs Behind Walls", true));
    private final BoolSetting hidePlayers = add(new BoolSetting("hide_players", "Hide Players Behind Walls", false));
    private final NumberSetting entityDistance = add(new NumberSetting("entity_distance", "Entity Distance", 64, 16, 128, 8));
    private final NumberSetting blockEntityDistance = add(new NumberSetting("block_entity_distance", "Block Entity Distance", 48, 16, 64, 4));
    private final NumberSetting particleDistance = add(new NumberSetting("particle_distance", "Particle Distance", 32, 8, 64, 4));

    /** Results of the behind-a-wall check: entity id -> (visible, re-check after this frame). */
    private static final Map<Integer, long[]> SEEN = new HashMap<>();
    private static long frame;
    private static ClientWorld seenWorld;
    private static final int RECHECK_FRAMES = 6;
    private static final double OCCLUSION_RANGE = 96;   // past this the ray walk gets long; distance limits take over

    public FpsSaverModule() {
        super("fps_saver", "FPS Saver", "Skips drawing mobs behind walls and far-away entities, block entities and particles.",
                Category.RENDER, false);
        keyHint = "Players are never hidden by distance, and only hidden behind walls if you turn that on.";
    }

    private static FpsSaverModule on() {
        FpsSaverModule m = ModuleManager.get(FpsSaverModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (c.world != seenWorld) { SEEN.clear(); seenWorld = c.world; }
        if (SEEN.size() > 4096) SEEN.clear();
    }

    @Override
    public void onDisable() { SEEN.clear(); }

    // ------------------------------------------------------------------ entities
    /** Should this entity be skipped this frame? Called from EntityRenderer.shouldRender for every entity. */
    public static boolean hide(Entity e) {
        FpsSaverModule m = on();
        if (m == null) return false;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.gameRenderer == null || e == c.getCameraEntity() || e == c.player) return false;
        if (e.isGlowing() || (c.player != null && (e.hasPassenger(c.player) || c.player.hasPassenger(e)))) return false;
        Vec3d cam = c.gameRenderer.getCamera().getCameraPos();
        double d2 = e.squaredDistanceTo(cam);
        boolean player = e instanceof PlayerEntity;

        if (!player) {
            double max = m.entityDistance.get();
            if (d2 > max * max) return true;
        }
        if (!m.hideBehindWalls.get() || (player && !m.hidePlayers.get())) return false;
        if (d2 < 9 || d2 > OCCLUSION_RANGE * OCCLUSION_RANGE) return false;
        if (ModuleManager.enabled(XrayModule.class)) return false;          // x-ray shows through walls on purpose
        if (e.getBoundingBox().getAverageSideLength() > 6) return false;   // huge (dragon, ...): not worth the risk

        long[] s = SEEN.get(e.getId());
        if (s == null || frame >= s[1]) {
            boolean visible = visible(c.world, cam, e.getBoundingBox().expand(0.25));
            // spread re-checks over frames (by entity id) so a crowd doesn't all re-check on the same frame
            long next = frame + RECHECK_FRAMES + (e.getId() & 3);
            if (s == null) SEEN.put(e.getId(), new long[]{visible ? 1 : 0, next});
            else { s[0] = visible ? 1 : 0; s[1] = next; }
            return !visible;
        }
        return s[0] == 0;
    }

    /** Called once per frame (from the world render) so cached results age. */
    public static void nextFrame() { frame++; }

    /** Is any of a few points on the box reachable from the camera without passing through a full solid block? */
    private static boolean visible(ClientWorld w, Vec3d cam, Box b) {
        double cx = (b.minX + b.maxX) / 2, cy = (b.minY + b.maxY) / 2, cz = (b.minZ + b.maxZ) / 2;
        if (clear(w, cam, cx, cy, cz)) return true;
        if (clear(w, cam, cx, b.maxY, cz)) return true;
        if (clear(w, cam, cx, b.minY + 0.1, cz)) return true;
        // the two sides of the box as seen from the camera
        double dx = cx - cam.x, dz = cz - cam.z, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return true;
        double rx = -dz / len, rz = dx / len;
        double half = Math.max(b.maxX - b.minX, b.maxZ - b.minZ) / 2;
        if (clear(w, cam, cx + rx * half, cy, cz + rz * half)) return true;
        return clear(w, cam, cx - rx * half, cy, cz - rz * half);
    }

    /** Walks the block grid from the camera to (x, y, z) (Amanatides & Woo); false at the first full solid block. */
    private static boolean clear(ClientWorld w, Vec3d cam, double x, double y, double z) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        int bx = MathHelper.floor(cam.x), by = MathHelper.floor(cam.y), bz = MathHelper.floor(cam.z);
        int ex = MathHelper.floor(x), ey = MathHelper.floor(y), ez = MathHelper.floor(z);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tdy = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tdz = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tmx = dx == 0 ? Double.MAX_VALUE : (sx > 0 ? (bx + 1 - cam.x) : (cam.x - bx)) * tdx;
        double tmy = dy == 0 ? Double.MAX_VALUE : (sy > 0 ? (by + 1 - cam.y) : (cam.y - by)) * tdy;
        double tmz = dz == 0 ? Double.MAX_VALUE : (sz > 0 ? (bz + 1 - cam.z) : (cam.z - bz)) * tdz;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int i = 0; i < 400; i++) {
            double t;                                                    // where along the ray this block starts (1 = target)
            if (tmx < tmy && tmx < tmz) { t = tmx; bx += sx; tmx += tdx; }
            else if (tmy < tmz) { t = tmy; by += sy; tmy += tdy; }
            else { t = tmz; bz += sz; tmz += tdz; }
            if (t > 1) return true;                                      // walked past the target point
            if (bx == ex && by == ey && bz == ez) return true;          // reached the entity's own block
            p.set(bx, by, bz);
            if (w.getBlockState(p).isOpaqueFullCube()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ block entities and particles
    /** Is this block entity close enough to draw? (on top of its own render distance) */
    public static boolean blockEntityInRange(BlockEntity be, Vec3d cam) {
        FpsSaverModule m = on();
        if (m == null || be == null || cam == null) return true;
        double max = m.blockEntityDistance.get();
        return Vec3d.ofCenter(be.getPos()).squaredDistanceTo(cam) <= max * max;
    }

    /** Skip creating a particle this far from the camera? */
    public static boolean skipParticle(double x, double y, double z) {
        FpsSaverModule m = on();
        if (m == null) return false;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.gameRenderer == null) return false;
        Vec3d cam = c.gameRenderer.getCamera().getCameraPos();
        double max = m.particleDistance.get();
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz > max * max;
    }
}
