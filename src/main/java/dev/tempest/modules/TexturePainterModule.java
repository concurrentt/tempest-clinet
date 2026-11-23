package dev.tempest.modules;

import dev.tempest.gui.TempestEditorScreen;
import dev.tempest.gui.TexturePainterScreen;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Opens Tempest Editor (gui/TempestEditorScreen, colour styles for PvP textures, HUD, sounds...) and the Texture Painter. */
public class TexturePainterModule extends Module {
    private final KeyBinding openKey = bindKey("texture_painter", "Open Tempest Editor Key", InputUtil.UNKNOWN_KEY.getCode());

    public TexturePainterModule() {
        super("texture_painter", "Tempest Editor", "Pack editor: recolour crystals, anchors, totems, armour, HUD, particles and sounds, or paint textures.",
                Category.UTILITY, true);
        add(new ActionSetting("open_editor", "Tempest Editor", "Open", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            c.setScreen(new TempestEditorScreen(c.currentScreen));
        }));
        add(new ActionSetting("open", "Texture Painter", "Open", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            c.setScreen(new TexturePainterScreen(c.currentScreen));
        }));
        keyHint = "Also in Right Shift -> Editor. Saves into resourcepacks/Tempest Custom and switches that pack on.";
    }

    @Override
    public boolean alwaysOn() { return true; }

    @Override
    public void onTick(MinecraftClient c) {
        while (openKey.wasPressed()) if (c.currentScreen == null) c.setScreen(new TempestEditorScreen(null));
    }
}
