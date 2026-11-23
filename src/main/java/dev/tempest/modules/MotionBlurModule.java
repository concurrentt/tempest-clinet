package dev.tempest.modules;

import dev.tempest.mixin.GameRendererPostAccess;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Real motion blur: every frame is blended with the frames before it (a post effect with a persistent
 * "previous frame" buffer, assets/tempest/post_effect/motion_blur_N.json). Applied to the world only, so the
 * HUD and menus stay sharp. Uses vanilla's post-effect slot; spectating a creeper/spider/enderman takes it over
 * and blur comes back afterwards.
 */
public final class MotionBlurModule extends Module {
    private final NumberSetting strength = add(new NumberSetting("strength", "Strength", 5, 1, 9, 1));
    private static boolean broken;

    public MotionBlurModule() {
        super("motion_blur", "Motion Blur", "Blends frames together for a smooth motion trail.", Category.VISUALS, false);
    }

    private static boolean ours(Identifier id) {
        return id != null && id.getNamespace().equals("tempest") && id.getPath().startsWith("motion_blur_");
    }

    /** Every client tick, enabled or not (so switching it off removes the effect). */
    public static void update(MinecraftClient c) {
        if (broken || c.gameRenderer == null) return;
        MotionBlurModule m = ModuleManager.get(MotionBlurModule.class);
        try {
            GameRendererPostAccess gr = (GameRendererPostAccess) c.gameRenderer;
            Identifier current = gr.tempest$getPostProcessorId();
            boolean want = m != null && m.isEnabled() && c.world != null;
            if (want) {
                Identifier id = Identifier.of("tempest", "motion_blur_" + Math.max(1, Math.min(9, (int) Math.round(m.strength.get()))));
                if (!id.equals(current) && (current == null || ours(current))) gr.tempest$setPostProcessor(id);
            } else if (ours(current)) {
                c.gameRenderer.clearPostProcessor();
            }
        } catch (Throwable t) {
            broken = true;   // mixin not applied on this version: stay off instead of erroring every tick
            System.err.println("[Tempest] Motion blur unavailable: " + t);
        }
    }
}
