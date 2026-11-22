package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;

/** Shows the local player's normal vanilla nameplate when viewed in third person. */
public final class WhoAmIModule extends Module {
    public WhoAmIModule() {
        super("who_am_i", "WhoAmI", "Shows your own name above your character in third-person and Free Look.", Category.RENDER, true);
    }
}
