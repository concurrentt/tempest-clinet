package dev.tempest.gui;

import dev.tempest.modules.ClientSettingsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Blur behind Tempest's in-game menus (Right Shift menu and mods menu), at the strength chosen in GUI & Configs ->
 * Menu Blur. Uses Minecraft's own menu blur (one cheap pass), which reads its strength from the "Menu Background
 * Blur" option: that option is borrowed while a Tempest menu is open and put back when it closes (never saved).
 */
public final class MenuBlur {
    private static Integer saved;

    private MenuBlur() { }

    /** Blurs everything drawn so far (the world and HUD). Returns false when blur is off or there's no world. */
    public static boolean apply(DrawContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        int strength = ClientSettingsModule.menuBlur();
        if (strength <= 0 || c.world == null) return false;
        try {
            var opt = c.options.getMenuBackgroundBlurriness();
            if (saved == null) saved = opt.getValue();
            if (opt.getValue() != strength) opt.setValue(strength);
            ctx.applyBlur();
            return true;
        } catch (Throwable t) {
            return false;   // e.g. something already blurred this frame
        }
    }

    /** Puts the player's own blur strength back (call when a Tempest menu closes). */
    public static void restore() {
        if (saved == null) return;
        try { MinecraftClient.getInstance().options.getMenuBackgroundBlurriness().setValue(saved); } catch (Throwable ignored) { }
        saved = null;
    }
}
