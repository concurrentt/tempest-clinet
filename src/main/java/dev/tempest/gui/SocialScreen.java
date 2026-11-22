package dev.tempest.gui;

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
import java.util.List;
import java.util.UUID;

/** Social menu: Tempest friends with online status and server, friend requests, and adding people. */
public class SocialScreen extends Screen {
    private static final int HEAD = 40, TABS_H = 26, FOOT = 22, ROW = 32;
    private static final String[] TABS = {"Friends", "Requests", "Add"};

    /** A list row: header text, or a player with up to two buttons. kind: 0 header, 1 friend, 2 incoming, 3 outgoing, 4 nearby */
    private record Row(int kind, UUID id, String text, Social.Friend friend) { }

    private final Screen parent;
    private int tab;
    private int pX, pY, pW, pH;
    private double scroll, scrollTarget;
    private long lastFrame = System.nanoTime();
    private TextFieldWidget field;
    private String message;
    private long messageAt;
    private UUID armedRemove;
    private long armedAt;

    public SocialScreen(Screen parent) {
        super(Text.literal("Social"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pW = Math.min(width - 20, 400);
        pH = Math.min(height - 20, 300);
        pX = (width - pW) / 2;
        pY = (height - pH) / 2;
        field = new TextFieldWidget(textRenderer, pX + 14, contentTop() + 4, pW - 28 - 90, 20, Text.literal("Player name"));
        field.setMaxLength(16);
        field.setPlaceholder(Text.literal("Minecraft name"));
        addDrawableChild(field);
        field.visible = tab == 2;
        Social.markScreenOpen();
        Social.refresh();
        if (Social.pendingCount() > 0 && Social.friends().isEmpty()) tab = 1;
        field.visible = tab == 2;
    }

    private int contentTop() { return pY + HEAD + TABS_H + 4; }
    private int listTop() { return tab == 2 ? contentTop() + 34 : contentTop(); }
    private int listH() { return pY + pH - FOOT - listTop() - 4; }

    private void say(String msg) {
        message = msg;
        messageAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ rows
    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                List<Social.Friend> fs = Social.friends();
                long on = fs.stream().filter(Social.Friend::online).count();
                if (!fs.isEmpty()) out.add(new Row(0, null, "ONLINE — " + on, null));
                boolean offHeader = false;
                for (Social.Friend f : fs) {
                    if (!f.online() && !offHeader) { out.add(new Row(0, null, "OFFLINE — " + (fs.size() - on), null)); offHeader = true; }
                    out.add(new Row(1, f.id(), null, f));
                }
            }
            case 1 -> {
                if (!Social.incoming().isEmpty()) {
                    out.add(new Row(0, null, "WANT TO BE YOUR FRIEND", null));
                    for (UUID id : Social.incoming()) out.add(new Row(2, id, null, null));
                }
                if (!Social.outgoing().isEmpty()) {
                    out.add(new Row(0, null, "SENT", null));
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
                    out.add(new Row(0, null, "PLAYERS NEAR YOU", null));
                    out.addAll(near);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        Social.markScreenOpen();
        Social.refreshIfStale();
        int online = (int) Social.friends().stream().filter(Social.Friend::online).count();
        Draw.window(ctx, tr, width, height, pX, pY, pW, pH, HEAD, "SOCIAL",
                Social.loaded() ? online + " of " + Social.friends().size() + " friends online" : "Tempest friends");

        // tabs
        int tx = pX + 12, ty = pY + HEAD + 6;
        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i];
            int badge = i == 1 ? Social.incoming().size() : 0;
            int w = tr.getWidth(label) + 20 + (badge > 0 ? 14 : 0);
            boolean sel = tab == i, hv = Draw.hit(mx, my, tx, ty, w, 18);
            float p = Draw.hoverProgress("social_tab" + i, hv || sel);
            Draw.roundRect(ctx, tx, ty, w, 18, 9, sel ? Draw.withAlpha(Draw.accent(), 0x40) : Draw.withAlpha(0xFFFFFF, Math.round(0x10 * p)));
            Draw.text(ctx, tr, label, tx + 10, ty + 5, sel ? 0xFFFFFFFF : Draw.lerpColor(Draw.DIM, Draw.TEXT, p), false);
            if (badge > 0) {
                int bx = tx + 14 + tr.getWidth(label);
                Draw.roundRect(ctx, bx, ty + 4, 11, 10, 5, 0xFFE0475B);
                String b = badge > 9 ? "9+" : Integer.toString(badge);
                Draw.text(ctx, tr, b, bx + 6 - tr.getWidth(b) / 2, ty + 5, 0xFFFFFFFF, false);
            }
            tx += w + 6;
        }

        // add tab: name field + send button
        if (tab == 2) {
            int bx = pX + pW - 14 - 84, by = contentTop() + 4;
            Draw.button(ctx, tr, bx, by, 84, 20, "Send request", Draw.hit(mx, my, bx, by, 84, 20), Draw.accent() & 0xFFFFFF);
        }

        // list
        List<Row> rows = rows();
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        double maxScroll = Math.max(0, rows.size() * ROW - listH());
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * (1.0 - Math.exp(-dt * 13.0));
        if (Math.abs(scrollTarget - scroll) < 0.08) scroll = scrollTarget;

        ctx.enableScissor(pX + 1, listTop(), pX + pW - 1, listTop() + listH());
        for (int i = 0; i < rows.size(); i++) {
            int ry = listTop() + i * ROW - (int) Math.round(scroll);
            if (ry + ROW < listTop() || ry > listTop() + listH()) continue;
            drawRow(ctx, tr, rows.get(i), i, ry, mx, my);
        }
        ctx.disableScissor();
        Draw.scrollbar(ctx, pX + pW - 7, listTop(), listH(), maxScroll, scroll);
        if (rows.isEmpty()) Draw.centered(ctx, tr, emptyText(), pX + pW / 2, listTop() + 40, Draw.DIM);

        // footer: connection status or the last action's result
        String status = TempestNet.status();
        String foot = message != null && System.currentTimeMillis() - messageAt < 6000 ? message : status;
        if (foot != null) {
            int col = foot == status ? 0xFFFFB36B : Draw.TEXT;
            Draw.text(ctx, tr, Draw.ellipsize(tr, foot, pW - 28), pX + 14, pY + pH - FOOT + 6, col, false);
        } else {
            Draw.text(ctx, tr, "Friends see when you're online and which server you're on (change in Social settings).", pX + 14,
                    pY + pH - FOOT + 6, Draw.DIM2, false);
        }
        super.render(ctx, mx, my, delta);
    }

    private String emptyText() {
        if (!TempestNet.signedIn() && !Social.loaded()) return "Connecting to Tempest…";
        return switch (tab) {
            case 0 -> "No friends yet - add some in the Add tab";
            case 1 -> "No friend requests";
            default -> "Type a name above, or join a server to see players near you";
        };
    }

    private void drawRow(DrawContext ctx, TextRenderer tr, Row r, int i, int ry, int mx, int my) {
        if (r.kind() == 0) {
            Draw.text(ctx, tr, r.text(), pX + 16, ry + 14, Draw.DIM2, false);
            return;
        }
        int x = pX + 12, w = pW - 24, h = ROW - 4;
        float hover = Draw.hoverProgress("social_row" + tab + ":" + i, Draw.hit(mx, my, x, ry, w, h));
        Draw.panel(ctx, x, ry, w, h, 7, Draw.lerpColor(Draw.card(), Draw.cardHover(), hover), Draw.lerpColor(Draw.border(), Draw.accent(), hover * 0.4f));

        // head
        Identifier head = PlayerHeads.get(r.id());
        if (head != null) ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, x + 5, ry + 4, 0f, 0f, 20, 20, 32, 32, 32, 32, 0xFFFFFFFF);
        else Draw.roundRect(ctx, x + 5, ry + 4, 20, 20, 4, 0xFF2A2D38);

        String name = r.text() != null ? r.text() : Social.nameOf(r.id());
        String sub;
        int subCol = Draw.DIM;
        switch (r.kind()) {
            case 1 -> {
                Social.Friend f = r.friend();
                Draw.roundRect(ctx, x + 20, ry + 19, 7, 7, 3, 0xFF15161C);
                Draw.roundRect(ctx, x + 21, ry + 20, 5, 5, 2, f.online() ? 0xFF43D17A : 0xFF6B6F7E);
                sub = !f.online() ? "Offline" : f.server() == null ? "In menus" : f.server().equals("In game") ? "In game" : "Playing on " + f.server();
                if (f.online()) subCol = 0xFF8EE3AE;
            }
            case 2 -> sub = "Sent you a friend request";
            case 3 -> sub = "Waiting for them to accept";
            default -> sub = TempestNet.isTempestUser(r.id()) ? "Uses Tempest" : "Doesn't use Tempest yet";
        }
        Draw.text(ctx, tr, name, x + 32, ry + 5, Draw.TEXT, false);
        Draw.text(ctx, tr, Draw.ellipsize(tr, sub, w - 32 - 140), x + 32, ry + 16, subCol, false);

        String[] b = buttons(r);
        for (int k = 0; k < b.length; k++) {
            int bx = btnX(k, b.length), by = ry + 5;
            boolean hv = Draw.hit(mx, my, bx, by, 62, 18);
            if (k == 0 && (r.kind() == 2 || r.kind() == 4)) Draw.button(ctx, tr, bx, by, 62, 18, b[k], hv, Draw.accent() & 0xFFFFFF);
            else if (b[k].equals("Sure?")) Draw.button(ctx, tr, bx, by, 62, 18, b[k], hv, 0xE0475B);
            else Draw.ghost(ctx, tr, bx, by, 62, 18, b[k], hv);
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
    private int btnX(int k, int count) { return pX + pW - 12 - 6 - (count - k) * 66 + 4; }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        double mx = click.x(), my = click.y();
        TextRenderer tr = textRenderer;

        int tx = pX + 12, ty = pY + HEAD + 6;
        for (int i = 0; i < TABS.length; i++) {
            int badge = i == 1 ? Social.incoming().size() : 0;
            int w = tr.getWidth(TABS[i]) + 20 + (badge > 0 ? 14 : 0);
            if (Draw.hit(mx, my, tx, ty, w, 18)) { setTab(i); return true; }
            tx += w + 6;
        }

        if (tab == 2 && Draw.hit(mx, my, pX + pW - 14 - 84, contentTop() + 4, 84, 20)) { sendFromField(); return true; }

        if (my >= listTop() && my < listTop() + listH()) {
            List<Row> rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (r.kind() == 0) continue;
                int ry = listTop() + i * ROW - (int) Math.round(scroll);
                String[] b = buttons(r);
                for (int k = 0; k < b.length; k++) {
                    if (!Draw.hit(mx, my, btnX(k, b.length), ry + 5, 62, 18)) continue;
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
            case "Sure?" -> { armedRemove = null; Social.remove(id, m -> say("Removed " + Social.nameOf(id))); }
            case "Accept" -> Social.accept(id, m -> say(m != null ? m : "You and " + Social.nameOf(id) + " are now friends!"));
            case "Decline" -> Social.decline(id, m -> say(m != null ? m : "Declined"));
            case "Cancel" -> Social.decline(id, m -> say(m != null ? m : "Request cancelled"));
            case "Add" -> Social.request(id, this::say);
            default -> { }
        }
    }

    private void sendFromField() {
        String n = field.getText().trim();
        if (n.isEmpty()) return;
        if (!TempestNet.signedIn()) { say("Not connected yet - try again in a moment"); return; }
        say("Looking up " + n + "…");
        Social.requestByName(n, m -> say(m));
        field.setText("");
    }

    private void setTab(int t) {
        tab = t;
        scroll = scrollTarget = 0;
        field.visible = t == 2;
        if (t == 2) setFocused(field);
        else if (getFocused() == field) setFocused(null);
        Social.refresh();
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
        scrollTarget = Math.max(0, Math.min(Math.max(0, rows().size() * ROW - listH()), scrollTarget - vertical * 32));
        return true;
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
