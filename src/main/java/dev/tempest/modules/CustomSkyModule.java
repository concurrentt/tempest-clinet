package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import dev.tempest.render.SkyScenes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.joml.Vector4f;

/**
 * Custom Sky: one scene at a time (Deep Space, Black Hole, Northern Lights, ...), cycled from the menu or with
 * the "Next Sky" key. Scenes live in assets/tempest/sky/scenes.json. Everything is drawn inside Minecraft's own
 * sky pass (render/SkyRenderer), so it stays fixed in the world and blocks cover it.
 */
public class CustomSkyModule extends Module {
    public final ModeSetting scene = add(new ModeSetting("scene", "Scene", SkyScenes.names()[0], SkyScenes.names()));
    private final KeyBinding nextKey = bindKey("next_sky", "Next Sky Key", InputUtil.UNKNOWN_KEY.getCode());
    public final NumberSetting brightness = add(new NumberSetting("brightness", "Brightness", 1.0, 0.3, 1.6, 0.05));
    public final NumberSetting rotation = add(new NumberSetting("rotation", "Turn Scene", 0, 0, 355, 5));
    public final BoolSetting meteors = add(new BoolSetting("meteors", "Shooting Stars", true));
    public final ModeSetting showAt = add(new ModeSetting("show_at", "Show", "Always", "Always", "Night Only"));
    public final BoolSetting hideClouds = add(new BoolSetting("hide_clouds", "Hide Clouds", true));
    public final NumberSetting horizon = add(new NumberSetting("horizon", "Horizon Blend", 0.85, 0, 1, 0.05));

    public CustomSkyModule() {
        super("custom_sky", "Custom Sky", "Swap the sky for a scene: deep space, black hole, northern lights, galaxy and more.",
                Category.VISUALS, false);
        keyHint = "Click Scene to cycle through the skies, or bind Next Sky Key to switch in game.";
    }

    @Override
    public void onTick(MinecraftClient c) {
        while (nextKey.wasPressed()) {
            if (!isEnabled()) setEnabled(true);
            else scene.cycle(1);
            if (c.inGameHud != null) c.inGameHud.setOverlayMessage(Text.literal("Sky: " + scene.get()), false);
        }
    }

    public SkyScenes.Scene current() { return SkyScenes.byName(scene.get()); }

    // ------------------------------------------------------------------ hooks used by the mixins
    /** The module, if it is on and the player is in the Overworld. */
    public static CustomSkyModule active() {
        CustomSkyModule m = ModuleManager.get(CustomSkyModule.class);
        if (m == null || !m.isEnabled()) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.world.getRegistryKey() != World.OVERWORLD) return null;
        return m;
    }

    public static boolean hideCelestial() { return active() != null; }

    public static boolean hideClouds() { CustomSkyModule m = active(); return m != null && m.hideClouds.get(); }

    public static boolean hideSunsetGlow() { return active() != null; }

    /** New colour for the vanilla sky dome (keeps vanilla's alpha byte). */
    public static int skyColor(int vanilla) {
        CustomSkyModule m = active();
        if (m == null) return vanilla;
        return (vanilla & 0xFF000000) | SkyScenes.rgb(m.current().top);
    }

    /** Pulls the fog / clear colour toward the scene's horizon colour. Leaves underwater and lava fog alone. */
    public static void applyFog(Vector4f c) {
        CustomSkyModule m = active();
        if (m == null || c == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && (mc.player.isSubmergedInWater() || mc.player.isInLava())) return;
        int rgb = SkyScenes.rgb(m.current().horizon);
        float k = m.horizon.getFloat();
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        c.set(c.x + (r - c.x) * k, c.y + (g - c.y) * k, c.z + (b - c.z) * k, c.w);
    }
}
