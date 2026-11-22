package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;

/**
 * Xray: chunks are rebuilt without any block that is not on the list (mixin/BlockRenderManagerMixin), and what is
 * left is drawn fully lit (mixin/XrayLightMixin). Works with the vanilla renderer; renderer mods like Sodium
 * build chunks their own way and skip these hooks.
 */
public final class XrayModule extends Module {
    private final BoolSetting ores = add(new BoolSetting("ores", "Ores", true));
    private final BoolSetting debris = add(new BoolSetting("debris", "Ancient Debris", true));
    private final BoolSetting storage = add(new BoolSetting("storage", "Chests & Barrels", false));
    private final BoolSetting spawners = add(new BoolSetting("spawners", "Spawners", true));
    private final BoolSetting lava = add(new BoolSetting("lava", "Lava", true));

    private String applied = "";

    public XrayModule() {
        super("xray", "Xray", "See ores through walls: everything else is hidden and ores are fully lit.", Category.RENDER, false);
    }

    @Override public void onEnable() { rebuild(); }
    @Override public void onDisable() { rebuild(); }

    /** Rebuild the chunks whenever a setting changes while xray is on. */
    @Override
    public void onTick(MinecraftClient c) {
        String now = "" + ores.get() + debris.get() + storage.get() + spawners.get() + lava.get();
        if (!now.equals(applied)) {
            boolean first = applied.isEmpty();
            applied = now;
            if (!first && isEnabled()) rebuild();
        }
    }

    private static void rebuild() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null && c.worldRenderer != null) c.worldRenderer.reload();
    }

    public static boolean active() { return ModuleManager.enabled(XrayModule.class); }

    /** Should this block be drawn while xray is on? */
    public static boolean shows(BlockState state) {
        XrayModule m = ModuleManager.get(XrayModule.class);
        if (m == null || state == null) return true;
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        if (m.ores.get() && (path.endsWith("_ore") || path.startsWith("raw_") && path.endsWith("_block"))) return true;
        if (m.debris.get() && path.equals("ancient_debris")) return true;
        if (m.storage.get() && (path.endsWith("chest") || path.equals("barrel") || path.endsWith("shulker_box"))) return true;
        if (m.spawners.get() && (path.equals("spawner") || path.equals("trial_spawner"))) return true;
        return false;
    }

    /** Kept for the face-culling hooks: an ore is anything xray shows. */
    public static boolean isOre(BlockState state) { return shows(state); }

    public static boolean showsFluid(FluidState fluid) {
        XrayModule m = ModuleManager.get(XrayModule.class);
        if (m == null || fluid == null || fluid.isEmpty()) return true;
        boolean isLava = fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA;
        return isLava && m.lava.get();
    }
}
