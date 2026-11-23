package dev.tempest.gui;

import dev.tempest.util.Updater;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** "Update available" window: release notes, download progress, and restart prompt. */
public class UpdateScreen extends Screen {
    private static final int W = 380, H = 270, HEAD = 40, BH = 20;

    private final Screen parent;
    private List<OrderedText> lines = new ArrayList<>();
    private int scroll;

    public UpdateScreen(Screen parent) {
        super(Text.literal("Update"));
        this.parent = parent;
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return Math.max(8, (height - H) / 2); }
    private int notesTop() { return y0() + HEAD + 34; }
    private int notesH() { return H - HEAD - 34 - 50; }
    private int btnY() { return y0() + H - 32; }

    @Override
    protected void init() {
        lines = new ArrayList<>();
        int wrap = W - 48;
        String notes = Updater.notes().replace("\r", "");
        if (notes.isBlank()) notes = "No release notes for this version.";
        for (String raw : notes.split("\n")) {
            String l = raw.strip();
            if (l.startsWith("#")) l = "§f§l" + l.replaceFirst("^#+\\s*", "");
            else if (l.startsWith("- ") || l.startsWith("* ")) l = "• " + l.substring(2);
            l = l.replace("**", "").replace("`", "");
            if (l.isEmpty()) { lines.add(OrderedText.EMPTY); continue; }
            lines.addAll(textRenderer.wrapLines(Draw.ui(Text.literal(l)), wrap));
        }
        scroll = 0;
    }

    /** Buttons for the current state: {label, id}. */
    private String[][] buttons() {
        return switch (Updater.state()) {
            case AVAILABLE -> Updater.canInstall()
                    ? new String[][]{{"Update", "update"}, {"Later", "later"}, {"Skip this version", "skip"}}
                    : new String[][]{{"Open releases page", "page"}, {"Later", "later"}};
            case READY -> new String[][]{{"Quit Game", "quit"}, {"Later", "later"}};
            case FAILED -> new String[][]{{"Open releases page", "page"}, {"Close", "later"}};
            default -> new String[][]{{"Close", "later"}};
        };
    }

    private int btnW(String[] b) { return Math.max(70, Draw.textW(textRenderer, b[0]) + 24); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        int x = x0(), y = y0();
        String latest = Updater.latestVersion() == null ? "?" : Updater.latestVersion();
        Draw.window(ctx, tr, width, height, x, y, W, H, HEAD, "UPDATE AVAILABLE",
                "Tempest " + Updater.currentVersion() + "  →  " + latest);

        Draw.text(ctx, tr, "WHAT'S NEW", x + 20, y + HEAD + 16, Draw.DIM, false);

        // release notes, scrollable
        int nt = notesTop(), nh = notesH();
        Draw.panel(ctx, x + 16, nt - 4, W - 32, nh + 8, 6, Draw.card(), Draw.border());
        int visible = nh / 10;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        ctx.enableScissor(x + 16, nt - 2, x + W - 16, nt + nh + 2);
        for (int i = 0; i < visible && i + scroll < lines.size(); i++) {
            Draw.text(ctx, tr, lines.get(i + scroll), x + 24, nt + 2 + i * 10, 0xFFD6D9EA, false);
        }
        ctx.disableScissor();
        if (lines.size() > visible) {
            int trackH = nh, thumb = Math.max(14, trackH * visible / lines.size());
            int ty = nt + (trackH - thumb) * scroll / Math.max(1, lines.size() - visible);
            Draw.roundRect(ctx, x + W - 22, ty, 3, thumb, 1, Draw.withAlpha(Draw.accent(), 0xA0));
        }

        // status line / progress
        int sy = btnY() - 14;
        switch (Updater.state()) {
            case DOWNLOADING -> {
                int pw = W - 40;
                Draw.roundRect(ctx, x + 20, sy, pw, 6, 3, 0x40FFFFFF);
                int fw = Math.max(6, Math.round(pw * Updater.progress()));
                Draw.roundRect(ctx, x + 20, sy, fw, 6, 3, Draw.accent());
                String p = "Downloading… " + Math.round(Updater.progress() * 100) + "%";
                Draw.text(ctx, tr, p, x + 20, btnY() + 6, Draw.DIM, false);
            }
            case READY -> Draw.text(ctx, tr, "Downloaded and verified. Restart Minecraft to finish updating.", x + 20, sy - 2, 0xFF7CE0A0, false);
            case FAILED -> Draw.text(ctx, tr, "Update failed: " + Updater.error(), x + 20, sy - 2, 0xFFFF7A7A, false);
            case AVAILABLE -> {
                if (!Updater.canInstall()) Draw.text(ctx, tr, "Dev build - install the new jar by hand.", x + 20, sy - 2, Draw.DIM, false);
            }
            default -> { }
        }

        if (Updater.state() != Updater.State.DOWNLOADING) {
            int bx = x + W - 16;
            String[][] bs = buttons();
            for (int i = bs.length - 1; i >= 0; i--) {
                int bw = btnW(bs[i]);
                bx -= bw;
                boolean hv = Draw.hit(mx, my, bx, btnY(), bw, BH);
                if (i == 0) Draw.button(ctx, tr, bx, btnY(), bw, BH, bs[i][0], hv, Draw.accent() & 0xFFFFFF);
                else Draw.ghost(ctx, tr, bx, btnY(), bw, BH, bs[i][0], hv);
                bx -= 6;
            }
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0 || Updater.state() == Updater.State.DOWNLOADING) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();
        int bx = x0() + W - 16;
        String[][] bs = buttons();
        for (int i = bs.length - 1; i >= 0; i--) {
            int bw = btnW(bs[i]);
            bx -= bw;
            if (Draw.hit(mx, my, bx, btnY(), bw, BH)) {
                switch (bs[i][1]) {
                    case "update" -> Updater.downloadAsync();
                    case "skip" -> { Updater.skip(); close(); }
                    case "quit" -> client.scheduleStop();
                    case "page" -> net.minecraft.util.Util.getOperatingSystem().open("https://github.com/" + Updater.REPO + "/releases/latest");
                    default -> close();
                }
                return true;
            }
            bx -= 6;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll -= (int) Math.signum(v) * 3;
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (Updater.state() == Updater.State.DOWNLOADING) return;
        if (client != null) client.setScreen(parent);
    }
}
