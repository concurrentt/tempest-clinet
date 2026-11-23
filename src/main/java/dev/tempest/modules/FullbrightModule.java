package dev.tempest.modules;

import dev.tempest.mixin.SimpleOptionAccessor;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Fullbright: see in the dark. Sets the game's brightness (gamma) far past the slider's 100% maximum - written
 * straight into the option so the slider's 0-1 limit doesn't clamp it. The old version only set it to 1.0,
 * which is just vanilla's "Bright" and does nothing in caves.
 *
 * Your real brightness is put back when Fullbright is switched off, and options.txt always saves your real value
 * (GameOptionsMixin swaps it in around every save), so nothing breaks if the game closes with Fullbright on.
 */
public class FullbrightModule extends Module {
    private final NumberSetting strength = add(new NumberSetting("strength", "Strength", 15, 2, 20, 1));
    private static Double original = null;
    private static boolean applied;

    public FullbrightModule() { super("fullbright", "Fullbright", "See everything, even in pitch-black caves.", Category.VISUALS, false); }

    @Override
    public void onInit() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> restore(c));
    }

    @SuppressWarnings("unchecked")
    private static void raw(SimpleOption<Double> option, double v) {
        try {
            ((SimpleOptionAccessor<Double>) (Object) option).tempest$setRawValue(v);
        } catch (Throwable t) {
            option.setValue(Math.max(0.0, Math.min(1.0, v)));   // accessor missing: best effort, never crash
        }
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (c.options == null) return;
        SimpleOption<Double> gamma = c.options.getGamma();
        double target = strength.get();
        double cur = gamma.getValue();
        if (original == null) original = cur > 1.0 ? 1.0 : cur;
        if (cur != target) raw(gamma, target);
        applied = true;
    }

    @Override public void onDisable() { restore(MinecraftClient.getInstance()); }

    private static void restore(MinecraftClient c) {
        if (c.options != null && original != null) {
            raw(c.options.getGamma(), original);
            original = null;
        }
        applied = false;
    }

    /** GameOptionsMixin: options.txt is about to be written - put the real brightness back for the save. */
    public static void beforeSave(MinecraftClient c) {
        if (applied && original != null && c.options != null) raw(c.options.getGamma(), original);
    }

    /** GameOptionsMixin: the save is done - Fullbright again (the next tick would do it too). */
    public static void afterSave(MinecraftClient c) {
        FullbrightModule m = ModuleManager.get(FullbrightModule.class);
        if (applied && m != null && m.isEnabled() && c.options != null) raw(c.options.getGamma(), m.strength.get());
    }
}
