package dev.tempest.gui;

import dev.tempest.util.PlayerHeads;
import dev.tempest.util.Social;
import dev.tempest.util.TempestNet;
import dev.tempest.util.TierLists;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Player Card (middle click a player): their face and name, whether they use Tempest, their MCTiers / NovaTiers
 * tiers, and Add Friend / Message buttons. Esc or clicking outside closes it.
 */
public class PlayerCardScreen extends Screen {
    private static final int W = 220, H = 150;
    private final UUID id;
    private final String name;
    private String note = "";

    public PlayerCardScreen(UUID id, String name) {
        super(Text.literal(name));
        this.id = id;
        this.name = name;
    }

    @Override public boolean shouldPause() { return false; }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    private boolean friend() { return Social.isFriend(id); }

    private String button() {
        if (friend()) return "Message";
        if (id.version() != 4) return null;                         // not a Microsoft account: can't be added
        return "Add Friend";
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        int x = x0(), y = y0();
        Draw.shadow(ctx, x, y, W, H, 10, 12);
        Draw.panel(ctx, x, y, W, H, 8, 0xF0121318, Draw.border());

        Identifier head = PlayerHeads.get(id);
        if (head != null) ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, x + 12, y + 12, 0f, 0f, 32, 32, 32, 32, 32, 32, 0xFFFFFFFF);
        Draw.text(ctx, tr, name, x + 52, y + 15, Draw.TEXT, false);
        boolean tempest = TempestNet.isTempestUser(id) || friend();
        String sub = friend() ? "Tempest friend" : tempest ? "Uses Tempest" : "Not on Tempest";
        Draw.text(ctx, tr, sub, x + 52, y + 29, tempest ? 0xFF8E7CFF : Draw.DIM, false);
        if (tempest) Icons.draw(ctx, "cloud", x + W - 26, y + 12, 14, 0xFF8E7CFF);

        // tiers
        int ty = y + 56;
        ctx.fill(x + 10, ty - 6, x + W - 10, ty - 5, Draw.border());
        ty = tierLine(ctx, tr, "MCTiers", TierLists.get(TierLists.Source.MCTIERS, id), x + 12, ty);
        tierLine(ctx, tr, "NovaTiers", TierLists.get(TierLists.Source.NOVA, id), x + 12, ty);

        // buttons
        String b = button();
        int by = y + H - 28;
        if (b != null) Draw.button(ctx, tr, x + 12, by, 96, 18, b, Draw.hit(mx, my, x + 12, by, 96, 18), Draw.accent() & 0xFFFFFF);
        Draw.button(ctx, tr, x + W - 72, by, 60, 18, "Close", Draw.hit(mx, my, x + W - 72, by, 60, 18), 0x6B6F7E);
        if (!note.isEmpty()) Draw.text(ctx, tr, Draw.trim(tr, note, W - 24), x + 12, by - 14, Draw.DIM, false);
        super.render(ctx, mx, my, delta);
    }

    private int tierLine(DrawContext ctx, TextRenderer tr, String label, TierLists.Profile p, int x, int y) {
        Draw.text(ctx, tr, label, x, y, Draw.DIM, false);
        String text;
        if (p == null) text = "Loading…";
        else if (p.ranks().isEmpty()) text = "Not ranked";
        else {
            StringBuilder sb = new StringBuilder();
            p.ranks().entrySet().stream().sorted((a, b) -> Integer.compare(a.getValue().score(), b.getValue().score())).limit(4)
                    .forEach(e -> sb.append(sb.length() > 0 ? "  " : "").append(e.getValue().label()).append(' ').append(e.getKey()));
            text = sb.toString();
        }
        Draw.text(ctx, tr, Draw.trim(tr, text, W - 90), x + 62, y, Draw.TEXT, false);
        return y + 14;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int x = x0(), y = y0(), by = y + H - 28;
        if (!Draw.hit(mx, my, x, y, W, H) || Draw.hit(mx, my, x + W - 72, by, 60, 18)) { close(); return true; }
        String b = button();
        if (b != null && Draw.hit(mx, my, x + 12, by, 96, 18)) {
            if (b.equals("Message")) {
                close();
                if (!dev.tempest.modules.ChatTabsModule.openPrivate(id)) dev.tempest.util.Chat.info("Turn on Chat Tabs to message friends.");
            } else if (!TempestNet.signedIn()) {
                note = "Not connected to Tempest yet - try again in a moment";
            } else {
                note = "Sending…";
                Social.request(id, msg -> note = msg == null ? "Friend request sent" : msg);
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }
}
