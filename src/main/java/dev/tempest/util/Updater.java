package dev.tempest.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tempest.TempestConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Tempest auto-updater, backed by GitHub Releases of {@link #REPO}.
 *
 * <ol>
 *   <li>On start (in the background) it asks GitHub for the latest release and compares its tag (v0.18.0) with
 *       this version. Newer + a .jar attached = an update is offered on the main menu. Nothing is downloaded
 *       without you clicking Update.</li>
 *   <li>Update downloads the jar over HTTPS from that release only, checks its SHA-256 against the digest
 *       GitHub publishes for the asset (a damaged or swapped file is thrown away), and saves it next to the
 *       current jar as "&lt;name&gt;.jar.update" (Fabric ignores that name).</li>
 *   <li>Windows can't delete a running jar, so a tiny helper process is started that waits for Minecraft to
 *       close, then deletes the old jar and renames the new one to .jar. If anything fails, the old jar simply
 *       stays and nothing breaks.</li>
 * </ol>
 */
public final class Updater {
    /** owner/name of the public GitHub repository that publishes Tempest releases. */
    public static final String REPO = "concurrentt/tempest-clinet";

    public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private static volatile State state = State.IDLE;
    private static volatile String latest, notes, downloadUrl, sha256, error;
    private static volatile long size;
    private static volatile float progress;

    private Updater() { }

    public static State state() { return state; }
    public static String latestVersion() { return latest; }
    public static String notes() { return notes == null ? "" : notes; }
    public static String error() { return error; }
    public static float progress() { return progress; }

    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer("tempest")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
    }

    /** Is there an update the main menu should offer (not skipped)? */
    public static boolean offer() {
        return state == State.AVAILABLE && latest != null && !latest.equals(TempestConfig.get().skippedUpdate);
    }

    public static void skip() {
        TempestConfig.get().skippedUpdate = latest;
        TempestConfig.save();
    }

    // ------------------------------------------------------------------ check
    private static volatile long lastCheck;

    /** Re-check when returning to the main menu, at most every 10 minutes (no-op if the setting is off). */
    public static void checkIfStale() {
        if (!dev.tempest.modules.ClientSettingsModule.checkUpdatesOn()) return;
        if (System.currentTimeMillis() - lastCheck < 10 * 60_000L) return;
        if (state == State.AVAILABLE) return;
        checkAsync();
    }

    private static volatile boolean manual;
    private static volatile String announced;

    /** "Check for updates" button (main menu or in game): checks now and always says what it found. */
    public static void checkNow() {
        if (state == State.AVAILABLE) { announced = null; announce(); manualDone(); return; }
        manual = true;
        if (state == State.UP_TO_DATE || state == State.FAILED) state = State.IDLE;
        checkAsync();
    }

    private static void manualDone() { manual = false; }

    /** After a check: a pop-up (and a chat line in game) for a new version, once per version; results of a manual check. */
    private static void announce() {
        if (state == State.AVAILABLE && latest != null && !latest.equals(announced)) {
            announced = latest;
            dev.tempest.gui.TempestToast.showIcon("refresh", "Tempest update available", "v" + latest + " - install it from the main menu", 0x8E7CFF, 8000);
            net.minecraft.client.MinecraftClient c = net.minecraft.client.MinecraftClient.getInstance();
            c.execute(() -> { if (c.world != null) Chat.info("Tempest v" + latest + " is out (you have v" + currentVersion() + "). Install it from the main menu."); });
        } else if (manual && state == State.UP_TO_DATE) {
            dev.tempest.gui.TempestToast.showIcon("refresh", "Tempest is up to date", "You have the newest version, v" + currentVersion(), 0x43D17A, 4000);
        } else if (manual && state == State.FAILED) {
            dev.tempest.gui.TempestToast.showIcon("refresh", "Couldn't check for updates", error == null ? "Try again later" : error, 0xFF6B6B, 5000);
        }
        manual = false;
    }

    public static void checkAsync() {
        if (state == State.CHECKING || state == State.DOWNLOADING || state == State.READY) return;
        state = State.CHECKING;
        lastCheck = System.currentTimeMillis();
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Tempest-Updater")
                .GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            try {
                if (err != null || res == null) throw new IllegalStateException("no connection");
                if (res.statusCode() == 404) { state = State.UP_TO_DATE; return; }       // no releases yet
                if (res.statusCode() != 200) throw new IllegalStateException("GitHub said " + res.statusCode());
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                String tag = o.get("tag_name").getAsString();
                String ver = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
                JsonObject asset = null;
                JsonArray assets = o.getAsJsonArray("assets");
                if (assets != null) {
                    for (JsonElement e : assets) {
                        JsonObject a = e.getAsJsonObject();
                        String name = a.get("name").getAsString().toLowerCase(Locale.ROOT);
                        if (name.endsWith(".jar") && !name.contains("sources") && !name.contains("dev")) { asset = a; break; }
                    }
                }
                if (asset == null || compare(ver, currentVersion()) <= 0) { state = State.UP_TO_DATE; return; }
                latest = ver;
                notes = o.has("body") && !o.get("body").isJsonNull() ? o.get("body").getAsString() : "";
                downloadUrl = asset.get("browser_download_url").getAsString();
                size = asset.has("size") ? asset.get("size").getAsLong() : -1;
                sha256 = null;
                if (asset.has("digest") && !asset.get("digest").isJsonNull()) {
                    String d = asset.get("digest").getAsString();
                    if (d.startsWith("sha256:")) sha256 = d.substring(7).toLowerCase(Locale.ROOT);
                }
                state = State.AVAILABLE;
            } catch (Exception e) {
                error = e.getMessage();
                state = State.FAILED;
            } finally {
                announce();
            }
        });
    }

    /** Numeric version compare: 0.17.10 > 0.17.9; anything after '-' or '+' is ignored. */
    public static int compare(String a, String b) {
        String[] x = a.split("[-+]")[0].split("\\."), y = b.split("[-+]")[0].split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (NumberFormatException e) { return 0; }
    }

    // ------------------------------------------------------------------ install
    /** The jar Tempest is running from, or null in a development environment (classes folder). */
    private static Path currentJar() {
        Optional<ModContainer> mc = FabricLoader.getInstance().getModContainer("tempest");
        if (mc.isEmpty()) return null;
        try {
            List<Path> paths = mc.get().getOrigin().getPaths();
            for (Path p : paths) if (p.toString().toLowerCase(Locale.ROOT).endsWith(".jar") && Files.isRegularFile(p)) return p;
        } catch (Throwable ignored) { }
        return null;
    }

    public static boolean canInstall() { return currentJar() != null; }

    public static void downloadAsync() {
        if (state != State.AVAILABLE || downloadUrl == null) return;
        Path jar = currentJar();
        if (jar == null) { error = "Running from a development build - update by hand."; state = State.FAILED; return; }
        if (!downloadUrl.startsWith("https://github.com/" + REPO + "/releases/download/")) {
            error = "Unexpected download address - update refused.";
            state = State.FAILED;
            return;
        }
        state = State.DOWNLOADING;
        progress = 0f;
        Thread t = new Thread(() -> {
            Path dir = jar.getParent();
            String newName = "tempest-" + latest + ".jar";
            Path temp = dir.resolve(newName + ".update");
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(downloadUrl)).timeout(Duration.ofMinutes(5))
                        .header("User-Agent", "Tempest-Updater").GET().build();
                HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() != 200) throw new IllegalStateException("download failed (" + res.statusCode() + ")");
                long total = size > 0 ? size : res.headers().firstValueAsLong("Content-Length").orElse(-1);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long read = 0;
                try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(temp)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        md.update(buf, 0, n);
                        read += n;
                        if (total > 0) progress = Math.min(1f, read / (float) total);
                    }
                }
                String got = HexFormat.of().formatHex(md.digest());
                if (sha256 != null && !sha256.equals(got)) {
                    Files.deleteIfExists(temp);
                    throw new IllegalStateException("the download was damaged (checksum mismatch) and was removed");
                }
                if (read < 10_000) {
                    Files.deleteIfExists(temp);
                    throw new IllegalStateException("the downloaded file is too small to be Tempest");
                }
                scheduleSwap(jar, temp, dir.resolve(newName));
                progress = 1f;
                state = State.READY;
            } catch (Exception e) {
                try { Files.deleteIfExists(temp); } catch (Exception ignored) { }
                error = e.getMessage();
                state = State.FAILED;
            }
        }, "Tempest updater");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Starts a hidden helper that waits for this game process to exit, then replaces the old jar with the new one.
     * Paths go in through environment variables, never into the command text, so spaces, quotes or $ in folder
     * names can't break (or inject into) the script.
     */
    private static void scheduleSwap(Path oldJar, Path downloaded, Path finalJar) throws Exception {
        long pid = ProcessHandle.current().pid();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        ProcessBuilder pb;
        if (windows) {
            String script = "Wait-Process -Id ([int]$env:TEMPEST_PID) -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 800; "
                    + "for($i=0;$i -lt 30;$i++){ try { Remove-Item -LiteralPath $env:TEMPEST_OLD -Force -ErrorAction Stop; break } catch { Start-Sleep -Milliseconds 500 } }; "
                    + "if (-not (Test-Path -LiteralPath $env:TEMPEST_OLD)) { Move-Item -LiteralPath $env:TEMPEST_TMP -Destination $env:TEMPEST_NEW -Force }";
            pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-Command", script);
        } else {
            pb = new ProcessBuilder("sh", "-c", "while kill -0 \"$TEMPEST_PID\" 2>/dev/null; do sleep 1; done; "
                    + "rm -f \"$TEMPEST_OLD\" && mv -f \"$TEMPEST_TMP\" \"$TEMPEST_NEW\"");
        }
        pb.environment().put("TEMPEST_PID", String.valueOf(pid));
        pb.environment().put("TEMPEST_OLD", oldJar.toAbsolutePath().toString());
        pb.environment().put("TEMPEST_TMP", downloaded.toAbsolutePath().toString());
        pb.environment().put("TEMPEST_NEW", finalJar.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.start();
    }

    /**
     * If a previous update was downloaded but the swap never ran (some launchers kill child processes when the game
     * closes), pick it up again: re-arm the helper so the next restart finishes it, and keep the menu prompt up.
     */
    public static void resumePending() {
        Path jar = currentJar();
        if (jar == null) return;
        try (var files = Files.list(jar.getParent())) {
            String cur = currentVersion();
            for (Path p : (Iterable<Path>) files::iterator) {
                String n = p.getFileName().toString();
                if (!n.startsWith("tempest-") || !n.endsWith(".jar.update")) continue;
                String ver = n.substring("tempest-".length(), n.length() - ".jar.update".length());
                if (compare(ver, cur) <= 0 || Files.size(p) < 10_000) { Files.deleteIfExists(p); continue; }
                latest = ver;
                scheduleSwap(jar, p, jar.getParent().resolve("tempest-" + ver + ".jar"));
                progress = 1f;
                state = State.READY;
                return;
            }
        } catch (Exception ignored) { }
    }
}
