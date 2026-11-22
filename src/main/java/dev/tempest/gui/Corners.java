package dev.tempest.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Cached anti-aliased circle / ring textures used for rounded corners. One textured quad per corner replaces the
 * old row-by-row fills (hundreds of rectangles per shape at high GUI scales), which was the main GUI FPS cost.
 */
final class Corners {
    private static final Map<Long, Identifier> CACHE = new HashMap<>();

    private Corners() { }

    /** Filled disc of radius r (texture is 2r x 2r, white with coverage alpha). */
    static Identifier disc(int r) { return get(r, 0); }

    /** Ring of outer radius r and thickness t. */
    static Identifier ring(int r, int t) { return get(r, Math.max(1, Math.min(t, r))); }

    private static Identifier get(int r, int t) {
        long key = ((long) r << 20) | t;
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        int d = 2 * r;
        NativeImage img = new NativeImage(d, d, true);
        for (int py = 0; py < d; py++) {
            for (int px = 0; px < d; px++) {
                double dx = px + 0.5 - r, dy = py + 0.5 - r, dist = Math.sqrt(dx * dx + dy * dy);
                double cov = clamp(r - dist + 0.5);
                if (t > 0) cov -= clamp((r - t) - dist + 0.5);
                int a = (int) Math.round(Math.max(0, cov) * 255);
                img.setColorArgb(px, py, (a << 24) | 0xFFFFFF);
            }
        }
        id = Identifier.of("tempest", "dynamic/corner_" + r + "_" + t);
        final String name = "Tempest corner " + r + "/" + t;
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> name, img));
        CACHE.put(key, id);
        return id;
    }

    private static double clamp(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    /** Draws the four r x r corner quadrants of {@code tex} at the corners of the box. */
    static void draw(DrawContext c, Identifier tex, int x, int y, int w, int h, int r, int topCol, int bottomCol) {
        int d = 2 * r;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, r, r, r, r, d, d, topCol);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y, r, 0f, r, r, r, r, d, d, topCol);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y + h - r, 0f, r, r, r, r, r, d, d, bottomCol);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y + h - r, r, r, r, r, r, r, d, d, bottomCol);
    }
}
