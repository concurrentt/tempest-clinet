package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;

/** Merges nearby XP orbs to reduce lag. */
public final class ClumpsModule extends Module {
    public ClumpsModule() {
        super("clumps", "Clumps", "Merges nearby XP orbs to reduce entity lag.", Category.QOL, true);
    }

    public static boolean active() {
        return ModuleManager.enabled(ClumpsModule.class);
    }
}
