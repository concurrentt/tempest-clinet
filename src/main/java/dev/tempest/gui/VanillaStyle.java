package dev.tempest.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Tempest look for vanilla widgets (used by mixin/VanillaButtonStyleMixin, VanillaSliderStyleMixin, ScreenBackgroundMixin). */
public final class VanillaStyle {
    private VanillaStyle() { }

    /** Rounded glass button background. The label is still drawn by vanilla on top. */
    public static void button(DrawContext c, int x, int y, int w, int h, boolean hovered, boolean active) {
        int r = Math.min(8, h / 2);
        int fill, border;
        if (!active) {
            fill = 0x900D0E13;
            border = 0x18FFFFFF;
        } else if (hovered) {
            fill = 0xD8232636;
            border = Draw.withAlpha(Draw.accent(), 0xC8);
        } else {
            fill = 0xB8141620;
            border = 0x2EFFFFFF;
        }
        Draw.panel(c, x, y, w, h, r, fill, border);
        if (active) c.fill(x + r, y + 1, x + w - r, y + 2, hovered ? 0x22FFFFFF : 0x12FFFFFF);
    }

    /** Whole slider: track, filled part, knob and the centred value text. */
    public static void slider(DrawContext c, int x, int y, int w, int h, double value, Text message, boolean hovered, boolean active) {
        button(c, x, y, w, h, hovered, active);
        int r = Math.min(8, h / 2);
        int inner = w - 4;
        int fillW = (int) Math.round(Math.max(0, Math.min(1, value)) * inner);
        if (fillW > 0) Draw.roundRect(c, x + 2, y + 2, Math.max(r * 2, fillW), h - 4, Math.max(1, r - 1),
                Draw.withAlpha(Draw.accent(), active ? (hovered ? 0x70 : 0x50) : 0x24));
        int kx = x + 2 + fillW - 3;
        kx = Math.max(x + 2, Math.min(x + w - 8, kx));
        Draw.roundRect(c, kx, y + 3, 6, h - 6, 3, active ? (hovered ? 0xFFFFFFFF : 0xFFE3E6F2) : 0xFF6C7080);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Text t = message;
        int tw = tr.getWidth(t);
        if (tw > w - 8) {
            String s = Draw.ellipsize(tr, t.getString(), w - 8);
            c.drawText(tr, s, x + (w - tr.getWidth(s)) / 2, y + (h - 8) / 2, active ? 0xFFFFFFFF : 0xFFA0A0A0, true);
        } else {
            c.drawText(tr, t, x + (w - tw) / 2, y + (h - 8) / 2, active ? 0xFFFFFFFF : 0xFFA0A0A0, true);
        }
    }
}
