package dev.tempest.gui;

import dev.tempest.util.FriendManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Friends list, incoming requests and nearby players. */
public class FriendsScreen extends Screen {
    private static final int ROW = 24, HEAD = 40, FOOT = 40;
    private final Screen parent;
    private int pX, pY, pW, pH;
    private double scroll = 0, scrollTarget = 0;
    private long lastScrollFrame = System.nanoTime();
    private TextFieldWidget field;

    private record Row(String name, int type) { } // 0 header, 1 friend, 3 nearby

    public FriendsScreen(Screen parent) {
        super(Text.literal("Friends"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pW = Math.min(width - 20, 360);
        pH = Math.min(height - 20, 280);
        pX = (width - pW) / 2;
        pY = (height - pH) / 2;
        field = new TextFieldWidget(textRenderer, pX + 14, pY + pH - FOOT + 10, pW - 120, 20, Text.literal("Player name"));
        field.setMaxLength(16);
        field.setPlaceholder(Text.literal("Player name"));
        addDrawableChild(field);
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        out.add(new Row("Friends (" + FriendManager.friends().size() + ")", 0));
        for (String n : FriendManager.friends()) out.add(new Row(n, 1));
        List<Row> near = new ArrayList<>();
        if (client != null && client.world != null) {
            for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
                String n = p.getName().getString();
                if (p != client.player && !FriendManager.isFriend(n)) near.add(new Row(n, 3));
            }
        }
        if (!near.isEmpty()) {
            out.add(new Row("Nearby players", 0));
            out.addAll(near);
        }
        return out;
    }

    private int listTop() { return pY + HEAD + 4; }
    private int listH() { return pH - HEAD - FOOT - 8; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        Draw.window(ctx, tr, width, height, pX, pY, pW, pH, HEAD, "Friends", "Friends glow on your screen");

        List<Row> rows = rows();
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastScrollFrame) / 1_000_000_000f);
        lastScrollFrame = now;
        double maxScroll = Math.max(0, rows.size() * ROW - listH());
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * (1.0 - Math.exp(-dt * 13.0));
        if (Math.abs(scrollTarget - scroll) < 0.08) scroll = scrollTarget;
        ctx.enableScissor(pX + 1, listTop(), pX + pW - 1, listTop() + listH());
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = listTop() + i * ROW - (int) Math.round(scroll);
            if (ry + ROW < listTop() || ry > listTop() + listH()) continue;
            if (r.type() == 0) {
                Draw.text(ctx, tr, r.name(), pX + 16, ry + 8, Draw.accent(), false);
                continue;
            }
            float rowHover = Draw.hoverProgress("friends-row-" + i, Draw.hit(mx, my, pX + 12, ry, pW - 24, ROW - 3));
            Draw.panel(ctx, pX + 12, ry, pW - 24, ROW - 3, 6, Draw.lerpColor(Draw.card(), Draw.cardHover(), rowHover), Draw.lerpColor(Draw.border(), Draw.accent(), rowHover * 0.45f));
            Draw.text(ctx, tr, r.name(), pX + 22, ry + 7, Draw.TEXT, false);
            if (r.type() == 1) button(ctx, tr, mx, my, ry, 1, "Remove", 0xE0475B);
            if (r.type() == 3) button(ctx, tr, mx, my, ry, 1, "Add", 0x38D9A0);
        }
        ctx.disableScissor();
        Draw.scrollbar(ctx, pX + pW - 7, listTop(), listH(), Math.max(0, rows.size() * ROW - listH()), scroll);
        if (rows.size() <= 1) Draw.centered(ctx, tr, "No friends yet", pX + pW / 2, listTop() + 40, Draw.DIM);

        ctx.fill(pX + 1, pY + pH - FOOT, pX + pW - 1, pY + pH - FOOT + 1, Draw.border());
        Draw.button(ctx, tr, pX + pW - 98, pY + pH - FOOT + 10, 84, 20, "Add Friend", Draw.hit(mx, my, pX + pW - 98, pY + pH - FOOT + 10, 84, 20), Draw.accent() & 0xFFFFFF);
        super.render(ctx, mx, my, delta);
    }

    private void button(DrawContext ctx, TextRenderer tr, int mx, int my, int ry, int slot, String label, int rgb) {
        int bw = 52, bx = btnX(slot);
        Draw.button(ctx, tr, bx, ry + 2, bw, ROW - 7, label, Draw.hit(mx, my, bx, ry + 2, bw, ROW - 7), rgb);
    }

    private int btnX(int slot) { return pX + pW - 24 - slot * 56 + 8; }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();
        if (Draw.hit(mx, my, pX + pW - 98, pY + pH - FOOT + 10, 84, 20)) {
            String n = field.getText().trim();
            if (!n.isEmpty()) { FriendManager.addFriend(n); field.setText(""); }
            return true;
        }
        if (my >= listTop() && my < listTop() + listH()) {
            List<Row> rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (r.type() == 0) continue;
                int ry = listTop() + i * ROW - (int) Math.round(scroll);
                if (Draw.hit(mx, my, btnX(1), ry + 2, 52, ROW - 7)) {
                    switch (r.type()) {
                        case 1 -> FriendManager.removeFriend(r.name());
                        case 3 -> FriendManager.addFriend(r.name());
                        default -> { }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = Math.max(0, Math.min(Math.max(0, rows().size() * ROW - listH()), scrollTarget - vertical * 32));
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
