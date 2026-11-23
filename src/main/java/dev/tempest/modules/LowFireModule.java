package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Low Fire: how much fire you see. Screen Fire is the burning overlay in first person; Ground Fire is fire blocks
 * in the world. 100% = vanilla, lower = shorter flames, 0% = gone.
 */
public final class LowFireModule extends Module {
    private final NumberSetting screen = add(new NumberSetting("screen", "Screen Fire Height (%)", 40, 0, 100, 5));
    private final NumberSetting ground = add(new NumberSetting("ground", "Ground Fire Height (%)", 40, 0, 100, 5));

    /** Set while the block mesher hook has already squashed the current fire block (so the model wrapper skips it). */
    public static final ThreadLocal<Boolean> MATRIX_SCALED = ThreadLocal.withInitial(() -> false);

    private double appliedGround = -1;
    private int settleTicks;

    public LowFireModule() {
        super("low_fire", "Low Fire", "Lower or remove the fire on your screen and fire blocks on the ground.", Category.VISUALS, true);
    }

    /** Installs the fire model wrapper (render/LowFireModel). Called once at startup. */
    public static void registerModels() {
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(ctx ->
                ctx.modifyBlockModelAfterBake().register((model, context) -> {
                    var st = context.state();
                    boolean fire = st.isOf(net.minecraft.block.Blocks.FIRE) || st.isOf(net.minecraft.block.Blocks.SOUL_FIRE);
                    return fire && !(model instanceof dev.tempest.render.LowFireModel) ? new dev.tempest.render.LowFireModel(model) : model;
                }));
    }

    @Override public void onEnable() { rebuild(); }
    @Override public void onDisable() { rebuild(); }

    /** Ground fire is baked into chunk meshes, so rebuild them once the slider stops moving. */
    @Override
    public void onTick(MinecraftClient c) {
        double g = ground.get();
        if (appliedGround < 0) { appliedGround = g; return; }
        if (g != appliedGround) {
            if (++settleTicks >= 8) {
                appliedGround = g;
                settleTicks = 0;
                if (isEnabled()) rebuild();
            }
        } else {
            settleTicks = 0;
        }
    }

    private static void rebuild() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null && c.worldRenderer != null && c.world != null) c.worldRenderer.reload();
    }

    private static LowFireModule on() {
        LowFireModule m = ModuleManager.get(LowFireModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public static boolean shouldHideOverlay() {
        LowFireModule m = on();
        return m != null && m.screen.get() <= 0;
    }

    /** Downward shift of the first-person fire overlay. */
    public static double offset() {
        LowFireModule m = on();
        return m == null ? 0.0 : (1.0 - m.screen.get() / 100.0) * 0.6;
    }

    /** Height of fire blocks, 0..1 (1 = vanilla). */
    public static float groundHeight() {
        LowFireModule m = on();
        return m == null ? 1f : (float) (m.ground.get() / 100.0);
    }
}
