package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;

import java.util.ArrayDeque;

/** Shared combat/click state used by CPS, Reach, Combo and Target Health. */
public final class CombatTracker {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

    public static double reach;
    public static long reachTime;
    public static int combo;

    // advanced reach stats
    public static int reachHits;
    public static double reachSum, reachMax, reachMin = Double.MAX_VALUE;
    private static final java.util.ArrayDeque<Double> RECENT = new java.util.ArrayDeque<>();

    public static void resetReachStats() {
        reachHits = 0;
        reachSum = 0;
        reachMax = 0;
        reachMin = Double.MAX_VALUE;
        RECENT.clear();
    }

    public static double reachAvg() { return reachHits == 0 ? 0 : reachSum / reachHits; }
    public static java.util.List<Double> recentReaches() { return new java.util.ArrayList<>(RECENT); }

    private static LivingEntity target;
    private static long targetTime;
    private static long attackTime;
    private static long lastHitTime;
    private static int lastTargetHurt;
    private static int lastSelfHurt;

    /** A physical left click (from InputHooks). */
    public static void onLeftClick() { synchronized (LEFT) { LEFT.addLast(System.currentTimeMillis()); } }

    /** A physical right click (from InputHooks). */
    public static void onRightClick() { synchronized (RIGHT) { RIGHT.addLast(System.currentTimeMillis()); } }

    /** You hit an entity (ClientPlayerInteractionManager.attackEntity): reach, combo and target tracking. */
    public static void onHit(MinecraftClient c, net.minecraft.entity.Entity hit) {
        dev.tempest.modules.CombatTimerModule.onAttack(hit);
        long now = System.currentTimeMillis();
        if (c.player == null || !(hit instanceof LivingEntity le)) return;
        {
            net.minecraft.util.math.Vec3d eye = c.player.getEyePos();
            net.minecraft.util.math.Vec3d at = c.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == hit
                    ? ehr.getPos() : closestPoint(le.getBoundingBox(), eye);
            reach = eye.distanceTo(at);
            reachTime = now;
            reachHits++;
            reachSum += reach;
            reachMax = Math.max(reachMax, reach);
            reachMin = Math.min(reachMin, reach);
            RECENT.addLast(reach);
            while (RECENT.size() > 5) RECENT.removeFirst();
            target = le;
            targetTime = now;
            attackTime = now;
            lastTargetHurt = le.hurtTime;
        }
    }

    /** Closest point of a box to p (p itself if inside). */
    public static net.minecraft.util.math.Vec3d closestPoint(net.minecraft.util.math.Box b, net.minecraft.util.math.Vec3d p) {
        return new net.minecraft.util.math.Vec3d(Math.max(b.minX, Math.min(b.maxX, p.x)),
                Math.max(b.minY, Math.min(b.maxY, p.y)), Math.max(b.minZ, Math.min(b.maxZ, p.z)));
    }

    public static int leftCps() { return count(LEFT); }
    public static int rightCps() { return count(RIGHT); }

    private static int count(ArrayDeque<Long> q) {
        synchronized (q) {
            long cutoff = System.currentTimeMillis() - 1000;
            while (!q.isEmpty() && q.peekFirst() < cutoff) q.removeFirst();
            return q.size();
        }
    }

    public static void tick(MinecraftClient c) {
        InputHooks.ensureInstalled(c);
        if (c.player == null) return;
        long now = System.currentTimeMillis();

        if (target != null) {
            if (attackTime != 0 && now - attackTime < 500 && target.hurtTime > lastTargetHurt) {
                combo++;
                lastHitTime = now;
                attackTime = 0;
            }
            lastTargetHurt = target.hurtTime;
        }
        if (c.player.hurtTime > lastSelfHurt) combo = 0;
        lastSelfHurt = c.player.hurtTime;
        if (combo > 0 && now - lastHitTime > 3000) combo = 0;
    }

    /** The entity we hit recently, or the one under the crosshair. */
    public static LivingEntity currentTarget() {
        MinecraftClient c = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        if (target != null && target.isAlive() && !target.isRemoved() && now - targetTime < 6000) return target;
        if (c.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) return le;
        return null;
    }

    public static LivingEntity lastTarget() { return target; }
    public static long lastTargetAge() { return System.currentTimeMillis() - targetTime; }
}
