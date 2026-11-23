package dev.tempest.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PvP tier lists for Tier Tagger: MCTiers (mctiers.net, the list the TierTagger mod uses) and NovaTiers
 * (novatiers.com). Look-ups are cached for 20 minutes and done one at a time in the background (a few per second at
 * most), only for players you actually see, so a full tab list can't flood either site.
 */
public final class TierLists {
    public enum Source { MCTIERS, NOVA }

    /** One ranking: level 1 (best) .. 5, HT (high) or LT (low). peakLevel 0 = no peak known. */
    public record Rank(String mode, int level, boolean high, boolean retired, int peakLevel, boolean peakHigh) {
        public int score() { return level * 2 + (high ? 0 : 1); }
        public int peakScore() { return peakLevel <= 0 ? Integer.MAX_VALUE : peakLevel * 2 + (peakHigh ? 0 : 1); }
        public String label() { return (high ? "HT" : "LT") + level; }
        public String peakLabel() { return (peakHigh ? "HT" : "LT") + peakLevel; }
    }

    /** All of a player's rankings on one list (mode id -> rank). Empty = not on the list. */
    public record Profile(Map<String, Rank> ranks) {
        public static final Profile NONE = new Profile(Map.of());

        public Rank best() {
            Rank b = null;
            for (Rank r : ranks.values()) if (b == null || r.score() < b.score()) b = r;
            return b;
        }

        /** Mode ids are matched ignoring case, spaces and "_" (so "Diamond OP" == "diamond_op"). */
        public Rank in(String mode) {
            String want = norm(mode);
            for (Map.Entry<String, Rank> e : ranks.entrySet()) if (norm(e.getKey()).equals(want)) return e.getValue();
            return null;
        }
    }

    private static final class Entry {
        volatile Profile profile;
        volatile long at;
    }

    private record Job(Source source, UUID id) { }

    private static final long TTL = 20 * 60_000, RETRY = 2 * 60_000;
    private static final int MAX_QUEUE = 400;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<Source, Map<UUID, Entry>> CACHE = Map.of(Source.MCTIERS, new ConcurrentHashMap<>(), Source.NOVA, new ConcurrentHashMap<>());
    private static final ConcurrentLinkedDeque<Job> QUEUE = new ConcurrentLinkedDeque<>();
    private static final Set<Job> QUEUED = ConcurrentHashMap.newKeySet();
    private static final Pattern TIER = Pattern.compile("([HL])T\\s*([1-5])", Pattern.CASE_INSENSITIVE);
    private static ScheduledExecutorService worker;

    private TierLists() { }

    /**
     * The player's profile on a list, or null while it's still being looked up (a look-up is started if needed).
     * Only real Minecraft accounts (version-4 UUIDs) are looked up; NPCs and offline players get NONE.
     */
    public static Profile get(Source source, UUID id) {
        if (id == null || id.version() != 4) return Profile.NONE;
        Entry e = CACHE.get(source).get(id);
        long now = System.currentTimeMillis();
        if (e != null && e.profile != null && now - e.at < TTL) return e.profile;
        if (e != null && e.profile == null && now - e.at < RETRY) return null;   // failed recently: wait before retrying
        Job j = new Job(source, id);
        if (QUEUED.size() < MAX_QUEUE && QUEUED.add(j)) {
            QUEUE.addFirst(j);              // newest first: the players you're looking at right now
            start();
        }
        return e == null ? null : e.profile;
    }

    public static void clear() {
        for (Map<UUID, Entry> m : CACHE.values()) m.clear();
    }

