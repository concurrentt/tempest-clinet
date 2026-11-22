package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Totem tweaks: pop animation size/speed/rotation, held totem size, no equip animation. */
public class TotemTweaksModule extends Module {
    private final BoolSetting pop = add(new BoolSetting("pop", "Pop Animation", true));
    private final NumberSetting popSize = add(new NumberSetting("pop_size", "Pop Size", 0.2, 0.05, 1.5, 0.05));
    private final NumberSetting popSpeed = add(new NumberSetting("pop_speed", "Pop Speed", 1.5, 0.25, 4.0, 0.25));
    private final BoolSetting noSpin = add(new BoolSetting("no_spin", "Disable Rotation", true));
    private final BoolSetting lockPos = add(new BoolSetting("lock_pos", "Lock Position (no wobble)", true));
    private final NumberSetting heldSize = add(new NumberSetting("held_size", "Held Totem Size", 0.75, 0.3, 1.5, 0.05));
    private final BoolSetting noEquip = add(new BoolSetting("no_equip", "Disable Equip Animation", true));

    public TotemTweaksModule() { super("totem_tweaks", "Totem Tweaks", "Smaller, faster totem pop and held totem; no equip animation.", Category.PVP, true); }

    private static TotemTweaksModule on() {
        TotemTweaksModule m = ModuleManager.get(TotemTweaksModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public static boolean popHidden() { TotemTweaksModule m = on(); return m != null && !m.pop.get(); }
    public static float popScale() { TotemTweaksModule m = on(); return m == null || !m.pop.get() ? 1f : m.popSize.getFloat(); }
    public static float popSpeed() { TotemTweaksModule m = on(); return m == null ? 1f : m.popSpeed.getFloat(); }
    public static boolean noSpin() { TotemTweaksModule m = on(); return m != null && m.noSpin.get(); }
    public static boolean lockPos() { TotemTweaksModule m = on(); return m != null && m.lockPos.get(); }
    public static boolean noEquip() { TotemTweaksModule m = on(); return m != null && m.noEquip.get(); }

    /**
     * Size of a totem held in first person, or 1. Applied around the item itself in HeldItemRenderer.renderItem,
     * so it shrinks in place in your hand.
     */
    public static float heldScale(Object entity, ItemStack stack, Object displayContext) {
        TotemTweaksModule m = on();
        MinecraftClient c = MinecraftClient.getInstance();
        if (m == null || c.player == null || entity != c.player || stack == null || !stack.isOf(Items.TOTEM_OF_UNDYING)) return 1f;
        if (displayContext == null || !String.valueOf(displayContext).toUpperCase(java.util.Locale.ROOT).contains("FIRST_PERSON")) return 1f;
        return m.heldSize.getFloat();
    }
}
