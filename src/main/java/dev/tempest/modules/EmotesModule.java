package dev.tempest.modules;

import dev.tempest.gui.EmoteWheelScreen;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.util.Emotes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

/**
 * Emotes (util/Emotes): hold or tap B for the emote wheel. Every Tempest player near you sees them, and you see theirs.
 * Moving cancels an emote.
 */
public class EmotesModule extends Module {
    private final KeyBinding wheelKey = bindKey("emote_wheel", "Emote Wheel Key", GLFW.GLFW_KEY_B);
    public final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Third Person While Emoting", true));
    public final BoolSetting seeOthers = add(new BoolSetting("see_others", "See Other Players' Emotes", true));

    public EmotesModule() {
        super("emotes", "Emotes", "Wave, dance, dab and more. Other Tempest players see your emotes. Moving cancels one.",
                Category.CLIENT, true);
        add(new ActionSetting("open_wheel", "Emote Wheel", "Open", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player != null) c.setScreen(new EmoteWheelScreen(false));
        }));
        keyHint = "Hold the key, point at an emote and let go - or tap it and click one.";
    }

    @Override
    public void onTick(MinecraftClient c) {
        Emotes.tick(c);
        while (wheelKey.wasPressed()) {
            if (c.currentScreen == null && c.player != null) c.setScreen(new EmoteWheelScreen(true));
        }
    }

    @Override
    public void onDisable() {
        Emotes.stop();
        Emotes.clearRemote();
    }

    /** The key's GLFW code (for the wheel's hold-and-release behaviour). */
    public int wheelKeyCode() {
        return net.minecraft.client.util.InputUtil.fromTranslationKey(wheelKey.getBoundKeyTranslationKey()).getCode();
    }
}
