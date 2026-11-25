package dev.tempest.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;

/**
 * Account switching is done by In-Game Account Switcher (IAS, by The_Fireplace and VidTu, LGPL-3.0-or-later),
 * which ships inside the Tempest jar (build.gradle: include). IAS comes with its own Microsoft sign-in app, so
 * players can add and switch accounts without Tempest needing an approved app ID of its own.
 *
 * Its screen is opened by reflection so Tempest still builds and runs if IAS is ever missing or changes.
 */
public final class Ias {
    private static final String SCREEN = "ru.vidtu.ias.screen.AccountScreen";
    private static Constructor<?> ctor;
    private static boolean looked;

    private Ias() { }

    /** True when IAS is loaded and its account screen can be opened. */
    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("ias") && constructor() != null;
    }

    /** IAS's account list, returning to parent when closed - or null if IAS isn't there. */
    public static Screen screen(Screen parent) {
        Constructor<?> c = constructor();
        if (c == null) return null;
        try {
            return (Screen) c.newInstance(parent);
        } catch (Throwable t) {
            System.out.println("[Tempest] Couldn't open In-Game Account Switcher: " + t);
            return null;
        }
    }

    private static synchronized Constructor<?> constructor() {
        if (!looked) {
            looked = true;
            try {
                ctor = Class.forName(SCREEN).getConstructor(Screen.class);
            } catch (Throwable t) {
                ctor = null;
            }
        }
        return ctor;
    }
}
