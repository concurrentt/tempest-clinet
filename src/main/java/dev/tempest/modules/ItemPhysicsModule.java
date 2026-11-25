package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;

/**
 * Dropped items, your way. Flat: they lie flat on the ground instead of spinning and bobbing. Hover: they float a
 * little above the ground and bob up and down smoothly, spinning slowly if you like. The work is done in
 * ItemEntityRendererMixin.
 */
public class ItemPhysicsModule extends Module {
    public final ModeSetting style = add(new ModeSetting("style", "Style", "Flat", "Flat", "Hover"));
    public final NumberSetting height = add(new NumberSetting("hover_height", "Hover Height", 0.25, 0.05, 0.75, 0.05));
    public final NumberSetting bob = add(new NumberSetting("bob_height", "Bob Height", 0.08, 0.0, 0.3, 0.01));
    public final NumberSetting speed = add(new NumberSetting("bob_speed", "Bob Speed", 1.0, 0.2, 3.0, 0.1));
    public final BoolSetting spin = add(new BoolSetting("hover_spin", "Spin While Hovering", true));

    public ItemPhysicsModule() {
        super("item_physics", "Item Physics", "Dropped items lie flat, or hover and bob above the ground.", Category.VISUALS, false);
    }

    private static ItemPhysicsModule get() { return ModuleManager.get(ItemPhysicsModule.class); }

    public static boolean active() { return ModuleManager.enabled(ItemPhysicsModule.class); }

    /** Lying flat on the ground (the original Item Physics). */
    public static boolean flat() { return active() && get().style.is("Flat"); }

    /** Floating and bobbing. */
    public static boolean hover() { return active() && get().style.is("Hover"); }

    /** Height of a hovering item above its resting spot, in blocks. {@code age} in ticks, {@code offset} = its own phase. */
    public static float hoverY(float age, float offset) {
        ItemPhysicsModule m = get();
        float t = age / 20f * (float) (Math.PI * 2) * 0.35f * m.speed.getFloat();   // ~0.35 bobs a second at speed 1
        return m.height.getFloat() + (float) Math.sin(t + offset) * m.bob.getFloat();
    }

    public static boolean hoverSpin() { return get().spin.get(); }
}
