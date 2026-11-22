package dev.tempest.render;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Sky scenes, read from assets/tempest/sky/scenes.json (the same file tools/preview_sky.py renders).
 * Directions: az = compass degrees (0 north, 90 east), el = degrees above the horizon, size = degrees across.
 */
public final class SkyScenes {
    public static final class Glow { public String color = "#22346E"; public float alpha = 0.4f; }
    public static final class Stars { public int count = 1800; public String tint = "#FFFFFF"; public float brightness = 1f; }
    public static final class MilkyWay { public float alpha = 0.8f; public float width = 13; public float angle = 35; public String tint = "#FFFFFF"; }
    public static final class Nebula { public float az, el, size = 30, alpha = 0.3f; public String color = "#B45CFF"; }
    public static final class Aurora {
        public float az, span = 150, base = 12, height = 26, alpha = 1f, speed = 1f;
        public String[] colors = {"#6CFFB0", "#2EE6A0", "#A45CFF"};
    }
    public static final class BlackHole {
        public float az = 160, el = 30, size = 12, tilt = 12, spin = 1f;
        public String[] colors = {"#FFF6E0", "#FFB64A", "#D2461E"};
    }
    public static final class Planet { public String texture = "planet_saturn"; public float az, el, size = 20; public String glow = "#F0D9A8"; }
    public static final class Moon { public float az, el, size = 8, glowAlpha = 0.3f; public String tint = "#FFFFFF", glow = "#FFFFFF"; }
    public static final class Meteors { public float perMinute = 6; public boolean shower; public float radiantAz = 45, radiantEl = 55; }

    public static final class Scene {
        public String name = "Scene";
        public String top = "#03040A", horizon = "#0A0E20";
        public Glow glow;
        public Stars stars;
        public MilkyWay milkyWay;
        public List<Nebula> nebulae = new ArrayList<>();
        public List<Aurora> auroras = new ArrayList<>();
        public BlackHole blackHole;
        public List<Planet> planets = new ArrayList<>();
        public List<Moon> moons = new ArrayList<>();
        public Meteors meteors;
    }

    private static final class Root { List<Scene> scenes; }

    private static final List<Scene> SCENES = load();

    private SkyScenes() { }

    private static List<Scene> load() {
        List<Scene> out = new ArrayList<>();
        try (InputStream in = SkyScenes.class.getResourceAsStream("/assets/tempest/sky/scenes.json")) {
            if (in != null) {
                Root r = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Root.class);
                if (r != null && r.scenes != null) {
                    for (Scene s : r.scenes) {
                        if (s == null || s.name == null) continue;
                        if (s.nebulae == null) s.nebulae = new ArrayList<>();
                        if (s.auroras == null) s.auroras = new ArrayList<>();
                        if (s.planets == null) s.planets = new ArrayList<>();
                        if (s.moons == null) s.moons = new ArrayList<>();
                        out.add(s);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Tempest] Could not read sky scenes: " + e);
        }
        if (out.isEmpty()) {
            Scene s = new Scene();
            s.name = "Deep Space";
            s.glow = new Glow();
            s.stars = new Stars();
            s.milkyWay = new MilkyWay();
            out.add(s);
        }
        return out;
    }

    public static List<Scene> all() { return SCENES; }

    public static String[] names() {
        String[] n = new String[SCENES.size()];
        for (int i = 0; i < n.length; i++) n[i] = SCENES.get(i).name;
        return n;
    }

    public static Scene byName(String name) {
        for (Scene s : SCENES) if (s.name.equals(name)) return s;
        return SCENES.get(0);
    }

    /** "#RRGGBB" -> 0xRRGGBB (white if unreadable). */
    public static int rgb(String hex) {
        if (hex == null) return 0xFFFFFF;
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}
