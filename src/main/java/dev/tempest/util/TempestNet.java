package dev.tempest.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tempest.TempestConfig;
import dev.tempest.modules.NametagsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.entity.player.PlayerEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to the Tempest online service (tempest-worker/, hosted on Cloudflare) so Tempest users can see each other.
 *
 * <ul>
 *   <li>Sign-in: the service hands out a one-time code; the game signs it with your Mojang-issued player key (the
 *       same key that signs your chat) and sends the public half plus Mojang's certificate for it. The service
 *       checks both. Your Minecraft access token is never used or sent anywhere.</li>
 *   <li>Heartbeat every 60 s while you are in a world ("I'm online with Tempest").</li>
 *   <li>Lookup every 10 s for players near you, in one batch, cached for 2 minutes.</li>
 * </ul>
 * Everything runs off the game thread. If the service is unreachable it backs off and the game is unaffected.
 * Switch it off in Render -> Nametags.
 */
public final class TempestNet {
    /**
     * Your Railway address, e.g. "https://tempest-backend-production.up.railway.app". Can be overridden in
     * config/tempest.json ("backendUrl"). Empty = the online feature is off.
     */
    public static final String DEFAULT_URL = "https://tempest-backend.imamunchlmaoii.workers.dev";

    private static final long HEARTBEAT_MS = 45_000, LOOKUP_MS = 10_000, CACHE_MS = 120_000;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    /** uuid -> time we last learned they use Tempest */
    private static final Map<UUID, Long> TEMPEST = new ConcurrentHashMap<>();
    /** uuid -> time we last asked about them */
    private static final Map<UUID, Long> ASKED = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private static volatile String token;
    private static volatile long tokenExpires;
    private static volatile boolean busy;
    private static volatile String lastError = "";
    private static volatile String lastProblem;
    private static volatile boolean loggedOut;
    private static volatile long lastBeatOk;
    private static long lastHeartbeat, lastLookup, retryAt;
    private static long backoff = 60_000;

    private TempestNet() { }

