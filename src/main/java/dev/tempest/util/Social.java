package dev.tempest.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tempest friends: the list, requests and who's online, kept in sync with the online service.
 * Everything is keyed by account UUID (proven at sign-in); names shown come from Mojang via {@link PlayerHeads}.
 */
public final class Social {
    public record Friend(UUID id, boolean online, String server) { }

    private static volatile List<Friend> friends = List.of();
    private static volatile List<UUID> incoming = List.of(), outgoing = List.of();
    private static final Set<UUID> FRIEND_IDS = ConcurrentHashMap.newKeySet();
    private static volatile long lastRefresh, screenOpenUntil;
    private static volatile boolean loaded, refreshing;
    private static volatile int incomingCount = -1;

    private Social() { }

    public static List<Friend> friends() { return friends; }

    /** Forget the friends list (the account was switched); it's loaded again for the new account. */
    public static void reset() {
        friends = List.of();
        incoming = List.of();
        outgoing = List.of();
        FRIEND_IDS.clear();
        lastRefresh = 0;
        loaded = false;
        forgetStates();
        incomingCount = -1;
    }
    public static List<UUID> incoming() { return incoming; }
    public static List<UUID> outgoing() { return outgoing; }
    public static boolean loaded() { return loaded; }
    public static boolean isFriend(UUID id) { return id != null && FRIEND_IDS.contains(id); }
    public static int pendingCount() { return Math.max(0, incomingCount); }

    /** The social screen is open (keeps the connection up even if sharing is off). */
    public static void markScreenOpen() { screenOpenUntil = System.currentTimeMillis() + 30_000; }
    public static boolean wanted() { return System.currentTimeMillis() < screenOpenUntil || !FRIEND_IDS.isEmpty(); }

    /** Heartbeat reply: number of pending requests to you. Announces new ones. */
    public static void onIncomingCount(int n) {
        int before = incomingCount;
        incomingCount = n;
        if (before < 0 && n > 0 && dev.tempest.modules.FriendsModule.requestAlerts()) {
            MinecraftClient c = MinecraftClient.getInstance();
            c.execute(() -> Chat.info("You have " + n + " friend request" + (n == 1 ? "" : "s") + ". Open Social ("
                    + dev.tempest.modules.FriendsModule.keyName() + ") to answer."));
        }
        if (before >= 0 && n > before) {
            refresh();
            MinecraftClient c = MinecraftClient.getInstance();
            if (dev.tempest.modules.FriendsModule.requestAlerts()) {
                c.execute(() -> Chat.info("New friend request! Open Social (" + dev.tempest.modules.FriendsModule.keyName() + ") to answer."));
            }
        }
        if (!loaded && TempestNet.signedIn()) refresh();
    }

    /** Re-fetches the list (at most every 4 seconds unless forced by an action). */
    public static void refreshIfStale() {
        if (System.currentTimeMillis() - lastRefresh > 4_000) refresh();
    }

