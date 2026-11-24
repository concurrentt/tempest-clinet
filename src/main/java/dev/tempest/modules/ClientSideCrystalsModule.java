package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight visual-only client-side end crystal placement. */
public final class ClientSideCrystalsModule extends Module {
    private static final Map<Integer, Long> FAKE_CRYSTALS = new ConcurrentHashMap<>();
    private final NumberSetting timeoutMs = add(new NumberSetting("timeout", "Timeout ms", 800, 100, 3000, 50));
    private final BoolSetting onlyWhenHolding = add(new BoolSetting("hold_only", "Only When Holding Crystal", true));

    public ClientSideCrystalsModule() {
        super("client_crystals", "Clientside Crystals", "Shows end crystals instantly on placement (visual only).", Category.PVP, false);
    }

    public static boolean active() {
        return ModuleManager.enabled(ClientSideCrystalsModule.class);
    }

    public static void onInteractBlock(BlockHitResult hit) {
        ClientSideCrystalsModule m = ModuleManager.get(ClientSideCrystalsModule.class);
        if (m == null || !m.isEnabled() || hit == null) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null || c.interactionManager == null) return;

        boolean main = c.player.getMainHandStack().isOf(Items.END_CRYSTAL);
        boolean off = c.player.getOffHandStack().isOf(Items.END_CRYSTAL);
        if (m.onlyWhenHolding.get() && !main && !off) return;
        if (!main && !off) return;

        BlockPos pos = hit.getBlockPos();
        var state = c.world.getBlockState(pos);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) return;
        if (hit.getSide() != Direction.UP) return;

        BlockPos above = pos.up();
        if (!c.world.isAir(above)) return;
        BlockPos above2 = above.up();
        if (!c.world.isAir(above2)) return;
        if (!c.world.getOtherEntities(null, new Box(above)).isEmpty()) return;

        try {
            EndCrystalEntity fake = new EndCrystalEntity(c.world, above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
            fake.setShowBottom(false);
            int id = -(100000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000));
            fake.setId(id);
            c.world.addEntity(fake);
            FAKE_CRYSTALS.put(id, System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
    }

    // ---- hitting a crystal before the server's real one has arrived --------------------------------------------
    // The crystal you see first is ours (a negative entity id the server doesn't know). Attacking it would send a hit
    // for an id that doesn't exist, so nothing breaks until you click again - that's the "delay". Instead: hit the
    // real crystal if it's already here, otherwise remember the hit and send it the moment the real one spawns.
    private static net.minecraft.util.math.Vec3d pendingAt;
    private static long pendingTime;

    /** From ClientPlayerInteractionManager.attackEntity: true = handled here (don't send the original hit). */
    public static boolean redirectAttack(net.minecraft.entity.player.PlayerEntity player, net.minecraft.entity.Entity target) {
        if (!(target instanceof EndCrystalEntity fake) || target.getId() >= 0 || !FAKE_CRYSTALS.containsKey(target.getId())) return false;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.interactionManager == null) return false;
        EndCrystalEntity real = realNear(c, fake.getEntityPos());
        removeFake(c, fake.getId());
        if (real != null) {
            c.interactionManager.attackEntity(player, real);
        } else {
            pendingAt = fake.getEntityPos();
            pendingTime = System.currentTimeMillis();
        }
        return true;
    }

    private static EndCrystalEntity realNear(MinecraftClient c, net.minecraft.util.math.Vec3d at) {
        var list = c.world.getEntitiesByClass(EndCrystalEntity.class, new Box(at, at).expand(0.6), e -> e.getId() >= 0 && e.isAlive());
        return list.isEmpty() ? null : list.get(0);
    }

    private static void removeFake(MinecraftClient c, int id) {
        FAKE_CRYSTALS.remove(id);
        var ent = c.world.getEntityById(id);
        if (ent != null) try { ent.discard(); } catch (Throwable ignored) { }
        try { c.world.removeEntity(id, net.minecraft.entity.Entity.RemovalReason.DISCARDED); } catch (Throwable ignored) { }
    }

    /** A real entity just spawned (ClientWorldMixin): swap out our stand-in at once and send a waiting hit. */
    public static void onEntityAdded(net.minecraft.entity.Entity e) {
        if (!(e instanceof EndCrystalEntity) || e.getId() < 0) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        for (Integer id : new java.util.ArrayList<>(FAKE_CRYSTALS.keySet())) {
            var f = c.world.getEntityById(id);
            if (f != null && f.squaredDistanceTo(e) < 0.36) removeFake(c, id);
        }
        if (pendingAt != null && System.currentTimeMillis() - pendingTime < 600 && e.getEntityPos().squaredDistanceTo(pendingAt) < 0.5
                && c.player != null && c.interactionManager != null) {
            pendingAt = null;
            c.interactionManager.attackEntity(c.player, e);
        }
    }

    public static void tick() {
        ClientSideCrystalsModule m = ModuleManager.get(ClientSideCrystalsModule.class);
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) {
            FAKE_CRYSTALS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        long timeout = m != null ? (long) m.timeoutMs.get() : 800L;

        Iterator<Map.Entry<Integer, Long>> it = FAKE_CRYSTALS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> e = it.next();
            int id = e.getKey();
            long age = now - e.getValue();
            var ent = c.world.getEntityById(id);
            boolean remove = age > timeout;
            if (ent instanceof EndCrystalEntity fake) {
                boolean realNearby = !c.world.getEntitiesByClass(EndCrystalEntity.class, fake.getBoundingBox().expand(0.5),
                        other -> other != fake && other.getId() >= 0).isEmpty();
                if (realNearby || remove) {
                    try { fake.discard(); } catch (Throwable ignored) {}
                    try { c.world.removeEntity(id, net.minecraft.entity.Entity.RemovalReason.DISCARDED); } catch (Throwable ignored) {}
                    it.remove();
                }
            } else {
                it.remove();
            }
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        tick();
    }

    @Override
    public void onDisable() {
        clear();
    }

    public static void clear() {
        FAKE_CRYSTALS.clear();
    }
}
