package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Scrambles block textures with a per-session random seed (pixel noise + shifted/flipped tileable
 * textures) so screenshots and streams don't match a known pack. Applies on resource reload (F3+T).
 */
public class AntiLeakModule extends Module {
    private static final long SEED = new SecureRandom().nextLong();
    private static final String[] TILEABLE = {"stone", "dirt", "cobble", "deepslate", "netherrack", "planks", "sand",
            "gravel", "andesite", "diorite", "granite", "tuff", "_ore", "concrete", "terracotta", "wool", "blackstone",
            "end_stone", "obsidian", "clay", "mud", "moss_block", "calcite", "dripstone"};
    private static final String[] NOT_TILEABLE = {"brick", "side", "top", "front", "bottom", "slab"};

    private final NumberSetting noise = add(new NumberSetting("noise", "Color Noise", 6, 0, 24, 1));
    private final BoolSetting shuffle = add(new BoolSetting("shuffle", "Shift/Flip Tileable Textures", true));

    public AntiLeakModule() {
        super("anti_leak", "Anti-Leak", "Scrambles texture patterns so bases can't be traced from screenshots. Press F3+T after changing.",
                Category.UTILITY, false);
    }

    private static boolean tileable(String path) {
        for (String n : NOT_TILEABLE) if (path.contains(n)) return false;
        for (String t : TILEABLE) if (path.contains(t)) return true;
        return false;
    }

    public static void apply(NativeImage img, Identifier id) {
        AntiLeakModule m = ModuleManager.get(AntiLeakModule.class);
        if (m == null || !m.isEnabled()) return;
        String path = id.getPath();
        if (!path.startsWith("block/")) return;

        int w = img.getWidth(), h = img.getHeight();
        long seed = SEED ^ (id.toString().hashCode() * 0x9E3779B97F4A7C15L);
        Random rng = new Random(seed);

        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = img.getColorArgb(x, y);

        if (m.shuffle.get() && w == h && tileable(path)) {
            int dx = rng.nextInt(w), dy = rng.nextInt(h);
            boolean flip = rng.nextBoolean();
            int[] out = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int sx = (x + dx) % w, sy = (y + dy) % h;
                    if (flip) sx = w - 1 - sx;
                    out[y * w + x] = px[sy * w + sx];
                }
            }
            px = out;
        }

        int n = m.noise.getInt();
        if (n > 0) {
            for (int i = 0; i < px.length; i++) {
                int c = px[i];
                if ((c >>> 24) == 0) continue;
                long z = (i + seed) * 0x9E3779B97F4A7C15L;
                z ^= z >>> 32;
                int d = (int) Math.floorMod(z, (long) (2 * n + 1)) - n;
                int r = clamp(((c >> 16) & 255) + d), g = clamp(((c >> 8) & 255) + d), b = clamp((c & 255) + d);
                px[i] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
        }

        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setColorArgb(x, y, px[y * w + x]);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
