package dev.tempest.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tempest.TempestConfig;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Saves/loads which modules are on, their settings and HUD layout. Slots are files; export/import use the clipboard. */
public final class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Data {
        public Map<String, Boolean> enabled = new LinkedHashMap<>();
        public Map<String, String> settings = new LinkedHashMap<>();
        public Map<String, int[]> hud = new LinkedHashMap<>();
    }

    private ProfileManager() { }

    private static Path file(String slot) {
        return FabricLoader.getInstance().getConfigDir().resolve("tempest-profile-" + slot + ".json");
    }

    private static String snapshot() {
        for (Module m : ModuleManager.all()) m.commitAll();
        TempestConfig c = TempestConfig.get();
        Data d = new Data();
        d.enabled.putAll(c.enabled);
        d.settings.putAll(c.settings);
        d.hud.putAll(c.hud);
        return GSON.toJson(d);
    }

    private static boolean apply(String json) {
        try {
            Data d = GSON.fromJson(json, Data.class);
            if (d == null) return false;
            TempestConfig c = TempestConfig.get();
            if (d.enabled != null) { c.enabled.clear(); c.enabled.putAll(d.enabled); }
            if (d.settings != null) { c.settings.clear(); c.settings.putAll(d.settings); }
            if (d.hud != null) { c.hud.clear(); c.hud.putAll(d.hud); }
            TempestConfig.touch();
            for (Module m : ModuleManager.all()) m.reloadSettings();
            TempestConfig.save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void save(String slot) {
        try {
            Files.writeString(file(slot), snapshot());
            Chat.info("Saved profile " + slot + ".");
        } catch (Exception e) {
            Chat.info("Could not save profile: " + e.getMessage());
        }
    }

    public static void load(String slot) {
        Path f = file(slot);
        try {
            if (!Files.exists(f)) { Chat.info("Profile " + slot + " is empty."); return; }
            Chat.info(apply(Files.readString(f)) ? "Loaded profile " + slot + "." : "Profile " + slot + " is invalid.");
        } catch (Exception e) {
            Chat.info("Could not load profile: " + e.getMessage());
        }
    }

    public static void exportClipboard() {
        MinecraftClient.getInstance().keyboard.setClipboard(snapshot());
        Chat.info("Profile copied to clipboard.");
    }

    public static void importClipboard() {
        String s = MinecraftClient.getInstance().keyboard.getClipboard();
        Chat.info(apply(s) ? "Imported profile from clipboard." : "Clipboard does not contain a valid profile.");
    }
}
