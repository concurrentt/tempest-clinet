package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.gui.Icons;
import dev.tempest.gui.TempestLoading;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModeSetting;
import dev.tempest.util.Lyrics;
import dev.tempest.util.NowPlaying;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Spotify HUD: the song that's playing, its artist and album, the cover, a progress bar, and time-synced lyrics that
 * slide along with the song. The card picks up the cover's colours. Song info comes from your own computer
 * (util/NowPlaying), lyrics from LRCLIB (util/Lyrics).
 */
public class SpotifyModule extends HudModule {
    private final ModeSetting lyrics = add(new ModeSetting("lyrics", "Lyrics", "3 Lines", "Off", "1 Line", "2 Lines", "3 Lines"));
    private final BoolSetting albumColors = add(new BoolSetting("album_colors", "Album Colours", true));
    private final BoolSetting showAlbum = add(new BoolSetting("show_album", "Show Album", true));
    private final BoolSetting hideIdle = add(new BoolSetting("hide_idle", "Hide When Nothing Plays", true));
    private final BoolSetting anyPlayer = add(new BoolSetting("any_player", "Any Music Player (not just Spotify)", false));

    private static final int W = 220, HEAD = 60, LINE = 12;

    private int lastIndex = Integer.MIN_VALUE;
    private long indexChangedAt;
    private String lastTrack = "";
    private long trackChangedAt;

    public SpotifyModule() {
        super("spotify", "Spotify", "What's playing on Spotify: song, artist, cover, progress and synced lyrics.", Category.HUD, false, 6, 150);
        keyHint = "Reads what's playing from your computer (no login). Lyrics from lrclib.net.";
    }

    @Override
    public void onTick(MinecraftClient c) { NowPlaying.tick(); }

    @Override
    public void onDisable() { NowPlaying.stop(); }

    private int lyricLines() {
        return switch (lyrics.get()) { case "1 Line" -> 1; case "2 Lines" -> 2; case "3 Lines" -> 3; default -> 0; };
    }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        NowPlaying.want(anyPlayer.get());
        NowPlaying.Track t = NowPlaying.track();
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1e9f;

        String title, artist, album;
        long pos, dur;
        boolean playing;
        Identifier art;
        int tint;
        if (preview && t == null) {
            title = "Song Title"; artist = "Artist"; album = "Album";
            dur = 200_000; pos = (long) ((time * 1000) % dur); playing = true; art = null; tint = 0x8A5CFF;
        } else if (t == null) {
            if (hideIdle.get()) { w = 0; h = 0; return; }
            drawIdle(ctx, tr);
            return;
        } else {
            title = t.title(); artist = t.artist(); album = t.album();
            pos = NowPlaying.position(); dur = t.durationMs(); playing = NowPlaying.playing();
            art = NowPlaying.art(); tint = NowPlaying.artColor();
        }
        if (!albumColors.get()) tint = Draw.accent() & 0xFFFFFF;
        int accent = vivid(tint);

        if (t != null && !t.key().equals(lastTrack)) { lastTrack = t.key(); trackChangedAt = System.currentTimeMillis(); lastIndex = Integer.MIN_VALUE; }
        float fadeIn = Math.min(1f, (System.currentTimeMillis() - trackChangedAt) / 350f);

        int nLines = lyricLines();
        w = W;
        h = HEAD + (nLines > 0 ? nLines * LINE + 8 : 0);
        int a = alpha();

        // ---- card
        if (background.get() && a > 0) {
            Draw.shadow(ctx, x, y, w, h, 8, 3);
            int top = Draw.withAlpha(Draw.lerpColor(0xFF15161F, 0xFF000000 | tint, 0.38f), a);
            int bot = Draw.withAlpha(Draw.lerpColor(0xFF0B0C12, 0xFF000000 | tint, 0.10f), a);
            Draw.roundRectV(ctx, x, y, w, h, 8, top, bot);
            Draw.ring(ctx, x, y, w, h, 8, Draw.withAlpha(Draw.lerpColor(0xFFFFFFFF, 0xFF000000 | accent, 0.5f), 0x38));
            ctx.fill(x + 8, y + 1, x + w - 8, y + 2, Draw.withAlpha(0xFFFFFF, 0x14));
        }

