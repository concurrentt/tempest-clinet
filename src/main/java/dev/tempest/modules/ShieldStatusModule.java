package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Shield Statuses: is a shield up or disabled (by an axe)? Shown for every player holding a shield as a coloured
 * shield badge on their nametag (enabled colour / disabled colour, optionally fading from one to the other as
 * the shield comes back, with the seconds left), and for you as a HUD element.
 *
 * Your own cooldown is exact. For other players the client only learns that their shield was disabled (the
 * "shield disabled" entity event), so the time left assumes the vanilla 5 seconds (configurable).
 */
public class ShieldStatusModule extends HudModule {
    private final ColorSetting enabledColor = add(new ColorSetting("ready", "Enabled Color", 0xFF55FF7A));
    private final ColorSetting disabledColor = add(new ColorSetting("disabled", "Disabled Color", 0xFFFF4A4A));
    private final BoolSetting interpolate = add(new BoolSetting("interpolate", "Fade Back To Enabled", true));
    private final BoolSetting others = add(new BoolSetting("others", "Show On Nametags", true));
    private final BoolSetting seconds = add(new BoolSetting("seconds", "Show Seconds Left", true));
    private final BoolSetting showWhenReady = add(new BoolSetting("show_ready", "HUD When Ready", false));
    private final NumberSetting disableSeconds = add(new NumberSetting("disable_seconds", "Disable Time (others)", 5.0, 1.0, 10.0, 0.5));

    private static final Map<Integer, Long> DISABLED_AT = new HashMap<>();   // entity id -> client time (ms)
    private static final Text SHIELD = Text.literal("")
            .styled(s -> s.withFont(new StyleSpriteSource.Font(Identifier.of("tempest", "icons"))));

    public ShieldStatusModule() {
        super("shield_status", "Shield Status", "Shield enabled / disabled on nametags and your HUD, with a fade as it comes back.",
                Category.PVP, false, 120, 200);
    }

    private static ShieldStatusModule on() {
        ShieldStatusModule m = ModuleManager.get(ShieldStatusModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Entity event 30 = shield disabled. */
    public static void onStatus(Entity e, byte status) {
        if (status == 30 && e != null) DISABLED_AT.put(e.getId(), System.currentTimeMillis());
        if (DISABLED_AT.size() > 256) DISABLED_AT.clear();
    }

    private static boolean holdsShield(PlayerEntity p) {
        return p.getMainHandStack().isOf(Items.SHIELD) || p.getOffHandStack().isOf(Items.SHIELD);
    }

    /** 0 = ready, 1 = just disabled. */
    private float progress(PlayerEntity p) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (p == c.player) return c.player.getItemCooldownManager().getCooldownProgress(new ItemStack(Items.SHIELD), 0f);
        Long at = DISABLED_AT.get(p.getId());
        if (at == null) return 0f;
        float total = (float) (disableSeconds.get() * 1000.0);
        float left = 1f - (System.currentTimeMillis() - at) / total;
        if (left <= 0f) { DISABLED_AT.remove(p.getId()); return 0f; }
        return left;
    }

    private int color(float progress) {
        if (progress <= 0f) return enabledColor.get();
        return interpolate.get() ? Draw.lerpColor(enabledColor.get(), disabledColor.get(), progress) : disabledColor.get();
    }

    /** Nametag badge for a player holding a shield, or null. */
    public static Text badge(PlayerEntity p) {
        ShieldStatusModule m = on();
        if (m == null || !m.others.get() || p == null || !holdsShield(p)) return null;
        float prog = m.progress(p);
        int col = m.color(prog) & 0xFFFFFF;
        MutableText t = Text.literal(" ").append(SHIELD.copy().styled(s -> s.withColor(TextColor.fromRgb(col))));
        if (prog > 0f && m.seconds.get()) {
            float secs = p == MinecraftClient.getInstance().player ? prog * 5f : (float) (prog * m.disableSeconds.get());
            t.append(Text.literal(" " + String.format(java.util.Locale.ROOT, "%.1fs", secs)).styled(s -> s.withColor(TextColor.fromRgb(col))));
        }
        return t;
    }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        float prog;
        if (preview || c.player == null) prog = 0.45f;
        else {
            if (!holdsShield(c.player)) return;
            prog = progress(c.player);
            if (prog <= 0f && !showWhenReady.get()) return;
        }
        int col = color(prog);
        w = 78;
        h = 20;
        box(ctx);
        ctx.drawItem(new ItemStack(Items.SHIELD), x + 3, y + 2);
        String label = prog > 0f ? String.format(java.util.Locale.ROOT, "%.1fs", prog * 5f) : "Ready";
        ctx.drawText(c.textRenderer, label, x + 23, y + 4, col, true);
        // thin progress bar under the text
        int bw = w - 28;
        Draw.roundRect(ctx, x + 23, y + 14, bw, 2, 1, 0x40FFFFFF);
        if (prog > 0f) Draw.roundRect(ctx, x + 23, y + 14, Math.max(2, Math.round(bw * (1f - prog))), 2, 1, col);
    }
}
