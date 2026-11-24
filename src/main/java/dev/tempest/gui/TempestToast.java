package dev.tempest.gui;

import dev.tempest.util.PlayerHeads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * A small Tempest-styled pop-up in the top-right corner (the game's own toast slot, so it also shows in menus):
 * a player's face, a title and one line of text. Used for friend alerts and invites.
 */
public class TempestToast implements Toast {
    private static final int W = 170, H = 32;
    private final UUID player;
    private final String title, line;
    private final int accent;
    private final long showMs;
    private Visibility visibility = Visibility.SHOW;

    public TempestToast(UUID player, String title, String line, int accent, long showMs) {
        this.player = player;
        this.title = title;
        this.line = line;
        this.accent = accent;
        this.showMs = showMs;
    }

    /** Shows a toast (from any thread). */
    public static void show(UUID player, String title, String line, int accent, long showMs) {
        MinecraftClient c = MinecraftClient.getInstance();
        c.execute(() -> {
            if (c.getToastManager() != null) c.getToastManager().add(new TempestToast(player, title, line, accent, showMs));
        });
    }

    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return H; }
    @Override public Visibility getVisibility() { return visibility; }

    @Override
    public void update(ToastManager manager, long time) {
        visibility = time >= showMs * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void draw(DrawContext ctx, TextRenderer tr, long time) {
        Draw.panel(ctx, 0, 0, W, H, 8, 0xF0121318, Draw.withAlpha(accent, 0x90));
        ctx.fill(1, 6, 3, H - 6, 0xFF000000 | accent);
        int hx = 8, hy = 6, hs = 20;
        Identifier head = player != null ? PlayerHeads.get(player) : null;
        if (head != null) {
            Draw.roundRect(ctx, hx - 1, hy - 1, hs + 2, hs + 2, 5, 0xFF0C0D12);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, hx, hy, 0f, 0f, hs, hs, 32, 32, 32, 32, 0xFFFFFFFF);
        } else {
            Icons.draw(ctx, "people", hx + 2, hy + 2, 16, 0xFFFFFFFF);
        }
        int tx = hx + hs + 7, tw = W - tx - 6;
        Draw.text(ctx, tr, Draw.trim(tr, title, tw), tx, 6, 0xFFFFFFFF, false);
        Draw.text(ctx, tr, Draw.trim(tr, line, tw), tx, 18, 0xFFB8BED0, false);
    }
}