        // ---- cover (with a soft glow in its colour)
        int ax = x + 8, ay = y + 8, as = 44;
        TempestLoading.drawGlow(ctx, ax + as / 2, ay + as / 2, 110, accent, 0.22f * fadeIn);
        if (art != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, art, ax, ay, 0f, 0f, as, as, 128, 128, 128, 128, Draw.withAlpha(0xFFFFFF, Math.round(255 * fadeIn)));
            Draw.ring(ctx, ax, ay, as, as, 5, 0x30FFFFFF);
        } else {
            Draw.roundRectV(ctx, ax, ay, as, as, 5, 0xFF000000 | Draw.lerpColor(0xFF22242E, 0xFF000000 | tint, 0.5f), 0xFF14151C);
            Icons.draw(ctx, "note", ax + 12, ay + 12, 20, 0xC0FFFFFF);
        }

        // ---- text
        int tx = ax + as + 9, tw = x + w - 10 - tx;
        int titleW = tw - 16;   // room for the equaliser
        marquee(ctx, tr, Text.literal(title).styled(s -> s.withBold(true)), tx, y + 9, titleW, 0xFFFFFFFF, time);
        equalizer(ctx, x + w - 19, y + 9, playing, accent, time);
        ctx.drawText(tr, Draw.ellipsize(tr, artist, tw), tx, y + 21, 0xFFC9CCD8, false);
        if (showAlbum.get() && !album.isBlank()) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(tx, y + 32);
            ctx.getMatrices().scale(0.75f, 0.75f);
            ctx.drawText(tr, Draw.ellipsize(tr, album, Math.round(tw / 0.75f)), 0, 0, 0xFF8B90A0, false);
            ctx.getMatrices().popMatrix();
        }

        // ---- progress
        int by = y + 43;
        float f = dur > 0 ? Math.min(1f, pos / (float) dur) : 0f;
        Draw.roundRect(ctx, tx, by, tw, 3, 1, 0x40FFFFFF);
        int fw = Math.round(tw * f);
        if (fw > 0) Draw.roundRect(ctx, tx, by, Math.max(2, fw), 3, 1, 0xFF000000 | accent);
        Draw.circle(ctx, tx + fw, by + 1, 2, 0xFFFFFFFF);
        String el = clock(pos), tot = dur > 0 ? clock(dur) : "--:--";
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(tx, by + 6);
        ctx.getMatrices().scale(0.7f, 0.7f);
        ctx.drawText(tr, el, 0, 0, 0xFFA9AEC0, false);
        ctx.drawText(tr, tot, Math.round(tw / 0.7f) - tr.getWidth(tot), 0, 0xFFA9AEC0, false);
        ctx.getMatrices().popMatrix();
        if (!playing) {
            // paused: two small bars over the cover's corner
            Draw.roundRect(ctx, ax + as - 13, ay + as - 13, 11, 11, 3, 0xC0000000);
            ctx.fill(ax + as - 10, ay + as - 10, ax + as - 8, ay + as - 5, 0xFFFFFFFF);
            ctx.fill(ax + as - 7, ay + as - 10, ax + as - 5, ay + as - 5, 0xFFFFFFFF);
        }

        // ---- lyrics
        if (nLines > 0) drawLyrics(ctx, tr, preview && t == null ? null : t, pos, nLines, accent);
    }

    private void drawIdle(DrawContext ctx, TextRenderer tr) {
        w = 150;
        h = 26;
        int a = alpha();
        if (background.get() && a > 0) {
            Draw.roundRectV(ctx, x, y, w, h, 7, Draw.withAlpha(0x15161F, a), Draw.withAlpha(0x0B0C12, a));
            Draw.ring(ctx, x, y, w, h, 7, 0x24FFFFFF);
        }
        Icons.draw(ctx, "note", x + 8, y + 8, 10, 0xFF1DB954);
        ctx.drawText(tr, "Nothing playing", x + 24, y + 9, 0xFFA9AEC0, false);
    }

    // ------------------------------------------------------------------ lyrics
    private void drawLyrics(DrawContext ctx, TextRenderer tr, NowPlaying.Track t, long pos, int n, int accent) {
        int ly = y + HEAD + 2, lh = n * LINE + 4, cx = x + w / 2;
        ctx.fill(x + 10, ly - 3, x + w - 10, ly - 2, 0x18FFFFFF);
        List<Lyrics.Line> lines;
        String note = null;
        if (t == null) {
            lines = List.of(new Lyrics.Line(0, "Lyrics show up here"), new Lyrics.Line(3000, "line by line, in time with the song"),
                    new Lyrics.Line(6000, "the current line lights up"));
        } else {
            Lyrics.Result r = Lyrics.of(t);
            lines = r.lines();
            note = switch (r.state()) {
                case "LOADING" -> "Finding lyrics…";
                case "PLAIN" -> "No synced lyrics for this song";
                case "INSTRUMENTAL" -> "♪ Instrumental";
                case "NONE" -> "No lyrics found";
                default -> null;
            };
        }
        if (note != null) {
            ctx.drawText(tr, note, cx - tr.getWidth(note) / 2, ly + lh / 2 - 5, 0xFF8B90A0, false);
            return;
        }
        int idx = t == null ? (int) ((System.currentTimeMillis() / 3000) % lines.size()) : Lyrics.indexAt(lines, pos);
        long now = System.currentTimeMillis();
        if (idx != lastIndex) {
            if (lastIndex != Integer.MIN_VALUE) indexChangedAt = now;
            lastIndex = idx;
        }
        float k = Math.min(1f, (now - indexChangedAt) / 280f);
        k = 1f - (1f - k) * (1f - k) * (1f - k);          // ease out
        float slide = (1f - k) * LINE;                    // lines glide up into place

        // which lines: with 3 lines the current one sits in the middle, otherwise at the top
        int first = n == 3 ? idx - 1 : idx;
        ctx.enableScissor(x + 4, ly - 1, x + w - 4, ly + lh);
        for (int i = -1; i <= n; i++) {
            int li = first + i;
            float yy = ly + 2 + i * LINE + slide;
            boolean current = li == idx;
            String text = li < 0 ? (li == -1 && idx == -1 ? "♪" : null) : li < lines.size() ? lines.get(li).text() : null;
            if (text == null) continue;
            if (text.isBlank()) text = "♪";
            int col;
            if (current) col = Draw.lerpColor(0xFF9DA2B3, 0xFFFFFFFF, k);
            else {
                int dist = Math.abs(li - idx);
                col = dist == 1 ? 0x9AC9CCD8 : 0x50C9CCD8;
            }
            Text txt = current ? Text.literal(text).styled(s -> s.withBold(true)) : Text.literal(text);
            int tw = tr.getWidth(txt);
            float scale = Math.max(0.72f, Math.min(1f, (w - 18f) / Math.max(1, tw)));
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx, yy);
            ctx.getMatrices().scale(scale, scale);
            if (tw * scale > w - 18) txt = Text.literal(Draw.ellipsize(tr, text, Math.round((w - 18) / scale))).styled(s -> s.withBold(current));
            ctx.drawText(tr, txt, -tr.getWidth(txt) / 2, 0, col, current);
            ctx.getMatrices().popMatrix();
        }
        ctx.disableScissor();
    }

    // ------------------------------------------------------------------ bits
    /** Text that scrolls sideways when it doesn't fit (pausing at the start). */
    private static void marquee(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int width, int color, float time) {
        int tw = tr.getWidth(text);
        if (tw <= width) { ctx.drawText(tr, text, x, y, color, false); return; }
        int gap = 28;
        float speed = 22f, pause = 2.2f, cycle = (tw + gap) / speed + pause;
        float tt = time % cycle;
        int off = Math.round(Math.max(0f, tt - pause) * speed);
        ctx.enableScissor(x, y - 1, x + width, y + 10);
        ctx.drawText(tr, text, x - off, y, color, false);
        ctx.drawText(tr, text, x - off + tw + gap, y, color, false);
        ctx.disableScissor();
    }

    /** Three little bars that dance while the song plays. */
    private static void equalizer(DrawContext ctx, int x, int y, boolean playing, int accent, float time) {
        for (int i = 0; i < 3; i++) {
            float v = playing ? 0.35f + 0.65f * Math.abs((float) Math.sin(time * (4.2f + i * 1.7f) + i * 1.3f)) : 0.25f;
            int bh = Math.max(2, Math.round(9 * v));
            Draw.roundRect(ctx, x + i * 4, y + 9 - bh, 3, bh, 1, 0xFF000000 | (playing ? accent : 0x8B90A0));
        }
    }

    /** The cover's colour made bright enough to read as an accent. */
    private static int vivid(int rgb) {
        float[] hsv = Draw.rgbToHsv(rgb);
        float s = Math.min(0.8f, Math.max(hsv[1], hsv[1] < 0.08f ? 0f : 0.45f));
        float v = Math.max(0.85f, hsv[2]);
        return Draw.hsvToRgb(hsv[0], s, v) & 0xFFFFFF;
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }
}
