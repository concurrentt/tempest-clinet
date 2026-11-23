package dev.tempest.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * What Spotify is playing, read from your own computer (no Spotify login):
 * <ul>
 *   <li>Windows: one small hidden PowerShell helper reads Windows' media controls (the same info as the media
 *       overlay / lock screen): title, artist, album, cover, position. It checks once a second and quits by itself
 *       when the game closes.</li>
 *   <li>macOS: asks the Spotify app (AppleScript) once a second.</li>
 *   <li>Linux: asks playerctl (MPRIS) once a second.</li>
 * </ul>
 * Runs only while the Spotify HUD is on.
 */
public final class NowPlaying {
    public record Track(String title, String artist, String album, long durationMs, String key) { }

    private static volatile Track track;
    private static volatile long posMs, posAt;
    private static volatile boolean playing;
    private static volatile Identifier art;
    private static volatile int artColor = 0x1DB954;
    private static volatile String artKey = "";
    private static volatile long lastWanted;
    private static volatile Thread thread;
    private static volatile Process process;
    private static volatile boolean any;
    private static int artCounter;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private NowPlaying() { }

    public static Track track() { return track; }
    public static boolean playing() { return playing; }
    public static Identifier art() { return art; }
    public static int artColor() { return artColor; }

    /** Current position, moved on smoothly between updates while playing. */
    public static long position() {
        Track t = track;
        long p = posMs + (playing ? System.currentTimeMillis() - posAt : 0);
        if (t != null && t.durationMs > 0) p = Math.min(p, t.durationMs);
        return Math.max(0, p);
    }

    /** Called every frame the HUD is drawn: starts the reader if needed (and keeps it alive). */
    public static void want(boolean anyPlayer) {
        lastWanted = System.currentTimeMillis();
        if (anyPlayer != any) { any = anyPlayer; stop(); }
        Thread t = thread;
        if (t == null || !t.isAlive()) start();
    }

    /** Stops when the HUD hasn't been drawn for a while (turned off, or HUD hidden). */
    public static void tick() {
        if (thread != null && System.currentTimeMillis() - lastWanted > 15_000) stop();
    }

    public static synchronized void stop() {
        Thread t = thread;
        thread = null;
        Process p = process;
        process = null;
        if (p != null) p.destroy();
        if (t != null) t.interrupt();
    }

