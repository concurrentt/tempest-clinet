package dev.tempest.gui;

import dev.tempest.util.Chat;
import dev.tempest.util.PlayerHeads;
import dev.tempest.util.Social;
import dev.tempest.util.TempestChat;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pick friends for Tempest chat: make a group (with a name), invite people to your party, or add people to a group.
 * Only friends are listed (the service only lets you chat with friends).
 */
public class ChatGroupScreen extends Screen {
    public enum Mode { CREATE, PARTY, ADD }

    private static final int ROW_H = 22;
    private final Mode mode;
    private final String groupId;
    private final Set<UUID> picked = new LinkedHashSet<>();
    private TextFieldWidget name;
    private double scroll;
    private String error = "";
    private boolean busy;

    public ChatGroupScreen(Mode mode, String groupId) {
        super(Text.literal("Tempest Chat"));
        this.mode = mode;
        this.groupId = groupId;
    }

    private int pw() { return Math.min(width - 24, 300); }
    private int ph() { return Math.min(height - 24, 280); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int listY() { return py() + (mode == Mode.CREATE ? 64 : 40); }
    private int listH() { return py() + ph() - 40 - listY(); }

    @Override
    protected void init() {
        Social.refreshIfStale();
        if (mode == Mode.CREATE) {
            String old = name != null ? name.getText() : "";
            name = new TextFieldWidget(textRenderer, px() + 16, py() + 42, pw() - 32, 12, Text.literal("Group name"));
            name.setDrawsBackground(false);
            name.setMaxLength(24);
            name.setPlaceholder(Text.literal("Group name, e.g. PvP Squad"));
            name.setText(old);
            addDrawableChild(name);
            setFocused(name);
            name.setFocused(true);
        }
    }

    /** Friends you can pick (people already in the group / party are left out). */
    private List<Social.Friend> candidates() {
        Set<UUID> already = new LinkedHashSet<>();
        TempestChat.Group g = mode == Mode.ADD ? TempestChat.group(groupId) : mode == Mode.PARTY ? TempestChat.party() : null;
        if (g != null) already.addAll(g.members());
        List<Social.Friend> out = new ArrayList<>();
        for (Social.Friend f : Social.friends()) if (!already.contains(f.id())) out.add(f);
        return out;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        ctx.fillGradient(0, 0, width, height, 0x90000000, 0x70000000);
        int x = px(), y = py(), w = pw(), h = ph();
        Draw.shadow(ctx, x, y, w, h, 10, 10);
        Draw.glass(ctx, x, y, w, h, 10, 0xE8101218, 0x40FFFFFF);
        String title = switch (mode) {
            case CREATE -> "New group chat";
            case PARTY -> TempestChat.party() == null ? "Start a party" : "Invite to your party";
            case ADD -> "Add to " + (TempestChat.group(groupId) == null ? "group" : TempestChat.group(groupId).name());
        };
        Draw.text(ctx, tr, Draw.bold(title), x + 14, y + 12, 0xFFFFFFFF, false);
        Draw.text(ctx, tr, "Pick friends", x + 14, y + 24, 0xFF8B90A0, false);
        if (mode == Mode.CREATE) {
            Draw.panel(ctx, x + 12, y + 38, w - 24, 18, 5, name.isFocused() ? 0xE0202330 : 0xC0181A22,
                    name.isFocused() ? Draw.withAlpha(Draw.accent(), 0xA0) : 0x30FFFFFF);
        }

        List<Social.Friend> fs = candidates();
        int ly = listY(), lh = listH();
        Draw.roundRect(ctx, x + 12, ly, w - 24, lh, 6, 0x70000000);
        if (fs.isEmpty()) {
            String msg = Social.friends().isEmpty() ? "No friends yet - add some in Social" : "All your friends are already in";
            Draw.centered(ctx, tr, msg, x + w / 2, ly + lh / 2 - 4, 0xFF8B90A0);
        }
        ctx.enableScissor(x + 12, ly, x + w - 12, ly + lh);
        for (int i = 0; i < fs.size(); i++) {
            Social.Friend f = fs.get(i);
            int ry = ly + 3 + i * ROW_H - (int) scroll;
            if (ry + ROW_H < ly || ry > ly + lh) continue;
            boolean on = picked.contains(f.id()), hv = Draw.hit(mx, my, x + 14, ry, w - 28, ROW_H - 2) && my >= ly && my < ly + lh;
            if (on || hv) Draw.roundRect(ctx, x + 14, ry, w - 28, ROW_H - 2, 5, on ? Draw.withAlpha(Draw.accent(), 0x60) : 0x30FFFFFF);
            Identifier head = PlayerHeads.get(f.id());
            if (head != null) ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, x + 20, ry + 2, 0f, 0f, 16, 16, 32, 32, 32, 32, 0xFFFFFFFF);
            else Draw.roundRect(ctx, x + 20, ry + 2, 16, 16, 3, 0x40FFFFFF);
            Draw.text(ctx, tr, Social.nameOf(f.id()), x + 42, ry + 6, 0xFFFFFFFF, false);
            String st = f.online() ? "Online" : "Offline";
            Draw.text(ctx, tr, st, x + w - 44 - Draw.textW(tr, st), ry + 6, f.online() ? 0xFF6AF09A : 0xFF6B7080, false);
            // tick box
            int bx = x + w - 34;
            Draw.panel(ctx, bx, ry + 4, 12, 12, 3, on ? Draw.accent() : 0xC0181A22, 0x60FFFFFF);
            if (on) Draw.centered(ctx, tr, "✔", bx + 6, ry + 6, 0xFFFFFFFF);
        }
        ctx.disableScissor();

        int by = y + h - 30;
        String ok = switch (mode) { case CREATE -> "Create"; case PARTY -> "Invite"; default -> "Add"; };
        boolean can = !picked.isEmpty() && !busy;
        MenuKit.flatButton(ctx, tr, "cg_ok", x + w - 92, by, 80, 20, null, busy ? "…" : ok + (picked.isEmpty() ? "" : " (" + picked.size() + ")"),
                MenuKit.GREEN, Draw.hit(mx, my, x + w - 92, by, 80, 20), can, 1f);
        MenuKit.flatButton(ctx, tr, "cg_cancel", x + w - 160, by, 62, 20, null, "Cancel", MenuKit.OUTLINE, Draw.hit(mx, my, x + w - 160, by, 62, 20), true, 1f);
        if (!error.isEmpty()) Draw.text(ctx, tr, Draw.ellipsize(tr, error, w - 180), x + 14, by + 6, 0xFFFF8A8A, false);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (super.mouseClicked(click, doubled)) return true;
        int x = px(), y = py(), w = pw(), h = ph();
        int by = y + h - 30;
        if (Draw.hit(mx, my, x + w - 160, by, 62, 20)) { close(); return true; }
        if (Draw.hit(mx, my, x + w - 92, by, 80, 20)) { submit(); return true; }
        int ly = listY(), lh = listH();
        if (Draw.hit(mx, my, x + 12, ly, w - 24, lh)) {
            List<Social.Friend> fs = candidates();
            int i = (int) ((my - ly - 3 + scroll) / ROW_H);
            if (i >= 0 && i < fs.size()) {
                UUID id = fs.get(i).id();
                if (!picked.remove(id)) picked.add(id);
                error = "";
            }
            return true;
        }
        return false;
    }

    private void submit() {
        if (picked.isEmpty() || busy) return;
        List<UUID> list = new ArrayList<>(picked);
        busy = true;
        java.util.function.Consumer<String> done = msg -> {
            busy = false;
            if (msg != null) { error = Character.toUpperCase(msg.charAt(0)) + msg.substring(1); return; }
            Chat.info(switch (mode) {
                case CREATE -> "Group \"" + name.getText().trim() + "\" made - open chat and click its tab.";
                case PARTY -> "Invited to your party - talk in the Party tab.";
                default -> "Added.";
            });
            close();
        };
        switch (mode) {
            case CREATE -> {
                String n = name.getText().trim();
                if (n.isEmpty()) { busy = false; error = "Give the group a name"; return; }
                TempestChat.createGroup(n, list, done);
            }
            case PARTY -> TempestChat.inviteToParty(list, done);
            default -> TempestChat.addToGroup(groupId, list, done);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        double max = Math.max(0, candidates().size() * ROW_H + 6 - listH());
        scroll = Math.max(0, Math.min(max, scroll - vertical * ROW_H));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER && mode == Mode.CREATE) { submit(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(null);
    }
}
