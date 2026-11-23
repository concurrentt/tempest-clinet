package dev.tempest.module;

import dev.tempest.TempestClient;
import dev.tempest.TempestConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String id, name, description;
    private final Category category;
    private final boolean defaultEnabled;
    private final List<Setting> settings = new ArrayList<>();
    public String keyHint = null;

    protected Module(String id, String name, String description, Category category, boolean defaultEnabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultEnabled = defaultEnabled;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting> getSettings() { return Collections.unmodifiableList(settings); }

    /** Modules that cannot be switched off (the GUI hides their toggle). */
    public boolean alwaysOn() { return false; }

    private volatile boolean enabledCache;
    private volatile int enabledGen = -1;

    public boolean isEnabled() {
        if (alwaysOn()) return true;
        int g = TempestConfig.generation();
        if (enabledGen != g) {
            enabledCache = TempestConfig.get().enabled.getOrDefault(id, defaultEnabled);
            enabledGen = g;
        }
        return enabledCache;
    }

    public void setEnabled(boolean v) {
        if (alwaysOn()) return;
        boolean was = isEnabled();
        TempestConfig.get().enabled.put(id, v);
        enabledCache = v;
        enabledGen = TempestConfig.generation();
        if (!was && v) onEnable();
        else if (was && !v) onDisable();
    }

    public void toggle() { setEnabled(!isEnabled()); }

    protected <T extends Setting> T add(T s) {
        s.bind(this);
        s.restore();
        settings.add(s);
        return s;
    }

    /** Registers a vanilla keybind (shown in Controls AND in this module's settings page). */
    protected KeyBinding bindKey(String keyId, String label, int defaultCode) {
        KeyBinding kb = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tempest." + keyId, InputUtil.Type.KEYSYM, defaultCode, TempestClient.CATEGORY));
        add(new KeySetting(keyId + "_key", label, kb));
        return kb;
    }

    public void reloadSettings() {
        enabledGen = -1;          // the enabled map may have been replaced (config switch)
        for (Setting s : settings) s.restore();
    }
    public void commitAll() { for (Setting s : settings) s.commit(); }

    public void onInit() { }
    public void onEnable() { }
    public void onDisable() { }
    public void onTick(MinecraftClient client) { }
}
