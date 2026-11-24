package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;

/**
 * Fly Speed: choose how fast you fly in creative mode (1x = the normal speed). Sprinting still doubles it, like
 * normal. Only changes your own flying in creative - survival, adventure and spectator are left alone.
 */
public class FlySpeedModule extends Module {
    private static final float DEFAULT = 0.05f;      // vanilla creative fly speed

    private final NumberSetting speed = add(new NumberSetting("speed", "Speed", 2.0, 0.5, 10.0, 0.5));
    private boolean applied;

    public FlySpeedModule() {
        super("fly_speed", "Fly Speed", "Choose how fast you fly in creative mode.", Category.UTILITY, false);
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (c.player == null) { applied = false; return; }
        PlayerAbilities a = c.player.getAbilities();
        if (c.player.isCreative()) {
            // set every tick: the server resends the abilities (and the normal speed) on respawn, world change etc.
            float want = DEFAULT * speed.getFloat();
            if (a.getFlySpeed() != want) a.setFlySpeed(want);
            applied = true;
        } else if (applied) {
            a.setFlySpeed(DEFAULT);
            applied = false;
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (applied && c.player != null) c.player.getAbilities().setFlySpeed(DEFAULT);
        applied = false;
    }
}
