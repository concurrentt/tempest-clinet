package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;

public class StreamerModeModule extends Module {
    private final BoolSetting hideF3 = add(new BoolSetting("hide_f3", "Hide F3 Screen", true));
    private final BoolSetting hideCoords = add(new BoolSetting("hide_coords", "Hide Coordinates (HUD, waypoints, death)", true));

    public StreamerModeModule() { super("streamer_mode", "Streamer Mode", "Hides coordinate leaks while streaming or recording.", Category.UTILITY, false); }

    public static boolean hideF3() {
        StreamerModeModule m = ModuleManager.get(StreamerModeModule.class);
        return m != null && m.isEnabled() && m.hideF3.get();
    }

    public static boolean hideCoords() {
        StreamerModeModule m = ModuleManager.get(StreamerModeModule.class);
        return m != null && m.isEnabled() && m.hideCoords.get();
    }
}
