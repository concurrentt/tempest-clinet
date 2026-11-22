package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;

import java.util.Locale;

/**
 * Wear one of five Tempest capes. The choice is client-side: only you see it.
 * The texture files are assets/tempest/textures/cape/&lt;name&gt;.png (lower-case option name).
 */
public final class CapesModule extends Module {
    public final ModeSetting cape = add(new ModeSetting("cape", "Cape", "Tempest",
            "None", "Tempest", "Stormcloud", "Voltage", "Ember", "Frostbite",
            "Aurora", "Galaxy", "Sakura", "Inferno", "Ocean", "Void", "Royal", "Synthwave"));

    public CapesModule() {
        super("capes", "Capes", "Wear a Tempest cape: Tempest, Stormcloud, Voltage, Ember or Frostbite. Only you can see it.",
                Category.RENDER, true);
    }

    /** Try-on from the cosmetics carousel (Right Shift menu). */
    public static String previewCape;

    /** Lower-case file name of the cape to wear, or null when capes are off. */
    public static String selected() {
        if (previewCape != null) return previewCape.equals("None") ? null : previewCape.toLowerCase(Locale.ROOT);
        CapesModule m = ModuleManager.get(CapesModule.class);
        if (m == null || !m.isEnabled() || m.cape.is("None")) return null;
        return m.cape.get().toLowerCase(Locale.ROOT);
    }
}
