package dev.tempest.gui;

import dev.tempest.util.Ias;
import dev.tempest.util.PlayerHeads;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * "Switch account": Tempest's account list, run by In-Game Account Switcher (see util/Ias). Each row shows the
 * account's head, name and type, with Log in / Edit / Remove; "+ Add account" opens IAS's add popup (Microsoft
 * sign-in in the browser, or an offline name). IAS keeps the accounts encrypted on this PC and does the sign-in.
 */
public class IasAccountsScreen extends Screen {
    private static final int ROW_H = 32;
    private final Screen parent;
    private TextFieldWidget search;
    private double scroll;

    public IasAccountsScreen(Screen parent) {
        super(Text.literal("Accounts"));
        this.parent = parent;
    }

    private int pw() { return Math.min(width - 24, 380); }
    private int ph() { return Math.min(height - 24, 300); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int listY() { return py() + 62; }
    private int listH() { return py() + ph() - 44 - listY(); }

    @Override
    protected void init() {
        String old = search != null ? search.getText() : "";
        search = new TextFieldWidget(textRenderer, px() + 26, py() + 43, pw() - 40, 12, Text.literal("Search accounts"));
        search.setDrawsBackground(false);
        search.setMaxLength(32);
        search.setPlaceholder(Text.literal("Search accounts"));
        search.setText(old);
        search.setChangedListener(s -> scroll = 0);
        addDrawableChild(search);
    }

    private List<Ias.Acc> rows() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Ias.Acc> out = new ArrayList<>();
        for (Ias.Acc a : Ias.accounts()) if (q.isEmpty() || a.name().toLowerCase(Locale.ROOT).contains(q)) out.add(a);
        return out;
    }

    private UUID current() {
        try { return client.getSession().getUuidOrNull(); } catch (Throwable t) { return null; }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        if (client.world == null) TempestTitleScreen.drawBackdrop(ctx, width, height, MenuKit.time());
        ctx.fillGradient(0, 0, width, height, 0x90000000, 0x70000000);
        int x = px(), y = py(), w = pw(), h = ph();
        Draw.shadow(ctx, x, y, w, h, 10, 10);
        Draw.glass(ctx, x, y, w, h, 10, 0xE8101218, 0x40FFFFFF);
        Draw.roundRectV(ctx, x + 1, y + 1, w - 2, 32, 9, Draw.withAlpha(Draw.accent(), 0x26), Draw.withAlpha(Draw.accent(), 0x00));
        Draw.text(ctx, tr, Draw.bold("Accounts"), x + 14, y + 11, 0xFFFFFFFF, false);
        Draw.text(ctx, tr, "Switch accounts without restarting", x + 14, y + 23, 0xFF8B90A0, false);
        Draw.hairline(ctx, x + 10, y + 35, w - 20, Draw.accent() & 0xFFFFFF, 0x50);

        // search box
        boolean sf = search.isFocused();
        Draw.panel(ctx, x + 12, y + 39, w - 24, 18, 6, sf ? 0xE0202330 : 0xC0181A22, sf ? Draw.withAlpha(Draw.accent(), 0xA0) : 0x30FFFFFF);
        Draw.text(ctx, tr, "⌕", x + 17, y + 44, 0xFF7C8194, false);

        renderList(ctx, tr, mx, my);

        Draw.centered(ctx, tr, "Logins are encrypted on this PC by In-Game Account Switcher.", x + w / 2, y + h - 40, 0xFF5E6375);
        int by = y + h - 28;
        MenuKit.flatButton(ctx, tr, "ias_add", x + 12, by, 110, 20, null, "+ Add account", MenuKit.GREEN, Draw.hit(mx, my, x + 12, by, 110, 20), true, 1f);
        MenuKit.flatButton(ctx, tr, "ias_done", x + w - 72, by, 60, 20, null, "Done", MenuKit.OUTLINE, Draw.hit(mx, my, x + w - 72, by, 60, 20), true, 1f);
        super.render(ctx, mx, my, delta);
    }

    /** Button layout of one row, right to left: Remove, Edit, then Log in or "● Current". */
    private int[][] rowButtons(TextRenderer tr, int rx, int rw, int ry) {
        int right = rx + rw - 6;
        int remW = Draw.textW(tr, "Remove") + 12, editW = Draw.textW(tr, "Edit") + 12, logW = 56;
        int[] rem = {right - remW, ry + 6, remW, 18};
        int[] edit = {rem[0] - 4 - editW, ry + 6, editW, 18};
        int[] log = {edit[0] - 6 - logW, ry + 6, logW, 18};
        return new int[][]{log, edit, rem};
    }

