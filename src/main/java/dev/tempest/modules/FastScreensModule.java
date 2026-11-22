package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;

/** Skips or speeds up loading screens. */
public final class FastScreensModule extends Module {
    private final BoolSetting fastResource = add(new BoolSetting("fast_resources", "Fast Resource Reload", true));
    private final BoolSetting skipWorldLoading = add(new BoolSetting("skip_world_loading", "Skip World Loading", true));

    public FastScreensModule() {
        super("fast_screens", "Fast Screens", "Speed up resource reload and close world loading screens faster.", Category.QOL, false);
    }

    public static boolean fastResourceReload() {
        FastScreensModule m = ModuleManager.get(FastScreensModule.class);
        return m != null && m.isEnabled() && m.fastResource.get();
    }

    public static boolean skipWorldLoading() {
        FastScreensModule m = ModuleManager.get(FastScreensModule.class);
        return m != null && m.isEnabled() && m.skipWorldLoading.get();
    }
}
