package dev.tempest.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import dev.tempest.TempestConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-game account switcher.
 *
 * <p><b>Signing in</b> uses Microsoft's own sign-in page (the "device code" flow: you enter a short code at
 * microsoft.com/link), so Tempest never sees your password. The chain is the same one the launcher uses:
 * Microsoft → Xbox Live → XSTS → Minecraft services.</p>
 *
 * <p><b>Storage</b>: only the Microsoft refresh token of each account you add is saved, in
 * config/tempest-accounts.json, encrypted. On Windows that's Windows' own data protection (DPAPI, tied to your Windows
 * user); elsewhere AES-GCM with a random key kept in a separate file. Minecraft access tokens are kept in memory only.
 * <b>Nothing here is ever sent to the Tempest service</b> - only to Microsoft, Xbox and Mojang.</p>
 *
 * <p><b>Switching</b> swaps the game's session (and the services built from it: user API, chat keys, social
 * interactions, report context) without a restart, only while you're in the menus. Fields are found by their type, so
 * nothing depends on Minecraft's field names.</p>
 */
public final class Accounts {
    public record Saved(UUID id, String name, String token) { }

    private static final String MS = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Tempest Accounts");
        t.setDaemon(true);
        return t;
    });

    private static final List<Saved> SAVED = new ArrayList<>();
    /** uuid -> [Minecraft access token, expiry millis]; memory only. */
    private static final Map<UUID, Object[]> LIVE = new ConcurrentHashMap<>();
    private static boolean loaded;
    private static Session launcher;

    // state shown by the Accounts screen
    private static volatile String userCode, verifyUrl, status = "", error = "";
    private static volatile long codeExpires;
    private static volatile boolean busy, cancel;

    private Accounts() { }

    public static String userCode() { return userCode; }
    public static String verifyUrl() { return verifyUrl; }
    public static long codeExpires() { return codeExpires; }
    public static String status() { return status; }
    public static String error() { return error; }
    public static boolean busy() { return busy; }
    public static void clearMessages() { if (!busy) { status = ""; error = ""; } }

    public static String clientId() {
        String id = TempestConfig.get().accountsClientId;
        return id == null ? "" : id.trim();
    }

    public static void setClientId(String id) {
        TempestConfig.get().accountsClientId = id == null ? "" : id.trim();
        TempestConfig.save();
    }

    /** The account the launcher started the game with (kept in memory so you can switch back to it). */
    public static Session launcher() {
        init();
        return launcher;
    }

    public static synchronized List<Saved> saved() {
        init();
        return new ArrayList<>(SAVED);
    }

    public static UUID current() {
        Session s = MinecraftClient.getInstance().getSession();
        return s == null ? null : s.getUuidOrNull();
    }

    public static boolean canSwitch() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c.world == null && c.getNetworkHandler() == null && !busy;
    }

    // ------------------------------------------------------------------ adding an account
    /** Starts the Microsoft sign-in; the screen shows {@link #userCode()} while we wait. */
    public static void add() {
        if (busy) return;
        if (clientId().isEmpty()) { error = "Set up the Microsoft app ID first."; return; }
        busy = true;
        cancel = false;
        error = "";
        status = "Asking Microsoft for a code…";
        WORKER.execute(() -> {
            try {
                JsonObject dc = form(MS + "devicecode", "client_id", clientId(), "scope", SCOPE);
                if (dc.has("error")) throw new Fail(msError(dc));
                userCode = dc.get("user_code").getAsString();
                verifyUrl = dc.get("verification_uri").getAsString();
                codeExpires = System.currentTimeMillis() + dc.get("expires_in").getAsLong() * 1000;
                long interval = Math.max(1, dc.has("interval") ? dc.get("interval").getAsLong() : 5) * 1000;
                status = "Waiting for you to sign in…";
                JsonObject tok;
                while (true) {
                    Thread.sleep(interval);
                    if (cancel) { status = ""; return; }
                    if (System.currentTimeMillis() > codeExpires) throw new Fail("The code ran out - press Add account again.");
                    tok = form(MS + "token", "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                            "client_id", clientId(), "device_code", dc.get("device_code").getAsString());
                    String err = tok.has("error") ? tok.get("error").getAsString() : null;
                    if (err == null) break;
                    if (err.equals("authorization_pending")) continue;
                    if (err.equals("slow_down")) { interval += 5000; continue; }
                    if (err.equals("authorization_declined")) throw new Fail("Sign-in was cancelled on Microsoft's page.");
                    if (err.equals("expired_token")) throw new Fail("The code ran out - press Add account again.");
                    throw new Fail(msError(tok));
                }
                userCode = null;
                status = "Signing in to Minecraft…";
                Minecraft mc = minecraft(tok.get("access_token").getAsString());
                String refresh = tok.get("refresh_token").getAsString();
                synchronized (Accounts.class) {
                    SAVED.removeIf(a -> a.id().equals(mc.id));
                    SAVED.add(new Saved(mc.id, mc.name, protect(refresh)));
                    store();
                }
                LIVE.put(mc.id, new Object[]{mc.token, mc.expires});
                PlayerHeads.rememberName(mc.id, mc.name);
                status = "Added " + mc.name + ".";
            } catch (Fail f) {
                error = f.getMessage();
                status = "";
            } catch (InterruptedException e) {
                status = "";
            } catch (Exception e) {
                error = "Sign-in failed: " + e.getMessage();
                status = "";
                System.err.println("[Tempest] Account sign-in failed: " + e);
            } finally {
                userCode = null;
                busy = false;
            }
        });
    }

    public static void cancelAdd() { cancel = true; userCode = null; }

    public static synchronized void remove(UUID id) {
        init();
        SAVED.removeIf(a -> a.id().equals(id));
        LIVE.remove(id);
        store();
    }

    // ------------------------------------------------------------------ switching
    public static void switchToLauncher() {
        init();
        if (launcher == null || !canSwitch()) return;
        error = "";
        MinecraftClient c = MinecraftClient.getInstance();
        try {
            apply(c, launcher);
            status = "Switched to " + launcher.getUsername() + ".";
        } catch (Exception e) {
            error = "Couldn't switch: " + e.getMessage();
        }
    }

    public static void switchTo(Saved a) {
        if (!canSwitch()) { error = "Leave the world first."; return; }
        busy = true;
        error = "";
        status = "Signing in as " + a.name() + "…";
        WORKER.execute(() -> {
            try {
                String mcToken = null;
                String name = a.name();
                Object[] live = LIVE.get(a.id());
                if (live != null && System.currentTimeMillis() < (long) live[1] - 120_000) mcToken = (String) live[0];
                if (mcToken == null) {
                    String refresh = unprotect(a.token());
                    JsonObject tok = form(MS + "token", "grant_type", "refresh_token", "client_id", clientId(),
                            "refresh_token", refresh, "scope", SCOPE);
                    if (tok.has("error")) {
                        String e = tok.get("error").getAsString();
                        throw new Fail(e.equals("invalid_grant") ? "This login expired - remove the account and add it again." : msError(tok));
                    }
                    Minecraft mc = minecraft(tok.get("access_token").getAsString());
                    if (!mc.id.equals(a.id())) throw new Fail("Microsoft returned a different account.");
                    mcToken = mc.token;
                    name = mc.name;
                    LIVE.put(a.id(), new Object[]{mc.token, mc.expires});
                    if (tok.has("refresh_token")) {   // Microsoft rotates refresh tokens: keep the new one
                        String fresh = protect(tok.get("refresh_token").getAsString());
                        String nm = name;
                        synchronized (Accounts.class) {
                            for (int i = 0; i < SAVED.size(); i++) if (SAVED.get(i).id().equals(a.id())) SAVED.set(i, new Saved(a.id(), nm, fresh));
                            store();
                        }
                    }
                }
                String token = mcToken, nm = name;
                MinecraftClient c = MinecraftClient.getInstance();
                c.execute(() -> {
                    try {
                        if (c.world != null) { error = "Leave the world first."; status = ""; return; }
                        apply(c, newSession(nm, a.id(), token));
                        status = "Switched to " + nm + ".";
                    } catch (Exception e) {
                        error = "Couldn't switch: " + e.getMessage();
                        status = "";
                        System.err.println("[Tempest] Account switch failed: " + e);
                    } finally {
                        busy = false;
                    }
                });
                return;
            } catch (Fail f) {
                error = f.getMessage();
            } catch (Exception e) {
                error = "Couldn't sign in: " + e.getMessage();
                System.err.println("[Tempest] Account sign-in failed: " + e);
            }
            status = "";
            busy = false;
        });
    }

    // ------------------------------------------------------------------ Microsoft -> Minecraft
    private record Minecraft(UUID id, String name, String token, long expires) { }

    private static Minecraft minecraft(String msAccess) throws Exception {
        JsonObject xbl = json("https://user.auth.xboxlive.com/user/authenticate", obj(
                "Properties", obj("AuthMethod", "RPS", "SiteName", "user.auth.xboxlive.com", "RpsTicket", "d=" + msAccess),
                "RelyingParty", "http://auth.xboxlive.com", "TokenType", "JWT"), null);
        if (!xbl.has("Token")) throw new Fail("Xbox Live sign-in failed.");
        JsonArray users = new JsonArray();
        users.add(xbl.get("Token").getAsString());
        JsonObject xsts = json("https://xsts.auth.xboxlive.com/xsts/authorize", obj(
                "Properties", obj("SandboxId", "RETAIL", "UserTokens", users),
                "RelyingParty", "rp://api.minecraftservices.com/", "TokenType", "JWT"), null);
        if (xsts.has("XErr")) {
            long x = xsts.get("XErr").getAsLong();
            throw new Fail(x == 2148916233L ? "This Microsoft account has no Xbox profile - sign in at minecraft.net once first."
                    : x == 2148916235L ? "Xbox Live isn't available in this account's country."
                    : x == 2148916238L || x == 2148916236L || x == 2148916237L ? "This is a child account - an adult has to allow it in Microsoft Family."
                    : "Xbox refused the sign-in (" + x + ").");
        }
        if (!xsts.has("Token")) throw new Fail("Xbox sign-in failed.");
        String uhs = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        JsonObject login = json("https://api.minecraftservices.com/authentication/login_with_xbox",
                obj("identityToken", "XBL3.0 x=" + uhs + ";" + xsts.get("Token").getAsString()), null);
        if (!login.has("access_token")) {
            String msg = login.has("errorMessage") ? login.get("errorMessage").getAsString() : login.toString();
            if (msg.toLowerCase().contains("app registration") || login.has("_status") && login.get("_status").getAsInt() == 403)
                throw new Fail("Mojang hasn't approved this Microsoft app ID yet (see aka.ms/mce-reviewappid).");
            throw new Fail("Minecraft sign-in failed: " + msg);
        }
        String mcToken = login.get("access_token").getAsString();
        long expires = System.currentTimeMillis() + (login.has("expires_in") ? login.get("expires_in").getAsLong() : 86_400) * 1000;
        JsonObject prof = json("https://api.minecraftservices.com/minecraft/profile", null, mcToken);
        if (!prof.has("id")) throw new Fail("This account doesn't own Minecraft: Java Edition.");
        String raw = prof.get("id").getAsString();
        UUID id = UUID.fromString(raw.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        return new Minecraft(id, prof.get("name").getAsString(), mcToken, expires);
    }

    private static String msError(JsonObject o) {
        String e = o.has("error") ? o.get("error").getAsString() : "error";
        String d = o.has("error_description") ? o.get("error_description").getAsString() : "";
        if (d.contains("AADSTS700016") || e.equals("unauthorized_client"))
            return "Microsoft doesn't know this app ID, or \"Allow public client flows\" is off in Azure.";
        if (d.contains("AADSTS70002")) return "Turn on \"Allow public client flows\" for the app in Azure.";
        int nl = d.indexOf('\n');
        if (nl > 0) d = d.substring(0, nl);
        return "Microsoft: " + (d.isEmpty() ? e : d.replaceFirst("^AADSTS\\d+: ", ""));
    }

    // ------------------------------------------------------------------ session swap
    /** Builds a Session whatever its constructor looks like in this version (name, uuid, token, optionals, type). */
    private static Session newSession(String name, UUID id, String token) throws Exception {
        for (Constructor<?> k : Session.class.getConstructors()) {
            Class<?>[] ps = k.getParameterTypes();
            Object[] args = new Object[ps.length];
            int strings = 0;
            boolean ok = true;
            for (int i = 0; i < ps.length; i++) {
                Class<?> p = ps[i];
                if (p == String.class) args[i] = strings++ == 0 ? name : token;
                else if (p == UUID.class) args[i] = id;
                else if (p == Optional.class) args[i] = Optional.empty();
                else if (p.isEnum()) {
                    Object pick = p.getEnumConstants()[0];
                    for (Object e : p.getEnumConstants()) if (((Enum<?>) e).name().equalsIgnoreCase("MSA")) pick = e;
                    args[i] = pick;
                } else { ok = false; break; }
            }
            if (ok && strings == 2) return (Session) k.newInstance(args);
        }
        throw new IllegalStateException("unknown Session constructor");
    }

    /** Puts a session into the running game and rebuilds what depends on it. Render thread, menus only. */
    private static void apply(MinecraftClient c, Session s) throws Exception {
        if (c.world != null) throw new IllegalStateException("leave the world first");
        Class<?> mc = MinecraftClient.class;
        set(c, field(mc, Session.class), s);

        UserApiService api = UserApiService.OFFLINE;
        Field authF = fieldOrNull(mc, YggdrasilAuthenticationService.class);
        if (authF != null) {
            try { api = ((YggdrasilAuthenticationService) authF.get(c)).createUserApiService(s.getAccessToken()); }
            catch (Throwable e) { System.err.println("[Tempest] Account switch: user API unavailable: " + e); }
        }
        Field apiF = fieldOrNull(mc, UserApiService.class);
        if (apiF != null) set(c, apiF, api);

        Path run = c.runDirectory.toPath();
        for (Field f : mc.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            Class<?> t = f.getType();
            try {
                // chat keys: static X create(UserApiService, Session, Path)
                Method create = staticFactory(t, UserApiService.class, Session.class, Path.class);
                if (create != null) { set(c, f, create.invoke(null, api, s, run)); continue; }
                // social interactions (blocking/hiding players): new X(MinecraftClient, UserApiService)
                Constructor<?> k = ctor(t, MinecraftClient.class, UserApiService.class);
                if (k != null) { set(c, f, k.newInstance(c, api)); continue; }
                // abuse report context: static X create(Env, UserApiService), Env having a no-arg static factory
                for (Method m : t.getDeclaredMethods()) {
                    if (!Modifier.isStatic(m.getModifiers()) || m.getReturnType() != t || m.getParameterCount() != 2
                            || m.getParameterTypes()[1] != UserApiService.class) continue;
                    Class<?> env = m.getParameterTypes()[0];
                    Method mk = staticFactory(env);
                    if (mk == null) continue;
                    m.setAccessible(true);
                    mk.setAccessible(true);
                    set(c, f, m.invoke(null, mk.invoke(null), api));
                    break;
                }
            } catch (Throwable e) {
                System.err.println("[Tempest] Account switch: couldn't refresh " + t.getSimpleName() + ": " + e);
            }
        }
        // the game profile future (used for your skin in the menus)
        Field sessF = fieldOrNull(mc, MinecraftSessionService.class);
        if (sessF != null) {
            MinecraftSessionService ss = (MinecraftSessionService) sessF.get(c);
            for (Field f : mc.getDeclaredFields()) {
                if (f.getType() != CompletableFuture.class || Modifier.isStatic(f.getModifiers())) continue;
                if (!(f.getGenericType() instanceof ParameterizedType pt) || !pt.getActualTypeArguments()[0].getTypeName().endsWith("ProfileResult")) continue;
                UUID id = s.getUuidOrNull();
                CompletableFuture<?> old = (CompletableFuture<?>) f.get(c);
                // if the lookup fails, keep the previous result rather than leave the game with a failed future
                try { set(c, f, CompletableFuture.supplyAsync(() -> {
                    try {
                        Object r = ss.fetchProfile(id, true);
                        if (r != null) return r;
                    } catch (Throwable ignored) { }
                    return old.getNow(null);
                }, WORKER)); }
                catch (Throwable e) { System.err.println("[Tempest] Account switch: profile: " + e); }
            }
        }
        TempestNet.accountChanged();
        Social.reset();
        TempestChat.reset();
        System.out.println("[Tempest] Switched account to " + s.getUsername());
    }

    /**
     * Mojang's server-join handshake (session/minecraft/join) for the current session - what the game does when you join
     * any online-mode server. The access token goes to Mojang's session server only. Blocking; throws if Mojang refuses.
     */
    public static void joinServer(MinecraftClient c, Session s, String serverId) throws Exception {
        // 1.21.11 keeps the session service in ApiServices; older versions had a field on MinecraftClient
        MinecraftSessionService ss;
        try { ss = c.getApiServices().sessionService(); }
        catch (Throwable t) { ss = (MinecraftSessionService) field(MinecraftClient.class, MinecraftSessionService.class).get(c); }
        for (Method m : MinecraftSessionService.class.getMethods()) {
            if (!m.getName().equals("joinServer") || m.getParameterCount() != 3) continue;
            Class<?> first = m.getParameterTypes()[0];
            Object who = first == UUID.class ? s.getUuidOrNull()
                    : first.getConstructor(UUID.class, String.class).newInstance(s.getUuidOrNull(), s.getUsername());   // older authlib: GameProfile
            m.invoke(ss, who, s.getAccessToken(), serverId);
            return;
        }
        throw new IllegalStateException("no joinServer in this authlib");
    }

    private static Method staticFactory(Class<?> owner, Class<?>... params) {
        for (Method m : owner.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == owner && java.util.Arrays.equals(m.getParameterTypes(), params)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Constructor<?> ctor(Class<?> owner, Class<?>... params) {
        try {
            Constructor<?> k = owner.getDeclaredConstructor(params);
            k.setAccessible(true);
            return k;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Field field(Class<?> owner, Class<?> type) {
        Field f = fieldOrNull(owner, type);
        if (f == null) throw new IllegalStateException("no " + type.getSimpleName() + " field");
        return f;
    }

    private static Field fieldOrNull(Class<?> owner, Class<?> type) {
        for (Field f : owner.getDeclaredFields()) {
            if (f.getType() == type && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    private static void set(Object o, Field f, Object v) throws IllegalAccessException {
        f.setAccessible(true);
        f.set(o, v);
    }

    // ------------------------------------------------------------------ storage
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("tempest-accounts.json"); }
    private static Path keyFile() { return FabricLoader.getInstance().getConfigDir().resolve("tempest-accounts.key"); }

    private static synchronized void init() {
        if (launcher == null) launcher = MinecraftClient.getInstance().getSession();
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(file())) return;
            JsonObject root = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("accounts")) {
                JsonObject o = e.getAsJsonObject();
                SAVED.add(new Saved(UUID.fromString(o.get("id").getAsString()), o.get("name").getAsString(), o.get("token").getAsString()));
            }
        } catch (Exception e) {
            System.err.println("[Tempest] Could not read saved accounts: " + e);
        }
    }

    private static void store() {
        JsonObject root = new JsonObject();
        root.addProperty("note", "Saved Tempest account logins. Encrypted for this computer only - never share this file.");
        JsonArray arr = new JsonArray();
        for (Saved a : SAVED) {
            JsonObject o = new JsonObject();
            o.addProperty("id", a.id().toString());
            o.addProperty("name", a.name());
            o.addProperty("token", a.token());
            arr.add(o);
        }
        root.add("accounts", arr);
        try {
            Files.writeString(file(), GSON.toJson(root));
        } catch (Exception e) {
            System.err.println("[Tempest] Could not save accounts: " + e);
        }
    }

    private static boolean windows() { return System.getProperty("os.name", "").toLowerCase().startsWith("windows"); }

    /** Encrypts a refresh token for this computer: "dpapi:..." on Windows, "aes:..." elsewhere. */
    private static String protect(String plain) throws Exception {
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder b64 = Base64.getEncoder();
        if (windows()) {
            try {
                Method m = Class.forName("com.sun.jna.platform.win32.Crypt32Util").getMethod("cryptProtectData", byte[].class);
                return "dpapi:" + b64.encodeToString((byte[]) m.invoke(null, (Object) data));
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // JNA missing (shouldn't happen - Minecraft ships it): fall back to AES below
            }
        }
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey(), "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = ci.doFinal(data);
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return "aes:" + b64.encodeToString(out);
    }

    private static String unprotect(String s) throws Exception {
        Base64.Decoder b64 = Base64.getDecoder();
        try {
            if (s.startsWith("dpapi:")) {
                Method m = Class.forName("com.sun.jna.platform.win32.Crypt32Util").getMethod("cryptUnprotectData", byte[].class);
                return new String((byte[]) m.invoke(null, (Object) b64.decode(s.substring(6))), StandardCharsets.UTF_8);
            }
            if (s.startsWith("aes:")) {
                byte[] all = b64.decode(s.substring(4));
                Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
                ci.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey(), "AES"), new GCMParameterSpec(128, all, 0, 12));
                return new String(ci.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new Fail("This saved login can't be read on this computer - remove it and add it again.");
        }
        throw new Fail("Unknown saved login format - remove it and add it again.");
    }

    private static byte[] aesKey() throws Exception {
        Path k = keyFile();
        if (Files.exists(k)) {
            byte[] key = Base64.getDecoder().decode(Files.readString(k).trim());
            if (key.length == 32) return key;
        }
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Files.writeString(k, Base64.getEncoder().encodeToString(key));
        try { Files.setPosixFilePermissions(k, PosixFilePermissions.fromString("rw-------")); } catch (Exception ignored) { }
        return key;
    }

    // ------------------------------------------------------------------ http
    private static JsonObject form(String url, String... kv) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
        }
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString())).build();
        return parse(HTTP.send(r, HttpResponse.BodyHandlers.ofString()));
    }

    private static JsonObject json(String url, JsonObject body, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        if (body != null) b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        else b.GET();
        return parse(HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString()));
    }

    private static JsonObject parse(HttpResponse<String> res) {
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(res.body());
            o = e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            o = new JsonObject();
        }
        o.addProperty("_status", res.statusCode());
        return o;
    }

    private static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof JsonElement je) o.add((String) kv[i], je);
            else o.addProperty((String) kv[i], (String) v);
        }
        return o;
    }

    private static final class Fail extends Exception {
        Fail(String msg) { super(msg); }
    }
}
