package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;

public class MouseTweaksModule extends Module {
    private final BoolSetting shiftDrag = add(new BoolSetting("shift_drag", "Shift-Drag Move", true));
    private final BoolSetting collect = add(new BoolSetting("collect", "Drag Collect", true));

    public MouseTweaksModule() {
        super("mouse_tweaks", "Mouse Tweaks", "Drag to spread (vanilla), drag to collect, shift-drag to move items.", Category.QOL, true);
        keyHint = "Spread = hold left/right click and drag with items on your cursor (vanilla).";
    }

    public static boolean shiftDrag() {
        MouseTweaksModule m = ModuleManager.get(MouseTweaksModule.class);
        return m != null && m.isEnabled() && m.shiftDrag.get();
    }

    public static boolean collect() {
        MouseTweaksModule m = ModuleManager.get(MouseTweaksModule.class);
        return m != null && m.isEnabled() && m.collect.get();
    }
}
