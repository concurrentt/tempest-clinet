package dev.tempest.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Social menu's Messages tab: private messages with Tempest friends. Messages go over the live link (the same as
 * Chat Tabs' Private tab) and the Tempest service keeps them for one hour, so a friend who was offline still sees
 * them when they come back. Everything older than an hour is deleted, here and on the service.
 */
public final class Messages {
    public record Msg(UUID from, String text, long at) {
        public boolean mine() { UUID me = me(); return me != null && me.equals(from); }
    }

    private static final long TTL_MS = 60 * 60_000;
    /** friend -> messages, oldest first. */
    private static final Map<UUID, List<Msg>> CHATS = new LinkedHashMap<>();
    private static final Map<UUID, Long> READ = new java.util.HashMap<>();
    private static long lastRecent;

    private Messages() { }

    private static UUID me() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c.getSession() != null ? c.getSession().getUuidOrNull() : null;
    }

    private static void prune() {
        long cut = System.currentTimeMillis() - TTL_MS;
        CHATS.values().forEach(l -> l.removeIf(m -> m.at() < cut));
        CHATS.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /** Conversations, newest first. */
    public static synchronized List<UUID> conversations() {
        prune();
        List<UUID> out = new ArrayList<>(CHATS.keySet());
        out.sort(Comparator.comparingLong((UUID u) -> last(u) == null ? 0 : last(u).at()).reversed());
        return out;
    }

    public static synchronized List<Msg> chat(UUID friend) {
        prune();
        return new ArrayList<>(CHATS.getOrDefault(friend, List.of()));
    }

    public static synchronized Msg last(UUID friend) {
        List<Msg> l = CHATS.get(friend);
        return l == null || l.isEmpty() ? null : l.get(l.size() - 1);
    }

    public static synchronized boolean unread(UUID friend) {
        Msg m = last(friend);
        return m != null && !m.mine() && m.at() > READ.getOrDefault(friend, 0L);
    }

    public static synchronized int unreadCount() {
        int n = 0;
        for (UUID u : CHATS.keySet()) if (unread(u)) n++;
        return n;
    }

    public static synchronized void markRead(UUID friend) { READ.put(friend, System.currentTimeMillis()); }

    private static synchronized void add(UUID friend, Msg m) {
        List<Msg> l = CHATS.computeIfAbsent(friend, k -> new ArrayList<>());
        for (Msg x : l) if (Math.abs(x.at() - m.at()) < 15_000 && x.from().equals(m.from()) && x.text().equals(m.text())) return;   // already have it
        l.add(m);
        l.sort(Comparator.comparingLong(Msg::at));
        if (l.size() > 200) l.subList(0, l.size() - 200).clear();
    }

    // ------------------------------------------------------------------ sending / receiving
    /** Sends a message (also shows it in Chat Tabs' Private tab, like any Tempest DM). */
    public static String send(UUID friend, String text) {
        text = text == null ? "" : text.trim();
        if (text.isEmpty()) return null;
        if (!TempestLive.connected()) return "Not connected to Tempest yet - try again in a moment";
        if (text.length() > 256) text = text.substring(0, 256);
        TempestChat.send("pm:" + friend, text);
        UUID me = me();
        if (me != null) add(friend, new Msg(me, text, System.currentTimeMillis()));
        return null;
    }

    /** A private message arrived over the live link (TempestChat). */
    static void received(UUID from, String text, long at) {
        add(from, new Msg(from, text, at > 0 ? at : System.currentTimeMillis()));
    }

    /** Loads your conversations from the last hour (when the Messages tab opens; at most every 20 s). */
    public static void loadRecent() {
        long now = System.currentTimeMillis();
        if (now - lastRecent < 20_000 || !TempestNet.signedIn()) return;
        lastRecent = now;
        TempestNet.api("/v1/chat/dms", new JsonObject()).whenComplete((r, err) -> {
            if (r == null || !r.has("conversations")) return;
            for (JsonElement e : r.getAsJsonArray("conversations")) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    UUID friend = UUID.fromString(o.get("uuid").getAsString());
                    add(friend, new Msg(UUID.fromString(o.get("from").getAsString()), o.get("text").getAsString(), o.get("at").getAsLong()));
                } catch (Exception ignored) { }
            }
        });
    }

    /** Loads the last hour with one friend (when you open the chat). */
    public static void loadChat(UUID friend) {
        if (!TempestNet.signedIn()) return;
        JsonObject b = new JsonObject();
        b.addProperty("uuid", friend.toString());
        TempestNet.api("/v1/chat/dm", b).whenComplete((r, err) -> {
            if (r == null || !r.has("messages")) return;
            JsonArray a = r.getAsJsonArray("messages");
            for (JsonElement e : a) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    add(friend, new Msg(UUID.fromString(o.get("from").getAsString()), o.get("text").getAsString(), o.get("at").getAsLong()));
                } catch (Exception ignored) { }
            }
        });
    }

    /** Account switched: forget everything. */
    public static synchronized void reset() { CHATS.clear(); READ.clear(); lastRecent = 0; }
}
