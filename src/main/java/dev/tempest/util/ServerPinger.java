package dev.tempest.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal Minecraft Server List Ping (works without touching Minecraft's own pinger). */
public final class ServerPinger {
    public static final class Result {
        public volatile int pingMs = -1;
        public volatile int online = -1, max = -1;
        public volatile boolean done, failed;
        public volatile long time;
        /** The server's 64x64 PNG icon from its status reply, if it sent one. */
        public volatile byte[] favicon;
        /** When the server sends no icon: its website's icon (e.g. crystalbox.org's logo), if it has one. */
        public volatile byte[] webIcon;

        /** The icon to show: the server's own, else its website's, else null. */
        public byte[] icon() { return favicon != null ? favicon : webIcon; }
    }

    /** Website icons by domain (an empty array = looked, none found), fetched once per game. */
    private static final Map<String, byte[]> WEB_ICONS = new ConcurrentHashMap<>();
    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5)).followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();

    /**
     * The icon of the website behind a server address ("play.example.net:25565" -> play.example.net, example.net,
     * www.example.net, store.example.net - the first that has one), via Google's favicon service. Blocking; called on the ping threads.
     */
    private static byte[] webIcon(String address) {
        String host = address.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        if (host.isEmpty() || host.matches("[0-9.]+") || host.contains(":") || !host.contains(".")) return null;   // IPs have no website
        String[] parts = host.split("\\.");
        String domain = parts.length >= 2 ? parts[parts.length - 2] + "." + parts[parts.length - 1] : host;
        byte[] known = WEB_ICONS.get(domain);
        if (known != null) return known.length == 0 ? null : known;
        byte[] found = new byte[0];
        // The address's own host, the main domain, then the sites servers usually have (store.x is the Tebex
        // store most servers link to - often the only site a server has).
        java.util.LinkedHashSet<String> sites = new java.util.LinkedHashSet<>();
        sites.add(host); sites.add(domain); sites.add("www." + domain); sites.add("store." + domain);
        for (String site : sites) {
            try {
                var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        "https://www.google.com/s2/favicons?sz=64&domain_url=https://" + site)).timeout(java.time.Duration.ofSeconds(6)).GET().build();
                var res = HTTP.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                // Google answers 404 (with its generic globe) when it knows no icon for the site
                if (res.statusCode() == 200 && res.body().length > 0) {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(res.body()));
                    if (img != null && img.getWidth() >= 16 && img.getHeight() >= 16) { found = res.body(); break; }
                }
            } catch (Exception ignored) { }
        }
        WEB_ICONS.put(domain, found);
        return found.length == 0 ? null : found;
    }

    private static final Map<String, Result> RESULTS = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Tempest-Ping");
        t.setDaemon(true);
        return t;
    });

    private ServerPinger() { }

    public static Result get(String address) { return RESULTS.get(address); }

    /** Forgets the last result and pings again right away. */
    public static void refresh(String address) {
        Result old = RESULTS.get(address);
        if (old != null && old.done) old.time = 0;   // expired: the next ping runs now but keeps the icon meanwhile
        else if (old == null) RESULTS.remove(address);
        ping(address);
    }

    /** Starts a ping unless one ran in the last 20 seconds. */
    public static void ping(String address) { ping(address, 20_000); }

    /** Starts a ping unless one finished less than {@code maxAgeMs} ago (or one is still running). */
    public static void ping(String address, long maxAgeMs) {
        Result old = RESULTS.get(address);
        if (old != null && (!old.done || System.currentTimeMillis() - old.time < maxAgeMs)) return;
        Result r = new Result();
        r.time = System.currentTimeMillis();
        if (old != null) {                           // keep showing the last icon and ping while the new ping runs
            r.favicon = old.favicon;
            r.webIcon = old.webIcon;
            if (old.done && !old.failed) { r.pingMs = old.pingMs; r.online = old.online; r.max = old.max; }
        }
        RESULTS.put(address, r);
        POOL.execute(() -> {
            try { run(address, r); } catch (Exception e) { r.failed = true; }
            if (r.favicon == null && r.webIcon == null) r.webIcon = webIcon(address);
            r.done = true;
            r.time = System.currentTimeMillis();
        });
    }

    private static void run(String address, Result r) throws Exception {
        String host = address.trim();
        int port = 25565;
        boolean explicitPort = false;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            port = Integer.parseInt(host.substring(colon + 1));
            host = host.substring(0, colon);
            explicitPort = true;
        }
        String connectHost = host;
        if (!explicitPort) {
            String[] srv = srv(host);
            if (srv != null) { connectHost = srv[0]; port = Integer.parseInt(srv[1]); }
        }

        try (Socket s = new Socket()) {
            // Without this, Nagle's algorithm holds the little ping packet back until the server ACKs the status
            // request, which added 40-200 ms to every reading (vanilla's own pinger turns it off too).
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(connectHost, port), 3000);
            s.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(s.getInputStream());

            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hs);
            writeVarInt(h, 0);
            writeVarInt(h, protocol());
            byte[] hb = host.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            writeVarInt(h, hb.length);
            h.write(hb);
            h.writeShort(port);
            writeVarInt(h, 1);
            writeVarInt(out, hs.size());
            out.write(hs.toByteArray());
            writeVarInt(out, 1);
            out.writeByte(0);
            out.flush();

            readVarInt(in);
            if (readVarInt(in) != 0) throw new IOException("bad status packet");
            int len = readVarInt(in);
            byte[] json = new byte[len];
            in.readFully(json);
            try {
                JsonObject o = JsonParser.parseString(new String(json, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                // icon first, on its own, so an odd "players" section can't lose it
                try {
                    if (o.has("favicon") && o.get("favicon").isJsonPrimitive()) {
                        String fav = o.get("favicon").getAsString();
                        int comma = fav.indexOf(',');
                        if (fav.regionMatches(true, 0, "data:image/", 0, 11) && comma > 0) {
                            r.favicon = java.util.Base64.getMimeDecoder().decode(fav.substring(comma + 1).trim());
                        }
                    }
                } catch (Exception ignored) { }
                JsonObject pl = o.getAsJsonObject("players");
                if (pl != null) {
                    if (pl.has("online")) r.online = pl.get("online").getAsInt();
                    if (pl.has("max")) r.max = pl.get("max").getAsInt();
                }
            } catch (Exception ignored) { }

            long sent = System.nanoTime();
            writeVarInt(out, 9);
            out.writeByte(1);
            out.writeLong(sent);
            out.flush();
            readVarInt(in);
            readVarInt(in);
            in.readLong();
            r.pingMs = (int) Math.max(1, (System.nanoTime() - sent) / 1_000_000L);
        }
    }

    /** The game's own protocol number (some proxies refuse the "-1 = any" handshake and send no icon). */
    private static int protocol() {
        try {
            return net.minecraft.SharedConstants.getProtocolVersion();
        } catch (Throwable t) {
            return 774;   // 1.21.11
        }
    }

    private static String[] srv(String host) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            env.put("com.sun.jndi.dns.timeout.initial", "1500");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            Attribute a = new InitialDirContext(env).getAttributes("_minecraft._tcp." + host, new String[]{"SRV"}).get("srv");
            if (a != null && a.size() > 0) {
                String[] p = a.get(0).toString().split(" ");
                String target = p[3].endsWith(".") ? p[3].substring(0, p[3].length() - 1) : p[3];
                return new String[]{target, p[2]};
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static void writeVarInt(DataOutputStream o, int v) throws IOException {
        while ((v & ~0x7F) != 0) { o.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
        o.writeByte(v);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int v = 0, pos = 0;
        while (true) {
            int b = in.readUnsignedByte();
            v |= (b & 0x7F) << pos;
            if ((b & 0x80) == 0) return v;
            pos += 7;
            if (pos >= 35) throw new IOException("varint too big");
        }
    }
}
