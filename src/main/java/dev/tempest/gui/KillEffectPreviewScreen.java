package dev.tempest.gui;

import dev.tempest.module.ModuleManager;
import dev.tempest.modules.KillEffectsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

/**
 * Kill Effects preview: a small window that plays the chosen effect on a loop, using the module's own effect code
 * (KillEffectsModule.Preview) so it looks exactly like a real kill. The effect's glowing quads are drawn by a tiny
 * software renderer into a texture (additive, like the in-world "lightning" blending), over a dark floor grid.
 * Works from the menus too - no world needed.
 */
public class KillEffectPreviewScreen extends Screen {
    private static int counter;
    private final Screen parent;
    private KillEffectsModule.Preview preview;
    private Raster raster;
    private NativeImageBackedTexture texture;
    private Identifier texId;
    private long finishedAt;

    public KillEffectPreviewScreen(Screen parent) {
        super(Text.literal("Kill Effect Preview"));
        this.parent = parent;
    }

    private KillEffectsModule module() { return ModuleManager.get(KillEffectsModule.class); }

    private int pw() { return Math.min(width - 24, 320); }
    private int ph() { return Math.min(height - 24, 240); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int vx() { return px() + 10; }
    private int vy() { return py() + 32; }
    private int vw() { return pw() - 20; }
    private int vh() { return ph() - 32 - 40; }

    @Override
    protected void init() {
        KillEffectsModule m = module();
        if (preview == null) preview = new KillEffectsModule.Preview(m == null ? 0 : m.effectIndex());
        // render at twice the GUI size (sharp at GUI scale 2), capped so it stays cheap
        int k = 2;
        int iw = Math.max(32, Math.min(640, vw() * k)), ih = Math.max(32, Math.min(400, vh() * k));
        if (raster == null || raster.w != iw || raster.h != ih) {
            freeTexture();
            raster = new Raster(iw, ih);
            NativeImage img = new NativeImage(iw, ih, true);
            texture = new NativeImageBackedTexture(() -> "Tempest kill effect preview", img);
            texId = Identifier.of("tempest", "dynamic/kill_preview_" + (counter++));
            client.getTextureManager().registerTexture(texId, texture);
        }
    }

    private void freeTexture() {
        if (texId != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(texId);
        texId = null;
        texture = null;
    }

    @Override
    public void removed() { freeTexture(); }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        if (client.world == null) TempestTitleScreen.drawBackdrop(ctx, width, height, MenuKit.time());
        ctx.fillGradient(0, 0, width, height, 0x90000000, 0x70000000);
        int x = px(), y = py(), w = pw(), h = ph();
        Draw.shadow(ctx, x, y, w, h, 10, 10);
        Draw.glass(ctx, x, y, w, h, 10, 0xE8101218, 0x40FFFFFF);
        Draw.roundRectV(ctx, x + 1, y + 1, w - 2, 30, 9, Draw.withAlpha(Draw.accent(), 0x26), Draw.withAlpha(Draw.accent(), 0x00));
        Draw.text(ctx, tr, Draw.bold("Kill Effect Preview"), x + 12, y + 11, 0xFFFFFFFF, false);
        KillEffectsModule m = module();
        String[] fx = KillEffectsModule.effects();
        int sel = m == null ? 0 : m.effectIndex();
        String now = fx[sel].equals("Random") ? "Random: " + preview.playing() : preview.playing();
        Draw.text(ctx, tr, now, x + w - 12 - tr.getWidth(now), y + 11, Draw.withAlpha(Draw.accent(), 0xFF), false);

        // loop: a short pause after each play, then again
        if (preview.finished()) {
            if (finishedAt == 0) finishedAt = System.currentTimeMillis();
            else if (System.currentTimeMillis() - finishedAt > 500) { preview.loop(sel); finishedAt = 0; }
        }
        // viewport
        int vx = vx(), vy = vy(), vw = vw(), vh = vh();
        if (texture != null && texture.getImage() != null) {
            raster.frame(preview);
            NativeImage img = texture.getImage();
            int[] px = raster.out;
            for (int yy = 0; yy < raster.h; yy++) for (int xx = 0; xx < raster.w; xx++) img.setColorArgb(xx, yy, px[yy * raster.w + xx]);
            texture.upload();
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, vx, vy, 0f, 0f, vw, vh, raster.w, raster.h, raster.w, raster.h, 0xFFFFFFFF);
        }
        Draw.ring(ctx, vx, vy, vw, vh, 6, 0x40FFFFFF);
        // progress line
        ctx.fill(vx + 6, vy + vh - 4, vx + vw - 6, vy + vh - 3, 0x30FFFFFF);
        ctx.fill(vx + 6, vy + vh - 4, vx + 6 + Math.round((vw - 12) * preview.progress()), vy + vh - 3, Draw.withAlpha(Draw.accent(), 0xC0));

        // bottom bar: < effect > | Replay | Play in world | Done
        int by = y + h - 30;
        String name = fx[sel];
        boolean lh = Draw.hit(mx, my, x + 12, by, 20, 20), rh = Draw.hit(mx, my, x + 136, by, 20, 20);
        MenuKit.flatButton(ctx, tr, "kp_prev", x + 12, by, 20, 20, null, "<", MenuKit.OUTLINE, lh, true, 1f);
        Draw.centered(ctx, tr, Draw.ellipsize(tr, name, 96), x + 84, by + 6, 0xFFFFFFFF);
        MenuKit.flatButton(ctx, tr, "kp_next", x + 136, by, 20, 20, null, ">", MenuKit.OUTLINE, rh, true, 1f);
        int bx = x + w - 12 - 50;
        MenuKit.flatButton(ctx, tr, "kp_done", bx, by, 50, 20, null, "Done", MenuKit.OUTLINE, Draw.hit(mx, my, bx, by, 50, 20), true, 1f);
        bx -= 56;
        MenuKit.flatButton(ctx, tr, "kp_replay", bx, by, 50, 20, null, "Replay", MenuKit.GREEN, Draw.hit(mx, my, bx, by, 50, 20), true, 1f);
        if (client.player != null && w >= 300) {
            bx -= 72;
            MenuKit.flatButton(ctx, tr, "kp_world", bx, by, 66, 20, null, "In world", MenuKit.DARK, Draw.hit(mx, my, bx, by, 66, 20), true, 1f);
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();
        int x = px(), y = py(), w = pw(), h = ph(), by = y + h - 30;
        KillEffectsModule m = module();
        String[] fx = KillEffectsModule.effects();
        if (m != null && (Draw.hit(mx, my, x + 12, by, 20, 20) || Draw.hit(mx, my, x + 136, by, 20, 20))) {
            int dir = Draw.hit(mx, my, x + 12, by, 20, 20) ? -1 : 1;
            m.setEffect(fx[Math.floorMod(m.effectIndex() + dir, fx.length)]);
            preview.restart(m.effectIndex());
            finishedAt = 0;
            return true;
        }
        int bx = x + w - 12 - 50;
        if (Draw.hit(mx, my, bx, by, 50, 20)) { close(); return true; }
        bx -= 56;
        if (Draw.hit(mx, my, bx, by, 50, 20)) { preview.restart(m == null ? 0 : m.effectIndex()); finishedAt = 0; return true; }
        if (client.player != null && w >= 300) {
            bx -= 72;
            if (Draw.hit(mx, my, bx, by, 66, 20)) {
                client.setScreen(null);
                KillEffectsModule.playInWorld();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void close() {
        dev.tempest.TempestConfig.save();
        client.setScreen(parent);
    }

    // ------------------------------------------------------------------ software renderer
    /**
     * Rasterises the effect's quads with additive blending (colour x alpha, like Minecraft's lightning layer) over a
     * pre-drawn background. Camera: 6.5 blocks back, 1.5 up, looking straight at the effect.
     */
    private static final class Raster implements KillEffectsModule.Sink {
        private static final Vec3d CAM = new Vec3d(0, 1.5, 6.5);
        final int w, h;
        final int[] out;
        private final int[] bg;
        private final float[] r, g, b;
        private final float f, cx, cy;
        private final float[] qx = new float[4], qy = new float[4], qz = new float[4];
        private final int[] qc = new int[4];
        private int n, quad;

        Raster(int w, int h) {
            this.w = w;
            this.h = h;
            out = new int[w * h];
            bg = new int[w * h];
            r = new float[w * h];
            g = new float[w * h];
            b = new float[w * h];
            f = h / (2f * (float) Math.tan(Math.toRadians(30)));      // 60 degree vertical field of view
            cx = w / 2f;
            cy = h * 0.46f;
            background();
        }

        /** Night-sky gradient and a floor grid that fades into the distance (drawn once). */
        private void background() {
            float camY = (float) CAM.y;
            for (int y = 0; y < h; y++) {
                float t = y / (float) (h - 1);
                int sky = Draw.lerpColor(0xFF07080D, 0xFF141726, t);
                for (int x = 0; x < w; x++) {
                    int c = sky;
                    float dy = (y - cy) / f;
                    if (dy > 0.004f) {
                        float dist = camY / dy;                      // where this pixel's ray meets the floor
                        float wx = (x - cx) / f * dist, wz = (float) CAM.z - dist;
                        float fx = Math.abs(wx - Math.round(wx)), fz = Math.abs(wz - Math.round(wz));
                        float line = Math.max(0f, 1f - Math.min(fx, fz) * f / dist * 1.4f);
                        float fade = Math.max(0f, 1f - dist / 22f);
                        int floor = Draw.lerpColor(0xFF0C0E16, 0xFF10131D, Math.min(1f, 3f / dist));
                        c = Draw.lerpColor(floor, 0xFF2A3048, line * fade * 0.8f);
                    }
                    bg[y * w + x] = c;
                }
            }
        }

        void frame(KillEffectsModule.Preview p) {
            Arrays.fill(r, 0f);
            Arrays.fill(g, 0f);
            Arrays.fill(b, 0f);
            n = 0;
            quad = 0;
            try { p.draw(this, CAM); } catch (RuntimeException ignored) { }
            for (int i = 0; i < out.length; i++) {
                int c = bg[i];
                int rr = Math.min(255, ((c >> 16) & 255) + Math.round(r[i] * 255));
                int gg = Math.min(255, ((c >> 8) & 255) + Math.round(g[i] * 255));
                int bb = Math.min(255, (c & 255) + Math.round(b[i] * 255));
                out[i] = 0xFF000000 | rr << 16 | gg << 8 | bb;
            }
        }

        @Override
        public void v(float x, float y, float z, int argb) {
            qx[n] = x; qy[n] = y; qz[n] = z; qc[n] = argb;
            if (++n < 4) return;
            n = 0;
            // every quad is sent twice (once per winding, so it shows from both sides in the world): draw it once
            if ((quad++ & 1) == 0) drawQuad();
        }

        private final float[] sx = new float[4], sy = new float[4];

        private void drawQuad() {
            for (int i = 0; i < 4; i++) {
                if (qz[i] > -0.05f) return;                       // behind the camera
                float d = -qz[i];
                sx[i] = cx + f * qx[i] / d;
                sy[i] = cy - f * qy[i] / d;
            }
            tri(0, 1, 2);
            tri(0, 2, 3);
        }

        /** Fills one triangle, interpolating colour x alpha and adding it to the buffers. */
        private void tri(int a, int b2, int c) {
            float x0 = sx[a], y0 = sy[a], x1 = sx[b2], y1 = sy[b2], x2 = sx[c], y2 = sy[c];
            float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
            if (Math.abs(area) < 1e-4f) return;
            int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
            int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
            int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
            int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
            if (minX > maxX || minY > maxY) return;
            float[] ca = premul(qc[a]), cb = premul(qc[b2]), cc = premul(qc[c]);
            if (ca[3] + cb[3] + cc[3] <= 0.0005f) return;
            float inv = 1f / area;
            for (int py = minY; py <= maxY; py++) {
                float fy = py + 0.5f;
                for (int px = minX; px <= maxX; px++) {
                    float fx = px + 0.5f;
                    float w0 = ((x1 - fx) * (y2 - fy) - (x2 - fx) * (y1 - fy)) * inv;
                    float w1 = ((x2 - fx) * (y0 - fy) - (x0 - fx) * (y2 - fy)) * inv;
                    float w2 = 1f - w0 - w1;
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                    int i = py * w + px;
                    r[i] += w0 * ca[0] + w1 * cb[0] + w2 * cc[0];
                    g[i] += w0 * ca[1] + w1 * cb[1] + w2 * cc[1];
                    b[i] += w0 * ca[2] + w1 * cb[2] + w2 * cc[2];
                }
            }
        }

        private final float[][] pm = new float[3][4];
        private int pmNext;

        /** rgb x alpha in 0..1, plus alpha (reuses three small arrays - one per triangle corner). */
        private float[] premul(int argb) {
            float[] o = pm[pmNext];
            pmNext = (pmNext + 1) % 3;
            float a = ((argb >>> 24) & 255) / 255f;
            o[0] = ((argb >> 16) & 255) / 255f * a;
            o[1] = ((argb >> 8) & 255) / 255f * a;
            o[2] = (argb & 255) / 255f * a;
            o[3] = a;
            return o;
        }
    }
}
