package dev.tempest.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Shows/edits a vanilla keybind inside the Tempest menu (vanilla saves it to options.txt). */
public class KeySetting extends Setting {
    public final KeyBinding binding;

    public KeySetting(String id, String name, KeyBinding binding) {
        super(id, name);
        this.binding = binding;
    }

    public String label() { return binding.getBoundKeyLocalizedText().getString(); }

    /** @param keyCode GLFW key code, or -1 to unbind */
    public void assign(int keyCode) {
        binding.setBoundKey(keyCode < 0 ? InputUtil.UNKNOWN_KEY : InputUtil.Type.KEYSYM.createFromCode(keyCode));
        KeyBinding.updateKeysByCode();
        MinecraftClient.getInstance().options.write();
    }

    @Override protected String serialize() { return ""; }
    @Override protected void load(String s) { }
}
