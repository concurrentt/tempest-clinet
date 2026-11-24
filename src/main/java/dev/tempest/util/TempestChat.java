package dev.tempest.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Tempest chat: private messages with your friends, your party, and groups you make. Everything goes through the
 * Tempest service's live link (TempestLive), so it works on any server and the server never sees it.
 *
 * <p>Messages are shown in the normal chat box, tagged with their conversation, and Chat Tabs (ChatTabsModule) shows
 * the right ones in each tab. Conversation keys: "pm:&lt;uuid&gt;" for a friend, "g:&lt;group id&gt;" for a
 * party or group.</p>
 */
public final class TempestChat {
    public record Group(String id, String name, String kind, UUID owner, List<UUID> members) {
        public boolean isParty() { return kind.equals("party"); }
        public String key() { return "g:" + id; }
    }

    private static final Map<String, Group> GROUPS = Collections.synchronizedMap(new LinkedHashMap<>());
    /** Chat line -> conversation key, for the lines we add (weak: lines that scroll out of history are forgotten). */
    private static final Map<Text, String> TAGGED = Collections.synchronizedMap(new WeakHashMap<>());
    /** True while we add one of our own lines, so the chat line processing (stacking, mentions...) leaves it alone. */
    public static boolean adding;
    private static volatile long lastGroupFetch;

    private TempestChat() { }

    // ------------------------------------------------------------------ groups
    public static List<Group> groups() {
        synchronized (GROUPS) { return new ArrayList<>(GROUPS.values()); }
    }

    public static Group party() {
        for (Group g : groups()) if (g.isParty()) return g;
        return null;
    }

    public static Group group(String id) { return id == null ? null : GROUPS.get(id); }

    /** The account was switched: the groups belong to the old account. */
    public static void reset() {
        GROUPS.clear();
        lastGroupFetch = 0;
    }

    /** Loads the groups you're in (on connect, and when asked). */
    public static void refreshGroups() {
        if (!TempestNet.signedIn() || System.currentTimeMillis() - lastGroupFetch < 2_000) return;
        lastGroupFetch = System.currentTimeMillis();
        TempestNet.api("/v1/chat/groups", new JsonObject()).thenAccept(r -> {
            if (r == null || !r.has("groups")) return;
            Map<String, Group> fresh = new LinkedHashMap<>();
            for (JsonElement e : r.getAsJsonArray("groups")) {
                Group g = parseGroup(e.getAsJsonObject());
                if (g != null) fresh.put(g.id(), g);
            }
            synchronized (GROUPS) { GROUPS.clear(); GROUPS.putAll(fresh); }
        });
    }

    static Group parseGroup(JsonObject o) {
        try {
            List<UUID> members = new ArrayList<>();
            for (JsonElement m : o.getAsJsonArray("members")) members.add(UUID.fromString(m.getAsString()));
            return new Group(o.get("id").getAsString(), o.get("name").getAsString(), o.get("kind").getAsString(),
                    UUID.fromString(o.get("owner").getAsString()), List.copyOf(members));
        } catch (Exception e) {
            return null;
        }
    }

    public static void createGroup(String name, List<UUID> members, Consumer<String> done) { groupCall("/v1/chat/group/create", body(name, "group", members), done); }
    public static void inviteToParty(List<UUID> members, Consumer<String> done) { groupCall("/v1/chat/group/create", body(null, "party", members), done); }

    public static void addToGroup(String id, List<UUID> members, Consumer<String> done) {
        JsonObject b = new JsonObject();
        b.addProperty("id", id);
        b.add("add", ids(members));
        groupCall("/v1/chat/group/update", b, done);
    }

    public static void leave(String id, Consumer<String> done) {
        JsonObject b = new JsonObject();
        b.addProperty("id", id);
        TempestNet.api("/v1/chat/group/leave", b).thenAccept(r -> {
            if (r != null && !r.has("error")) GROUPS.remove(id);
            finish(r, done);
        });
    }

    private static JsonObject body(String name, String kind, List<UUID> members) {
        JsonObject b = new JsonObject();
        if (name != null) b.addProperty("name", name);
        b.addProperty("kind", kind);
        b.add("members", ids(members));
        return b;
    }

    private static JsonArray ids(List<UUID> members) {
        JsonArray a = new JsonArray();
        for (UUID u : members) a.add(u.toString());
        return a;
    }

    private static void groupCall(String path, JsonObject body, Consumer<String> done) {
        TempestNet.api(path, body).thenAccept(r -> {
            if (r != null && r.has("g")) {
                Group g = parseGroup(r.getAsJsonObject("g"));
                if (g != null) GROUPS.put(g.id(), g);
            }
            finish(r, done);
        });
    }

    private static void finish(JsonObject r, Consumer<String> done) {
        String msg = r == null ? (TempestNet.signedIn() ? "Couldn't reach the Tempest service" : "Not signed in to Tempest: " + TempestNet.status()) : r.has("error") ? r.get("error").getAsString() : null;
        if (done != null) MinecraftClient.getInstance().execute(() -> done.accept(msg));
    }

    // ------------------------------------------------------------------ sending
    /** Sends to a conversation ("pm:uuid" or "g:id") and shows it in your chat. */
    public static void send(String key, String text) {
        text = text == null ? "" : text.trim();
        if (text.isEmpty() || key == null) return;
        if (!TempestLive.connected()) {
            system(key, "Not connected to Tempest - your message wasn't sent.");
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("t", "msg");
        if (key.startsWith("pm:")) o.addProperty("to", key.substring(3));
        else o.addProperty("g", key.substring(2));
        o.addProperty("text", text.length() > 256 ? text.substring(0, 256) : text);
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.getSession() != null) o.addProperty("n", c.getSession().getUsername());
        TempestLive.sendRaw(o.toString());
        show(key, line(key, null, text));
    }