    private void renderList(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int x = px(), w = pw(), ly = listY(), lh = listH();
        List<Ias.Acc> rows = rows();
        UUID cur = current();
        ctx.enableScissor(x + 10, ly, x + w - 10, ly + lh);
        for (int i = 0; i < rows.size(); i++) {
            Ias.Acc a = rows.get(i);
            int ry = ly + 2 + i * ROW_H - (int) scroll;
            if (ry + ROW_H < ly || ry > ly + lh) continue;
            int rx = x + 12, rw = w - 24;
            boolean active = a.uuid() != null && a.uuid().equals(cur);
            boolean inList = my >= ly && my < ly + lh;
            boolean hv = inList && Draw.hit(mx, my, rx, ry, rw, ROW_H - 4);
            float p = Draw.hoverProgress("ias_row" + i + a.name(), hv);
            Draw.panel(ctx, rx, ry, rw, ROW_H - 4, 6,
                    active ? Draw.withAlpha(Draw.lerpColor(0xFF1C1F2A, Draw.accent(), 0.16f), 0xE0) : Draw.lerpColor(0xA01A1C24, 0xD0262935, p),
                    active ? Draw.withAlpha(Draw.accent(), 0xB0) : Draw.lerpColor(0x26FFFFFF, 0x60FFFFFF, p));
            Identifier head = PlayerHeads.get(a.skin() != null ? a.skin() : a.uuid());
            Draw.roundRect(ctx, rx + 6, ry + 4, 20, 20, 4, 0xFF0C0D12);
            if (head != null) ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, rx + 7, ry + 5, 0f, 0f, 18, 18, 32, 32, 32, 32, 0xFFFFFFFF);
            int[][] b = rowButtons(tr, rx, rw, ry);
            Draw.text(ctx, tr, Draw.ellipsize(tr, a.name(), b[0][0] - rx - 40), rx + 32, ry + 6, 0xFFFFFFFF, false);
            Draw.text(ctx, tr, a.microsoft() ? "Microsoft" : "Offline", rx + 32, ry + 16, a.microsoft() ? 0xFF7C8194 : 0xFFB89A6A, false);

            if (active) {
                String c = "● Current";
                Draw.text(ctx, tr, c, b[0][0] + b[0][2] - Draw.textW(tr, c), ry + 11, 0xFF6AF09A, false);
            } else {
                MenuKit.flatButton(ctx, tr, "ias_log" + i, b[0][0], b[0][1], b[0][2], b[0][3], null, "Log in", MenuKit.GREEN,
                        inList && Draw.hit(mx, my, b[0][0], b[0][1], b[0][2], b[0][3]), true, 1f);
            }
            for (int k = 1; k <= 2; k++) {
                int[] bb = b[k];
                boolean bh = inList && Draw.hit(mx, my, bb[0], bb[1], bb[2], bb[3]);
                boolean danger = k == 2;
                Draw.panel(ctx, bb[0], bb[1], bb[2], bb[3], 5,
                        bh ? (danger ? 0xC03A1C22 : 0xC0262935) : 0x80181A22,
                        bh ? (danger ? 0xA0FF6B6B : 0x70FFFFFF) : 0x30FFFFFF);
                Draw.centered(ctx, tr, k == 1 ? "Edit" : "Remove", bb[0] + bb[2] / 2, bb[1] + 5,
                        bh && danger ? 0xFFFF8A8A : 0xFFC9CCD6);
            }
        }
        ctx.disableScissor();
        if (rows.isEmpty()) {
            boolean filtered = search != null && !search.getText().isBlank();
            Draw.centered(ctx, tr, filtered ? "No accounts match." : "No accounts yet - add one below.", x + w / 2, ly + 18, 0xFF8B90A0);
            if (!filtered) Draw.centered(ctx, tr, "Microsoft accounts sign in through your browser.", x + w / 2, ly + 30, 0xFF5E6375);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        int x = px(), y = py(), w = pw(), h = ph(), by = y + h - 28;
        if (Draw.hit(mx, my, x + 12, by, 110, 20)) { Ias.addOrEdit(this, null); return true; }
        if (Draw.hit(mx, my, x + w - 72, by, 60, 20)) { close(); return true; }
        int ly = listY(), lh = listH();
        if (!Draw.hit(mx, my, x + 10, ly, w - 20, lh)) return false;
        List<Ias.Acc> rows = rows();
        int i = (int) ((my - ly - 2 + scroll) / ROW_H);
        if (i < 0 || i >= rows.size()) return false;
        Ias.Acc a = rows.get(i);
        int ry = ly + 2 + i * ROW_H - (int) scroll;
        int[][] b = rowButtons(textRenderer, x + 12, w - 24, ry);
        boolean active = a.uuid() != null && a.uuid().equals(current());
        if (Draw.hit(mx, my, b[2][0], b[2][1], b[2][2], b[2][3])) { Ias.remove(this, a); return true; }
        if (Draw.hit(mx, my, b[1][0], b[1][1], b[1][2], b[1][3])) { Ias.addOrEdit(this, a); return true; }
        if (!active && (Draw.hit(mx, my, b[0][0], b[0][1], b[0][2], b[0][3]) || doubled)) { Ias.login(a, this); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        double max = Math.max(0, rows().size() * ROW_H + 4 - listH());
        scroll = Math.max(0, Math.min(max, scroll - vertical * ROW_H));
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
