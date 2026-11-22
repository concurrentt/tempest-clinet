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
