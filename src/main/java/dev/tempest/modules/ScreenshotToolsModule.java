package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Util;

import java.io.File;

public class ScreenshotToolsModule extends Module {
    private final KeyBinding folderKey = bindKey("screenshots", "Open Folder Key", InputUtil.UNKNOWN_KEY.getCode());

    public ScreenshotToolsModule() { super("screenshot_tools", "Screenshot Tools", "Key to open your screenshots folder.", Category.UTILITY, true); }

    @Override
    public void onTick(MinecraftClient c) {
        while (folderKey.wasPressed()) {
            File dir = new File(c.runDirectory, "screenshots");
            dir.mkdirs();
            Util.getOperatingSystem().open(dir);
        }
    }
}
