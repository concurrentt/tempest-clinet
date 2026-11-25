package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import dev.tempest.util.Chat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Fullbright: see in the dark, using gamma. The lightmap is built with a brightness (gamma) far past the slider's
 * 100% maximum - Strength 15 means 1500%. mixin/FullbrightLightmapMixin hands this value to the lightmap only,
 * so the Brightness slider and options.txt keep the player's real value and nothing needs restoring.
 *
 * Xray turns this on too, so ores in caves are always lit.
 */
public class FullbrightModule extends Module {
    private final NumberSetting strength = add(new NumberSetting("strength", "Strength", 15, 2, 20, 1));

    /** Set by FullbrightLightmapMixin the first time it boosts the lightmap - used for the "didn't hook" warning. */
    public static volatile boolean hooked;
    private long onSince;
    private boolean warned;

    public FullbrightModule() { super("fullbright", "Fullbright", "See everything, even in pitch-black caves.", Category.VISUALS, false); }

    /** The gamma the lightmap should use right now, or NaN to use the player's own Brightness. */
    public static double gammaOverride() {
        FullbrightModule m = ModuleManager.get(FullbrightModule.class);
        if (m != null && m.isEnabled()) return m.strength.get();
        if (XrayModule.active()) return 15.0;
        return Double.NaN;
    }

    @Override public void onEnable() { onSince = 0; warned = false; }

    @Override
    public void onTick(MinecraftClient c) {
        // Older versions wrote the huge value into the option itself; put a sane value back if one is left over.
        if (c.options != null) {
            SimpleOption<Double> gamma = c.options.getGamma();
            if (gamma.getValue() > 1.0) gamma.setValue(1.0);
        }
        // Tell the player once if the lightmap hook never ran (another mod replacing the lightmap, or a mixin error).
        if (c.world == null) { onSince = 0; return; }
        if (onSince == 0) onSince = System.currentTimeMillis();
        if (!warned && !hooked && System.currentTimeMillis() - onSince > 4000) {
            warned = true;
            Chat.info("Fullbright couldn't hook the lightmap - check the game log for Tempest mixin errors.");
        }
    }

    /** GameOptionsSaveMixin: options.txt is about to be written. Fullbright no longer touches the option. */
    public static void beforeSave(MinecraftClient c) { }

    /** GameOptionsSaveMixin: the save is done. */
    public static void afterSave(MinecraftClient c) { }
}
