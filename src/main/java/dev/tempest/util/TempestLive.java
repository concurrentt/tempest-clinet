package dev.tempest.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The live link to the Tempest service (GET /v1/live, a WebSocket). While you're in a world it carries:
 * <ul>
 *   <li>out: which Tempest players are near you ("watch", up to 48), and the emote you start or stop;</li>
 *   <li>in: emotes and cosmetics changes from those nearby players.</li>
 * </ul>
 * One small message when something changes, plus a "ping" every 30 s so the connection stays open (the service
 * answers it without waking up). Reconnects with back-off; if it can't connect nothing else is affected.
 */
public final class TempestLive {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final long PING_MS = 30_000, WATCH_MS = 2_000;

    private static volatile WebSocket ws;
    private static volatile boolean connecting;
    private static volatile long retryAt;
    private static long backoff = 5_000, lastPing, lastWatchCheck;
    private static String lastWatch = "";
    private static CompletableFuture<?> sending = CompletableFuture.completedFuture(null);

    private TempestLive() { }

    public static boolean connected() { return ws != null; }

    /** Called every client tick by TempestNet with the current token (null = not signed in). */
    static void tick(MinecraftClient c, String base, String token) {
        boolean want = token != null && c.world != null && c.player != null && (Emotes.wanted() || RemoteCosmetics.seeOthers());
        if (!want) { close(); return; }
        long now = System.currentTimeMillis();
        WebSocket w = ws;
        if (w == null) {
            if (!connecting && now >= retryAt) connect(base, token);
            return;
        }
        if (now - lastPing >= PING_MS) { lastPing = now; send("ping"); }
        if (now - lastWatchCheck >= WATCH_MS) {
            lastWatchCheck = now;
            String watch = watchList(c);
            if (!watch.equals(lastWatch)) {
                lastWatch = watch;
                JsonObject o = new JsonObject();
                o.addProperty("t", "watch");
                o.addProperty("u", watch);
                send(o.toString());
            }
        }
    }

    /** Nearby players who run Tempest, nearest first, as one string of 32-hex ids. */
    private static String watchList(MinecraftClient c) {
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : c.world.getPlayers()) if (p != c.player && TempestNet.isTempestUser(p.getUuid())) list.add(p);
        list.sort(Comparator.comparingDouble(p -> p.squaredDistanceTo(c.player)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() && i < 48; i++) sb.append(list.get(i).getUuid().toString().replace("-", ""));
        return sb.toString();
    }

    /** Your emote started (id) or stopped (null). */
    public static void sendEmote(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "emote");
        if (id == null) o.add("id", com.google.gson.JsonNull.INSTANCE); else o.addProperty("id", id);
        send(o.toString());
    }

    private static synchronized void send(String text) {
        WebSocket w = ws;
        if (w == null) return;
        // a WebSocket may only have one send in flight: chain them
        sending = sending.handle((x, e) -> null).thenCompose(x -> w.sendText(text, true)).exceptionally(e -> null);
    }

    private static void connect(String base, String token) {
        connecting = true;
        try {
            URI uri = URI.create(base.replaceFirst("^http", "ws") + "/v1/live");
            HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8)).header("Authorization", "Bearer " + token)
                    .buildAsync(uri, new Listener()).whenComplete((w, err) -> {
                        connecting = false;
                        if (err != null || w == null) { failed(); return; }
                        ws = w;
                        backoff = 5_000;
                        lastWatch = "";
                        lastWatchCheck = 0;
                        lastPing = System.currentTimeMillis();
                    });
        } catch (Exception e) {
            connecting = false;
            failed();
        }
    }

    private static void failed() {
        ws = null;
        retryAt = System.currentTimeMillis() + backoff;
        backoff = Math.min(backoff * 2, 300_000);
    }

    public static void close() {
        WebSocket w = ws;
        ws = null;
        lastWatch = "";
        if (w != null) {
            try { w.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); } catch (Exception ignored) { }
            Emotes.clearRemote();
        }
    }

    private static void handle(String text) {
        if (text.equals("pong")) return;
        try {
            JsonObject m = JsonParser.parseString(text).getAsJsonObject();
            String t = m.has("t") ? m.get("t").getAsString() : "";
            UUID id = m.has("uuid") ? UUID.fromString(m.get("uuid").getAsString()) : null;
            if (id == null) return;
            switch (t) {
                case "emote" -> Emotes.onRemote(id, m.has("id") && !m.get("id").isJsonNull() ? m.get("id").getAsString() : null);
                case "cos" -> RemoteCosmetics.put(id, m.has("c") && m.get("c").isJsonObject() ? m.getAsJsonObject("c") : null);
                default -> { }
            }
        } catch (Exception ignored) {
            // malformed message: ignore
        }
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) { webSocket.request(1); }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (buf.length() < 16_384) buf.append(data);
            if (last) {
                String s = buf.toString();
                buf.setLength(0);
                handle(s);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (ws == webSocket) failed();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (ws == webSocket || ws == null) failed();
        }
    }
}
