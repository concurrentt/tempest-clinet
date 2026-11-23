package dev.tempest.modules;

import dev.tempest.gui.TexturePainterScreen;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Opens the Texture Painter (gui/TexturePainterScreen): paint item and block textures into your own pack. */
public class TexturePainterModule extends Module {
    private final KeyBinding openKey = bindKey("texture_painter", "Open Key", InputUtil.UNKNOWN_KEY.getCode());

    public TexturePainterModule() {
        super("texture_painter", "Texture Painter", "Paint any item or block texture and save it into your own resource pack.",
                Category.UTILITY, true);
        add(new ActionSetting("open", "Texture Painter", "Open", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            c.setScreen(new TexturePainterScreen(c.currentScreen));
        }));
        keyHint = "Saves into resourcepacks/Tempest Custom and switches that pack on.";
    }

    @Override
    public boolean alwaysOn() { return true; }

    @Override
    public void onTick(MinecraftClient c) {
        while (openKey.wasPressed()) if (c.currentScreen == null) c.setScreen(new TexturePainterScreen(null));
    }
}
