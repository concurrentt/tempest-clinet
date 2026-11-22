package dev.tempest.gui;

import dev.tempest.modules.ClientSettingsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;

/**
 * Swaps Minecraft's title / singleplayer / multiplayer screens for the Tempest ones.
 * Hooked in by mixin/ScreenRedirectMixin (MinecraftClient#setScreen). Vanilla screens can still be
 * opened on purpose through {@link #openVanilla}, and the whole thing is switched off by the
 * "Custom Main Menu" setting (GUI & Profiles).
 */
public final class TempestScreens {
    private static boolean bypass;

    private TempestScreens() { }

    public static Screen redirect(Screen s) {
        if (bypass || !ClientSettingsModule.customMenuOn()) return s;
        if (s == null) {
            // Closing to "no screen" on the main menu makes vanilla open its own title screen further down in
            // setScreen, after this hook has run. Hand back the Tempest one first - also while a loading overlay
            // is up (a resource pack reload), which is exactly when vanilla used to slip its title screen in.
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.world == null ? new TempestTitleScreen() : null;
        }
        Class<?> k = s.getClass(); // exact class only, so other mods' subclasses are left alone
        if (k == TitleScreen.class) return new TempestTitleScreen();
        if (k == MultiplayerScreen.class) return new TempestMultiplayerScreen(new TempestTitleScreen());
        if (k == SelectWorldScreen.class) return new TempestSinglePlayerScreen(new TempestTitleScreen());
        if (k == net.minecraft.client.gui.screen.option.KeybindsScreen.class && ClientSettingsModule.styledWidgetsOn()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            return new TempestControlsScreen(mc.currentScreen);
        }
        return s;
    }

    /**
     * Safety net, every client tick: if Minecraft ever shows its own title screen without going through
     * setScreen's argument (e.g. after turning resource packs on or off), swap the Tempest one back in.
     */
    public static void tick(MinecraftClient mc) {
        if (bypass || mc == null || mc.world != null || !ClientSettingsModule.customMenuOn()) return;
        Screen s = mc.currentScreen;
        if (s != null && s.getClass() == TitleScreen.class) mc.setScreen(new TempestTitleScreen());
    }

    /** Opens a vanilla screen without it being swapped back. */
    public static void openVanilla(MinecraftClient client, Screen vanilla) {
        bypass = true;
        try { client.setScreen(vanilla); } finally { bypass = false; }
    }

    public static boolean isTempestMenu(Screen s) {
        return s instanceof TempestTitleScreen || s instanceof TempestSinglePlayerScreen || s instanceof TempestMultiplayerScreen;
    }
}
