package dev.tempest.gui;

import dev.tempest.module.ModuleManager;
import dev.tempest.modules.CinematicModule;
import dev.tempest.modules.FirefliesModule;
import dev.tempest.util.SkyView;
import net.minecraft.client.gui.DrawContext;

/**
 * HUD-layer visuals (fireflies, cinematic grade). The sky itself is drawn in the world by render/SkyRenderer.
 * Registered before every vanilla HUD element, so these sit under the hotbar, chat and crosshair.
 */
public final class VisualsRenderer {
    private VisualsRenderer() { }

    public static void render(DrawContext ctx, float tickDelta) {
        try {
            if (ModuleManager.enabled(FirefliesModule.class) && SkyView.begin(ctx, tickDelta)) FirefliesModule.render(ctx);
            CinematicModule.render(ctx);
        } catch (RuntimeException e) {
            // A visual effect must never take the game down; skip this frame instead.
        }
    }
}
