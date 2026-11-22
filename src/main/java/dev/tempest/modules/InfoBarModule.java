package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Slim top bar: client name, your name, where you are, and the time. */
public class InfoBarModule extends HudModule {
    public InfoBarModule() { super("info_bar", "Info Bar", "A slim bar with your name, server and the time.", Category.HUD, false, 150, 6); }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        TextRenderer tr = c.textRenderer;
        String name = preview || c.player == null ? "Player" : c.player.getName().getString();
        String where = preview || c.getCurrentServerEntry() == null ? "Singleplayer" : c.getCurrentServerEntry().address;
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        Text logo = Text.literal("TEMPEST").formatted(Formatting.BOLD);
        List<String> parts = List.of(name, where, time);
        int tw = tr.getWidth(logo) + 12 + 18;
        for (String p : parts) tw += tr.getWidth(p) + 17;
        w = tw;
        h = 20;
        box(ctx);

        int cx = x + 6;
        dev.tempest.gui.TempestLoading.drawLogo(ctx, cx, y + 2, 16);
        cx += 19;
        ctx.drawText(tr, logo, cx, y + 6, Draw.accent(), false);
        cx += tr.getWidth(logo) + 6;
        for (String p : parts) {
            ctx.fill(cx + 3, y + 5, cx + 4, y + h - 5, Draw.withAlpha(0xFFFFFF, 0x30));
            cx += 10;
            ctx.drawText(tr, p, cx, y + 6, color.get(), false);
            cx += tr.getWidth(p) + 7;
        }
    }
}
