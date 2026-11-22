package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class ToggleSprintModule extends Module {
    private final KeyBinding toggleKey = bindKey("toggle_sprint", "Toggle Key", InputUtil.UNKNOWN_KEY.getCode());
    private boolean toggled = false;

    public ToggleSprintModule() { super("toggle_sprint", "Toggle Sprint", "Press a key once to keep sprinting while moving forward.", Category.UTILITY, true); }

    @Override
    public void onTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) toggled = !toggled;
        ClientPlayerEntity p = client.player;
        if (p == null || !toggled) return;
        boolean can = client.options.forwardKey.isPressed() && !p.isSneaking() && !p.horizontalCollision
                && !p.isUsingItem() && (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying);
        if (can) p.setSprinting(true);
    }
}
