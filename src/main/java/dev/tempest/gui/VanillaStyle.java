package dev.tempest.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Tempest look for vanilla widgets (used by mixin/VanillaButtonStyleMixin, VanillaSliderStyleMixin, ScreenBackgroundMixin). */
public final class VanillaStyle {
    private VanillaStyle() { }

    /** Plain rectangle button on the see-through background: a dark fill and a 1 px border. The label is drawn by vanilla. */
    public static void button(DrawContext c, int x, int y, int w, int h, boolean hovered, boolean active) {
        int fill = !active ? 0x700D0E13 : hovered ? 0xB0262A38 : 0x90121420;
        int border = !active ? 0x14FFFFFF : hovered ? 0xB0FFFFFF : 0x30FFFFFF;
        c.fill(x, y, x + w, y + h, fill);
        c.fill(x, y, x + w, y + 1, border);
        c.fill(x, y + h - 1, x + w, y + h, border);
        c.fill(x, y + 1, x + 1, y + h - 1, border);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
    }

    /** Whole slider: rectangle, filled part, a thin knob and the centred value text. */
    public static void slider(DrawContext c, int x, int y, int w, int h, double value, Text message, boolean hovered, boolean active) {
        button(c, x, y, w, h, hovered, active);
        int inner = w - 2;
        int fillW = (int) Math.round(Math.max(0, Math.min(1, value)) * inner);
        if (fillW > 0) c.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, Draw.withAlpha(Draw.accent(), active ? (hovered ? 0x60 : 0x40) : 0x20));
        int kx = Math.max(x + 1, Math.min(x + w - 3, x + 1 + fillW - 1));
        c.fill(kx, y + 1, kx + 2, y + h - 1, active ? 0xFFFFFFFF : 0xFF6C7080);
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
