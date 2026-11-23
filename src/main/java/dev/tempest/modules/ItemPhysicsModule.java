package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;

/** Dropped items lie flat on the ground instead of spinning and bobbing. The work is done in ItemEntityRendererMixin. */
public class ItemPhysicsModule extends Module {
    public ItemPhysicsModule() { super("item_physics", "Item Physics", "Dropped items lie flat on the ground instead of spinning.", Category.VISUALS, false); }

    public static boolean active() { return ModuleManager.enabled(ItemPhysicsModule.class); }
}