    // ------------------------------------------------------------------ receiving (from TempestLive, network thread)
    static void onLive(JsonObject m) {
        String t = m.has("t") ? m.get("t").getAsString() : "";
        MinecraftClient c = MinecraftClient.getInstance();
        switch (t) {
            case "msg" -> {
                UUID from;
                try { from = UUID.fromString(m.get("from").getAsString()); } catch (Exception e) { return; }
                String text = m.has("text") ? m.get("text").getAsString() : "";
                String n = m.has("n") && !m.get("n").isJsonNull() ? m.get("n").getAsString() : null;
                if (n != null) PlayerHeads.rememberName(from, n);
                String key;
                if (m.has("g")) {
                    key = "g:" + m.get("g").getAsString();
                    if (group(m.get("g").getAsString()) == null) refreshGroups();
                } else {
                    key = "pm:" + from;
                    Messages.received(from, text, m.has("at") ? m.get("at").getAsLong() : 0);
                }
                c.execute(() -> {
                    show(key, line(key, from, text));
                    dev.tempest.modules.ChatTabsModule.onTempestMessage(key, from);
                });
            }
            case "msgqueued" -> {
                String key = "pm:" + (m.has("to") ? m.get("to").getAsString() : "");
                c.execute(() -> system(key, "They're offline - they'll see it in Social > Messages for the next hour."));
            }
            case "msgfail" -> {
                String key = m.has("g") ? "g:" + m.get("g").getAsString() : "pm:" + (m.has("to") ? m.get("to").getAsString() : "");
                String reason = m.has("reason") ? m.get("reason").getAsString() : "";
                String why = switch (reason) {
                    case "offline" -> key.startsWith("pm:") ? "They're offline - message not delivered." : "Nobody else in there is online right now.";
                    case "not friends" -> "You can only message friends.";
                    default -> "That group doesn't exist any more.";
                };
                c.execute(() -> system(key, why));
            }
            case "group" -> {
                Group g = m.has("g") ? parseGroup(m.getAsJsonObject("g")) : null;
                if (g == null) return;
                boolean isNew = !GROUPS.containsKey(g.id());
                GROUPS.put(g.id(), g);
                if (isNew && !g.owner().equals(me())) {
                    c.execute(() -> system(g.key(), Social.nameOf(g.owner()) + " added you to " + (g.isParty() ? "their party" : "\"" + g.name() + "\"")
                            + ". Open chat and click its tab to talk."));
                }
            }
            case "group_gone" -> {
                String id = m.has("id") ? m.get("id").getAsString() : null;
                Group g = id == null ? null : GROUPS.remove(id);
                if (g != null) c.execute(() -> system(g.key(), g.isParty() ? "The party ended." : "You're no longer in \"" + g.name() + "\"."));
            }
            default -> { }
        }
    }

    private static UUID me() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c.getSession() == null ? null : c.getSession().getUuidOrNull();
    }

    // ------------------------------------------------------------------ chat lines
    private static final int PM = 0xD58CFF, PARTY = 0x5AB4FF, GROUP = 0x6AF09A, NAME = 0xFFFFFF, TEXT = 0xE6E8F0, DIM = 0x9DA2B3;

    /** Prefix for a conversation: [PM], [Party] or [group name]. */
    private static MutableText prefix(String key) {
        if (key.startsWith("pm:")) return Text.literal("[PM] ").styled(s -> s.withColor(PM).withBold(true));
        Group g = group(key.substring(2));
        if (g != null && g.isParty()) return Text.literal("[Party] ").styled(s -> s.withColor(PARTY).withBold(true));
        String n = g == null ? "Group" : g.name();
        return Text.literal("[" + n + "] ").styled(s -> s.withColor(GROUP).withBold(true));
    }

    /** from == null: your own message. */
    private static Text line(String key, UUID from, String text) {
        MutableText out = Text.empty().append(prefix(key));
        if (from == null) {
            if (key.startsWith("pm:")) {
                UUID to = uuid(key.substring(3));
                out.append(Text.literal("You → " + (to == null ? "?" : Social.nameOf(to))).styled(s -> s.withColor(DIM)));
            } else {
                out.append(Text.literal("You").styled(s -> s.withColor(DIM)));
            }
        } else {
            out.append(Text.literal(Social.nameOf(from)).styled(s -> s.withColor(NAME)));
        }
        out.append(Text.literal(": ").styled(s -> s.withColor(DIM)));
        out.append(Text.literal(text).styled(s -> s.withColor(TEXT)));
        return out;
    }

    private static void system(String key, String msg) {
        show(key, Text.empty().append(prefix(key)).append(Text.literal(msg).styled(s -> s.withColor(DIM).withItalic(true))));
    }

    private static void show(String key, Text line) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.inGameHud == null) return;
        TAGGED.put(line, key);
        adding = true;
        try {
            c.inGameHud.getChatHud().addMessage(line);
        } finally {
            adding = false;
        }
    }

    /** Conversation a chat line belongs to, or null for normal server chat. */
    public static String keyOf(Text line) { return line == null ? null : TAGGED.get(line); }

    static UUID uuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
