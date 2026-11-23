package dev.tempest.render;

import dev.tempest.modules.SpotifyModule;
import dev.tempest.util.Lyrics;
import dev.tempest.util.NowPlaying;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spotify lyrics hanging in the sky, as part of the world: the current line (large, glowing in the song's colour)
 * with the next one fainter underneath, painted into a texture and drawn on a big panel far away, high above the
 * horizon. Buildings and trees in front of it hide it, it has real perspective, and it fades out where you can't see
 * the sky. It sits where you look and drifts after you slowly when you turn, like the moon.
 *
 * <p>Each new line is painted once (off the render thread) with Inter and a blurred glow, then cross-faded in.
 * Drawn from WorldRendererEffectsMixin, where the camera is at the origin.</p>
 */
public final class SkyLyrics {
    private static final int TW = 1024, TH = 192;
    private static final ExecutorService PAINTER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Tempest sky lyrics");
        t.setDaemon(true);
        return t;
    });
    private static Font font;
    private static boolean fontTried;
    private static int counter;

    // what's showing: the current texture and the one fading out
    private static Identifier cur, prev;
    private static String curKey = "", wantKey = "";
    private static long curSince;
    // where it hangs
    private static float yaw = Float.NaN, vis;
    private static long last;

    private SkyLyrics() { }

    public static void render(MatrixStack matrices, OrderedRenderCommandQueue queue) {
        SpotifyModule m = SpotifyModule.skyOn();
        MinecraftClient c = MinecraftClient.getInstance();
        if (m == null || c.player == null || c.world == null || c.gameRenderer == null) { yaw = Float.NaN; return; }
        NowPlaying.want(m.anyPlayerOn());
        NowPlaying.Track t = NowPlaying.track();
        if (t == null) { yaw = Float.NaN; return; }
        Lyrics.Result r = Lyrics.of(t);
        List<Lyrics.Line> lines = r.lines();
        if (!"SYNCED".equals(r.state()) || lines == null || lines.isEmpty()) return;   // only timed lyrics go up

        // the line to show (and the next one)
        int idx = Lyrics.indexAt(lines, NowPlaying.position());
        String line = idx < 0 ? "♪" : idx < lines.size() ? lines.get(idx).text() : "";
        if (line == null || line.isBlank()) line = "♪";
        String next = idx + 1 >= 0 && idx + 1 < lines.size() ? lines.get(idx + 1).text() : "";
        int glow = m.skyGlow();
        String key = t.key() + "#" + idx + "#" + Integer.toHexString(glow);
        if (!key.equals(wantKey)) {
            wantKey = key;
            final String a = line, b = next == null ? "" : next;
            PAINTER.execute(() -> paint(key, a, b, glow));
        }

        long now = System.currentTimeMillis();
        float dt = last == 0 ? 0f : Math.min(0.25f, (now - last) / 1000f);
        last = now;
        var cam = c.gameRenderer.getCamera();
        float camYaw = cam.getYaw();
        if (Float.isNaN(yaw)) yaw = camYaw;
        yaw += wrap(camYaw - yaw) * (1f - (float) Math.exp(-dt / 2.2f));      // drifts after you, slowly
        boolean sky;
        try { sky = c.world.isSkyVisible(BlockPos.ofFloored(c.player.getEyePos())); } catch (Throwable e) { sky = true; }
        vis += ((sky ? 1f : 0f) - vis) * (1f - (float) Math.exp(-dt / 0.6f));
        if (vis < 0.02f || cur == null) return;

        // a panel far away (but inside the fog), facing you
        int chunks;
        try { chunks = c.options.getClampedViewDistance(); } catch (Throwable e) { chunks = 8; }
        float dist = Math.max(18f, Math.min(48f, chunks * 16f * 0.55f));
        double e = Math.toRadians(m.skyElevation()), y = Math.toRadians(yaw);
        float fx = (float) (-Math.sin(y) * Math.cos(e)), fy = (float) Math.sin(e), fz = (float) (Math.cos(y) * Math.cos(e));
        float cx = fx * dist, cy = fy * dist + (float) Math.sin(now / 1600.0) * dist * 0.004f, cz = fz * dist;
        // right = forward x up, up = right x forward
        float rx = -fz, rz = fx;                                    // (fx,fy,fz) x (0,1,0)
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) return;
        rx /= rl; rz /= rl;
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;    // (rx,0,rz) x (fx,fy,fz)
        // the current line is the top ~45% of the texture: size the panel so that line spans skyLineDegrees
        float lineH = (float) (2 * dist * Math.tan(Math.toRadians(m.skyLineDegrees()) / 2));
        float halfH = lineH / 0.45f / 2f, halfW = halfH * TW / TH;
        float fade = Math.min(1f, (now - curSince) / 450f);
        float rise = (1f - fade) * (1f - fade) * halfH * 0.12f;
        if (prev != null && fade < 1f) quad(matrices, queue, prev, cx, cy, cz, rx, rz, ux, uy, uz, fx, fy, fz, halfW, halfH, vis * (1f - fade), 0f);
        quad(matrices, queue, cur, cx, cy - rise, cz, rx, rz, ux, uy, uz, fx, fy, fz, halfW, halfH, vis * fade, 0f);
        if (fade >= 1f && prev != null) { free(prev); prev = null; }
    }

    private static void quad(MatrixStack matrices, OrderedRenderCommandQueue queue, Identifier tex, float cx, float cy, float cz,
                             float rx, float rz, float ux, float uy, float uz, float fx, float fy, float fz,
                             float hw, float hh, float alpha, float unused) {
        if (alpha <= 0.01f) return;
        int col = (Math.round(255 * Math.min(1f, alpha)) << 24) | 0xFFFFFF;
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(tex), (entry, vc) -> {
            // corners: top-left, bottom-left, bottom-right, top-right (as seen from the camera)
            v(vc, entry, cx - rx * hw + ux * hh, cy + uy * hh, cz - rz * hw + uz * hh, 0, 0, col, fx, fy, fz);
            v(vc, entry, cx - rx * hw - ux * hh, cy - uy * hh, cz - rz * hw - uz * hh, 0, 1, col, fx, fy, fz);
            v(vc, entry, cx + rx * hw - ux * hh, cy - uy * hh, cz + rz * hw - uz * hh, 1, 1, col, fx, fy, fz);
            v(vc, entry, cx + rx * hw + ux * hh, cy + uy * hh, cz + rz * hw + uz * hh, 1, 0, col, fx, fy, fz);
        });
    }

    private static void v(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, float u, float v, int col,
                          float fx, float fy, float fz) {
        // normal points back at the camera so the panel is lit evenly
        vc.vertex(e, x, y, z).color(col).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(e, -fx, -fy, -fz);
    }

    // ------------------------------------------------------------------ painting a line
    private static Font font(float size) {
        if (!fontTried) {
            fontTried = true;
            try {
                var res = MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of("tempest", "font/inter.ttf"));
                if (res.isPresent()) try (InputStream in = res.get().getInputStream()) { font = Font.createFont(Font.TRUETYPE_FONT, in); }
            } catch (Exception ignored) { }
        }
        Font base = font != null ? font : new Font(Font.SANS_SERIF, Font.BOLD, 12);
        return base.deriveFont(size);
    }

    /** Draws the line (and the next one) with a glow into a texture, then swaps it in on the render thread. */
    private static void paint(String key, String line, String next, int glow) {
        try {
            int[] px = new int[TW * TH];
            BufferedImage glowImg = new BufferedImage(TW, TH, BufferedImage.TYPE_INT_ARGB);
            BufferedImage textImg = new BufferedImage(TW, TH, BufferedImage.TYPE_INT_ARGB);
            Font big = fit(line, 64f, TW - 90), small = fit(next, 34f, TW - 160);
            draw(glowImg, line, big, 84, 0xFF000000 | glow);
            if (!next.isBlank()) draw(glowImg, next, small, 158, 0x99000000 | glow);
            int[] g = glowImg.getRGB(0, 0, TW, TH, null, 0, TW);
            blur(g, TW, TH, 9);
            blur(g, TW, TH, 9);
            blur(g, TW, TH, 7);
            int white = lerp(0xFFFFFF, glow, 0.10f);
            draw(textImg, line, big, 84, 0xFF000000 | white);
            if (!next.isBlank()) draw(textImg, next, small, 158, 0x8C000000 | lerp(0xE6E8F0, glow, 0.3f));
            int[] tx = textImg.getRGB(0, 0, TW, TH, null, 0, TW);
            for (int i = 0; i < px.length; i++) px[i] = over(tx[i], boost(g[i], 2.4f));
            MinecraftClient.getInstance().execute(() -> {
                if (!key.equals(wantKey)) return;                  // a newer line is already on its way
                NativeImage img = new NativeImage(TW, TH, true);
                for (int y = 0; y < TH; y++) for (int x = 0; x < TW; x++) img.setColorArgb(x, y, px[y * TW + x]);
                Identifier id = Identifier.of("tempest", "dynamic/sky_lyrics_" + (counter++));
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "Tempest sky lyrics", img));
                if (prev != null) free(prev);
                prev = cur;
                cur = id;
                curKey = key;
                curSince = System.currentTimeMillis();
            });
        } catch (Throwable ignored) { }
    }

    private static Font fit(String s, float size, int maxW) {
        Font f = font(size);
        if (s == null || s.isBlank()) return f;
        if (f.canDisplayUpTo(s) != -1) f = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size));   // symbols Inter lacks
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        int w = g.getFontMetrics(f).stringWidth(s);
        g.dispose();
        return w > maxW ? f.deriveFont(Math.max(size * 0.5f, size * maxW / (float) w)) : f;
    }

    private static void draw(BufferedImage img, String s, Font f, int baseline, int argb) {
        if (s == null || s.isBlank()) return;
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        String shown = s;
        while (fm.stringWidth(shown) > TW - 40 && shown.length() > 4) shown = shown.substring(0, shown.length() - 2).stripTrailing() + "…";
        g.setColor(new java.awt.Color(argb, true));
        g.drawString(shown, (TW - fm.stringWidth(shown)) / 2, baseline);
        g.dispose();
    }

    /** Separable box blur on premultiplied colour (keeps the glow's colour right at its soft edges). */
    private static void blur(int[] px, int w, int h, int r) {
        float[] a = new float[w * h], rr = new float[w * h], gg = new float[w * h], bb = new float[w * h];
        for (int i = 0; i < px.length; i++) {
            float al = (px[i] >>> 24) / 255f;
            a[i] = al; rr[i] = ((px[i] >> 16) & 255) * al; gg[i] = ((px[i] >> 8) & 255) * al; bb[i] = (px[i] & 255) * al;
        }
        float[][] ch = {a, rr, gg, bb};
        float[] tmp = new float[Math.max(w, h)];
        for (float[] c : ch) {
            for (int y = 0; y < h; y++) {                       // horizontal
                float sum = 0; int o = y * w;
                for (int x = -r; x <= r; x++) sum += c[o + Math.max(0, Math.min(w - 1, x))];
                for (int x = 0; x < w; x++) {
                    tmp[x] = sum / (2 * r + 1);
                    sum += c[o + Math.min(w - 1, x + r + 1)] - c[o + Math.max(0, x - r)];
                }
                System.arraycopy(tmp, 0, c, o, w);
            }
            for (int x = 0; x < w; x++) {                       // vertical
                float sum = 0;
                for (int y = -r; y <= r; y++) sum += c[Math.max(0, Math.min(h - 1, y)) * w + x];
                for (int y = 0; y < h; y++) {
                    tmp[y] = sum / (2 * r + 1);
                    sum += c[Math.min(h - 1, y + r + 1) * w + x] - c[Math.max(0, y - r) * w + x];
                }
                for (int y = 0; y < h; y++) c[y * w + x] = tmp[y];
            }
        }
        for (int i = 0; i < px.length; i++) {
            float al = a[i];
            if (al <= 0.001f) { px[i] = 0; continue; }
            int R = Math.min(255, Math.round(rr[i] / al)), G = Math.min(255, Math.round(gg[i] / al)), B = Math.min(255, Math.round(bb[i] / al));
            px[i] = (Math.min(255, Math.round(al * 255)) << 24) | R << 16 | G << 8 | B;
        }
    }

    private static int boost(int argb, float k) {
        int a = Math.min(255, Math.round((argb >>> 24) * k));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** src over dst (straight alpha). */
    private static int over(int src, int dst) {
        float sa = (src >>> 24) / 255f, da = (dst >>> 24) / 255f;
        float oa = sa + da * (1 - sa);
        if (oa <= 0.0001f) return 0;
        int r = Math.round((((src >> 16) & 255) * sa + ((dst >> 16) & 255) * da * (1 - sa)) / oa);
        int g = Math.round((((src >> 8) & 255) * sa + ((dst >> 8) & 255) * da * (1 - sa)) / oa);
        int b = Math.round(((src & 255) * sa + (dst & 255) * da * (1 - sa)) / oa);
        return (Math.round(oa * 255) << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static void free(Identifier id) {
        try { MinecraftClient.getInstance().getTextureManager().destroyTexture(id); } catch (Throwable ignored) { }
    }

    private static float wrap(float d) {
        d %= 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }
}
