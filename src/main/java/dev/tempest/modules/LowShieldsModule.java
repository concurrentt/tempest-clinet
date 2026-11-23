package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Low Shields: the shield sits off to the side of the screen and is small while blocking, so it covers less of
 * your view. Works like the "Side Shield" resource pack: Tempest ships those shield model positions as a built-in
 * pack (resources/resourcepacks/low_shields) and this module switches it on and off.
 */
public final class LowShieldsModule extends Module {
    public static final Identifier PACK = Identifier.of("tempest", "low_shields");
    private static final String PACK_ID = PACK.toString();
    private int syncTimer;

    public LowShieldsModule() {
        super("low_shields", "Low Shields", "Moves the shield to the side of your screen and makes it small while blocking.",
                Category.VISUALS, false);
    }

    /** Registers the built-in pack. Call once during client init. */
    public static void registerPack() {
        FabricLoader.getInstance().getModContainer("tempest").ifPresent(mod ->
                ResourceManagerHelper.registerBuiltinResourcePack(PACK, mod, Text.literal("Tempest Low Shields"), ResourcePackActivationType.NORMAL));
    }

    @Override public void onEnable() { apply(true); }

    @Override public void onDisable() { apply(false); }

    /** Keeps the pack in step with the module (e.g. after loading a config, or if the pack was switched off by hand). */
    @Override
    public void onTick(MinecraftClient c) {
        if (++syncTimer < 40) return;
        syncTimer = 0;
        if (c.getOverlay() != null || c.world == null) return;
        ResourcePackManager mgr = c.getResourcePackManager();
        if (mgr.getProfile(PACK_ID) == null) return;
        if (mgr.getEnabledIds().contains(PACK_ID) != isEnabled()) apply(isEnabled());
    }

    private static void apply(boolean on) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.getResourcePackManager() == null) return;
        ResourcePackManager mgr = c.getResourcePackManager();
        if (mgr.getProfile(PACK_ID) == null) return;
        boolean changed = on ? mgr.enable(PACK_ID) : mgr.disable(PACK_ID);
        if (!changed) return;
        c.options.refreshResourcePacks(mgr);
        c.reloadResources();
    }

    public static boolean on() { return ModuleManager.enabled(LowShieldsModule.class); }
}
