package dev.tempest.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tempest.TempestConfig;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Named configs: which modules are on, every setting and the HUD layout, saved as
 * config/tempest/configs/&lt;name&gt;.json. Replaces the old numbered profile slots (migrated once on first use).
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_NAME = 24;

    public static class Data {
        public String name;
        public Map<String, Boolean> enabled = new LinkedHashMap<>();
        public Map<String, String> settings = new LinkedHashMap<>();
        public Map<String, int[]> hud = new LinkedHashMap<>();
    }

    public record Entry(String name, long savedAt) { }

    private static boolean migrated;

    private ConfigManager() { }

    private static Path dir() {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("tempest").resolve("configs");
        try { Files.createDirectories(d); } catch (IOException ignored) { }
        return d;
    }

    /** Letters, digits, spaces, - and _; trimmed and shortened. Empty if nothing usable is left. */
    public static String clean(String name) {
        if (name == null) return "";
        String s = name.replaceAll("[^A-Za-z0-9 _-]", "").replaceAll("\\s+", " ").trim();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).trim() : s;
    }

    private static Path file(String name) {
        return dir().resolve(clean(name).toLowerCase(Locale.ROOT).replace(' ', '_') + ".json");
    }

    public static boolean exists(String name) { return !clean(name).isEmpty() && Files.exists(file(name)); }

    /** All configs, newest first. */
    public static List<Entry> list() {
        migrateOldProfiles();
        List<Entry> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir())) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!p.getFileName().toString().endsWith(".json")) continue;
                try {
                    Data d = GSON.fromJson(Files.readString(p), Data.class);
                    String n = d != null && d.name != null && !d.name.isBlank() ? d.name : p.getFileName().toString().replace(".json", "");
                    out.add(new Entry(n, Files.getLastModifiedTime(p).toMillis()));
                } catch (Exception ignored) { }
            }
        } catch (IOException ignored) { }
        out.sort((a, b) -> Long.compare(b.savedAt(), a.savedAt()));
        return out;
    }

    public static String active() { return TempestConfig.get().activeConfig; }

    private static Data snapshot(String name) {
        for (Module m : ModuleManager.all()) m.commitAll();
        TempestConfig c = TempestConfig.get();
        Data d = new Data();
        d.name = name;
        d.enabled.putAll(c.enabled);
        d.settings.putAll(c.settings);
        d.hud.putAll(c.hud);
        return d;
    }

    private static boolean apply(Data d) {
        if (d == null) return false;
        TempestConfig c = TempestConfig.get();
        if (d.enabled != null) { c.enabled.clear(); c.enabled.putAll(d.enabled); }
        if (d.settings != null) { c.settings.clear(); c.settings.putAll(d.settings); }
        if (d.hud != null) { c.hud.clear(); c.hud.putAll(d.hud); }
        TempestConfig.touch();
        for (Module m : ModuleManager.all()) m.reloadSettings();
        TempestConfig.save();
        return true;
    }

    /** Saves the current setup under this name (creates or overwrites). Returns a message for the UI. */
    public static String save(String name) {
        String n = clean(name);
        if (n.isEmpty()) return "Give the config a name";
        try {
            Files.writeString(file(n), GSON.toJson(snapshot(n)));
            setActive(n);
            return "Saved \"" + n + "\"";
        } catch (Exception e) {
            return "Couldn't save: " + e.getMessage();
        }
    }

    public static String load(String name) {
        try {
            Path f = file(name);
            if (!Files.exists(f)) return "\"" + name + "\" doesn't exist";
            Data d = GSON.fromJson(Files.readString(f), Data.class);
            if (!apply(d)) return "\"" + name + "\" is damaged";
            setActive(clean(name));
            return "Loaded \"" + clean(name) + "\"";
        } catch (Exception e) {
            return "Couldn't load: " + e.getMessage();
        }
    }

    public static String rename(String from, String to) {
        String n = clean(to);
        if (n.isEmpty()) return "Give the config a name";
        if (n.equalsIgnoreCase(clean(from))) {            // only the capitalisation changes
            try {
                Data d = GSON.fromJson(Files.readString(file(from)), Data.class);
                d.name = n;
                Files.writeString(file(n), GSON.toJson(d));
                if (clean(from).equalsIgnoreCase(active())) setActive(n);
                return "Renamed to \"" + n + "\"";
            } catch (Exception e) { return "Couldn't rename: " + e.getMessage(); }
        }
        if (exists(n)) return "\"" + n + "\" already exists";
        try {
            Data d = GSON.fromJson(Files.readString(file(from)), Data.class);
            d.name = n;
            Files.writeString(file(n), GSON.toJson(d));
            Files.deleteIfExists(file(from));
            if (clean(from).equalsIgnoreCase(active())) setActive(n);
            return "Renamed to \"" + n + "\"";
        } catch (Exception e) {
            return "Couldn't rename: " + e.getMessage();
        }
    }

    public static String delete(String name) {
        try {
            Files.deleteIfExists(file(name));
            if (clean(name).equalsIgnoreCase(active())) setActive("");
            return "Deleted \"" + clean(name) + "\"";
        } catch (Exception e) {
            return "Couldn't delete: " + e.getMessage();
        }
    }

    /** Copies a saved config to the clipboard so it can be shared. */
    public static String copy(String name) {
        try {
            MinecraftClient.getInstance().keyboard.setClipboard(Files.readString(file(name)));
            return "Copied \"" + clean(name) + "\" - paste it to a friend";
        } catch (Exception e) {
            return "Couldn't copy: " + e.getMessage();
        }
    }

    /** Saves a config from the clipboard as a new config (keeps its name, or picks a free one). Doesn't load it. */
    public static String importClipboard() {
        try {
            Data d = GSON.fromJson(MinecraftClient.getInstance().keyboard.getClipboard(), Data.class);
            if (d == null || d.settings == null || d.enabled == null) return "The clipboard doesn't hold a Tempest config";
            String base = clean(d.name == null || d.name.isBlank() ? "Imported" : d.name);
            if (base.isEmpty()) base = "Imported";
            String n = base;
            for (int i = 2; exists(n); i++) n = clean(base + " " + i);
            d.name = n;
            Files.writeString(file(n), GSON.toJson(d));
            return "Imported \"" + n + "\" - press Load to use it";
        } catch (Exception e) {
            return "The clipboard doesn't hold a Tempest config";
        }
    }

    private static void setActive(String name) {
        TempestConfig.get().activeConfig = name;
        TempestConfig.save();
    }

    /** One-time: the old "Profile Slot" 1-3 files become configs named "Profile 1" ... "Profile 3". */
    private static void migrateOldProfiles() {
        if (migrated) return;
        migrated = true;
        Path cfg = FabricLoader.getInstance().getConfigDir();
        for (int i = 1; i <= 3; i++) {
            Path old = cfg.resolve("tempest-profile-" + i + ".json");
            if (!Files.exists(old)) continue;
            try {
                String n = "Profile " + i;
                if (!exists(n)) {
                    Data d = GSON.fromJson(Files.readString(old), Data.class);
                    if (d != null) { d.name = n; Files.writeString(file(n), GSON.toJson(d)); }
                }
                Files.move(old, cfg.resolve("tempest-profile-" + i + ".json.migrated"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) { }
        }
    }
}