    private static String base() {
        String u = TempestConfig.get().backendUrl;
        if (u == null || u.isBlank()) u = DEFAULT_URL;
        u = u.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    /** Does this player run Tempest (as far as we know right now)? */
    public static boolean isTempestUser(UUID id) {
        Long t = id == null ? null : TEMPEST.get(id);
        return t != null && System.currentTimeMillis() - t < CACHE_MS + LOOKUP_MS;
    }

    // ------------------------------------------------------------------ driven from the client tick
    public static void tick(MinecraftClient c) {
        if (base().isEmpty()) return;
        boolean share = NametagsModule.shareOnline(), show = NametagsModule.showOnline();
        boolean friends = dev.tempest.modules.FriendsModule.onlineToFriends();
        boolean wanted = share || friends || show || Social.wanted();
        if (!wanted) {
            if (token != null && !loggedOut) { post("/v1/logout", new JsonObject(), true); loggedOut = true; }
            return;
        }
        long now = System.currentTimeMillis();
        if (busy || now < retryAt) return;

        if (token == null || now > tokenExpires - 60_000) {
            signIn(c);
            return;
        }
        if ((share || friends) && now - lastHeartbeat >= HEARTBEAT_MS) {
            lastHeartbeat = now;
            loggedOut = false;
            JsonObject hb = new JsonObject();
            hb.addProperty("public", share);
            hb.addProperty("friends", friends);
            String server = currentServer(c);
            if (server != null) hb.addProperty("server", server);
            post("/v1/heartbeat", hb, true).thenAccept(r -> {
                if (r == null) { lastBeatOk = 0; return; }
                lastBeatOk = System.currentTimeMillis();
                if (r.has("incoming")) Social.onIncomingCount(r.get("incoming").getAsInt());
            });
            return;
        }
        if (show && c.world != null && c.player != null && now - lastLookup >= LOOKUP_MS) {
            lastLookup = now;
            lookup(c, now);
        }
    }

    /** What your friends see: the server address, "Singleplayer", "In game" (if you hide it), or nothing (menus). */
    private static String currentServer(MinecraftClient c) {
        if (c.world == null) return null;
        if (!dev.tempest.modules.FriendsModule.shareServer()) return "In game";
        if (c.isInSingleplayer()) return "Singleplayer";
        var entry = c.getCurrentServerEntry();
        return entry != null ? entry.address : "In game";
    }

    /** Leaving a world: tell friends you're back in the menus on the next tick. */
    public static void onLeave() {
        lastHeartbeat = 0;
    }

    /** Game closing: drop off the online list straight away (best effort, short wait). */
    public static void shutdown() {
        if (token == null || base().isEmpty()) return;
        try { post("/v1/logout", new JsonObject(), true).get(2, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { }
    }

    public static boolean signedIn() { return token != null && System.currentTimeMillis() < tokenExpires; }

    /** Send a heartbeat on the next tick (e.g. the Social menu opened), unless one went out in the last 10 s. */
    public static void heartbeatSoon() {
        if (System.currentTimeMillis() - lastHeartbeat > 10_000) lastHeartbeat = 0;
    }

    /**
     * What your friends actually see, confirmed by the service: "Online", "Hidden from friends" (setting off),
     * or a reason it isn't getting through.
     */
    public static String presence() {
        if (!signedIn()) return status() == null ? "Connecting\u2026" : status();
        if (!dev.tempest.modules.FriendsModule.onlineToFriends()) return "Hidden from friends";
        if (System.currentTimeMillis() - lastBeatOk < HEARTBEAT_MS * 2 + 15_000) return "Online";
        return lastHeartbeat == 0 ? "Connecting\u2026" : "Status not reaching Tempest";
    }

    public static boolean visibleOnline() { return "Online".equals(presence()); }

    /** Short status for menus. */
    public static String status() {
        if (base().isEmpty()) return "Online features are off";
        if (signedIn()) return null;
        if (busy) return "Connecting\u2026";
        if (lastProblem != null) return lastProblem;
        return "Connecting\u2026";
    }

    /**
     * Signed-in call for the social features. Completes with the reply (which has "error" when the service said no),
     * or null if it couldn't be reached / you're not signed in yet.
     */
    public static CompletableFuture<JsonObject> api(String path, JsonObject body) {
        if (!signedIn()) return CompletableFuture.completedFuture(null);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
                if (res.statusCode() == 401) { token = null; return null; }
                if (res.statusCode() != 200 && res.statusCode() != 400) return null;
                try { return JsonParser.parseString(res.body()).getAsJsonObject(); } catch (Exception e) { return null; }
            }).exceptionally(e -> null);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    // ------------------------------------------------------------------ sign-in
    private static void signIn(MinecraftClient c) {
        Session s = c.getSession();
        UUID id = s == null ? null : s.getUuidOrNull();
        String name = s == null ? null : s.getUsername();
        if (id == null || name == null) { fail("no Minecraft account session"); return; }
        busy = true;
        JsonObject start = new JsonObject();
        start.addProperty("name", name);
        CompletableFuture<String> code = post("/v1/auth/start", start, false).thenApply(r -> {
            if (r == null || !r.has("serverId")) throw new IllegalStateException("start refused");
            return r.get("serverId").getAsString();
        });
        // the player key pair Mojang issues to every real account (offline / cracked accounts don't have one)
        code.thenCombine(c.getProfileKeys().fetchKeyPair(), (serverId, keys) -> {
            if (keys.isEmpty()) throw new IllegalStateException("no Mojang player key (offline account or chat keys unavailable)");
            var pair = keys.get();
            var data = pair.publicKey().data();
            try {
                java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
                sig.initSign(pair.privateKey());
                sig.update(("tempest-auth|" + serverId + "|" + id.toString().replace("-", "")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
                JsonObject finish = new JsonObject();
                finish.addProperty("name", name);
                finish.addProperty("serverId", serverId);
                finish.addProperty("uuid", id.toString());
                finish.addProperty("publicKey", b64.encodeToString(data.key().getEncoded()));
                finish.addProperty("expiresAt", data.expiresAt().toEpochMilli());
                finish.addProperty("keySignature", b64.encodeToString(data.keySignature()));
                finish.addProperty("signature", b64.encodeToString(sig.sign()));
                return finish;
            } catch (java.security.GeneralSecurityException e) {
                throw new IllegalStateException("could not sign: " + e.getMessage());
            }
        }).thenCompose(finish -> post("/v1/auth/finish", finish, false)).whenComplete((r, err) -> {
            busy = false;
            if (err != null || r == null || !r.has("token")) {
                Throwable cause = err != null && err.getCause() != null ? err.getCause() : err;
                fail(cause != null ? "sign-in failed: " + cause.getMessage() : "sign-in refused (" + lastError + ")");
                return;
            }
            token = r.get("token").getAsString();
            long ttl = r.has("expiresIn") ? r.get("expiresIn").getAsLong() * 1000 : 3_600_000;
            tokenExpires = System.currentTimeMillis() + ttl;
            backoff = 60_000;
            lastProblem = null;
            lastHeartbeat = 0;   // send a heartbeat (with your server and privacy choices) right away
        });
    }

    // ------------------------------------------------------------------ lookup
    private static void lookup(MinecraftClient c, long now) {
        List<UUID> ask = new ArrayList<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        List<UUID> candidates = new ArrayList<>();
        for (PlayerEntity p : c.world.getPlayers()) if (p != c.player) candidates.add(p.getUuid());   // nearby first
        if (c.getNetworkHandler() != null) {
            for (var entry : c.getNetworkHandler().getPlayerList()) candidates.add(ProfileIds.of(entry.getProfile()));  // whole tab list
        }
        UUID me = c.player.getUuid();
        for (UUID id : candidates) {
            if (id == null || id.equals(me) || id.version() != 4 || !seen.add(id)) continue;   // only real (premium) accounts
            Long last = ASKED.get(id);
            if (last == null || now - last >= CACHE_MS) ask.add(id);
            if (ask.size() >= 200) break;
        }
        if (ask.isEmpty()) return;
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        for (UUID id : ask) { arr.add(id.toString()); ASKED.put(id, now); }
        body.add("uuids", arr);
        post("/v1/lookup", body, true).thenAccept(r -> {
            if (r == null || !r.has("tempest")) return;
            long t = System.currentTimeMillis();
            for (UUID id : ask) TEMPEST.remove(id);
            for (var e : r.getAsJsonArray("tempest")) {
                try { TEMPEST.put(UUID.fromString(e.getAsString()), t); } catch (IllegalArgumentException ignored) { }
            }
        });
    }

    // ------------------------------------------------------------------ http
    /** POSTs JSON to the service; completes with the parsed reply, or null on any failure. */
    private static CompletableFuture<JsonObject> post(String path, JsonObject body, boolean authed) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            if (authed && token != null) b.header("Authorization", "Bearer " + token);
            return HTTP.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
                if (res.statusCode() == 401) { token = null; return null; }   // signed out on the service: sign in again
                if (res.statusCode() == 429) { fail("rate limited"); return null; }
                if (res.statusCode() != 200) { lastError = res.statusCode() + " " + res.body(); return null; }
                return JsonParser.parseString(res.body()).getAsJsonObject();
            }).exceptionally(e -> { fail("service unreachable"); return null; });
        } catch (Exception e) {
            fail("bad service address");
            return CompletableFuture.completedFuture(null);
        }
    }

    private static void fail(String why) {
        lastProblem = why.contains("no Mojang player key") ? "Needs a Microsoft account (online mode)"
                : why.contains("unreachable") ? "Can't reach the Tempest service" : "Couldn't sign in - retrying";
        retryAt = System.currentTimeMillis() + backoff;
        backoff = Math.min(backoff * 2, 600_000);
        if (LOGGED.add(why)) System.err.println("[Tempest] Online service: " + why + " (retrying later)");
    }
}
