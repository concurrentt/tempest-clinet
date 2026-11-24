package dev.tempest.util;

import com.google.gson.JsonObject;
import dev.tempest.gui.TempestToast;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * "Come play with me": invite a Tempest friend to the server you're on. The invite goes through the Tempest live link
 * (friends only); the friend gets a pop-up, a chat line with a [Join] button and a row at the top of their Social menu.
 * Nothing joins by itself - they have to click Join.
 */
public final class Invites {
    /** A received invite. */
    public record Invite(UUID from, String name, String server, long at) {
        public boolean fresh() { return System.currentTimeMillis() - at < EXPIRE_MS; }
    }

    private static final long EXPIRE_MS = 10 * 60_000;
    private static final List<Invite> RECEIVED = new ArrayList<>();
    private static Consumer<String> pendingReply;

    private Invites() { }

    /** Invites still worth showing, newest first (one per friend). */
    public static List<Invite> received() {
        synchronized (RECEIVED) {
            RECEIVED.removeIf(i -> !i.fresh());
            return new ArrayList<>(RECEIVED);
        }
    }

    public static void dismiss(UUID from) { synchronized (RECEIVED) { RECEIVED.removeIf(i -> i.from().equals(from)); } }

    /** The server you're on that a friend could join, or null (singleplayer, menus, LAN). */
    public static String myServer() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.isInSingleplayer() || c.getCurrentServerEntry() == null) return null;
        ServerInfo e = c.getCurrentServerEntry();
        if (e.isLocal() || e.address == null || e.address.isBlank()) return null;
        return e.address.trim();
    }

    /** Same server, however the address was typed ("Play.Example.net:25565" == "play.example.net"). */
    public static boolean sameServer(String a, String b) {
        return a != null && b != null && norm(a).equals(norm(b));
    }

    private static String norm(String a) {
        String h = a.trim().toLowerCase(java.util.Locale.ROOT);
        if (h.endsWith(":25565")) h = h.substring(0, h.length() - 6);
        return h.endsWith(".") ? h.substring(0, h.length() - 1) : h;
    }

    /** Sends an invite to your current server. done gets a message for the UI. */
    public static void send(UUID to, Consumer<String> done) {
        String server = myServer();
        if (server == null) { done.accept("Join a server first, then invite friends to it"); return; }
        if (!TempestLive.connected()) { done.accept("Not connected to Tempest yet - try again in a moment"); return; }
        JsonObject o = new JsonObject();
        o.addProperty("t", "invite");
        o.addProperty("to", to.toString());
        o.addProperty("server", server);
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.getSession() != null) o.addProperty("n", c.getSession().getUsername());
        pendingReply = done;
        TempestLive.sendRaw(o.toString());
        done.accept("Invited " + Social.nameOf(to) + " to " + server);
    }

    /** Joins the newest invite (the chat [Join] button runs "/tempest join"). */
    public static boolean joinLatest() {
        List<Invite> l = received();
        if (l.isEmpty()) return false;
        join(l.get(0));
        return true;
    }

    public static void join(Invite i) {
        MinecraftClient c = MinecraftClient.getInstance();
        dismiss(i.from());
        ServerJoin.join(c, new ServerInfo(i.server(), i.server(), ServerInfo.ServerType.OTHER), c.currentScreen);
    }

    // ------------------------------------------------------------------ from TempestLive (network thread)
    static void onLive(JsonObject m) {
        String t = m.has("t") ? m.get("t").getAsString() : "";
        MinecraftClient c = MinecraftClient.getInstance();
        if (t.equals("invitefail")) {
            String reason = m.has("reason") ? m.get("reason").getAsString() : "";
            String msg = switch (reason) {
                case "offline" -> "They're not online right now, so the invite didn't arrive";
                case "not friends" -> "You can only invite Tempest friends";
                default -> "That server address can't be sent";
            };
            Consumer<String> r = pendingReply;
            c.execute(() -> { if (r != null) r.accept(msg); Chat.info(msg); });
            return;
        }
        if (!t.equals("invite")) return;
        UUID from;
        try { from = UUID.fromString(m.get("from").getAsString()); } catch (Exception e) { return; }
        if (!Social.isFriend(from)) return;                                   // only from people you accepted
        String server = m.has("server") ? m.get("server").getAsString() : null;
        if (server == null || !server.matches("[a-z0-9.\\-]{1,253}(:\\d{1,5})?")) return;
        String n = m.has("n") && !m.get("n").isJsonNull() ? m.get("n").getAsString() : null;
        if (n != null) PlayerHeads.rememberName(from, n);
        Invite inv = new Invite(from, n != null ? n : Social.nameOf(from), server, System.currentTimeMillis());
        synchronized (RECEIVED) {
            RECEIVED.removeIf(i -> i.from().equals(from));
            RECEIVED.add(0, inv);
        }
        TempestToast.show(from, inv.name() + " invited you", "Play on " + server + " - open Social to join", 0x8E7CFF, 8000);
        c.execute(() -> {
            if (c.inGameHud == null) return;
            Text join = Text.literal("[Join]").styled(s -> s.withColor(0x55FF55).withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand("tempest join"))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Join " + server))));
            c.inGameHud.getChatHud().addMessage(Text.literal("[Tempest] ").formatted(Formatting.GOLD)
                    .append(Text.literal(inv.name() + " invited you to play on " + server + " ").formatted(Formatting.WHITE))
                    .append(join));
        });
    }
}
