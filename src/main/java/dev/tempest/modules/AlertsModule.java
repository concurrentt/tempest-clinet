package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** On-screen warnings for nearly broken armor and potion effects that are about to run out. */
public class AlertsModule extends HudModule {
    private final BoolSetting armor = add(new BoolSetting("armor", "Armor Alerts", true));
    private final NumberSetting armorPct = add(new NumberSetting("armor_pct", "Armor Warning Below (%)", 20, 5, 60, 5));
    private final BoolSetting potion = add(new BoolSetting("potion", "Potion Alerts", true));
    private final NumberSetting potionSec = add(new NumberSetting("potion_sec", "Potion Warning (s left)", 10, 3, 60, 1));
    private final BoolSetting beneficialOnly = add(new BoolSetting("beneficial", "Only Beneficial Effects", true));
    private final BoolSetting expired = add(new BoolSetting("expired", "Alert When Effect Ends", true));
    private final BoolSetting sound = add(new BoolSetting("sound", "Play Sound", true));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Set<EquipmentSlot> armorWarned = new HashSet<>();
    private final Set<RegistryEntry<StatusEffect>> potionWarned = new HashSet<>();
    private final Map<RegistryEntry<StatusEffect>, Integer> lastDuration = new HashMap<>();
    private final Map<RegistryEntry<StatusEffect>, String> lastName = new HashMap<>();
    private final Map<String, Long> endedAt = new HashMap<>();

    private final List<String> lines = new ArrayList<>();
    private final List<Integer> cols = new ArrayList<>();

    public AlertsModule() { super("alerts", "Armor & Potion Alerts", "Warns you about low armor durability and expiring potions.", Category.PVP, false, 150, 60); }

    @Override
    public void onTick(MinecraftClient c) {
        lines.clear();
        cols.clear();
        if (c.player == null) return;
        boolean beep = false;

        if (armor.get()) {
            for (EquipmentSlot slot : SLOTS) {
                ItemStack s = c.player.getEquippedStack(slot);
                if (s.isEmpty() || !s.isDamageable()) { armorWarned.remove(slot); continue; }
                int max = Math.max(1, s.getMaxDamage());
                int pct = (max - s.getDamage()) * 100 / max;
                if (pct <= armorPct.getInt()) {
                    lines.add(s.getName().getString() + " " + pct + "%");
                    cols.add(pct <= armorPct.getInt() / 2 ? 0xFFFF5555 : 0xFFFFAA00);
                    if (armorWarned.add(slot)) beep = true;
                } else if (pct > armorPct.getInt() + 5) {
                    armorWarned.remove(slot);
                }
            }
        }

        if (potion.get()) {
            Set<RegistryEntry<StatusEffect>> seen = new HashSet<>();
            for (StatusEffectInstance e : c.player.getStatusEffects()) {
                RegistryEntry<StatusEffect> type = e.getEffectType();
                if (e.isInfinite() || e.isAmbient()) continue;
                if (beneficialOnly.get() && !type.value().isBeneficial()) continue;
                seen.add(type);
                String name = type.value().getName().getString();
                lastName.put(type, name);
                lastDuration.put(type, e.getDuration());
                int left = e.getDuration() / 20;
                if (left <= potionSec.getInt()) {
                    lines.add(name + " ends in " + left + "s");
                    cols.add(left <= 3 ? 0xFFFF5555 : 0xFFFFD83A);
                    if (potionWarned.add(type)) beep = true;
                } else {
                    potionWarned.remove(type);
                }
            }
            long now = System.currentTimeMillis();
            for (RegistryEntry<StatusEffect> t : new ArrayList<>(lastDuration.keySet())) {
                if (seen.contains(t)) continue;
                int d = lastDuration.remove(t);
                String n = lastName.remove(t);
                potionWarned.remove(t);
                if (expired.get() && d <= 40 && n != null) { endedAt.put(n, now); beep = true; }
            }
            endedAt.values().removeIf(t -> now - t > 4000);
            for (String n : endedAt.keySet()) { lines.add(n + " ended"); cols.add(0xFFFF5555); }
        }

        if (beep && sound.get()) dev.tempest.util.Keys.sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f, 1f);
    }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        List<String> l = lines;
        List<Integer> k = cols;
        if (preview) {
            l = List.of("Diamond Chestplate 14%", "Speed II ends in 7s");
            k = List.of(0xFFFF5555, 0xFFFFD83A);
        }
        if (l.isEmpty()) return;
        drawLines(ctx, l, k);
    }
}
