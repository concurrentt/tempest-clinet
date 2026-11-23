package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import dev.tempest.util.CombatTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Says gg for you. Triggers (each can be switched off):
 * <ul>
 *   <li>Kills: vanilla death messages that name you as the killer ("X was slain by You"), the common plugin
 *       kill-feed phrasings ("X was killed by You", "You eliminated X"), and a player you just hit dying.</li>
 *   <li>Game end: "Winner", "1st Killer", "You won the game", "Victory!" and similar end-of-round lines.</li>
 *   <li>Your own death.</li>
 * </ul>
 * At most one message every 10 seconds, so a kill + game end never sends two.
 */
public class AutoGgModule extends Module {
    private final ModeSetting message = add(new ModeSetting("message", "Message", "gg", "gg", "GG", "good game", "good fight", "gf", "ggs"));
    private final NumberSetting delay = add(new NumberSetting("delay", "Delay (seconds)", 0.5, 0.0, 5.0, 0.5));
    private final BoolSetting onKill = add(new BoolSetting("on_kill", "After A Kill", true));
    private final BoolSetting onGameEnd = add(new BoolSetting("on_game_end", "When A Game Ends", true));
    private final BoolSetting onDeath = add(new BoolSetting("on_death", "When You Die", true));
    private long sendAt = 0, lastSent = 0;

    private static final Pattern GAME_END = Pattern.compile(
            "(?i)^\\s*(1st killer|1st place|winner\\s*[:!-]|winners?\\s*:|game over|match over)"
            + "|\\b(won the (game|match|duel|round|fight))\\b|^\\s*(#1\\s*)?victory\\s*!*\\s*$|^\\s*you (won|win)\\b");
    private static final String KILL_VERBS = "(killed|slain|eliminated|destroyed|murdered|shot|blown up|knocked off|knocked into the void|"
            + "pummeled|finished( off)?|defeated|obliterated|annihilated|wrecked|thrown off|pushed|struck down|sniped)";

    public AutoGgModule() {
        super("auto_gg", "Auto GG", "Sends gg after a kill, when a game ends or when you die.", Category.CHAT, false);
    }

    private static AutoGgModule on() {
        AutoGgModule m = ModuleManager.get(AutoGgModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** An entity death status (3) arrived. Kill detection runs even with Auto GG off (Kill Effects uses it). */
    public static void onDeath(Entity e) {
        if (!(e instanceof PlayerEntity)) return;
        MinecraftClient c = MinecraftClient.getInstance();
        AutoGgModule m = on();
        if (c.player != null && e == c.player) {
            if (m != null && m.onDeath.get()) m.queue();
            return;
        }
        if (e == CombatTracker.lastTarget() && CombatTracker.lastTargetAge() <= 8000) kill(e, e.getName().getString());
    }

    /** A kill: Kill Effects at the victim, and gg if Auto GG wants it. */
    private static void kill(Entity victim, String name) {
        KillEffectsModule.onKill(victim, name);
        AutoGgModule m = on();
        if (m != null && m.onKill.get()) m.queue();
    }

    /** Finds a player in the world whose name appears in the line (not you). */
    private static Entity findNamed(MinecraftClient c, String line, String me) {
        if (c.world == null) return null;
        Entity best = null;
        for (PlayerEntity p : c.world.getPlayers()) {
            String n = p.getName().getString();
            if (n.equalsIgnoreCase(me) || n.length() < 3) continue;
            if (Pattern.compile("(?i)(^|\\W)" + Pattern.quote(n) + "(\\W|$)").matcher(line).find()) {
                if (best == null || n.length() > best.getName().getString().length()) best = p;
            }
        }
        return best;
    }

    /** A system/chat line arrived (registered in TempestClient through Fabric's message events). */
    public static void onMessage(Text text) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (text == null || c.player == null) return;
        AutoGgModule m = on();
        String me = c.player.getName().getString();
        if (me == null || me.isEmpty()) return;

        // vanilla death messages: death.attack.* with (victim, killer, ...)
        if (text.getContent() instanceof TranslatableTextContent tc && tc.getKey().startsWith("death.")) {
            Object[] args = tc.getArgs();
            String victim = args.length > 0 ? argString(args[0]) : "";
            String killer = args.length > 1 ? argString(args[1]) : "";
            if (victim.equalsIgnoreCase(me)) { if (m != null && m.onDeath.get()) m.queue(); return; }
            if (killer.equalsIgnoreCase(me)) kill(findNamed(c, victim, me), victim);
            return;
        }

        String s = text.getString().replaceAll("\u00a7.", "").trim();
        if (s.isEmpty() || s.length() > 200) return;
        if (m != null && m.onGameEnd.get() && GAME_END.matcher(s).find()) { m.queue(); return; }
        String n = Pattern.quote(me);
        if (!s.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT))) return;
        boolean byMe = Pattern.compile("(?i)\\b" + KILL_VERBS + "\\b.*\\bby\\s+(\\W*)" + n + "\\b").matcher(s).find();
        boolean iDid = Pattern.compile("(?i)(^|\\s|\\W)" + n + "\\s+(has\\s+)?" + KILL_VERBS + "\\b").matcher(s).find();
        boolean iWasKilled = Pattern.compile("(?i)^\\W*" + n + "\\b.*\\b" + KILL_VERBS + "\\b").matcher(s).find() && !iDid;
        if (iWasKilled) { if (m != null && m.onDeath.get()) m.queue(); return; }
        if (byMe || iDid) {
            Entity v = findNamed(c, s, me);
            kill(v, v != null ? v.getName().getString() : s);
        }
    }

    private static String argString(Object o) {
        if (o instanceof Text t) return t.getString();
        return String.valueOf(o);
    }

    private void queue() {
        long now = System.currentTimeMillis();
        if (now - lastSent < 10_000 || sendAt != 0) return;
        sendAt = now + (long) (delay.get() * 1000);
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (sendAt != 0 && System.currentTimeMillis() >= sendAt) {
            sendAt = 0;
            lastSent = System.currentTimeMillis();
            if (c.getNetworkHandler() != null) c.getNetworkHandler().sendChatMessage(message.get());
        }
    }
}
