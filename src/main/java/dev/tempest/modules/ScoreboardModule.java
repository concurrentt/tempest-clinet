package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;

/** Move or hide the scoreboard sidebar. */
public final class ScoreboardModule extends Module {
    private final BoolSetting hide = add(new BoolSetting("hide", "Hide Scoreboard", false));
    private final NumberSetting offsetX = add(new NumberSetting("offset_x", "X Offset", 0, -400, 200, 1));
    private final NumberSetting offsetY = add(new NumberSetting("offset_y", "Y Offset", 0, -300, 300, 1));

    public ScoreboardModule() {
        super("scoreboard", "Scoreboard Tweaks", "Move or hide the scoreboard sidebar.", Category.HUD, false);
    }

    public static boolean shouldHide() {
        ScoreboardModule m = ModuleManager.get(ScoreboardModule.class);
        return m != null && m.isEnabled() && m.hide.get();
    }

    public static int offsetX() {
        ScoreboardModule m = ModuleManager.get(ScoreboardModule.class);
        return m != null && m.isEnabled() ? m.offsetX.getInt() : 0;
    }

    public static int offsetY() {
        ScoreboardModule m = ModuleManager.get(ScoreboardModule.class);
        return m != null && m.isEnabled() ? m.offsetY.getInt() : 0;
    }

    public static boolean shouldMove() {
        ScoreboardModule m = ModuleManager.get(ScoreboardModule.class);
        return m != null && m.isEnabled() && !m.hide.get() && (m.offsetX.getInt() != 0 || m.offsetY.getInt() != 0);
    }
}
