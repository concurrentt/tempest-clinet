package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.util.CombatTracker;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class CpsModule extends HudModule {
    private final BoolSetting showRight = add(new BoolSetting("right", "Show Right Click", true));

    public CpsModule() { super("cps", "CPS", "Clicks per second.", Category.HUD, true, 6, 24); }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        int l = preview ? 8 : CombatTracker.leftCps();
        int r = preview ? 3 : CombatTracker.rightCps();
        drawLines(ctx, List.of(showRight.get() ? "CPS: " + l + " | " + r : "CPS: " + l));
    }
}
