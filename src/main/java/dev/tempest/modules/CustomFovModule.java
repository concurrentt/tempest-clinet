package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;

/** Replaces the base client FOV while retaining vanilla sprint and status-effect FOV modifiers. */
public final class CustomFovModule extends Module {
    private final NumberSetting fov = add(new NumberSetting("fov", "Field of View", 90, 30, 160, 1));

    public CustomFovModule() {
        super("custom_fov", "FOV", "Set your client-side field of view from 30 to 160 degrees.", Category.QOL, false);
    }

    public static float fovScale() {
        CustomFovModule module = ModuleManager.get(CustomFovModule.class);
        if (module == null || !module.isEnabled()) return 1.0f;
        int vanillaFov = MinecraftClient.getInstance().options.getFov().getValue();
        return vanillaFov <= 0 ? 1.0f : module.fov.getFloat() / vanillaFov;
    }
}
