package dev.tempest.gui;

import dev.tempest.modules.CinematicModule;
import net.minecraft.client.gui.DrawContext;

/**
 * HUD-layer visuals (cinematic grade). The sky itself is drawn in the world by render/SkyRenderer.
 * Registered before every vanilla HUD element, so these sit under the hotbar, chat and crosshair.
 */
public final class VisualsRenderer {
    private VisualsRenderer() { }

    public static void render(DrawContext ctx, float tickDelta) {
        try {
            CinematicModule.render(ctx);
        } catch (RuntimeException e) {
            // A visual effect must never take the game down; skip this frame instead.
        }
    }
}
