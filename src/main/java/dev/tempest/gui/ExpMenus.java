package dev.tempest.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Every Right Shift menu style in one place, and which one you picked (the Style button in the top-right corner of
 * each menu). The choice is saved in config/tempest/exp-menu.txt; Orbit Ring is the default.
 */
public final class ExpMenus {
    public record Style(String id, String name, String line, Function<Screen, Screen> make) { }

    public static final List<Style> STYLES = List.of(
            new Style("orbit", "Orbit Ring", "Categories on a turning ring around the logo", ExpOrbitScreen::new),
            new Style("radial", "Radial Wheel", "Hold Right Shift, point, let go", ExpRadialScreen::new),
            new Style("palette", "Command Palette", "Type to find and toggle anything", ExpPaletteScreen::new),
            new Style("drawer", "Sidebar Drawer", "Slides in while you keep playing", ExpDrawerScreen::new),
            new Style("quick", "Quick Toggles", "Big tiles for your starred mods", ExpQuickScreen::new),
            new Style("dock", "Dock", "Icon bar along the bottom", ExpDockScreen::new),
            new Style("constellation", "Constellation", "Mods as stars, bright ones are on", ExpConstellationScreen::new),
            new Style("cards", "Storm Cards", "A hand of cards that flip over", ExpCardsScreen::new),
            new Style("hub", "Split Hub", "Your player next to the categories", ExpHubScreen::new));

    private static String cached;

    private ExpMenus() { }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("tempest").resolve("exp-menu.txt"); }

    public static String current() {
        if (cached == null) {
            try { cached = Files.readString(file(), StandardCharsets.UTF_8).trim(); } catch (Exception e) { cached = "orbit"; }
            if (STYLES.stream().noneMatch(s -> s.id().equals(cached))) cached = "orbit";
        }
        return cached;
    }

    public static void set(String id) {
        cached = id;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), id, StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }

    public static Style style(String id) {
        for (Style s : STYLES) if (s.id().equals(id)) return s;
        return STYLES.get(0);
    }

    /** The Right Shift menu in the chosen style. */
    public static Screen open(Screen parent) { return style(current()).make().apply(parent); }

    /** Switches style and opens it right away (from the style picker). */
    static void use(String id, Screen parent) {
        set(id);
        MinecraftClient.getInstance().setScreen(open(parent));
    }
}
