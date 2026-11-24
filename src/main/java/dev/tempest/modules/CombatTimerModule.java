package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Combat Timer: how long until you're out of combat (safe to log out) on servers that combat-tag.
 * <ul>
 *   <li>Starts (or restarts) when you hit a player, or get hurt with another player close by.</li>
 *   <li>Follows the server where it can: a chat or action-bar message like "Combat: 12s" sets the time left, and
 *       "You are no longer in combat" ends it.</li>
 *   <li>Otherwise counts down from "Combat Length" (most servers use 10-30 s; set it to match yours).</li>
 * </ul>
 * Hidden while you're not in combat. Only reads what your game already sees.
 */
public class CombatTimerModule extends HudModule {
    private final NumberSetting length = add(new NumberSetting("length", "Combat Length (s)", 15, 5, 60, 1));
    private final BoolSetting endSound = add(new BoolSetting("end_ping", "Sound When Over", false));

    private static final Pattern END = Pattern.compile(
            "(no longer|not) in combat|out of combat|left combat|combat( tag)?( has)? (ended|expired|is over|over)"
                    + "|(can|may) (now )?(safely )?log ?out|untagged", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile(
            "in combat|combat ?tag|combat ?log|combat:|pvp ?tag|you('re| are) (now )?tagged|combat timer", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECONDS = Pattern.compile("(\\d{1,3}(?:[.,]\\d)?)\\s*(s|sec|secs|seconds?)\\b", Pattern.CASE_INSENSITIVE);

    /** When combat ends (ms), 0 = not in combat. */
    private static long until;
    private static long startedAt;
    private static Object world;
    private int lastSelfHurt;

    public CombatTimerModule() {
        // -1, -1: starts just under the crosshair (see onTick) until you move it
        super("combat_timer", "Combat Timer", "Counts down until you're out of combat on servers that combat-tag you.",
                Category.PVP, false, -1, -1);
        keyHint = "Set Combat Length to your server's combat time. Servers that show the time in chat or above the hotbar are followed automatically.";
    }

    private static CombatTimerModule on() {
        CombatTimerModule m = ModuleManager.get(CombatTimerModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    @Override
    public void onInit() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onServerText(message));
    }

    // ------------------------------------------------------------------ triggers
    /** You hit something (ClientPlayerInteractionManagerMixin, via CombatTracker). */
    public static void onAttack(Entity target) {
        CombatTimerModule m = on();
        if (m != null && target instanceof PlayerEntity) m.tag(-1);
    }

    private void tag(double seconds) {
        long now = System.currentTimeMillis();
        if (until == 0) startedAt = now;
        long ms = Math.round((seconds > 0 ? seconds : length.get()) * 1000);
        until = now + ms;
    }

    private static void onServerText(Text message) {
        CombatTimerModule m = on();
        if (m == null || message == null) return;
        String s = message.getString();
        if (s.length() > 300) return;
        String low = s.toLowerCase(Locale.ROOT);
        if (END.matcher(low).find()) { m.end(true); return; }
        if (!TAG.matcher(low).find()) return;
        Matcher sec = SECONDS.matcher(low);
        m.tag(sec.find() ? parse(sec.group(1)) : -1);
    }

    private static double parse(String n) {
        try { return Math.min(300, Double.parseDouble(n.replace(',', '.'))); } catch (NumberFormatException e) { return -1; }
    }

    private void end(boolean sound) {
        if (until == 0) return;
        until = 0;
        if (sound && endSound.get()) {
            dev.tempest.util.Keys.sound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.6f, 1.6f);
        }
    }

    @Override
    public void onTick(MinecraftClient c) {
        if ((x < 0 || y < 0) && c.getWindow() != null) {
            int sw = c.getWindow().getScaledWidth(), sh = c.getWindow().getScaledHeight();
            setPos(Math.max(0, sw / 2 - 45), Math.max(0, sh / 2 + 18));
        }
        if (c.world != world) { world = c.world; until = 0; }
        if (c.player == null) return;
        if (c.player.isDead() || c.player.getHealth() <= 0f) { until = 0; lastSelfHurt = 0; return; }
        // got hurt with another player close by: count it as combat
        if (c.player.hurtTime > lastSelfHurt && c.world != null) {
            for (PlayerEntity p : c.world.getPlayers()) {
                if (p != c.player && !p.isSpectator() && p.squaredDistanceTo(c.player) < 8 * 8) { tag(-1); break; }
            }
        }
        lastSelfHurt = c.player.hurtTime;
        if (until != 0 && System.currentTimeMillis() >= until) end(true);
    }

    @Override
    public void onDisable() { until = 0; }

    @Override
    public void resetPos() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.getWindow() != null) setPos(Math.max(0, c.getWindow().getScaledWidth() / 2 - 45), Math.max(0, c.getWindow().getScaledHeight() / 2 + 18));
        else super.resetPos();
    }

    /** The server's own combat text from the action bar, shown in this element instead (see ActionBarCombatMixin). */
    private static Text serverText;
    private static long serverTextAt;

    /** True = it was a combat message: we show it ourselves, so the normal action bar skips it. */
    public static boolean takeActionBar(Text message) {
        CombatTimerModule m = on();
        if (m == null || message == null) return false;
        String low = message.getString().toLowerCase(Locale.ROOT);
        if (low.length() > 200 || !(TAG.matcher(low).find() || low.contains("combat"))) return false;
        serverText = message;
        serverTextAt = System.currentTimeMillis();
        return true;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        // the server shows its own combat countdown: draw exactly that, where you placed this element
        if (!preview && serverText != null && now - serverTextAt < 1500) {
            w = Draw.textW(c.textRenderer, serverText) + 14;
            h = 18;
            ctx.drawTextWithShadow(c.textRenderer, serverText, x + 7, y + 5, 0xFFFFFFFF);
            return;
        }
        double left = preview ? 9.4 : until == 0 ? 0 : (until - now) / 1000.0;
        if (left <= 0 || x < 0 || y < 0) { w = 0; h = 0; return; }
        double total = preview ? 15 : Math.max(1, (until - startedAt) / 1000.0);
        float frac = (float) Math.max(0, Math.min(1, left / total));
        w = 90;
        h = 20;
        boolean ending = left < 3;
        float pulse = ending ? 0.5f + 0.5f * (float) Math.sin(now / 90.0) : 1f;
        int red = Draw.lerpColor(0xFFFF4D4D, 0xFFFFB0B0, ending ? 1 - pulse : 0);
        String label = "COMBAT";
        String time = String.format(Locale.ROOT, "%.1fs", left);
        Draw.text(ctx, c.textRenderer, label, x + 7, y + 5, 0xFFFFFFFF, true);
        int tw = Draw.textW(c.textRenderer, time);
        Draw.text(ctx, c.textRenderer, time, x + w - 7 - tw, y + 5, red, true);
        // shrinking bar along the bottom
        int bw = w - 12;
        ctx.fill(x + 6, y + h - 4, x + 6 + bw, y + h - 3, 0x50FFFFFF);
        ctx.fill(x + 6, y + h - 4, x + 6 + Math.round(bw * frac), y + h - 3, red);
    }
}
