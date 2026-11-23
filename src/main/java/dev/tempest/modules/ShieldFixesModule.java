package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.Map;

/**
 * Shield Fixes (like Walksy's Complete Shield Fixes):
 * <ul>
 *   <li>Disable sound: plays the shield-break sound when anyone's shield gets disabled (MC-105068).</li>
 *   <li>Block sound: plays the block sound for every hit a shield takes, including explosions and arrows.</li>
 *   <li>Blocking state: a player using a shield shows as blocking - after the real 5-tick (0.25 s) raise delay when
 *       "Respect 5-Tick Delay" is on (accurate), or instantly when it is off.</li>
 *   <li>Raised shield on other players: when the "using an item" signal reaches you before their equipment does,
 *       the game drops their shield and never shows it raised; it's put back every tick (LivingEntityMixin).</li>
 * </ul>
 * Client-side only: the singleplayer server is never affected, and each sound plays at most once per event even if
 * the game already played it.
 */
public final class ShieldFixesModule extends Module {
    private final BoolSetting disableSound = add(new BoolSetting("disable_sound", "Shield Disable Sound", true));
    private final BoolSetting blockSound = add(new BoolSetting("block_sound", "Shield Block Sound", true));
    private final BoolSetting animationFix = add(new BoolSetting("anim_fix", "Blocking State Fix", true));
    private final BoolSetting delay = add(new BoolSetting("delay", "Respect 5-Tick Delay", true));

    private static final Map<Long, Long> RECENT = new HashMap<>();   // (entity id, status) -> ms

    public ShieldFixesModule() {
        super("shield_fixes", "Shield Fixes", "Shield sounds for every block and disable, and an accurate blocking state.", Category.PVP, true);
    }

    private static ShieldFixesModule on() {
        ShieldFixesModule m = ModuleManager.get(ShieldFixesModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public static void onStatus(Entity e, byte status) {
        ShieldFixesModule m = on();
        if (m == null || e == null || (status != 29 && status != 30)) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        long key = ((long) e.getId() << 8) | (status & 0xFF), now = System.currentTimeMillis();
        Long last = RECENT.get(key);
        if (last != null && now - last < 100) return;      // one sound per event
        RECENT.put(key, now);
        if (RECENT.size() > 128) RECENT.clear();
        if (status == 30 && m.disableSound.get()) {
            c.world.playSound(c.player, e.getX(), e.getY(), e.getZ(), SoundEvents.ITEM_SHIELD_BREAK, e.getSoundCategory(),
                    1.0f, 0.8f + c.world.random.nextFloat() * 0.4f);
        }
        if (status == 29 && m.blockSound.get()) {
            c.world.playSound(c.player, e.getX(), e.getY(), e.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, e.getSoundCategory(),
                    1.0f, 0.8f + c.world.random.nextFloat() * 0.4f);
        }
    }

    /** True when this is an entity in the client's world (never the singleplayer server's copy). */
    private static boolean clientSide(LivingEntity e) {
        MinecraftClient c = MinecraftClient.getInstance();
        return c.world != null && c.world.getEntityById(e.getId()) == e;
    }

    /** Should LivingEntityMixin repair this player's shield-in-use state? (other players, client side, while "using"). */
    public static boolean resyncWanted(LivingEntity e) {
        ShieldFixesModule m = on();
        if (m == null || !m.animationFix.get() || e == null || !e.isUsingItem()) return false;
        MinecraftClient c = MinecraftClient.getInstance();
        return e != c.player && clientSide(e);
    }

    /** A shield, or any item that blocks attacks like one. */
    public static boolean isShield(net.minecraft.item.ItemStack s) {
        return s.isOf(Items.SHIELD) || s.contains(net.minecraft.component.DataComponentTypes.BLOCKS_ATTACKS);
    }

    /**
     * isBlocking() override for the client: null = leave vanilla alone, otherwise the value to report.
     */
    public static Boolean blocking(LivingEntity e) {
        ShieldFixesModule m = on();
        if (m == null || !m.animationFix.get() || e == null || !e.isUsingItem() || !isShield(e.getActiveItem())) return null;
        if (!clientSide(e)) return null;
        return !m.delay.get() || e.getItemUseTime() >= 5;
    }
}
