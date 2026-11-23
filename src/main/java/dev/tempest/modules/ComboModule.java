package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.util.CombatTracker;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class ComboModule extends HudModule {
    public ComboModule() { super("combo", "Combo Counter", "Consecutive hits without taking damage.", Category.PVP, false, 6, 130); }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        drawLines(ctx, List.of("Combo: " + (preview ? 4 : CombatTracker.combo)));
    }
}
