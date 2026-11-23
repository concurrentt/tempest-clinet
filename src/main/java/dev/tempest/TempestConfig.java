package dev.tempest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.tempest.util.FavoriteServer;
import dev.tempest.util.Waypoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TempestConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("tempest.json");
    private static TempestConfig instance = new TempestConfig();

    /** module id -> enabled */
    public Map<String, Boolean> enabled = new LinkedHashMap<>();
    /** "module.setting" -> serialized value */
    public Map<String, String> settings = new LinkedHashMap<>();
    /** hud module id -> {x, y} */
    public Map<String, int[]> hud = new LinkedHashMap<>();
    public List<Waypoint> waypoints = new ArrayList<>();
    /** Quick-join list shown on the main menu. */
    public List<FavoriteServer> favoriteServers = new ArrayList<>();
    /** Lower-case names of confirmed Tempest friends. */
    public List<String> friends = new ArrayList<>();
    /** Tempest online service address (see util/TempestNet). Empty = use the built-in default. */
    public String backendUrl = "";
    /** Mod ids starred in the mod menu (shown first). */
    public List<String> favoriteMods = new ArrayList<>();
    /** Chat Tabs: regular expressions; matching chat lines are hidden (see /tempest filter). */
    public List<String> chatFilters = new ArrayList<>();
    /** Total time spent in a world/server, in milliseconds. */
    public long playtimeMs = 0;
    /** server address (or "singleplayer") -> milliseconds played. */
    public Map<String, Long> playtimeServers = new LinkedHashMap<>();
    /** Auto-updater: a release version the player chose to skip ("Skip this version"). */
    public String skippedUpdate = "";
    /** Name of the config last loaded or saved (shown as active in the Configs screen). */
    public String activeConfig = "";

    public static TempestConfig get() { return instance; }

    public static void load() {
        if (Files.exists(PATH)) {
            try {
                TempestConfig c = GSON.fromJson(Files.readString(PATH), TempestConfig.class);
                if (c != null) {
                    if (c.enabled == null) c.enabled = new LinkedHashMap<>();
                    if (c.settings == null) c.settings = new LinkedHashMap<>();
                    if (c.hud == null) c.hud = new LinkedHashMap<>();
                    if (c.waypoints == null) c.waypoints = new ArrayList<>();
                    if (c.favoriteServers == null) c.favoriteServers = new ArrayList<>();
                    if (c.friends == null) c.friends = new ArrayList<>();
                    if (c.favoriteMods == null) c.favoriteMods = new ArrayList<>();
                    if (c.playtimeServers == null) c.playtimeServers = new LinkedHashMap<>();
                    if (c.chatFilters == null) c.chatFilters = new ArrayList<>();

                    // Preserve the old View Model custom-FOV settings after moving FOV into QOL.
                    String oldCustomFov = c.settings.get("view_model.custom_fov");
                    String oldFov = c.settings.get("view_model.fov");
                    if ("true".equalsIgnoreCase(oldCustomFov)) c.enabled.putIfAbsent("custom_fov", true);
                    if (oldFov != null) c.settings.putIfAbsent("custom_fov.fov", oldFov);

                    instance = c;
                }
            } catch (Exception e) {
                System.err.println("[Tempest] Could not read config, using defaults: " + e);
            }
        }
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(instance));
        } catch (IOException e) {
            System.err.println("[Tempest] Could not save config: " + e);
        }
    }
}
