package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Compact top-right status-effect icons with their remaining time beside each icon. */
public class PotionModule extends HudModule {
    private record EffectRow(RegistryEntry<StatusEffect> type, String duration, String name) { }

    public PotionModule() {
        super("potions", "Potion Timers", "Effect icons with their remaining duration in the top-right HUD.", Category.HUD, false, 6, 6);
    }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<EffectRow> rows = new ArrayList<>();
        if (preview) {
            rows.add(new EffectRow(null, "0:34", "Speed II"));
            rows.add(new EffectRow(null, "3:12", "Strength"));
        } else if (client.player != null) {
            for (StatusEffectInstance effect : client.player.getStatusEffects()) {
                String name = effect.getEffectType().value().getName().getString();
                if (effect.getAmplifier() > 0) name += " " + roman(effect.getAmplifier() + 1);
                rows.add(new EffectRow(effect.getEffectType(), effect.isInfinite() ? "∞" : fmt(effect.getDuration() / 20), name));
            }
            rows.sort(Comparator.comparing(EffectRow::name, String.CASE_INSENSITIVE_ORDER));
        }

        if (rows.isEmpty()) { w = 42; h = 18; return; }
        int textWidth = 0;
        for (EffectRow row : rows) textWidth = Math.max(textWidth, client.textRenderer.getWidth(row.duration()));
        w = 18 + 7 + textWidth + 12;
        h = rows.size() * 22 + 6;

        if (!preview) x = client.getWindow().getScaledWidth() - w - 6;
        if (!preview) y = 6;
        box(ctx);

        for (int i = 0; i < rows.size(); i++) {
            EffectRow row = rows.get(i);
            int rowY = y + 4 + i * 22;
            int iconX = x + 5;
            int iconY = rowY;
            if (row.type() != null) {
                Identifier effectId = Registries.STATUS_EFFECT.getId(row.type().value());
                Identifier spriteId = Identifier.of(effectId.getNamespace(), "mob_effect/" + effectId.getPath());
                ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, spriteId, iconX, iconY, 18, 18);
            } else {
                // HUD-editor preview uses a recognizable potion glyph in place of a live effect sprite.
                ctx.drawText(client.textRenderer, "✦", iconX + 4, iconY + 5, 0xFFFFFFFF, true);
            }

            String dur = row.duration();
            int durationX = iconX + 18 + 7;
            int durationY = rowY + 5;
            ctx.drawText(client.textRenderer, dur, durationX, durationY, color.get(), true);

            // Overlay timer on the icon as well for quick readability
            ctx.getMatrices().pushMatrix();
            float scale = 0.75f;
            ctx.getMatrices().scale(scale, scale);
            int tw = client.textRenderer.getWidth(dur);
            int ox = (int) ((iconX + 18 - tw * scale - 1) / scale);
            int oy = (int) ((iconY + 18 - 7 * scale) / scale);
            ctx.drawText(client.textRenderer, dur, ox, oy, 0xFFFFFFFF, true);
            ctx.getMatrices().popMatrix();
        }
    }

    private static String fmt(int seconds) { return (seconds / 60) + ":" + String.format("%02d", seconds % 60); }

    private static String roman(int number) {
        String[] values = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number >= 1 && number <= 10 ? values[number] : Integer.toString(number);
    }
}
