package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class CinematicCameraModule extends Module {
    private final KeyBinding key = bindKey("cinematic", "Toggle Key", InputUtil.UNKNOWN_KEY.getCode());

    public CinematicCameraModule() { super("cinematic", "Cinematic Camera", "Press a key to toggle smooth camera movement.", Category.RENDER, true); }

    @Override
    public void onTick(MinecraftClient c) {
        while (key.wasPressed()) c.options.smoothCameraEnabled = !c.options.smoothCameraEnabled;
    }
}
