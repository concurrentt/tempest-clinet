package dev.tempest.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.util.DiscordRpc;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/**
 * Discord Status: shows "Playing Tempest Client" on your Discord profile, with where you are (a server, singleplayer
 * or the menus), how long you've been playing, the Tempest logo, your skin's face, and a "Get Tempest" button.
 * Talks only to the Discord app on this computer (util/DiscordRpc).
 */
public class DiscordModule extends Module {
    private static final String RELEASES = "https://github.com/concurrentt/tempest-clinet/releases/latest";
    private static final String LOGO = "https://raw.githubusercontent.com/concurrentt/tempest-clinet/main/src/main/resources/assets/tempest/icon.png";
    private static final long STARTED = System.currentTimeMillis() / 1000;

    private final BoolSetting showServer = add(new BoolSetting("show_server", "Show Server Address", true));
    private final BoolSetting showName = add(new BoolSetting("show_name", "Show Username", true));
    private final BoolSetting showButton = add(new BoolSetting("show_button", "Show \"Get Tempest\" Button", true));
    private long lastUpdate;

    public DiscordModule() {
        super("discord", "Discord Status", "Shows that you're playing Tempest (and where) on your Discord profile.", Category.CLIENT, true);
        keyHint = "Needs the Discord desktop app open on this computer.";
        add(new dev.tempest.module.ActionSetting("check", "Connection", "Check", () ->
                dev.tempest.util.Chat.info("Discord Status: " + (isEnabled() ? DiscordRpc.status() : "turned off"))));
    }

    @Override
    public void onTick(MinecraftClient c) {
        long now = System.currentTimeMillis();
        if (now - lastUpdate < 2000) return;
        lastUpdate = now;
        DiscordRpc.set(activity(c));
    }

    @Override
    public void onDisable() { DiscordRpc.set(null); }

    private JsonObject activity(MinecraftClient c) {
        String where;
        if (c.world == null) where = "In the menus";
        else if (c.isInSingleplayer()) where = "Singleplayer";
        else if (c.getCurrentServerEntry() != null && showServer.get()) where = "Playing on " + host(c.getCurrentServerEntry().address);
        else where = "Playing multiplayer";

        JsonObject a = new JsonObject();
        a.addProperty("type", 0);
        a.addProperty("details", clip(where));
        String name = c.getSession() != null ? c.getSession().getUsername() : null;
        if (showName.get() && name != null && name.length() >= 2) a.addProperty("state", clip("Playing as " + name));

        JsonObject ts = new JsonObject();
        ts.addProperty("start", STARTED);
        a.add("timestamps", ts);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", LOGO);
        assets.addProperty("large_text", clip("Tempest Client v" + version()));
        UUID id = c.getSession() != null ? c.getSession().getUuidOrNull() : null;
        if (showName.get() && id != null && name != null) {
            assets.addProperty("small_image", "https://mc-heads.net/avatar/" + id.toString().replace("-", "") + "/64");
            assets.addProperty("small_text", clip(name));
        }
        a.add("assets", assets);

        if (showButton.get()) {
            JsonArray buttons = new JsonArray();
            JsonObject b = new JsonObject();
            b.addProperty("label", "Get Tempest");
            b.addProperty("url", RELEASES);
            buttons.add(b);
            a.add("buttons", buttons);
        }
        return a;
    }

    /** "mc.hypixel.net:25565" -> "mc.hypixel.net" (lowercase, no port). */
    private static String host(String address) {
        if (address == null || address.isBlank()) return "a server";
        String h = address.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = h.lastIndexOf(':');
        if (colon > 0 && h.indexOf(':') == colon) h = h.substring(0, colon);
        return h;
    }

    /** Discord wants 2..128 characters. */
    private static String clip(String s) {
        if (s.length() > 128) s = s.substring(0, 127) + "…";
        return s.length() < 2 ? s + " " : s;
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer("tempest").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
    }
}
