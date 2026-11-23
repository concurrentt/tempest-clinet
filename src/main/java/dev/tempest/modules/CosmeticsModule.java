package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;

/**
 * Wings and head pieces (render/CosmeticsFeature). Your choices are shared with other Tempest players through the
 * Tempest service, and theirs are shown on them (util/RemoteCosmetics). Pick them in Right Shift -> Looks, or here.
 */
public final class CosmeticsModule extends Module {
    public static final String[] WINGS = {"None", "Angel", "Demon", "Dragon", "Butterfly", "Phoenix", "Crystal", "Fairy"};
    public static final String[] HEADS = {"None", "Halo", "Devil Horns", "Crown", "Cat Ears", "Bunny Ears", "Antlers",
            "Flower Crown", "Witch Hat", "Top Hat"};

    public final ModeSetting wings = add(new ModeSetting("wings", "Wings", "None", WINGS));
    public final ModeSetting head = add(new ModeSetting("head", "Head", "None", HEADS));
    public static final String[] HALO_COLORS = {"Gold", "Holy White", "Void Purple", "Crimson", "Theme"};
    public final ModeSetting haloColor = add(new ModeSetting("halo_color", "Halo Color", "Gold", HALO_COLORS));
    public final NumberSetting wingSize = add(new NumberSetting("wing_size", "Wing Size", 1.0, 0.6, 1.6, 0.05));
    public final NumberSetting flapSpeed = add(new NumberSetting("flap_speed", "Flap Speed", 1.0, 0, 3, 0.1));
    public final BoolSetting share = add(new BoolSetting("share", "Show Mine To Others", true));
    public final BoolSetting seeOthers = add(new BoolSetting("see_others", "See Other Players' Cosmetics", true));

    public CosmeticsModule() {
        super("cosmetics", "Cosmetics", "Wings and head pieces. Other Tempest players see yours, and you see theirs.",
                Category.RENDER, true);
        add(new dev.tempest.module.ActionSetting("open_menu", "Cosmetics Menu", "Open", () -> {
            net.minecraft.client.MinecraftClient c = net.minecraft.client.MinecraftClient.getInstance();
            c.setScreen(new dev.tempest.gui.CosmeticsScreen(c.currentScreen, dev.tempest.gui.CosmeticsScreen.CAPES));
        }));
    }

    public static CosmeticsModule get() {
        CosmeticsModule m = ModuleManager.get(CosmeticsModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Try-on from the cosmetics carousel (Right Shift menu): shown instead of the saved choice while set. */
    public static String previewWings, previewHead;

    /** The module for drawing: enabled, or disabled but something is being tried on. */
    public static CosmeticsModule forRender() {
        CosmeticsModule m = ModuleManager.get(CosmeticsModule.class);
        if (m == null) return null;
        return m.isEnabled() || previewWings != null || previewHead != null ? m : null;
    }

    public static String wingsName(CosmeticsModule m) {
        if (previewWings != null) return previewWings;
        return m.isEnabled() ? m.wings.get() : "None";
    }

    public static String headName(CosmeticsModule m) {
        if (previewHead != null) return previewHead;
        return m.isEnabled() ? m.head.get() : "None";
    }


    public static void clearPreview() {
        previewWings = null;
        previewHead = null;
        CapesModule.previewCape = null;
    }

    public int haloRgb() { return haloRgb(haloColor.get()); }

    public static int haloRgb(String name) {
        return switch (name == null ? "" : name) {
            case "Holy White" -> 0xFFFFFF;
            case "Void Purple" -> 0xB47CFF;
            case "Crimson" -> 0xFF4A5A;
            case "Theme" -> Draw.accent() & 0xFFFFFF;
            default -> 0xFFD86A;
        };
    }
}