    private static synchronized void start() {
        if (thread != null && thread.isAlive()) return;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Runnable r = os.contains("win") ? NowPlaying::runWindows : os.contains("mac") ? NowPlaying::runMac : NowPlaying::runLinux;
        Thread t = new Thread(() -> {
            try { r.run(); } catch (Throwable e) { System.err.println("[Tempest] Spotify HUD reader stopped: " + e); }
        }, "Tempest Now Playing");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    // ------------------------------------------------------------------ Windows
    private static final String PS = String.join("\n",
            "$ErrorActionPreference = 'SilentlyContinue'",
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
            "$g = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
            "function Await($op, [Type]$t) { $k = $g.MakeGenericMethod($t).Invoke($null, @($op)); [void]$k.Wait(5000); return $k.Result }",
            "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]",
            "[void][Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime]",
            "$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
            "$any = $env:TEMPEST_ANY -eq '1'",
            "$last = ''",
            "while ($true) {",
            "  if (-not (Get-Process -Id ([int]$env:TEMPEST_PID) -ErrorAction SilentlyContinue)) { exit }",
            "  $s = $null",
            "  foreach ($x in $mgr.GetSessions()) { if ($x.SourceAppUserModelId -like '*Spotify*') { $s = $x; break } }",
            "  if ($s -eq $null -and $any) { $s = $mgr.GetCurrentSession() }",
            "  try {",
            "    if ($s -eq $null) { [Console]::Out.WriteLine('{\"none\":true}') }",
            "    else {",
            "      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
            "      $tl = $s.GetTimelineProperties()",
            "      $pb = $s.GetPlaybackInfo()",
            "      $o = [ordered]@{ title = $p.Title; artist = $p.Artist; album = $p.AlbumTitle;",
            "        pos = [long]$tl.Position.TotalMilliseconds; end = [long]$tl.EndTime.TotalMilliseconds;",
            "        at = [long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds(); playing = ($pb.PlaybackStatus.ToString() -eq 'Playing') }",
            "      $key = \"$($p.Title)|$($p.Artist)|$($p.AlbumTitle)\"",
            "      if ($key -ne $last -and $p.Thumbnail -ne $null) {",
            "        $st = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])",
            "        if ($st -ne $null -and $st.Size -gt 0 -and $st.Size -lt 4000000) {",
            "          $r = [Windows.Storage.Streams.DataReader]::new($st.GetInputStreamAt(0))",
            "          [void](Await ($r.LoadAsync([uint32]$st.Size)) ([uint32]))",
            "          $b = New-Object byte[] ([int]$st.Size); $r.ReadBytes($b)",
            "          $o.art = [Convert]::ToBase64String($b); $last = $key",
            "        }",
            "      }",
            "      [Console]::Out.WriteLine(($o | ConvertTo-Json -Compress))",
            "    }",
            "    [Console]::Out.Flush()",
            "  } catch { exit }",
            "  Start-Sleep -Milliseconds 1000",
            "}");

    private static void runWindows() {
        try {
            String enc = Base64.getEncoder().encodeToString(PS.getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-EncodedCommand", enc);
            pb.environment().put("TEMPEST_PID", Long.toString(ProcessHandle.current().pid()));
            pb.environment().put("TEMPEST_ANY", any ? "1" : "0");
            pb.redirectErrorStream(false);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            process = p;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (thread == Thread.currentThread() && (line = in.readLine()) != null) {
                    if (!line.startsWith("{")) continue;
                    JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                    if (o.has("none")) { clear(); continue; }
                    String title = str(o, "title"), artist = str(o, "artist"), album = str(o, "album");
                    long end = o.has("end") ? o.get("end").getAsLong() : 0;
                    long pos = o.has("pos") ? o.get("pos").getAsLong() : 0;
                    long at = o.has("at") ? o.get("at").getAsLong() : System.currentTimeMillis();
                    boolean pl = o.has("playing") && o.get("playing").getAsBoolean();
                    // Windows reports the position as of its last update: carry it forward to now
                    long now = System.currentTimeMillis();
                    if (pl && at > 0 && at <= now && now - at < 6 * 3600_000L) { pos += now - at; at = now; } else at = now;
                    update(title, artist, album, end, pos, at, pl);
                    if (o.has("art")) {
                        byte[] img = Base64.getDecoder().decode(o.get("art").getAsString());
                        setArt(title + "|" + artist + "|" + album, img);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Tempest] Spotify HUD: couldn't read Windows media info (" + e.getMessage() + ")");
        } finally {
            Process p = process;
            if (p != null) p.destroy();
        }
        sleepQuietly(5_000);     // don't restart in a tight loop if PowerShell keeps failing
    }

    // ------------------------------------------------------------------ macOS
    private static final String OSA = "if application \"Spotify\" is running then\n"
            + "tell application \"Spotify\"\n"
            + "set t to current track\n"
            + "return (name of t) & tab & (artist of t) & tab & (album of t) & tab & (duration of t) & tab & (player position as string) & tab & (player state as string) & tab & (artwork url of t)\n"
            + "end tell\nelse\nreturn \"\"\nend if";

    private static void runMac() {
        while (thread == Thread.currentThread()) {
            String out = exec("osascript", "-e", OSA);
            if (out == null || out.isBlank()) clear();
            else {
                String[] f = out.trim().split("\t", -1);
                if (f.length >= 7) {
                    long dur = parseLong(f[3]);
                    long pos = Math.round(parseDouble(f[4]) * 1000);
                    update(f[0], f[1], f[2], dur, pos, System.currentTimeMillis(), f[5].equalsIgnoreCase("playing"));
                    artFromUrl(f[0] + "|" + f[1] + "|" + f[2], f[6]);
                }
            }
            if (!sleepQuietly(1_000)) return;
        }
    }

    // ------------------------------------------------------------------ Linux
    private static void runLinux() {
        while (thread == Thread.currentThread()) {
            String fmt = "{{title}}\t{{artist}}\t{{album}}\t{{mpris:length}}\t{{position}}\t{{status}}\t{{mpris:artUrl}}";
            String out = any ? exec("playerctl", "metadata", "--format", fmt) : exec("playerctl", "-p", "spotify", "metadata", "--format", fmt);
            if (out == null || out.isBlank()) clear();
            else {
                String[] f = out.trim().split("\t", -1);
                if (f.length >= 7) {
                    update(f[0], f[1], f[2], parseLong(f[3]) / 1000, parseLong(f[4]) / 1000, System.currentTimeMillis(), f[5].equalsIgnoreCase("Playing"));
                    artFromUrl(f[0] + "|" + f[1] + "|" + f[2], f[6]);
                }
            }
            if (!sleepQuietly(1_000)) return;
        }
    }

    private static String exec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            byte[] b = p.getInputStream().readAllBytes();
            p.waitFor();
            return new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ state
    private static void clear() {
        track = null;
        playing = false;
    }

    private static void update(String title, String artist, String album, long durMs, long pos, long at, boolean pl) {
        if (title == null || title.isBlank()) { clear(); return; }
        String key = title + "|" + artist + "|" + album;
        Track old = track;
        if (old == null || !old.key.equals(key) || old.durationMs != durMs) track = new Track(title, artist == null ? "" : artist, album == null ? "" : album, durMs, key);
        // small jitter between reports is ignored so the progress bar never jumps backwards
        long predicted = position();
        if (!pl || old == null || !old.key.equals(key) || Math.abs(predicted - pos) > 1500 || playing != pl) { posMs = pos; posAt = at; }
        playing = pl;
        Lyrics.prefetch(track);
    }

    private static void artFromUrl(String key, String url) {
        if (key.equals(artKey) || url == null || !url.startsWith("http")) return;
        artKey = key;
        try {
            HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() == 200) setArt(key, r.body());
        } catch (Exception ignored) { }
    }

    /** Decodes the cover (JPEG or PNG), rounds its corners, finds its main colour and uploads it. */
    private static void setArt(String key, byte[] bytes) {
        artKey = key;
        try {
            BufferedImage src = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) return;
            final int n = 128, rad = 16;
            BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            var g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // crop to a centred square first (some players give wide thumbnails)
            int s = Math.min(src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, n, n, (src.getWidth() - s) / 2, (src.getHeight() - s) / 2, (src.getWidth() + s) / 2, (src.getHeight() + s) / 2, null);
            g.dispose();
            int[] px = new int[n * n];
            double sr = 0, sg = 0, sb = 0, sw = 0;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int c = img.getRGB(x, y);
                    // rounded corners, anti-aliased
                    double dx = Math.max(0, Math.max(rad - x - 0.5, x + 0.5 - (n - rad))), dy = Math.max(0, Math.max(rad - y - 0.5, y + 0.5 - (n - rad)));
                    double cov = Math.max(0, Math.min(1, rad - Math.sqrt(dx * dx + dy * dy) + 0.5));
                    int a = (int) Math.round(((c >>> 24) & 255) * cov);
                    px[y * n + x] = (a << 24) | (c & 0xFFFFFF);
                    int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
                    int mx = Math.max(r, Math.max(gg, b)), mn = Math.min(r, Math.min(gg, b));
                    double w = 0.05 + (mx - mn) / 255.0 * (mx / 255.0);     // favour colourful, bright pixels
                    sr += r * w; sg += gg * w; sb += b * w; sw += w;
                }
            }
            int col = ((int) (sr / sw) << 16) | ((int) (sg / sw) << 8) | (int) (sb / sw);
            MinecraftClient.getInstance().execute(() -> {
                NativeImage ni = new NativeImage(n, n, true);
                for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) ni.setColorArgb(x, y, px[y * n + x]);
                Identifier old = art;
                Identifier id = Identifier.of("tempest", "dynamic/spotify_art_" + (artCounter++));
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "Tempest album cover", ni));
                art = id;
                artColor = col;
                if (old != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
            });
        } catch (Exception ignored) { }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static long parseLong(String s) {
        try { return (long) Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return 0; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return 0; }
    }

    private static boolean sleepQuietly(long ms) {
        try { Thread.sleep(ms); return true; } catch (InterruptedException e) { return false; }
    }
}
