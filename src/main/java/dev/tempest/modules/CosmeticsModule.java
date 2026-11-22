package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;

/**
 * Wings and head pieces drawn on your own player (render/CosmeticsFeature). Client-side: only you see them.
 * Pick them in the Right Shift menu (Cosmetics) or here.
 */
public final class CosmeticsModule extends Module {
    public static final String[] WINGS = {"None", "Angel", "Demon", "Dragon", "Butterfly", "Phoenix", "Crystal", "Fairy"};
    public static final String[] HEADS = {"None", "Halo", "Devil Horns", "Crown", "Cat Ears", "Bunny Ears", "Antlers",
            "Flower Crown", "Witch Hat", "Top Hat"};
    public static final String[] AURAS = {"None", "Sparkles", "Hearts", "Flames", "Snow", "Notes", "Theme Orbit", "Rings"};

    public final ModeSetting wings = add(new ModeSetting("wings", "Wings", "None", WINGS));
    public final ModeSetting head = add(new ModeSetting("head", "Head", "None", HEADS));
    public final ModeSetting aura = add(new ModeSetting("aura", "Aura", "None", AURAS));
    public final NumberSetting auraSpeed = add(new NumberSetting("aura_speed", "Aura Speed", 1.0, 0.25, 3.0, 0.05));
    public final ModeSetting haloColor = add(new ModeSetting("halo_color", "Halo Color", "Gold", "Gold", "Holy White", "Void Purple", "Crimson", "Theme"));
    public final NumberSetting wingSize = add(new NumberSetting("wing_size", "Wing Size", 1.0, 0.6, 1.6, 0.05));
    public final NumberSetting flapSpeed = add(new NumberSetting("flap_speed", "Flap Speed", 1.0, 0, 3, 0.1));

    public CosmeticsModule() {
        super("cosmetics", "Cosmetics", "Wings, head pieces and auras. Only you see them.",
                Category.RENDER, true);
    }

    public static CosmeticsModule get() {
        CosmeticsModule m = ModuleManager.get(CosmeticsModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Try-on from the cosmetics carousel (Right Shift menu): shown instead of the saved choice while set. */
    public static String previewWings, previewHead, previewAura;

    /** The module for drawing: enabled, or disabled but something is being tried on. */
    public static CosmeticsModule forRender() {
        CosmeticsModule m = ModuleManager.get(CosmeticsModule.class);
        if (m == null) return null;
        return m.isEnabled() || previewWings != null || previewHead != null || previewAura != null ? m : null;
    }

    public static String wingsName(CosmeticsModule m) {
        if (previewWings != null) return previewWings;
        return m.isEnabled() ? m.wings.get() : "None";
    }

    public static String headName(CosmeticsModule m) {
        if (previewHead != null) return previewHead;
        return m.isEnabled() ? m.head.get() : "None";
    }

    public static String auraName(CosmeticsModule m) {
        if (previewAura != null) return previewAura;
        return m.isEnabled() ? m.aura.get() : "None";
    }

    // Auras are drawn by render/CosmeticsFeature with Tempest's own sprites (no Minecraft particles).

    public static void clearPreview() {
        previewWings = null;
        previewHead = null;
        previewAura = null;
        CapesModule.previewCape = null;
    }

    public int haloRgb() {
        return switch (haloColor.get()) {
            case "Holy White" -> 0xFFFFFF;
            case "Void Purple" -> 0xB47CFF;
            case "Crimson" -> 0xFF4A5A;
            case "Theme" -> Draw.accent() & 0xFFFFFF;
            default -> 0xFFD86A;
        };
    }
}
