package dev.tempest.modules;

import dev.tempest.TempestConfig;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.util.Chat;
import dev.tempest.util.ConfigManager;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * Per-Server Setups: remember a saved config (Configs screen) for a server and load it automatically every time you
 * join that server - e.g. your crystal PvP setup on one server and your SMP setup on another.
 */
public class ServerSetupsModule extends Module {
    public ServerSetupsModule() {
        super("server_setups", "Per-Server Setups", "Loads the config you picked for a server whenever you join it.", Category.CLIENT, true);
        keyHint = "Save a config in Configs first, load it, then join the server and press \"Use for this server\".";
        add(new ActionSetting("link", "Current config on this server", "Use for this server", ServerSetupsModule::link));
        add(new ActionSetting("unlink", "This server's setup", "Forget", ServerSetupsModule::unlink));
        add(new ActionSetting("list", "All servers", "Show", ServerSetupsModule::list));
    }

    /** "Play.Example.net:25565" -> "play.example.net". */
    static String host(String address) {
        if (address == null) return null;
        String h = address.trim().toLowerCase(Locale.ROOT);
        if (h.endsWith(":25565")) h = h.substring(0, h.length() - 6);
        return h.isEmpty() ? null : h;
    }

    private static String currentHost() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.isInSingleplayer() || c.getCurrentServerEntry() == null) return null;
        return host(c.getCurrentServerEntry().address);
    }

    private static void link() {
        String h = currentHost();
        String cfg = ConfigManager.active();
        if (h == null) { Chat.info("Join a server first."); return; }
        if (cfg == null || cfg.isEmpty() || !ConfigManager.exists(cfg)) { Chat.info("Save or load a config in Configs first - that's the setup this server will use."); return; }
        TempestConfig.get().serverConfigs.put(h, cfg);
        TempestConfig.save();
        Chat.info("\"" + cfg + "\" will load every time you join " + h + ".");
    }

    private static void unlink() {
        String h = currentHost();
        if (h == null) { Chat.info("Join a server first."); return; }
        if (TempestConfig.get().serverConfigs.remove(h) != null) { TempestConfig.save(); Chat.info(h + " no longer loads a setup."); }
        else Chat.info(h + " has no setup.");
    }

    private static void list() {
        var m = TempestConfig.get().serverConfigs;
        if (m.isEmpty()) { Chat.info("No servers have a setup yet."); return; }
        m.forEach((h, cfg) -> Chat.info(h + "  ->  \"" + cfg + "\""));
    }

    /** Joined a server: load its setup (if it has one and it isn't loaded already). */
    public static void onJoin(MinecraftClient c) {
        if (!ModuleManager.enabled(ServerSetupsModule.class) || c.getCurrentServerEntry() == null) return;
        String h = host(c.getCurrentServerEntry().address);
        String cfg = h == null ? null : TempestConfig.get().serverConfigs.get(h);
        if (cfg == null || cfg.equals(ConfigManager.active()) || !ConfigManager.exists(cfg)) return;
        c.execute(() -> Chat.info(ConfigManager.load(cfg) + " for " + h + " (Per-Server Setups)"));
    }
}
