package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;

import java.util.ArrayList;
import java.util.List;

/** Client-side particle groups. Each option suppresses the matching particle types only. */
public final class ParticleFilterModule extends Module {
    private record Group(BoolSetting setting, ParticleType<?>[] types) { }

    private final List<Group> groups = new ArrayList<>();
    public final BoolSetting explosions;
    private final BoolSetting blockBreak = group("block_break", "Hide Block Debris",
            ParticleTypes.BLOCK, ParticleTypes.BLOCK_CRUMBLE, ParticleTypes.BLOCK_MARKER,
            ParticleTypes.DUST_PILLAR, ParticleTypes.FALLING_DUST);

    public ParticleFilterModule() {
        super("particle_filter", "Particles", "Choose exactly which particle groups are hidden on your client.", Category.VISUALS, false);
        explosions = group("explosions", "Hide Explosions & Fireworks",
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, ParticleTypes.FIREWORK, ParticleTypes.POOF);
        group("smoke", "Hide Smoke", ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE, ParticleTypes.WHITE_SMOKE,
                ParticleTypes.CAMPFIRE_COSY_SMOKE, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE);
        group("potion", "Hide Potion & Status Effects", ParticleTypes.ENTITY_EFFECT, ParticleTypes.EFFECT,
                ParticleTypes.INSTANT_EFFECT, ParticleTypes.WITCH);
        group("flame", "Hide Fire & Lava", ParticleTypes.FLAME, ParticleTypes.SMALL_FLAME,
                ParticleTypes.COPPER_FIRE_FLAME, ParticleTypes.LAVA, ParticleTypes.FALLING_LAVA,
                ParticleTypes.DRIPPING_LAVA, ParticleTypes.DRIPPING_DRIPSTONE_LAVA, ParticleTypes.LANDING_LAVA);
        group("combat", "Hide Combat Hits", ParticleTypes.CRIT, ParticleTypes.ENCHANTED_HIT,
                ParticleTypes.SWEEP_ATTACK, ParticleTypes.DAMAGE_INDICATOR);
        group("totem", "Hide Totem Particles", ParticleTypes.TOTEM_OF_UNDYING);
        group("hearts", "Hide Hearts", ParticleTypes.HEART);
        group("water", "Hide Water & Bubbles", ParticleTypes.RAIN, ParticleTypes.SPLASH, ParticleTypes.BUBBLE,
                ParticleTypes.BUBBLE_COLUMN_UP, ParticleTypes.BUBBLE_POP, ParticleTypes.CURRENT_DOWN,
                ParticleTypes.DRIPPING_WATER, ParticleTypes.FALLING_WATER, ParticleTypes.DRIPPING_DRIPSTONE_WATER);
        group("magic", "Hide Magic & Portal Effects", ParticleTypes.ENCHANT, ParticleTypes.PORTAL,
                ParticleTypes.REVERSE_PORTAL, ParticleTypes.END_ROD, ParticleTypes.DRAGON_BREATH,
                ParticleTypes.ELECTRIC_SPARK, ParticleTypes.GLOW);
        group("ambient", "Hide Ambient & Nature", ParticleTypes.CLOUD, ParticleTypes.ASH, ParticleTypes.MYCELIUM,
                ParticleTypes.CHERRY_LEAVES, ParticleTypes.PALE_OAK_LEAVES, ParticleTypes.FIREFLY);
        group("misc", "Hide Miscellaneous Particles", ParticleTypes.NOTE, ParticleTypes.HAPPY_VILLAGER,
                ParticleTypes.ANGRY_VILLAGER, ParticleTypes.CRIMSON_SPORE, ParticleTypes.INFESTED,
                ParticleTypes.ITEM_SLIME, ParticleTypes.ITEM_COBWEB, ParticleTypes.ITEM_SNOWBALL);
    }

    private BoolSetting group(String id, String label, ParticleType<?>... types) {
        BoolSetting setting = add(new BoolSetting(id, label, false));
        groups.add(new Group(setting, types));
        return setting;
    }

    /** Called before a ParticleEffect is converted into a concrete particle. */
    public static boolean hides(ParticleEffect effect) {
        ParticleFilterModule module = ModuleManager.get(ParticleFilterModule.class);
        if (module == null || !module.isEnabled() || effect == null) return false;
        ParticleType<?> type = effect.getType();
        for (Group group : module.groups) {
            if (!group.setting().get()) continue;
            for (ParticleType<?> hidden : group.types()) {
                if (hidden == type) return true;
            }
        }
        return false;
    }

    /** Explosion particles shown? (the Pack Editor's End Crystals switch) */
    public static boolean explosionsShown() {
        ParticleFilterModule m = ModuleManager.get(ParticleFilterModule.class);
        return m == null || !m.isEnabled() || !m.explosions.get();
    }

    public static void setExplosionsShown(boolean shown) {
        ParticleFilterModule m = ModuleManager.get(ParticleFilterModule.class);
        if (m == null) return;
        m.explosions.set(!shown);
        if (!shown && !m.isEnabled()) m.setEnabled(true);
    }

    /** Catches block debris particles created directly without a ParticleEffect argument. */
    public static boolean hides(Particle particle) {
        ParticleFilterModule module = ModuleManager.get(ParticleFilterModule.class);
        return module != null && module.isEnabled() && module.blockBreak.get() && particle instanceof BlockDustParticle;
    }
}
