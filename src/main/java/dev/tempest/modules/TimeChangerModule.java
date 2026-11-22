package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;

/** Client-side time of day and weather. Only changes what you see; the server is untouched. */
public class TimeChangerModule extends Module {
    private final ModeSetting mode = add(new ModeSetting("mode", "Time", "Day", "Day", "Noon", "Sunset", "Night", "Midnight", "Custom", "Cycle"));
    private final NumberSetting custom = add(new NumberSetting("custom", "Custom Time (ticks)", 6000, 0, 24000, 250));
    private final NumberSetting speed = add(new NumberSetting("speed", "Cycle Speed", 20, 1, 200, 1));
    private final ModeSetting weather = add(new ModeSetting("weather", "Weather", "World", "World", "Clear", "Rain", "Storm"));

    private static long cycle = 0;

    public TimeChangerModule() { super("time_changer", "Time Changer", "Set the time of day and weather on your screen (client-side only).", Category.RENDER, false); }

    @Override
    public void onTick(MinecraftClient c) { cycle += speed.getInt(); }

    /** @return the time of day to show, or -1 to leave it alone. */
    public static long override() {
        TimeChangerModule m = ModuleManager.get(TimeChangerModule.class);
        if (m == null || !m.isEnabled()) return -1;
        return Math.floorMod(switch (m.mode.get()) {
            case "Day" -> 1000L;
            case "Noon" -> 6000L;
            case "Sunset" -> 12000L;
            case "Night" -> 13000L;
            case "Midnight" -> 18000L;
            case "Cycle" -> cycle;
            default -> (long) m.custom.getInt();
        }, 24000L);
    }

    /** -1 means preserve vanilla weather. */
    public static float rainOverride() {
        TimeChangerModule m = ModuleManager.get(TimeChangerModule.class);
        if (m == null || !m.isEnabled()) return -1f;
        return switch (m.weather.get()) {
            case "Clear" -> 0f;
            case "Rain", "Storm" -> 1f;
            default -> -1f;
        };
    }

    public static float thunderOverride() {
        TimeChangerModule m = ModuleManager.get(TimeChangerModule.class);
        if (m == null || !m.isEnabled()) return -1f;
        return switch (m.weather.get()) {
            case "Storm" -> 1f;
            case "Clear", "Rain" -> 0f;
            default -> -1f;
        };
    }
}
