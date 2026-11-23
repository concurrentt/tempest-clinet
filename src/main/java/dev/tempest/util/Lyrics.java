package dev.tempest.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-synced lyrics from LRCLIB (lrclib.net, a free, open lyrics database; no account or key). One request per
 * song, cached for the session.
 */
public final class Lyrics {
    public record Line(long timeMs, String text) { }

    /** state: LOADING, SYNCED (lines), PLAIN (no timings, so not shown line by line), INSTRUMENTAL, NONE */
    public record Result(String state, List<Line> lines) {
        static final Result LOADING = new Result("LOADING", List.of());
        static final Result NONE = new Result("NONE", List.of());
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Map<String, Result> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Result> e) { return size() > 64; }
    });
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    private Lyrics() { }

    public static Result of(NowPlaying.Track t) {
        if (t == null) return Result.NONE;
        Result r = CACHE.get(t.key());
        return r == null ? Result.LOADING : r;
    }

    /** Starts fetching the lyrics for a song (once). */
    static void prefetch(NowPlaying.Track t) {
        if (t == null || CACHE.containsKey(t.key())) return;
        CACHE.put(t.key(), Result.LOADING);
        String q = "track_name=" + enc(t.title()) + "&artist_name=" + enc(t.artist())
                + (t.album().isBlank() ? "" : "&album_name=" + enc(t.album()))
                + (t.durationMs() > 0 ? "&duration=" + Math.round(t.durationMs() / 1000.0) : "");
        get("https://lrclib.net/api/get?" + q).thenCompose(r -> {
            if (r != null && r.isJsonObject()) return java.util.concurrent.CompletableFuture.completedFuture(r);
            // not an exact match: search instead and take the first result with synced lyrics
            return get("https://lrclib.net/api/search?track_name=" + enc(t.title()) + "&artist_name=" + enc(t.artist())).thenApply(s -> {
                if (s == null || !s.isJsonArray()) return null;
                JsonArray arr = s.getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) return e;
                }
                return arr.isEmpty() ? null : arr.get(0);
            });
        }).thenAccept(r -> CACHE.put(t.key(), parse(r))).exceptionally(e -> { CACHE.put(t.key(), Result.NONE); return null; });
    }

    private static java.util.concurrent.CompletableFuture<JsonElement> get(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Tempest Client (https://github.com/concurrentt/tempest-clinet)").GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            if (res.statusCode() != 200) return null;
            try { return JsonParser.parseString(res.body()); } catch (Exception e) { return null; }
        }).exceptionally(e -> null);
    }

    static Result parse(JsonElement e) {
        if (e == null || !e.isJsonObject()) return Result.NONE;
        JsonObject o = e.getAsJsonObject();
        if (o.has("instrumental") && o.get("instrumental").getAsBoolean()) return new Result("INSTRUMENTAL", List.of());
        String synced = o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull() ? o.get("syncedLyrics").getAsString() : null;
        if (synced != null && !synced.isBlank()) {
            List<Line> lines = new ArrayList<>();
            for (String raw : synced.split("\\r?\\n")) {
                Matcher m = STAMP.matcher(raw);
                List<Long> times = new ArrayList<>();
                int end = 0;
                while (m.find() && m.start() == end) {
                    long ms = Long.parseLong(m.group(1)) * 60_000 + Long.parseLong(m.group(2)) * 1000;
                    if (m.group(3) != null) {
                        String f = m.group(3);
                        ms += f.length() == 1 ? Long.parseLong(f) * 100 : f.length() == 2 ? Long.parseLong(f) * 10 : Long.parseLong(f);
                    }
                    times.add(ms);
                    end = m.end();
                }
                if (times.isEmpty()) continue;
                String text = raw.substring(end).trim();
                for (long t : times) lines.add(new Line(t, text));
            }
            lines.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
            if (!lines.isEmpty()) return new Result("SYNCED", lines);
        }
        String plain = o.has("plainLyrics") && !o.get("plainLyrics").isJsonNull() ? o.get("plainLyrics").getAsString() : null;
        return plain != null && !plain.isBlank() ? new Result("PLAIN", List.of()) : Result.NONE;
    }

    /** Index of the line being sung at this position (-1 = before the first line). */
    public static int indexAt(List<Line> lines, long posMs) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs <= posMs) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
}
