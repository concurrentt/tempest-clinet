package dev.tempest.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Discord Rich Presence, talking to the Discord desktop app over its local IPC channel (a named pipe on Windows,
 * a Unix socket on macOS / Linux) - no library needed. The protocol is small: frames of
 * [opcode int32 LE][length int32 LE][JSON]; a handshake with the application id, then SET_ACTIVITY commands.
 *
 * <p>Runs on its own thread: {@link #set(JsonObject)} just records the wanted activity, the thread connects (retrying
 * every 15 s while Discord isn't running) and sends it. Nothing here talks to the internet - only to Discord on this
 * computer.</p>
 */
public final class DiscordRpc {
    /** The Discord application ("Tempest Client") whose name shows after "Playing". Public, not a secret. */
    public static final String APP_ID = "1574565490360062022";

    private static final Object LOCK = new Object();
    private static JsonObject wanted;          // null = clear
    private static boolean dirty, running;
    private static Conn conn;
    private static volatile String status = "Off";

    /** Records the connection state and writes changes to the game log ([Tempest] Discord: ...). */
    private static void status(String s) {
        if (s.equals(status)) return;
        status = s;
        System.out.println("[Tempest] Discord: " + s);
    }

    private DiscordRpc() { }

    public static boolean configured() { return !APP_ID.isBlank(); }

    public static String status() { return status; }

    /** The activity to show (null clears it). Sent within a second or two; repeated equal calls are free. */
    public static void set(JsonObject activity) {
        if (!configured()) { status("No Discord app ID set"); return; }
        synchronized (LOCK) {
            if (activity == null ? wanted == null : activity.equals(wanted)) return;
            wanted = activity == null ? null : activity.deepCopy();
            dirty = true;
            if (!running) {
                running = true;
                Thread t = new Thread(DiscordRpc::loop, "Tempest Discord");
                t.setDaemon(true);
                t.start();
            }
            LOCK.notifyAll();
        }
    }

    private static void loop() {
        long nextTry = 0;
        while (true) {
            JsonObject send;
            synchronized (LOCK) {
                while (!dirty && conn != null) {
                    try { LOCK.wait(30_000); } catch (InterruptedException e) { return; }
                    if (!dirty && conn != null) break;      // periodic wake: check the connection is still alive
                }
                send = wanted;
            }
            try {
                if (conn == null) {
                    if (System.currentTimeMillis() < nextTry) { Thread.sleep(1000); continue; }
                    conn = connect();
                    if (conn == null) { status("Discord isn't running"); nextTry = System.currentTimeMillis() + 15_000; continue; }
                    status("Connected");
                }
                synchronized (LOCK) { dirty = false; }
                JsonObject args = new JsonObject();
                args.addProperty("pid", ProcessHandle.current().pid());
                if (send != null) args.add("activity", send);
                JsonObject cmd = new JsonObject();
                cmd.addProperty("cmd", "SET_ACTIVITY");
                cmd.add("args", args);
                cmd.addProperty("nonce", UUID.randomUUID().toString());
                conn.write(1, cmd.toString());
                JsonObject reply = conn.read();
                if (reply != null && "ERROR".equals(reply.has("evt") && !reply.get("evt").isJsonNull() ? reply.get("evt").getAsString() : "")) {
                    status("Discord refused the status: " + (reply.has("data") ? reply.get("data").toString() : reply.toString()));
                } else {
                    status(send == null ? "Connected (status cleared)" : "Connected - showing \"" + (send.has("details") ? send.get("details").getAsString() : "") + "\"");
                }
                if (send == null && wantedIsNull()) Thread.sleep(200);
            } catch (Exception e) {
                // Discord closed or restarted: reconnect and resend
                close();
                synchronized (LOCK) { dirty = true; }
                status("Lost the connection to Discord (" + e.getClass().getSimpleName() + "), retrying");
                nextTry = System.currentTimeMillis() + 15_000;
            }
        }
    }

    private static boolean wantedIsNull() { synchronized (LOCK) { return wanted == null; } }

    private static void close() {
        if (conn != null) try { conn.close(); } catch (Exception ignored) { }
        conn = null;
    }

    // ------------------------------------------------------------------ connection
    private interface Conn {
        void write(int op, String json) throws Exception;
        JsonObject read() throws Exception;
        void close() throws Exception;
    }

    private static Conn connect() {
        for (int i = 0; i < 10; i++) {
            Conn c = null;
            try {
                c = open(i);
                if (c == null) continue;
                JsonObject hs = new JsonObject();
                hs.addProperty("v", 1);
                hs.addProperty("client_id", APP_ID);
                c.write(0, hs.toString());
                JsonObject ready = c.read();                 // DISPATCH READY, or an error / close frame
                if (ready != null && ready.has("evt") && !ready.get("evt").isJsonNull() && "READY".equals(ready.get("evt").getAsString())) return c;
                status("Discord refused the handshake: " + ready);
                c.close();
            } catch (Exception e) {
                status("Couldn't talk to Discord on pipe " + i + ": " + e);
                if (c != null) try { c.close(); } catch (Exception ignored) { }
            }
        }
        return null;
    }

    private static Conn open(int i) throws Exception {
        if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            RandomAccessFile pipe;
            try { pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"); } catch (Exception e) { return null; }
            return new Conn() {
                public void write(int op, String json) throws Exception { pipe.write(frame(op, json)); }
                public JsonObject read() throws Exception {
                    byte[] head = new byte[8];
                    pipe.readFully(head);
                    int len = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
                    byte[] body = new byte[Math.max(0, len)];
                    pipe.readFully(body);
                    return parse(body);
                }
                public void close() throws Exception { pipe.close(); }
            };
        }
        // macOS / Linux: a Unix socket in the temp / runtime folder (also where Flatpak and Snap Discord put it)
        String[] bases = {System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), System.getenv("TMP"), System.getenv("TEMP"), "/tmp"};
        String[] subs = {"", "app/com.discordapp.Discord", "snap.discord", ".flatpak/com.discordapp.Discord/xdg-run"};
        for (String b : bases) {
            if (b == null || b.isBlank()) continue;
            for (String sub : subs) {
                Path p = Path.of(b, sub, "discord-ipc-" + i);
                if (!Files.exists(p)) continue;
                SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                try { ch.connect(UnixDomainSocketAddress.of(p)); } catch (Exception e) { ch.close(); continue; }
                return new Conn() {
                    public void write(int op, String json) throws Exception {
                        ByteBuffer buf = ByteBuffer.wrap(frame(op, json));
                        while (buf.hasRemaining()) ch.write(buf);
                    }
                    public JsonObject read() throws Exception {
                        ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                        while (head.hasRemaining()) if (ch.read(head) < 0) throw new java.io.EOFException();
                        int len = head.getInt(4);
                        ByteBuffer body = ByteBuffer.allocate(Math.max(0, len));
                        while (body.hasRemaining()) if (ch.read(body) < 0) throw new java.io.EOFException();
                        return parse(body.array());
                    }
                    public void close() throws Exception { ch.close(); }
                };
            }
        }
        return null;
    }

    private static byte[] frame(int op, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(op).putInt(body.length).put(body);
        return b.array();
    }

    private static JsonObject parse(byte[] body) {
        try {
            var e = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
