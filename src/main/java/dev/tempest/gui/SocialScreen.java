package dev.tempest.gui;

import dev.tempest.modules.ClientSettingsModule;
import dev.tempest.modules.FriendsModule;
import dev.tempest.util.PlayerHeads;
import dev.tempest.util.Social;
import dev.tempest.util.TempestNet;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Social menu, styled like the Tempest mods screen: a sidebar with your profile, sections and the glow switch, and a
 * list of friends (online first, with the server they're on), friend requests, or people to add.
 */
public class SocialScreen extends Screen {
    private static final int SIDE_W = 132, PAD = 8, HEAD_H = 40, ROW = 34, NAV_H = 24;
    private static final String[][] NAV = {{"people", "Friends"}, {"bell", "Requests"}, {"person_plus", "Add Friend"}};

    /** kind: 0 section header, 1 friend, 2 incoming request, 3 sent request, 4 nearby player */
    private record Row(int kind, UUID id, String text, Social.Friend friend) { }

    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private final Map<String, Float> anims = new HashMap<>();
    private int tab;
    private int pX, pY, pW, pH;
    private double scroll, scrollTarget, scrollVel;
    private long lastFrame = System.nanoTime();
    private float dt = 0.016f;
    private TextFieldWidget field;
    private String message;
    private boolean messageBad;
    private long messageAt;
    private UUID armedRemove;
    private long armedAt;

    public SocialScreen(Screen parent) {
        super(Text.literal("Social"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout
    @Override
    protected void init() {
        pW = Math.min(width - 24, 480);
        pH = Math.min(height - 24, 310);
        pX = (width - pW) / 2;
        pY = (height - pH) / 2;
        field = new TextFieldWidget(textRenderer, 0, 0, 10, 16, Text.literal("Search"));
        field.setMaxLength(16);
        field.setDrawsBackground(false);
        addDrawableChild(field);
        Social.markScreenOpen();
        Social.refresh();
        if (tab == 0 && Social.pendingCount() > 0 && Social.friends().isEmpty()) tab = 1;
        layoutField();
    }

    private int sideX() { return pX + PAD; }
    private int sideY() { return pY + PAD; }
    private int sideH() { return pH - 2 * PAD; }
    private int cX() { return pX + PAD + SIDE_W + 12; }
    private int cW() { return pX + pW - 14 - cX(); }
    private int cY() { return pY + PAD + HEAD_H; }
    private int listTop() { return cY() + (tab == 2 ? 34 : 6); }
    private int listH() { return pY + pH - PAD - 22 - listTop(); }

    /** The one text field: search box on Friends, name box on Add Friend, hidden on Requests. */
    private void layoutField() {
        if (field == null) return;
        if (tab == 0) {
            int w = Math.min(130, cW() / 2);
            field.setX(cX() + cW() - w + 18);
            field.setY(pY + PAD + 14);
            field.setWidth(w - 22);
            field.setPlaceholder(Text.literal("Search friends"));
            field.visible = true;
        } else if (tab == 2) {
            field.setX(cX() + 24);
            field.setY(cY() + 11);
            field.setWidth(cW() - 24 - 96);
            field.setPlaceholder(Text.literal("Minecraft name"));
            field.visible = true;
        } else {
            field.visible = false;
        }
    }

    private void setTab(int t) {
        if (t == tab) return;
        tab = t;
        scroll = scrollTarget = scrollVel = 0;
        field.setText("");
        layoutField();
        if (t == 2) setFocused(field);
        else if (getFocused() == field) setFocused(null);
        Social.refresh();
    }

    private void say(String msg, boolean bad) {
        message = msg;
        messageBad = bad;
        messageAt = System.currentTimeMillis();
    }

    private float anim(String key, boolean on, float speed) {
        float v = anims.getOrDefault(key, 0f);
        v += ((on ? 1f : 0f) - v) * (1f - (float) Math.exp(-dt * speed));
        anims.put(key, v);
        return v;
    }

    // ------------------------------------------------------------------ rows
    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                String q = field.getText().trim().toLowerCase(Locale.ROOT);
                List<Social.Friend> on = new ArrayList<>(), off = new ArrayList<>();
                for (Social.Friend f : Social.friends()) {
                    if (!q.isEmpty() && !Social.nameOf(f.id()).toLowerCase(Locale.ROOT).contains(q)) continue;
                    (f.online() ? on : off).add(f);
                }
                if (!on.isEmpty()) out.add(new Row(0, null, "ONLINE  —  " + on.size(), null));
                for (Social.Friend f : on) out.add(new Row(1, f.id(), null, f));
                if (!off.isEmpty()) out.add(new Row(0, null, "OFFLINE  —  " + off.size(), null));
                for (Social.Friend f : off) out.add(new Row(1, f.id(), null, f));
            }
            case 1 -> {
                if (!Social.incoming().isEmpty()) {
                    out.add(new Row(0, null, "INCOMING  —  " + Social.incoming().size(), null));
                    for (UUID id : Social.incoming()) out.add(new Row(2, id, null, null));
                }
                if (!Social.outgoing().isEmpty()) {
                    out.add(new Row(0, null, "SENT  —  " + Social.outgoing().size(), null));
                    for (UUID id : Social.outgoing()) out.add(new Row(3, id, null, null));
                }
            }
            default -> {
                List<Row> near = new ArrayList<>();
                if (client != null && client.world != null) {
                    for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
                        UUID id = p.getUuid();
                        if (p == client.player || id.version() != 4 || Social.isFriend(id) || Social.outgoing().contains(id)) continue;
                        near.add(new Row(4, id, p.getName().getString(), null));
                    }
                }
                if (!near.isEmpty()) {
                    out.add(new Row(0, null, "PLAYERS NEAR YOU  —  " + near.size(), null));
                    out.addAll(near);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        Social.markScreenOpen();
        Social.refreshIfStale();
        TextRenderer tr = textRenderer;

        float open = ClientSettingsModule.animationsOn() ? Math.min(1f, (System.currentTimeMillis() - openedAt) / 260f) : 1f;
        open = 1f - (1f - open) * (1f - open) * (1f - open);
        int baseY = pY;
        pY += Math.round((1f - open) * 14);
        layoutField();

        // backdrop, glow and the glass window
        if (client != null && client.world == null) Draw.menuBackground(ctx, width, height);
        ctx.fillGradient(0, 0, width, height, Draw.withAlpha(0x05070D, Math.round(0x70 * open)), Draw.withAlpha(0x05070D, Math.round(0x38 * open)));
        TempestLoading.drawGlow(ctx, pX + pW / 2, pY + pH / 2, Math.round(Math.max(pW, pH) * 1.7f), Draw.accent(), 0.16f * open);
        Draw.shadow(ctx, pX, pY, pW, pH, 11, 14);
        Draw.glass(ctx, pX, pY, pW, pH, 11, Draw.bg(), Draw.border());
        Draw.hairline(ctx, pX + 22, pY + 1, pW - 44, Draw.accent(), 0x70);

        renderSidebar(ctx, tr, mx, my);
        renderContentHeader(ctx, tr, mx, my);

        // list with smooth scrolling
        List<Row> rows = rows();
        double maxScroll = Math.max(0, rows.size() * ROW - listH());
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        stepScroll(maxScroll);
        int cx = cX(), cw = cW(), top = listTop(), lh = listH();
        ctx.enableScissor(cx - 2, top, cx + cw + 2, top + lh);
        for (int i = 0; i < rows.size(); i++) {
            int ry = top + i * ROW - (int) Math.round(scroll);
            if (ry + ROW < top || ry > top + lh) continue;
            drawRow(ctx, tr, rows.get(i), i, ry, mx, my);
        }
        ctx.disableScissor();
        if (maxScroll > 0) Draw.scrollbar(ctx, cx + cw + 5, top + 2, lh - 4, maxScroll, scroll);
        if (rows.isEmpty()) drawEmpty(ctx, tr);

        drawToast(ctx, tr);
        super.render(ctx, mx, my, delta);

        if (open < 1f) Draw.roundRect(ctx, pX + 1, pY + 1, pW - 2, pH - 2, 10, Draw.withAlpha(Draw.solid(), Math.round(0x90 * (1f - open))));
        pY = baseY;
    }

    private void stepScroll(double max) {
        final double omega = 18.0;
        double x = scroll - scrollTarget, e = Math.exp(-omega * dt), k = scrollVel + omega * x;
        scroll = scrollTarget + (x + k * dt) * e;
        scrollVel = (scrollVel - omega * k * dt) * e;
        if (scroll < 0) { scroll = 0; scrollVel = 0; }
        if (scroll > max) { scroll = max; scrollVel = 0; }
        if (Math.abs(scroll - scrollTarget) < 0.05 && Math.abs(scrollVel) < 0.5) { scroll = scrollTarget; scrollVel = 0; }
    }

    private void renderSidebar(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int sx = sideX(), sy = sideY(), sh = sideH();
        Draw.roundRect(ctx, sx, sy, SIDE_W, sh, 9, Draw.side());
        Draw.roundRectV(ctx, sx, sy, SIDE_W, 60, 7, Draw.withAlpha(Draw.accent(), 0x18), Draw.withAlpha(Draw.accent(), 0x00));

        // you
        UUID me = client != null && client.getSession() != null ? client.getSession().getUuidOrNull() : null;
        String myName = client != null && client.getSession() != null ? client.getSession().getUsername() : "You";
        drawHead(ctx, tr, me, myName, sx + 10, sy + 11, 28);
        boolean connected = TempestNet.signedIn();
        int dot = connected ? 0xFF43D17A : 0xFFFFB36B;
        Draw.circle(ctx, sx + 36, sy + 36, 4, Draw.solid());
        Draw.circle(ctx, sx + 36, sy + 36, 3, dot);
        Draw.text(ctx, tr, Draw.bold(Draw.trim(tr, myName, SIDE_W - 58)), sx + 46, sy + 14, Draw.TEXT, false);
        String st = connected ? "Online" : TempestNet.status() == null ? "Connecting…" : TempestNet.status();
        Draw.text(ctx, tr, Draw.trim(tr, st, SIDE_W - 54), sx + 46, sy + 26, connected ? 0xFF8EE3AE : 0xFFFFC48A, false);
        ctx.fill(sx + 12, sy + 50, sx + SIDE_W - 12, sy + 51, Draw.border());

        // sections
        int ny = sy + 58;
        int online = (int) Social.friends().stream().filter(Social.Friend::online).count();
        for (int i = 0; i < NAV.length; i++) {
            int y = ny + i * (NAV_H + 3);
            boolean sel = tab == i, over = Draw.hit(mx, my, sx + 6, y, SIDE_W - 12, NAV_H);
            float h = anim("nav_h" + i, over, 16f), s = anim("nav_s" + i, sel, 14f);
            int accent = Draw.accent();
            if (s > 0.01f) {
                Draw.roundRect(ctx, sx + 6, y, SIDE_W - 12, NAV_H, 6, Draw.withAlpha(accent, Math.round(0x2A * s)));
                Draw.roundRect(ctx, sx + 6, y + 6, 2, NAV_H - 12, 1, Draw.withAlpha(accent, Math.round(0xFF * s)));
            } else if (h > 0.01f) {
                Draw.roundRect(ctx, sx + 6, y, SIDE_W - 12, NAV_H, 6, Draw.withAlpha(0xFFFFFF, Math.round(0x0E * h)));
            }
            int mid = y + NAV_H / 2;
            int col = Draw.lerpColor(Draw.DIM, Draw.TEXT, Math.max(h * 0.8f, s));
            Icons.draw(ctx, NAV[i][0], sx + 14, mid - 5, 11, Draw.lerpColor(Draw.DIM2, accent, Math.max(h, s)));
            Draw.text(ctx, tr, NAV[i][1], sx + 31, mid - 4, col, false);
            // right side: online count / request badge
            if (i == 0 && Social.loaded()) {
                String c = online + "/" + Social.friends().size();
                int pw = Draw.textW(tr, c) + 9, px = sx + SIDE_W - 12 - pw;
                Draw.roundRect(ctx, px, mid - 6, pw, 12, 6, sel ? Draw.withAlpha(accent, 0x38) : Draw.header());
                Draw.text(ctx, tr, c, px + 5, mid - 4, sel ? accent : Draw.DIM2, false);
            } else if (i == 1 && !Social.incoming().isEmpty()) {
                String c = Social.incoming().size() > 9 ? "9+" : Integer.toString(Social.incoming().size());
                int pw = Math.max(12, Draw.textW(tr, c) + 8), px = sx + SIDE_W - 12 - pw;
                Draw.roundRect(ctx, px, mid - 6, pw, 12, 6, 0xFFE0475B);
                Draw.text(ctx, tr, c, px + (pw - Draw.textW(tr, c)) / 2, mid - 4, 0xFFFFFFFF, false);
            }
        }

        // bottom: friend glow switch + key hint
        int gy = sy + sh - 44;
        ctx.fill(sx + 12, gy - 6, sx + SIDE_W - 12, gy - 5, Draw.border());
        float g = anim("glow", FriendsModule.glowOn(), 14f);
        Draw.text(ctx, tr, "Friend glow", sx + 12, gy + 4, Draw.lerpColor(Draw.DIM, Draw.TEXT, g), false);
        Draw.toggle(ctx, sx + SIDE_W - 12 - 28, gy + 1, g);
        Draw.text(ctx, tr, "Press " + FriendsModule.keyName() + " to open", sx + 12, gy + 22, Draw.DIM2, false);
    }

    private int glowX() { return sideX() + SIDE_W - 12 - 28; }
    private int glowY() { return sideY() + sideH() - 44 + 1; }

    private void renderContentHeader(DrawContext ctx, TextRenderer tr, int mx, int my) {
        int x = cX(), y = pY + PAD + 8;
        String title = NAV[tab][1];
        String sub = switch (tab) {
            case 0 -> {
                int on = (int) Social.friends().stream().filter(Social.Friend::online).count();
                yield Social.loaded() ? on + " online · " + Social.friends().size() + " total" : "Loading…";
            }
            case 1 -> Social.incoming().size() + " waiting · " + Social.outgoing().size() + " sent";
            default -> "They'll show up under Requests until they accept";
        };
        Draw.text(ctx, tr, Draw.bold(title), x, y + 2, Draw.TEXT, false);
        Draw.text(ctx, tr, Draw.trim(tr, sub, cW() - (tab == 0 ? 140 : 0)), x, y + 14, Draw.DIM, false);

        if (tab == 0) {           // search box frame
            int w = Math.min(130, cW() / 2), bx = cX() + cW() - w, by = pY + PAD + 9;
            boolean f = field.isFocused();
            Draw.panel(ctx, bx, by, w, 18, 9, Draw.card(), f ? Draw.withAlpha(Draw.accent(), 0xB0) : Draw.border());
            Icons.draw(ctx, "search", bx + 6, by + 4, 10, f ? Draw.accent() : Draw.DIM2);
        }
        ctx.fill(x, cY() - 1, x + cW(), cY(), Draw.border());

        if (tab == 2) {           // name box + send button
            int bx = cX(), by = cY() + 4, w = cW() - 92;
            boolean f = field.isFocused();
            Draw.panel(ctx, bx, by, w, 22, 7, Draw.card(), f ? Draw.withAlpha(Draw.accent(), 0xB0) : Draw.border());
            Icons.draw(ctx, "person_plus", bx + 8, by + 6, 10, f ? Draw.accent() : Draw.DIM2);
            int sx = cX() + cW() - 86;
            Draw.button(ctx, tr, sx, by, 86, 22, "Send request", Draw.hit(mx, my, sx, by, 86, 22), Draw.accent() & 0xFFFFFF);
        }
    }

    private void drawEmpty(DrawContext ctx, TextRenderer tr) {
        String icon, title, sub;
        if (!TempestNet.signedIn() && !Social.loaded()) {
            icon = "refresh"; title = "Connecting to Tempest"; sub = TempestNet.status() == null ? "One moment…" : TempestNet.status();
        } else switch (tab) {
            case 0 -> {
                if (!field.getText().isBlank()) { icon = "search"; title = "No friends match"; sub = "Try another name"; }
                else { icon = "people"; title = "No friends yet"; sub = "Add people in the Add Friend tab"; }
            }
            case 1 -> { icon = "bell"; title = "No friend requests"; sub = "Requests you send or get show up here"; }
            default -> { icon = "person_plus"; title = "Add a friend"; sub = "Type their name above, or join a server to see players near you"; }
        }
        int cx = cX() + cW() / 2, cy = listTop() + Math.max(20, listH() / 2 - 34);
        Draw.circle(ctx, cx, cy + 14, 20, Draw.withAlpha(Draw.accent(), 0x1C));
        Icons.draw(ctx, icon, cx - 10, cy + 4, 20, Draw.withAlpha(Draw.accent(), 0xD0));
        Draw.centered(ctx, tr, title, cx, cy + 44, Draw.TEXT);
        Draw.centered(ctx, tr, Draw.trim(tr, sub, cW() - 20), cx, cy + 57, Draw.DIM);
    }

    private void drawToast(DrawContext ctx, TextRenderer tr) {
        if (message == null) return;
        long age = System.currentTimeMillis() - messageAt;
        if (age > 5000) return;
        float a = Math.min(1f, Math.min(age / 150f, (5000 - age) / 400f));
        String m = Draw.trim(tr, message, cW() - 30);
        int w = Draw.textW(tr, m) + 26, x = cX() + (cW() - w) / 2, y = pY + pH - PAD - 20;
        int edge = messageBad ? 0xFFFF6B6B : Draw.accent();
        Draw.panel(ctx, x, y, w, 18, 9, MenuKit.fa(Draw.withAlpha(Draw.solid(), 0xF0), a), MenuKit.fa(Draw.withAlpha(edge, 0xB0), a));
        Draw.circle(ctx, x + 10, y + 9, 3, MenuKit.fa(edge | 0xFF000000, a));
        Draw.text(ctx, tr, m, x + 18, y + 5, MenuKit.fa(Draw.TEXT, a), false);
    }

    private void drawHead(DrawContext ctx, TextRenderer tr, UUID id, String name, int x, int y, int size) {
        Identifier head = PlayerHeads.get(id);
        if (head != null) {
            Draw.roundRect(ctx, x - 1, y - 1, size + 2, size + 2, 5, 0xFF0C0D12);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, x, y, 0f, 0f, size, size, 32, 32, 32, 32, 0xFFFFFFFF);
        } else {
            MenuKit.tile(ctx, tr, x, y, size, name, 1f);
        }
    }

    private void drawRow(DrawContext ctx, TextRenderer tr, Row r, int i, int ry, int mx, int my) {
        int x = cX(), w = cW();
        if (r.kind() == 0) {
            if (r.text().startsWith("ONLINE")) Draw.circle(ctx, x + 3, ry + 18, 2, 0xFF43D17A);
            Draw.text(ctx, tr, r.text(), x + (r.text().startsWith("ONLINE") ? 9 : 2), ry + 15, Draw.DIM2, false);
            return;
        }
        int h = ROW - 4;
        boolean over = Draw.hit(mx, my, x, ry, w, h) && my >= listTop() && my < listTop() + listH();
        float hv = anim("row" + tab + ":" + r.id(), over, 16f);
        Draw.panel(ctx, x, ry, w, h, 8, Draw.lerpColor(Draw.card(), Draw.cardHover(), hv), Draw.lerpColor(Draw.border(), Draw.withAlpha(Draw.accent(), 0x90), hv));

        String name = r.text() != null ? r.text() : Social.nameOf(r.id());
        boolean online = r.friend() != null && r.friend().online();
        drawHead(ctx, tr, r.id(), name, x + 6, ry + 5, 20);
        if (r.kind() == 1) {
            Draw.circle(ctx, x + 25, ry + 24, 4, Draw.solid());
            Draw.circle(ctx, x + 25, ry + 24, 3, online ? 0xFF43D17A : 0xFF6B6F7E);
        }

        String sub;
        int subCol = Draw.DIM;
        switch (r.kind()) {
            case 1 -> {
                Social.Friend f = r.friend();
                sub = !f.online() ? "Offline" : f.server() == null ? "In menus" : f.server().equals("In game") ? "In game" : "Playing on " + f.server();
                if (f.online()) subCol = 0xFF8EE3AE;
            }
            case 2 -> sub = "Wants to be your friend";
            case 3 -> sub = "Request sent · waiting for them";
            default -> {
                boolean uses = TempestNet.isTempestUser(r.id());
                sub = uses ? "Uses Tempest" : "Doesn't use Tempest yet";
                if (uses) subCol = Draw.withAlpha(Draw.lerpColor(Draw.accent(), 0xFFFFFFFF, 0.35f), 0xFF);
            }
        }
        String[] b = buttons(r);
        int btnSpace = b.length * 66 + 6;
        Draw.text(ctx, tr, Draw.trim(tr, name, w - 36 - btnSpace), x + 32, ry + 6, online || r.kind() != 1 ? Draw.TEXT : Draw.lerpColor(Draw.DIM, Draw.TEXT, 0.5f), false);
        Draw.text(ctx, tr, Draw.trim(tr, sub, w - 36 - btnSpace), x + 32, ry + 17, subCol, false);

        // buttons: always on requests/add rows, on hover for friends (keeps the list calm)
        boolean showButtons = r.kind() != 1 || hv > 0.05f || r.id().equals(armedRemove);
        if (!showButtons) return;
        for (int k = 0; k < b.length; k++) {
            int bx = btnX(k, b.length), by = ry + 5;
            boolean bh = Draw.hit(mx, my, bx, by, 62, 16);
            if (k == 0 && (r.kind() == 2 || r.kind() == 4)) Draw.button(ctx, tr, bx, by, 62, 16, b[k], bh, Draw.accent() & 0xFFFFFF);
            else if (b[k].equals("Sure?")) Draw.button(ctx, tr, bx, by, 62, 16, b[k], bh, 0xE0475B);
            else Draw.ghost(ctx, tr, bx, by, 62, 16, b[k], bh);
        }
    }

    private String[] buttons(Row r) {
        return switch (r.kind()) {
            case 1 -> new String[]{r.id().equals(armedRemove) && System.currentTimeMillis() - armedAt < 3000 ? "Sure?" : "Remove"};
            case 2 -> new String[]{"Accept", "Decline"};
            case 3 -> new String[]{"Cancel"};
            case 4 -> new String[]{"Add"};
            default -> new String[0];
        };
    }

    /** Buttons sit at the right of a row; index 0 is leftmost. */
    private int btnX(int k, int count) { return cX() + cW() - 6 - (count - k) * 66 + 4; }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        double mx = click.x(), my = click.y();

        int sx = sideX(), ny = sideY() + 58;
        for (int i = 0; i < NAV.length; i++) {
            if (Draw.hit(mx, my, sx + 6, ny + i * (NAV_H + 3), SIDE_W - 12, NAV_H)) { setTab(i); return true; }
        }
        if (Draw.hit(mx, my, sx + 6, glowY() - 3, SIDE_W - 12, 20)) {
            FriendsModule.setGlow(!FriendsModule.glowOn());
            say(FriendsModule.glowOn() ? "Friends now glow" : "Friend glow off", false);
            return true;
        }
        if (tab == 2 && Draw.hit(mx, my, cX() + cW() - 86, cY() + 4, 86, 22)) { sendFromField(); return true; }

        if (my >= listTop() && my < listTop() + listH()) {
            List<Row> rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (r.kind() == 0) continue;
                int ry = listTop() + i * ROW - (int) Math.round(scroll);
                String[] b = buttons(r);
                for (int k = 0; k < b.length; k++) {
                    if (!Draw.hit(mx, my, btnX(k, b.length), ry + 5, 62, 16)) continue;
                    press(r, b[k]);
                    return true;
                }
            }
        }
        return false;
    }

    private void press(Row r, String label) {
        UUID id = r.id();
        switch (label) {
            case "Remove" -> { armedRemove = id; armedAt = System.currentTimeMillis(); }
            case "Sure?" -> { armedRemove = null; Social.remove(id, m -> say(m != null ? m : "Removed " + Social.nameOf(id), m != null)); }
            case "Accept" -> Social.accept(id, m -> say(m != null ? m : "You and " + Social.nameOf(id) + " are now friends!", m != null));
            case "Decline" -> Social.decline(id, m -> say(m != null ? m : "Request declined", m != null));
            case "Cancel" -> Social.decline(id, m -> say(m != null ? m : "Request cancelled", m != null));
            case "Add" -> Social.request(id, m -> say(m, m == null || !m.startsWith("Friend request sent") && !m.startsWith("They had")));
            default -> { }
        }
    }

    private void sendFromField() {
        String n = field.getText().trim();
        if (n.isEmpty()) return;
        if (!TempestNet.signedIn()) { say("Not connected yet - try again in a moment", true); return; }
        say("Looking up " + n + "…", false);
        Social.requestByName(n, m -> say(m, m == null || !m.startsWith("Friend request sent") && !m.startsWith("They had")));
        field.setText("");
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (tab == 2 && field.isFocused() && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            sendFromField();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = Math.max(0, Math.min(Math.max(0, rows().size() * ROW - listH()), scrollTarget - vertical * 40));
        return true;
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
