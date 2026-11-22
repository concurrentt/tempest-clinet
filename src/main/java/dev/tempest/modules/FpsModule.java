package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class FpsModule extends HudModule {
    public FpsModule() { super("fps", "FPS", "Shows your frame rate.", Category.HUD, true, 6, 6); }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient client = MinecraftClient.getInstance();
        double target = preview ? 144 : client.getCurrentFps();
        int value = Math.max(0, (int) Math.round(smoothMetric("fps", target, 7.5f)));
        drawLines(ctx, List.of("FPS: " + value));
    }
}