    private static synchronized void start() {
        if (worker != null) return;
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Tempest Tier Lists");
            t.setDaemon(true);
            return t;
        });
        worker.scheduleWithFixedDelay(TierLists::work, 0, 180, TimeUnit.MILLISECONDS);
    }

    private static void work() {
        Job j = QUEUE.pollFirst();
        if (j == null) return;
        QUEUED.remove(j);
        Entry e = CACHE.get(j.source()).computeIfAbsent(j.id(), k -> new Entry());
        if (e.profile != null && System.currentTimeMillis() - e.at < TTL) return;
        Profile p;
        try {
            p = j.source() == Source.MCTIERS ? fetchMcTiers(j.id()) : fetchNova(j.id());
        } catch (Exception ex) {
            problem(j.source() == Source.MCTIERS ? "MCTiers" : "NovaTiers", ex.getClass().getSimpleName() + " " + ex.getMessage());
            p = null;
        }
        long now = System.currentTimeMillis();
        if (p != null) { e.profile = p; e.at = now; }
        else if (e.profile == null) e.at = now;           // never loaded: try again in RETRY
        else e.at = now - TTL + RETRY;                    // refresh failed: keep the old data, retry in RETRY
    }

    // ------------------------------------------------------------------ MCTiers (same API as the TierTagger mod)
    private static Profile fetchMcTiers(UUID id) throws Exception {
        // TierTagger looks players up with the rankings endpoint, which answers with just {mode: ranking};
        // the full profile (with "rankings" inside) is the fallback
        HttpResponse<String> r = get("https://mctiers.net/api/v2/profile/" + id + "/rankings");
        if (r.statusCode() != 200 && r.statusCode() != 404) r = get("https://mctiers.net/api/v2/profile/" + id);
        if (r.statusCode() == 404) return Profile.NONE;
        if (r.statusCode() != 200) {
            problem("MCTiers", "HTTP " + r.statusCode());
            throw new IllegalStateException("MCTiers " + r.statusCode());
        }
        JsonElement root;
        try { root = JsonParser.parseString(r.body()); } catch (Exception ex) { problem("MCTiers", "not JSON"); throw ex; }
        if (!root.isJsonObject()) return Profile.NONE;
        JsonObject o = root.getAsJsonObject();
        JsonObject rankings = o.has("rankings") && o.get("rankings").isJsonObject() ? o.getAsJsonObject("rankings") : o;
        Map<String, Rank> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> m : rankings.entrySet()) {
            if (!m.getValue().isJsonObject()) continue;
            JsonObject k = m.getValue().getAsJsonObject();
            int tier = intOr(k, "tier", 0);
            if (tier < 1 || tier > 5) continue;
            boolean high = intOr(k, "pos", 1) == 0;
            int peak = intOr(k, "peak_tier", 0);
            boolean peakHigh = intOr(k, "peak_pos", 1) == 0;
            boolean retired = k.has("retired") && !k.get("retired").isJsonNull() && k.get("retired").getAsBoolean();
            out.put(m.getKey(), new Rank(m.getKey(), tier, high, retired, peak, peakHigh));
        }
        return new Profile(Collections.unmodifiableMap(out));
    }

    // ------------------------------------------------------------------ NovaTiers
    private static Profile fetchNova(UUID id) throws Exception {
        HttpResponse<String> r = get("https://www.novatiers.com/users/" + id.toString().replace("-", ""));
        if (r.statusCode() == 404) r = get("https://www.novatiers.com/users/" + id);   // some entries are stored with dashes
        if (r.statusCode() == 404) return Profile.NONE;
        if (r.statusCode() != 200) throw new IllegalStateException("NovaTiers " + r.statusCode());
        JsonElement root;
        try { root = JsonParser.parseString(r.body()); } catch (Exception ex) { return Profile.NONE; }
        if (!root.isJsonObject()) return Profile.NONE;
        JsonObject o = root.getAsJsonObject();
        if (!o.has("tiers") || !o.get("tiers").isJsonObject()) return Profile.NONE;
        JsonObject peaks = o.has("peakTiers") && o.get("peakTiers").isJsonObject() ? o.getAsJsonObject("peakTiers") : new JsonObject();
        JsonObject retired = o.has("retiredTiers") && o.get("retiredTiers").isJsonObject() ? o.getAsJsonObject("retiredTiers") : new JsonObject();
        Map<String, Rank> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("tiers").entrySet()) {
            if (!m.getValue().isJsonPrimitive()) continue;
            Matcher t = TIER.matcher(m.getValue().getAsString());
            if (!t.find()) continue;
            int peak = 0;
            boolean peakHigh = false;
            if (peaks.has(m.getKey()) && peaks.get(m.getKey()).isJsonPrimitive()) {
                Matcher p = TIER.matcher(peaks.get(m.getKey()).getAsString());
                if (p.find()) { peak = Integer.parseInt(p.group(2)); peakHigh = p.group(1).equalsIgnoreCase("H"); }
            }
            boolean ret = retired.has(m.getKey()) && retired.get(m.getKey()).isJsonPrimitive() && retired.get(m.getKey()).getAsBoolean();
            out.put(m.getKey(), new Rank(m.getKey(), Integer.parseInt(t.group(2)), t.group(1).equalsIgnoreCase("H"), ret, peak, peakHigh));
        }
        return new Profile(Collections.unmodifiableMap(out));
    }

    // ------------------------------------------------------------------ helpers
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** Writes why a list can't be read to the game log, once per kind of problem (so it can be diagnosed). */
    private static void problem(String list, String why) {
        if (REPORTED.add(list + why)) System.err.println("[Tempest] Tier Tagger: " + list + " look-up failed: " + why);
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json").header("User-Agent", "TierTagger/1.0 (Tempest client)").GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static int intOr(JsonObject o, String k, int def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def; } catch (Exception e) { return def; }
    }

    static String norm(String s) { return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s_\\-]", ""); }
}