    public static void refresh() {
        if (refreshing || !TempestNet.signedIn()) return;
        refreshing = true;
        lastRefresh = System.currentTimeMillis();
        TempestNet.api("/v1/friends/list", new JsonObject()).whenComplete((r, err) -> {
            refreshing = false;
            if (r == null || r.has("error")) return;
            List<Friend> fs = new ArrayList<>();
            JsonArray arr = r.getAsJsonArray("friends");
            if (arr != null) {
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    UUID id = uuid(o.get("uuid").getAsString());
                    if (id == null) continue;
                    String server = o.has("server") && !o.get("server").isJsonNull() ? o.get("server").getAsString() : null;
                    fs.add(new Friend(id, o.get("online").getAsBoolean(), server));
                }
            }
            // online first, then by name
            fs.sort((a, b) -> a.online() != b.online() ? (a.online() ? -1 : 1) : nameOf(a.id()).compareToIgnoreCase(nameOf(b.id())));
            alertChanges(fs);
            friends = Collections.unmodifiableList(fs);
            FRIEND_IDS.clear();
            for (Friend f : fs) FRIEND_IDS.add(f.id());
            incoming = ids(r.getAsJsonArray("incoming"));
            outgoing = ids(r.getAsJsonArray("outgoing"));
            incomingCount = incoming.size();
            loaded = true;
        });
    }

    /** Last known state of each friend, for "came online" / "joined your server" pop-ups (null until the first load). */
    private static java.util.Map<UUID, Friend> lastState;

    private static void alertChanges(List<Friend> now) {
        java.util.Map<UUID, Friend> prev = lastState;
        java.util.Map<UUID, Friend> next = new java.util.HashMap<>();
        for (Friend f : now) next.put(f.id(), f);
        lastState = next;
        if (prev == null) return;                                    // first load: nothing "changed"
        String mine = Invites.myServer();
        for (Friend f : now) {
            Friend p = prev.get(f.id());
            if (p == null) continue;                                 // just became friends
            String name = nameOf(f.id());
            if (!p.online() && f.online()) {
                if (dev.tempest.modules.FriendsModule.onlineAlerts())
                    dev.tempest.gui.TempestToast.show(f.id(), name + " is online", where(f), 0x43D17A, 5000);
            } else if (p.online() && !f.online()) {
                if (dev.tempest.modules.FriendsModule.offlineAlerts())
                    dev.tempest.gui.TempestToast.show(f.id(), name + " went offline", "See you later", 0x6B6F7E, 4000);
            } else if (f.online() && mine != null && Invites.sameServer(f.server(), mine) && !Invites.sameServer(p.server(), mine)) {
                if (dev.tempest.modules.FriendsModule.joinAlerts())
                    dev.tempest.gui.TempestToast.show(f.id(), name + " joined your server", mine, 0x5AB4FF, 5000);
            }
        }
    }

    private static String where(Friend f) {
        String s = f.server();
        if (s == null) return "In the menus";
        if (s.equals("In game") || s.equals("Singleplayer")) return s;
        return "Playing on " + s;
    }

    /** Checks friends every 20 s for the pop-ups (FriendsModule tick), only while you have friends. */
    public static void poll() {
        if (!loaded || FRIEND_IDS.isEmpty() || !TempestNet.signedIn()) return;
        if (System.currentTimeMillis() - lastRefresh > 20_000) refresh();
    }

    /** The account was switched: forget who was online. */
    private static void forgetStates() { lastState = null; }

    // ------------------------------------------------------------------ actions (callback gets a message for the UI)
    public static void request(UUID id, Consumer<String> done) { act("/v1/friends/request", id, done); }
    public static void accept(UUID id, Consumer<String> done) { act("/v1/friends/accept", id, done); }
    public static void decline(UUID id, Consumer<String> done) { act("/v1/friends/decline", id, done); }
    public static void remove(UUID id, Consumer<String> done) { act("/v1/friends/remove", id, done); }

    /** Name -> account -> friend request. */
    public static void requestByName(String name, Consumer<String> done) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerHeads.lookup(name).thenAccept(id -> {
            if (id == null) { mc.execute(() -> done.accept("No Minecraft account called \"" + name.trim() + "\"")); return; }
            if (mc.getSession() != null && id.equals(mc.getSession().getUuidOrNull())) { mc.execute(() -> done.accept("That's you!")); return; }
            request(id, done);
        });
    }

    private static void act(String path, UUID id, Consumer<String> done) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", id.toString());
        TempestNet.api(path, body).whenComplete((r, err) -> {
            String msg;
            if (r == null) msg = TempestNet.signedIn() ? "Couldn't reach the Tempest service" : "Not signed in to Tempest: " + TempestNet.status();
            else if (r.has("error")) msg = cap(r.get("error").getAsString());
            else if (r.has("result")) msg = switch (r.get("result").getAsString()) {
                case "accepted" -> "They had already asked you - you're now friends!";
                case "already" -> "You're already friends";
                default -> "Friend request sent to " + nameOf(id);
            };
            else msg = null;
            refresh();
            if (done != null) MinecraftClient.getInstance().execute(() -> done.accept(msg));
        });
    }

    /** Display name (Mojang), or a short placeholder while it loads. */
    public static String nameOf(UUID id) {
        String n = PlayerHeads.name(id);
        return n != null ? n : id.toString().substring(0, 8) + "…";
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static List<UUID> ids(JsonArray arr) {
        List<UUID> out = new ArrayList<>();
        if (arr != null) for (JsonElement e : arr) { UUID u = uuid(e.getAsString()); if (u != null) out.add(u); }
        return Collections.unmodifiableList(out);
    }

    private static UUID uuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
