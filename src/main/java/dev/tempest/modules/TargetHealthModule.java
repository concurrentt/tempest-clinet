package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.util.CombatTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

import java.util.Locale;

public class TargetHealthModule extends HudModule {
    private LivingEntity last;
    private float shown = -1f, trail = -1f;

    public TargetHealthModule() { super("target_health", "Target Health", "Health of the entity you are fighting.", Category.PVP, false, 120, 6); }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        LivingEntity t = preview ? null : CombatTracker.currentTarget();
        if (!preview && t == null) { last = null; return; }

        String name = preview ? "Steve" : t.getName().getString();
        float hp = preview ? 14f : t.getHealth();
        float max = preview ? 20f : Math.max(1f, t.getMaxHealth());
        float f = Math.max(0f, Math.min(1f, hp / max));

        if (shown < 0f || (!preview && t != last)) { shown = f; trail = f; last = t; }
        shown += (f - shown) * 0.3f;
        if (f > trail) trail = f; else trail += (f - trail) * 0.05f;

        String hpText = String.format(Locale.ROOT, "%.1f / %.0f", hp, max);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        w = Math.max(110, Draw.textW(tr, name) + Draw.textW(tr, hpText) + 24);
        h = 28;
        box(ctx);

        Draw.text(ctx, tr, name, x + 7, y + 4, color.get(), true);
        Draw.text(ctx, tr, hpText, x + w - 6 - Draw.textW(tr, hpText), y + 4, Draw.DIM, true);

        int bx = x + 7, by = y + 17, bw = w - 14;
        Draw.roundRect(ctx, bx, by, bw, 6, 3, 0xFF1C2030);
        int tw = (int) (bw * trail);
        if (tw > 0) Draw.roundRect(ctx, bx, by, Math.max(3, tw), 6, 3, 0xFFFF6B6B);
        int fw = (int) (bw * shown);
        int top = f > 0.6f ? 0xFF7CF58A : f > 0.3f ? 0xFFFFE066 : 0xFFFF8A8A;
        int bottom = f > 0.6f ? 0xFF2FBF4F : f > 0.3f ? 0xFFE0A800 : 0xFFE03A3A;
        if (fw > 0) Draw.roundRectV(ctx, bx, by, Math.max(3, fw), 6, 3, top, bottom);
    }
}
