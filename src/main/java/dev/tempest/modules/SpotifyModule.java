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
    private final ModeSetting lyricsWhere = add(new ModeSetting("lyrics_where", "Lyrics Display", "Card", "Card", "Sky", "Both"));
    private final ModeSetting skyHeight = add(new ModeSetting("sky_height", "Sky Lyrics Height", "Medium", "Low", "Medium", "High"));
    private final ModeSetting skySize = add(new ModeSetting("sky_size", "Sky Lyrics Size", "Large", "Medium", "Large", "Huge"));
    private final BoolSetting albumColors = add(new BoolSetting("album_colors", "Album Colours", true));
    private final BoolSetting showAlbum = add(new BoolSetting("show_album", "Show Album", true));
    private final BoolSetting hideIdle = add(new BoolSetting("hide_idle", "Hide When Nothing Plays", true));
    private final BoolSetting anyPlayer = add(new BoolSetting("any_player", "Any Music Player (not just Spotify)", false));

    private static final int W = 236, HEAD = 66, LINE = 12;

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
        if (lyricsWhere.is("Sky")) return 0;                // lyrics are in the sky instead of on the card
        return switch (lyrics.get()) { case "1 Line" -> 1; case "2 Lines" -> 2; case "3 Lines" -> 3; default -> 0; };
    }

    // ------------------------------------------------------------------ lyrics in the sky (render/SkyLyrics)
    /** This module when it should put lyrics in the sky, else null. */
    public static SpotifyModule skyOn() {
        SpotifyModule m = dev.tempest.module.ModuleManager.get(SpotifyModule.class);
        return m != null && m.isEnabled() && !m.lyricsWhere.is("Card") && !m.lyrics.is("Off") ? m : null;
    }

    public boolean anyPlayerOn() { return anyPlayer.get(); }

    /** Degrees above the horizon. */
    public float skyElevation() { return switch (skyHeight.get()) { case "Low" -> 16f; case "High" -> 42f; default -> 28f; }; }

    /** Height of the current lyric line, in degrees of your view. */
    public float skyLineDegrees() { return switch (skySize.get()) { case "Medium" -> 3.2f; case "Huge" -> 6.5f; default -> 4.6f; }; }

    /** The glow colour: the cover's colour (if album colours are on and a cover loaded), else the theme colour. */
    public int skyGlow() {
        int tint = NowPlaying.art() != null && albumColors.get() ? NowPlaying.artColor() : Draw.accent() & 0xFFFFFF;
        return vivid(tint);
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
        if (!albumColors.get() || (art == null && !(preview && t == null))) tint = Draw.accent() & 0xFFFFFF;
        int accent = vivid(tint);

        if (t != null && !t.key().equals(lastTrack)) { lastTrack = t.key(); trackChangedAt = System.currentTimeMillis(); lastIndex = Integer.MIN_VALUE; }
        float fadeIn = Math.min(1f, (System.currentTimeMillis() - trackChangedAt) / 350f);

        int nLines = lyricLines();
        w = W;
        int lyricH = nLines > 0 ? nLines * LINE + 12 : 0;
        h = HEAD + (nLines > 0 ? lyricH + 6 : 0);
        int a = alpha();

        // ---- card: dark glass with a soft wash of the cover's colour from the top
        if (background.get() && a > 0) {
            Draw.shadow(ctx, x, y, w, h, 10, 4);
            int top = Draw.withAlpha(Draw.lerpColor(0xFF121319, 0xFF000000 | tint, 0.20f), a);
            int bot = Draw.withAlpha(Draw.lerpColor(0xFF0A0B10, 0xFF000000 | tint, 0.04f), a);
            Draw.roundRectV(ctx, x, y, w, h, 9, top, bot);
            Draw.ring(ctx, x, y, w, h, 9, Draw.withAlpha(0xFFFFFF, 0x1C));
            Draw.hairline(ctx, x + 10, y + 1, w - 20, 0xFFFFFF, 0x26);
        }

        // ---- cover, with a soft glow in its colour
        int ax = x + 9, ay = y + 9, as = 48;
        TempestLoading.drawGlow(ctx, ax + as / 2, ay + as / 2, 90, accent, 0.16f * fadeIn);
        Draw.roundRect(ctx, ax - 1, ay - 1, as + 2, as + 2, 7, 0x50000000);
        if (art != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, art, ax, ay, 0f, 0f, as, as, 128, 128, 128, 128, Draw.withAlpha(0xFFFFFF, Math.round(255 * fadeIn)));
        } else {
            Draw.roundRectV(ctx, ax, ay, as, as, 6, 0xFF000000 | Draw.lerpColor(0xFF262833, 0xFF000000 | accent, 0.35f), 0xFF15161D);
            Icons.draw(ctx, "note", ax + 14, ay + 14, 20, 0x90FFFFFF);
        }
        if (!playing) {
            // paused: dim the cover and put a clean pause sign in the middle
            Draw.roundRect(ctx, ax, ay, as, as, 6, 0x70000000);
            int cx = ax + as / 2, cy = ay + as / 2;
            Draw.circle(ctx, cx, cy, 9, 0xD0FFFFFF);
            ctx.fill(cx - 3, cy - 4, cx - 1, cy + 4, 0xFF111217);
            ctx.fill(cx + 1, cy - 4, cx + 3, cy + 4, 0xFF111217);
        }

        // ---- text
        int tx = ax + as + 10, tw = x + w - 11 - tx;
        int titleW = playing ? tw - 16 : tw;      // room for the equaliser while playing
        marquee(ctx, tr, Text.literal(title).styled(s -> s.withBold(true)), tx, y + 11, titleW, 0xFFFFFFFF, time);
        if (playing) equalizer(ctx, x + w - 21, y + 11, accent, time);
        Draw.text(ctx, tr, Draw.ellipsize(tr, artist, tw), tx, y + 23, 0xFFD2D5E0, false);
        if (showAlbum.get() && !album.isBlank()) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(tx, y + 34);
            ctx.getMatrices().scale(0.75f, 0.75f);
            Draw.text(ctx, tr, Draw.ellipsize(tr, album, Math.round(tw / 0.75f)), 0, 0, 0xFF868B9C, false);
            ctx.getMatrices().popMatrix();
        }

        // ---- progress: a thin bar, elapsed on the left and time left on the right
        int by = y + 46;
        float f = dur > 0 ? Math.min(1f, pos / (float) dur) : 0f;
        Draw.roundRect(ctx, tx, by, tw, 2, 1, 0x30FFFFFF);
        int fw = Math.round(tw * f);
        if (fw > 0) Draw.roundRect(ctx, tx, by, Math.max(2, fw), 2, 1, 0xFF000000 | accent);
        Draw.circle(ctx, tx + fw, by + 1, 2, 0xFFFFFFFF);
        String el = clock(pos), left = dur > 0 ? "-" + clock(Math.max(0, dur - pos)) : "--:--";
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(tx, by + 6);
        ctx.getMatrices().scale(0.7f, 0.7f);
        Draw.text(ctx, tr, el, 0, 0, 0xFF9DA2B3, false);
        Draw.text(ctx, tr, left, Math.round(tw / 0.7f) - Draw.textW(tr, left), 0, 0xFF9DA2B3, false);
        ctx.getMatrices().popMatrix();

        // ---- lyrics, in their own inset panel
        if (nLines > 0) {
            int ly = y + HEAD;
            Draw.roundRect(ctx, x + 7, ly, w - 14, lyricH, 6, Draw.withAlpha(0x000000, Math.round(0x38 * Math.max(0.4f, a / 255f))));
            drawLyrics(ctx, tr, preview && t == null ? null : t, pos, nLines, accent, ly, lyricH);
        }
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
        Draw.text(ctx, tr, "Nothing playing", x + 24, y + 9, 0xFFA9AEC0, false);
    }

    // ------------------------------------------------------------------ lyrics
    private void drawLyrics(DrawContext ctx, TextRenderer tr, NowPlaying.Track t, long pos, int n, int accent, int ly, int lh) {
        int cx = x + w / 2, maxW = w - 26;
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
            Draw.text(ctx, tr, note, cx - Draw.textW(tr, note) / 2, ly + lh / 2 - 4, 0xFF7C8194, false);
            return;
        }
        int idx = t == null ? (int) ((System.currentTimeMillis() / 3000) % lines.size()) : Lyrics.indexAt(lines, pos);
        long now = System.currentTimeMillis();
        if (idx != lastIndex) {
            if (lastIndex != Integer.MIN_VALUE) indexChangedAt = now;
            lastIndex = idx;
        }
        float k = Math.min(1f, (now - indexChangedAt) / 300f);
        k = 1f - (1f - k) * (1f - k) * (1f - k);          // ease out
        float slide = (1f - k) * LINE;                    // lines glide up into place

        // with 3 lines the current one sits in the middle, otherwise at the top
        int first = n == 3 ? idx - 1 : idx;
        int top = ly + 6;
        ctx.enableScissor(x + 8, ly + 1, x + w - 8, ly + lh - 1);
        for (int i = -1; i <= n; i++) {
            int li = first + i;
            float yy = top + i * LINE + slide;
            boolean current = li == idx;
            String text = li < 0 ? (li == -1 && idx == -1 ? "♪" : null) : li < lines.size() ? lines.get(li).text() : null;
            if (text == null) continue;
            if (text.isBlank()) text = "♪";
            int col = current ? Draw.lerpColor(0xFF8A8FA0, 0xFFFFFFFF, k)
                    : Math.abs(li - idx) == 1 ? 0x8CC9CCD8 : 0x40C9CCD8;
            float scale = current ? 1f : 0.86f;
            int tw = Draw.textW(tr, text);
            if (tw * scale > maxW) scale = Math.max(0.7f, maxW / (float) tw);
            String shown = tw * scale > maxW ? Draw.ellipsize(tr, text, Math.round(maxW / scale)) : text;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx, yy + (current ? 0 : 1));
            ctx.getMatrices().scale(scale, scale);
            Draw.text(ctx, tr, shown, -Draw.textW(tr, shown) / 2, 0, col, false);
            ctx.getMatrices().popMatrix();
        }
        ctx.disableScissor();
        // soft fades at the top and bottom edges of the lyric panel
        ctx.fillGradient(x + 8, ly + 1, x + w - 8, ly + 5, 0x30000000, 0x00000000);
        ctx.fillGradient(x + 8, ly + lh - 5, x + w - 8, ly + lh - 1, 0x00000000, 0x30000000);
    }

    // ------------------------------------------------------------------ bits
    /** Text that scrolls sideways when it doesn't fit (pausing at the start). */
    private static void marquee(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int width, int color, float time) {
        int tw = Draw.textW(tr, text);
        if (tw <= width) { Draw.text(ctx, tr, text, x, y, color, false); return; }
        int gap = 28;
        float speed = 22f, pause = 2.2f, cycle = (tw + gap) / speed + pause;
        float tt = time % cycle;
        int off = Math.round(Math.max(0f, tt - pause) * speed);
        ctx.enableScissor(x, y - 1, x + width, y + 10);
        Draw.text(ctx, tr, text, x - off, y, color, false);
        Draw.text(ctx, tr, text, x - off + tw + gap, y, color, false);
        ctx.disableScissor();
    }

    /** Three little bars that dance while the song plays. */
    private static void equalizer(DrawContext ctx, int x, int y, int accent, float time) {
        for (int i = 0; i < 3; i++) {
            float v = 0.35f + 0.65f * Math.abs((float) Math.sin(time * (4.2f + i * 1.7f) + i * 1.3f));
            int bh = Math.max(2, Math.round(8 * v));
            Draw.roundRect(ctx, x + i * 4, y + 8 - bh, 3, bh, 1, 0xFF000000 | accent);
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
