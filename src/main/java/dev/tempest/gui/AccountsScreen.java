package dev.tempest.gui;

import dev.tempest.util.Accounts;
import dev.tempest.util.PlayerHeads;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Accounts: switch Minecraft accounts without restarting (see {@link Accounts}). Lists the launcher's account and the
 * accounts you added; "Add account" signs in with Microsoft's code page (microsoft.com/link).
 */
public class AccountsScreen extends Screen {
    private record Row(UUID id, String name, boolean launcher, Accounts.Saved saved) { }

    private static final int ROW_H = 30;
    private final Screen parent;
    private TextFieldWidget appId;
    private boolean setup;
    private double scroll;
    private UUID removeArmed;
    private long removeArmedAt;
    private String setupError = "";

    public AccountsScreen(Screen parent) {
        super(Text.literal("Accounts"));
        this.parent = parent;
    }

    private int pw() { return Math.min(width - 24, 340); }
    private int ph() { return Math.min(height - 24, 290); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int listY() { return py() + 40; }
    private int listH() { return py() + ph() - 62 - listY(); }

    @Override
    protected void init() {
        Accounts.clearMessages();
        setup = Accounts.clientId().isEmpty() || setup;
        String old = appId != null ? appId.getText() : Accounts.clientId();
        appId = new TextFieldWidget(textRenderer, px() + 20, py() + ph() - 84, pw() - 40, 12, Text.literal("Application (client) ID"));
        appId.setDrawsBackground(false);
        appId.setMaxLength(64);
        appId.setPlaceholder(Text.literal("e.g. 1a2b3c4d-…"));
        appId.setText(old);
        appId.visible = setup;
        addDrawableChild(appId);
        if (setup) { setFocused(appId); appId.setFocused(true); }
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        Session l = Accounts.launcher();
        if (l != null && l.getUuidOrNull() != null) out.add(new Row(l.getUuidOrNull(), l.getUsername(), true, null));
        for (Accounts.Saved a : Accounts.saved()) {
            if (l != null && a.id().equals(l.getUuidOrNull())) continue;
            out.add(new Row(a.id(), a.name(), false, a));
        }
        return out;
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

        appId.visible = setup;
        if (setup) renderSetup(ctx, tr, mx, my);
        else if (Accounts.userCode() != null) renderCode(ctx, tr, mx, my);
        else renderList(ctx, tr, mx, my);

        // status line
        String err = Accounts.error(), st = Accounts.status();
        int sy = y + h - 48;
        if (!err.isEmpty()) Draw.centered(ctx, tr, Draw.ellipsize(tr, err, w - 24), x + w / 2, sy, 0xFFFF8A8A);
        else if (!st.isEmpty()) Draw.centered(ctx, tr, Draw.ellipsize(tr, st, w - 24), x + w / 2, sy, 0xFFB8F5C8);
        else if (!setup && !Accounts.canSwitch() && client.world != null)
            Draw.centered(ctx, tr, "Leave the world to switch accounts", x + w / 2, sy, 0xFFFFD27A);

        // bottom bar
        int by = y + h - 30;
        if (setup) {
            boolean can = !appId.getText().trim().isEmpty();
            MenuKit.flatButton(ctx, tr, "acc_save", x + w - 92, by, 80, 20, null, "Save", MenuKit.GREEN, Draw.hit(mx, my, x + w - 92, by, 80, 20), can, 1f);
            MenuKit.flatButton(ctx, tr, "acc_back", x + w - 160, by, 62, 20, null, Accounts.clientId().isEmpty() ? "Close" : "Back",
                    MenuKit.OUTLINE, Draw.hit(mx, my, x + w - 160, by, 62, 20), true, 1f);
        } else {
            boolean waiting = Accounts.userCode() != null;
            MenuKit.flatButton(ctx, tr, "acc_add", x + 12, by, 110, 20, null, waiting ? "Cancel sign-in" : "+ Add account",
                    waiting ? MenuKit.OUTLINE : MenuKit.GREEN, Draw.hit(mx, my, x + 12, by, 110, 20), waiting || !Accounts.busy(), 1f);
            MenuKit.flatButton(ctx, tr, "acc_done", x + w - 72, by, 60, 20, null, "Done", MenuKit.OUTLINE, Draw.hit(mx, my, x + w - 72, by, 60, 20), true, 1f);
            String link = "App ID";
            int lw = tr.getWidth(link);
            boolean hv = Draw.hit(mx, my, x + w - 84 - lw, by + 4, lw, 12);
            Draw.text(ctx, tr, link, x + w - 84 - lw, by + 6, hv ? 0xFFFFFFFF : 0xFF6B7080, false);
        }
        super.render(ctx, mx, my, delta);
    }

    private void renderList(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int x = px(), w = pw(), ly = listY(), lh = listH();
        List<Row> rows = rows();
        UUID cur = Accounts.current();
        boolean can = Accounts.canSwitch();
        ctx.enableScissor(x + 10, ly, x + w - 10, ly + lh);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = ly + 4 + i * ROW_H - (int) scroll;
            if (ry + ROW_H < ly || ry > ly + lh) continue;
            boolean active = r.id().equals(cur);
            boolean hv = Draw.hit(mx, my, x + 12, ry, w - 24, ROW_H - 4) && my >= ly && my < ly + lh;
            float p = Draw.hoverProgress("acc_row" + r.id(), hv);
            Draw.panel(ctx, x + 12, ry, w - 24, ROW_H - 4, 6,
                    active ? Draw.withAlpha(Draw.lerpColor(0xFF1C1F2A, Draw.accent(), 0.16f), 0xE0) : Draw.lerpColor(0xA01A1C24, 0xD0262935, p),
                    active ? Draw.withAlpha(Draw.accent(), 0xB0) : Draw.lerpColor(0x26FFFFFF, 0x60FFFFFF, p));
            Identifier head = PlayerHeads.get(r.id());
            Draw.roundRect(ctx, x + 18, ry + 4, 18, 18, 3, 0xFF0C0D12);
            if (head != null) ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, x + 19, ry + 5, 0f, 0f, 16, 16, 32, 32, 32, 32, 0xFFFFFFFF);
            Draw.text(ctx, tr, Draw.ellipsize(tr, r.name(), w - 190), x + 42, ry + 5, 0xFFFFFFFF, false);
            String tag = r.launcher() ? "Launcher account" : "Saved on this PC";
            Draw.text(ctx, tr, tag, x + 42, ry + 15, 0xFF7C8194, false);

            int bx = x + w - 18;
            if (!r.launcher()) {
                boolean armed = r.id().equals(removeArmed) && System.currentTimeMillis() - removeArmedAt < 4000;
                String rl = armed ? "Sure?" : "Remove";
                int rw = tr.getWidth(rl) + 12;
                bx -= rw;
                boolean rh = Draw.hit(mx, my, bx, ry + 4, rw, 18);
                Draw.panel(ctx, bx, ry + 4, rw, 18, 5, rh || armed ? 0xC03A1C22 : 0x80181A22, rh || armed ? 0xA0FF6B6B : 0x30FFFFFF);
                Draw.centered(ctx, tr, rl, bx + rw / 2, ry + 9, armed ? 0xFFFF8A8A : 0xFFC9CCD6);
                bx -= 6;
            }
            if (active) {
                String a = "● Current";
                bx -= tr.getWidth(a);
                Draw.text(ctx, tr, a, bx, ry + 9, 0xFF6AF09A, false);
            } else {
                int sw = 52;
                bx -= sw;
                boolean sh = Draw.hit(mx, my, bx, ry + 4, sw, 18);
                MenuKit.flatButton(ctx, tr, "acc_sw" + r.id(), bx, ry + 4, sw, 18, null, "Switch", MenuKit.DARK, sh, can, 1f);
            }
        }
        ctx.disableScissor();
        if (rows.size() <= 1) {
            Draw.centered(ctx, tr, "Add another Microsoft account to switch to it.", x + w / 2, ly + 4 + rows.size() * ROW_H + 12, 0xFF8B90A0);
        }
        Draw.centered(ctx, tr, "Logins are encrypted on this PC - Tempest never sees them.", x + w / 2, py() + ph() - 62, 0xFF5E6375);
    }

    private void renderCode(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int x = px(), w = pw(), y = listY() + 6;
        Draw.centered(ctx, tr, "1. Open " + shortUrl() + " in your browser", x + w / 2, y + 6, 0xFFC9CCD6);
        Draw.centered(ctx, tr, "2. Enter this code and sign in", x + w / 2, y + 18, 0xFFC9CCD6);
        String code = Accounts.userCode();
        if (code == null) return;
        int cw = Math.min(w - 40, 200), cx = x + (w - cw) / 2, cy = y + 36;
        Draw.panel(ctx, cx, cy, cw, 44, 8, 0xE0181A22, Draw.withAlpha(Draw.accent(), 0xA0));
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x + w / 2f, cy + 13);
        ctx.getMatrices().scale(2.2f, 2.2f);
        Draw.spaced(ctx, tr, code, 0, 0, 0xFFFFFFFF, true, 1);
        ctx.getMatrices().popMatrix();
        int bw = 150, bx = x + (w - bw) / 2, by = cy + 54;
        MenuKit.flatButton(ctx, tr, "acc_open", bx, by, bw, 20, null, "Copy code & open page", MenuKit.GREEN, Draw.hit(mx, my, bx, by, bw, 20), true, 1f);
        long left = Math.max(0, (Accounts.codeExpires() - System.currentTimeMillis()) / 1000);
        Draw.centered(ctx, tr, "Code works for " + left / 60 + ":" + String.format("%02d", left % 60) + " more", x + w / 2, by + 28, 0xFF7C8194);
        Draw.centered(ctx, tr, "This window updates by itself when you're done.", x + w / 2, by + 40, 0xFF7C8194);
    }

    private String shortUrl() {
        String u = Accounts.verifyUrl();
        return u == null ? "microsoft.com/link" : u.replaceFirst("^https?://(www\\.)?", "");
    }

    private void renderSetup(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int x = px(), w = pw(), y = listY() + 4;
        String[] lines = {
                "One-time setup: Tempest needs your own Microsoft app ID",
                "to sign in to extra accounts (it isn't secret).",
                "",
                "1. portal.azure.com > App registrations > New registration",
                "2. Account types: \"Personal Microsoft accounts only\"",
                "3. Authentication > turn on \"Allow public client flows\"",
                "4. Copy the Application (client) ID and paste it below",
                "",
                "New app IDs must be approved by Mojang once:",
                "aka.ms/mce-reviewappid",
        };
        for (int i = 0; i < lines.length; i++) {
            int col = i < 2 ? 0xFFE4E6EC : i >= 8 ? 0xFFFFD27A : 0xFFB0B4C2;
            Draw.text(ctx, tr, Draw.ellipsize(tr, lines[i], w - 32), x + 16, y + i * 11, col, false);
        }
        int fy = py() + ph() - 88;
        if (!setupError.isEmpty()) Draw.centered(ctx, tr, setupError, x + w / 2, fy - 11, 0xFFFF8A8A);
        Draw.panel(ctx, x + 14, fy, w - 28, 18, 5, appId.isFocused() ? 0xE0202330 : 0xC0181A22,
                appId.isFocused() ? Draw.withAlpha(Draw.accent(), 0xA0) : 0x30FFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        int x = px(), y = py(), w = pw(), h = ph(), by = y + h - 30;
        if (setup) {
            if (Draw.hit(mx, my, x + w - 160, by, 62, 20)) {
                if (Accounts.clientId().isEmpty()) close();
                else { setup = false; appId.setText(Accounts.clientId()); }
                return true;
            }
            if (Draw.hit(mx, my, x + w - 92, by, 80, 20)) { saveId(); return true; }
            return false;
        }
        if (Draw.hit(mx, my, x + 12, by, 110, 20)) {
            if (Accounts.userCode() != null) Accounts.cancelAdd();
            else if (!Accounts.busy()) Accounts.add();
            return true;
        }
        if (Draw.hit(mx, my, x + w - 72, by, 60, 20)) { close(); return true; }
        int lw = textRenderer.getWidth("App ID");
        if (Draw.hit(mx, my, x + w - 84 - lw, by + 4, lw, 12)) {
            setup = true;
            clearAndInit();
            return true;
        }
        if (Accounts.userCode() != null) {
            int bw = 150, bx = x + (w - bw) / 2, bby = listY() + 6 + 36 + 54;
            if (Draw.hit(mx, my, bx, bby, bw, 20)) {
                client.keyboard.setClipboard(Accounts.userCode());
                String u = Accounts.verifyUrl();
                if (u != null && u.startsWith("https://")) Util.getOperatingSystem().open(u);
                return true;
            }
            return false;
        }
        int ly = listY(), lh = listH();
        if (!Draw.hit(mx, my, x + 10, ly, w - 20, lh)) return false;
        List<Row> rows = rows();
        int i = (int) ((my - ly - 4 + scroll) / ROW_H);
        if (i < 0 || i >= rows.size()) return false;
        Row r = rows.get(i);
        int ry = ly + 4 + i * ROW_H - (int) scroll;
        int bx = x + w - 18;
        if (!r.launcher()) {
            boolean armed = r.id().equals(removeArmed) && System.currentTimeMillis() - removeArmedAt < 4000;
            int rw = textRenderer.getWidth(armed ? "Sure?" : "Remove") + 12;
            bx -= rw;
            if (Draw.hit(mx, my, bx, ry + 4, rw, 18)) {
                if (armed) { Accounts.remove(r.id()); removeArmed = null; }
                else { removeArmed = r.id(); removeArmedAt = System.currentTimeMillis(); }
                return true;
            }
            bx -= 6;
        }
        if (!r.id().equals(Accounts.current()) && Draw.hit(mx, my, bx - 52, ry + 4, 52, 18) && Accounts.canSwitch()) {
            if (r.launcher()) Accounts.switchToLauncher();
            else Accounts.switchTo(r.saved());
            return true;
        }
        return false;
    }

    private void saveId() {
        String id = appId.getText().trim();
        if (id.isEmpty()) return;
        if (!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            setupError = "That doesn't look like an Application (client) ID.";
            return;
        }
        setupError = "";
        Accounts.setClientId(id);
        setup = false;
        clearAndInit();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        double max = Math.max(0, rows().size() * ROW_H + 8 - listH());
        scroll = Math.max(0, Math.min(max, scroll - vertical * ROW_H));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (setup && input.key() == GLFW.GLFW_KEY_ENTER) { saveId(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (Accounts.userCode() != null) Accounts.cancelAdd();
        client.setScreen(parent);
    }
}
