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
 * Xray: chunks are rebuilt without any block that is not on the list (mixin/SectionBuilderXrayMixin), and what is
 * left is drawn fully lit (mixin/XrayLightMixin, plus Fullbright's gamma boost). Works with the vanilla chunk
 * builder, including Fabric API's Indigo renderer; Sodium builds chunks its own way and skips these hooks, so the
 * player gets a chat message if xray is on but no chunk ever went through the hook.
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
        instance = this;
    }

    /** Block -> shown, filled as chunks are built (the answer only changes with the settings). */
    private static final java.util.concurrent.ConcurrentHashMap<net.minecraft.block.Block, Boolean> SHOWS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Set by SectionBuilderXrayMixin whenever a chunk is built with xray on - proof the hook is running. */
    public static volatile boolean hooked;
    private long inWorldSince;
    private boolean warned;

    @Override public void onEnable() { SHOWS.clear(); hooked = false; inWorldSince = 0; warned = false; rebuild(); }
    @Override public void onDisable() { rebuild(); }

    /** Rebuild the chunks whenever a setting changes while xray is on. */
    @Override
    public void onTick(MinecraftClient c) {
        String now = "" + ores.get() + debris.get() + storage.get() + spawners.get() + lava.get();
        if (!now.equals(applied)) {
            boolean first = applied.isEmpty();
            applied = now;
            SHOWS.clear();
            if (!first && isEnabled()) rebuild();
        }

        // A few seconds in a world with no chunk built through the hook: say why instead of silently doing nothing.
        if (c.world == null) { inWorldSince = 0; return; }
        if (inWorldSince == 0) inWorldSince = System.currentTimeMillis();
        if (!warned && !hooked && System.currentTimeMillis() - inWorldSince > 4000) {
            warned = true;
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
                dev.tempest.util.Chat.info("Xray can't hide blocks while Sodium is installed - Sodium builds chunks its own way.");
            } else {
                dev.tempest.util.Chat.info("Xray couldn't hook the chunk builder - check the game log for Tempest mixin errors.");
            }
        }
    }

    private static void rebuild() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null && c.worldRenderer != null) c.worldRenderer.reload();
    }

    private static XrayModule instance;

    /** Called for nearly every block while chunks are built, so it skips the module lookup. */
    public static boolean active() {
        XrayModule m = instance;
        return m != null && m.isEnabled();
    }

    /** Should this block be drawn while xray is on? */
    public static boolean shows(BlockState state) {
        XrayModule m = ModuleManager.get(XrayModule.class);
        if (m == null || state == null) return true;
        Boolean cached = SHOWS.get(state.getBlock());
        if (cached != null) return cached;
        boolean v = compute(m, state);
        SHOWS.put(state.getBlock(), v);
        return v;
    }

    private static boolean compute(XrayModule m, BlockState state) {
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
