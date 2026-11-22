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
import dev.tempest.util.ProfileManager;

public class ClientSettingsModule extends Module {
    public final ModeSetting theme = add(new ModeSetting("theme", "Theme", "Tempest", "Tempest", "Midnight", "Crimson", "Emerald", "Violet", "Amber", "Custom"));
    public final dev.tempest.module.ColorSetting customAccent = add(new dev.tempest.module.ColorSetting("custom_accent", "Custom Theme Colour", 0xFF4FC3F7));
    public final ModeSetting menuBackground = add(new ModeSetting("menu_bg", "Menu Background", "Nebula",
            "Nebula", "Galaxy", "Aurora", "Black Hole", "Ringed Planet", "Blood Moon", "Animated", "Plain"));
    public final BoolSetting animations = add(new BoolSetting("animations", "Menu Animations", true));
    public final ModeSetting animationEasing = add(new ModeSetting("anim_easing", "Animation Easing", "Smooth", "Smooth", "Linear", "Snappy"));
    public final NumberSetting guiScale = add(new NumberSetting("gui_scale", "GUI Size", 1.0, 0.6, 1.0, 0.05));
    public final BoolSetting worldLoading = add(new BoolSetting("world_loading", "Tempest Loading Screen", true));
    public final BoolSetting customMenu = add(new BoolSetting("custom_menu", "Custom Main Menu", true));
    public final BoolSetting styledWidgets = add(new BoolSetting("styled_widgets", "Tempest Style Buttons & Options", true));
    public final BoolSetting checkUpdates = add(new BoolSetting("check_updates", "Check For Updates", true));
    private final ModeSetting slot = add(new ModeSetting("slot", "Profile Slot", "1", "1", "2", "3"));

    public ClientSettingsModule() {
        super("client", "GUI & Profiles", "Theme, menu key, animations, profiles.", Category.CLIENT, true);
        add(new KeySetting("menu_key", "Open Menu Key", TempestClient.openMenuKey));
        add(new ActionSetting("save", "Save to Slot", "Save", () -> ProfileManager.save(slot.get())));
        add(new ActionSetting("load", "Load from Slot", "Load", () -> ProfileManager.load(slot.get())));
        add(new ActionSetting("export", "Share (copy to clipboard)", "Copy", ProfileManager::exportClipboard));
        add(new ActionSetting("import", "Import (from clipboard)", "Paste", ProfileManager::importClipboard));
        add(new ActionSetting("check_now", "Check for updates now", "Check", () -> dev.tempest.util.Updater.checkAsync()));
        keyHint = "Profiles store enabled mods, settings and HUD layout. Esc while binding a key unbinds it.";
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
