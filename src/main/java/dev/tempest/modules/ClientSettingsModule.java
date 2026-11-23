package dev.tempest.modules;

import dev.tempest.TempestClient;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.KeySetting;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;

public class ClientSettingsModule extends Module {
    public final ModeSetting theme = add(new ModeSetting("theme", "Theme", "Tempest", "Tempest", "Midnight", "Crimson", "Emerald", "Violet", "Amber", "Custom"));
    public final dev.tempest.module.ColorSetting customAccent = add(new dev.tempest.module.ColorSetting("custom_accent", "Custom Theme Colour", 0xFF4FC3F7));
    public final ModeSetting menuBackground = add(new ModeSetting("menu_bg", "Menu Background", "Nebula",
            "Nebula", "Galaxy", "Aurora", "Black Hole", "Ringed Planet", "Blood Moon", "Animated", "Plain"));
    public final ModeSetting font = add(new ModeSetting("font", "Font", "Inter", "Inter", "Minecraft"));
    public final ModeSetting menuBlur = add(new ModeSetting("menu_blur", "Menu Blur", "Medium", "Off", "Light", "Medium", "Strong"));
    public final BoolSetting animations = add(new BoolSetting("animations", "Menu Animations", true));
    public final ModeSetting animationEasing = add(new ModeSetting("anim_easing", "Animation Easing", "Smooth", "Smooth", "Linear", "Snappy"));
    public final NumberSetting guiScale = add(new NumberSetting("gui_scale", "GUI Size", 1.0, 0.6, 1.0, 0.05));
    public final BoolSetting worldLoading = add(new BoolSetting("world_loading", "Tempest Loading Screen", true));
    public final BoolSetting customMenu = add(new BoolSetting("custom_menu", "Custom Main Menu", true));
    public final BoolSetting styledWidgets = add(new BoolSetting("styled_widgets", "Tempest Style Buttons & Options", true));
    public final BoolSetting checkUpdates = add(new BoolSetting("check_updates", "Check For Updates", true));

    public ClientSettingsModule() {
        super("client", "GUI & Configs", "Theme, menu key, animations, configs.", Category.CLIENT, true);
        add(new KeySetting("menu_key", "Open Menu Key", TempestClient.openMenuKey));
        add(new ActionSetting("configs", "Configs (named setups)", "Open", () -> {
            net.minecraft.client.MinecraftClient c = net.minecraft.client.MinecraftClient.getInstance();
            c.setScreen(new dev.tempest.gui.ConfigsScreen(c.currentScreen));
        }));
        add(new ActionSetting("check_now", "Check for updates now", "Check", () -> dev.tempest.util.Updater.checkAsync()));
        keyHint = "Configs store enabled mods, settings and HUD layout under a name. Esc while binding a key unbinds it.";
    }

    @Override public boolean alwaysOn() { return true; }

    public static int themeIndex() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m == null ? 0 : m.theme.index();
    }

    /** "Custom" theme: the colour picked in the theme editor, else -1. */
    public static int customAccent() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m != null && m.theme.is("Custom") ? m.customAccent.get() : -1;
    }

    public static String menuBackground() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m == null ? "Nebula" : m.menuBackground.get();
    }

    /** Whether the Tempest screen covers joining / leaving a world or server. */
    public static boolean worldLoadingOn() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m == null || m.worldLoading.get();
    }

    /** Whether the Tempest title / singleplayer / multiplayer screens replace the vanilla ones. */
    public static boolean customMenuOn() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m == null || m.customMenu.get();
    }

    /** Vanilla buttons, sliders and menu backgrounds (Options, Video Settings, Pause ...) drawn in the Tempest style. */
    public static boolean styledWidgetsOn() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m == null || m.styledWidgets.get();
    }

    /** How strongly the game behind the mods menu is blurred: 0 (off) .. 10, Minecraft's own blur scale. */
    public static int menuBlur() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        if (m == null) return 5;
        return switch (m.menuBlur.get()) { case "Off" -> 0; case "Light" -> 3; case "Strong" -> 9; default -> 6; };
    }

    public static boolean animationsOn() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m == null || m.animations.get();
    }

    public static String animationEasing() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m == null ? "Smooth" : m.animationEasing.get();
    }

    public static float guiScale() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m == null ? 1.0f : m.guiScale.getFloat();
    }

    public static boolean checkUpdatesOn() {
        ClientSettingsModule m = ModuleManager.get(ClientSettingsModule.class);
        return m == null || m.checkUpdates.get();
    }
}
