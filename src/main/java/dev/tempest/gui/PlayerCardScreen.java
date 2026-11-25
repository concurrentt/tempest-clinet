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
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Player Card (middle click a player): the player standing on the left (their real model, turning to follow your
 * mouse), their name and whether they use Tempest, then their tiers the way tier sites show them - a round icon
 * per game mode with the tier underneath (MCTiers: every mode, unranked ones dimmed; NovaTiers: ranked modes).
 * Add Friend / Message and Close at the bottom. Esc or clicking outside closes it.
 */
public class PlayerCardScreen extends Screen {
    private static final int MODEL_W = 96, SLOT = 30, CIRCLE = 22, PAD = 12;
    /** MCTiers modes in the order mctiers.net shows them: {api id, name}. */
    private static final String[][] MC_MODES = {{"vanilla", "Vanilla"}, {"uhc", "UHC"}, {"pot", "Pot"}, {"nethop", "NethPot"},
            {"smp", "SMP"}, {"sword", "Sword"}, {"axe", "Axe"}, {"mace", "Mace"}, {"spear", "Spear"}};

    private final UUID id;
    private final String name;
    private String note = "";
    private String hoverTip = null;

    public PlayerCardScreen(UUID id, String name) {
        super(Text.literal(name));
        this.id = id;
        this.name = name;
    }

    @Override public boolean shouldPause() { return false; }

    private int cardW() { return Math.min(width - 16, MODEL_W + PAD + MC_MODES.length * SLOT + PAD + 4); }
    private int cardH() { return Math.min(height - 16, 188); }
    private int x0() { return (width - cardW()) / 2; }
    private int y0() { return (height - cardH()) / 2; }
    private int infoX() { return x0() + MODEL_W + PAD; }

    private boolean friend() { return Social.isFriend(id); }

    private String button() {
        if (friend()) return "Message";
        if (id.version() != 4) return null;                         // not a Microsoft account: can't be added
        return "Add Friend";
    }

    private AbstractClientPlayerEntity entity() {
        if (client == null || client.world == null) return null;
        for (AbstractClientPlayerEntity p : client.world.getPlayers()) if (p.getUuid().equals(id)) return p;
        return null;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        hoverTip = null;
        int x = x0(), y = y0(), w = cardW(), h = cardH();
        Draw.shadow(ctx, x, y, w, h, 12, 14);
        Draw.glass(ctx, x, y, w, h, 12, 0xEC101218, 0x40FFFFFF);

        // left: the player standing on a soft spotlight
        int mxL = x + 6, mw = MODEL_W - 6;
        Draw.roundRectV(ctx, mxL, y + 6, mw, h - 12, 9, Draw.withAlpha(Draw.accent(), 0x22), 0x08FFFFFF);
        TempestLoading.drawGlow(ctx, mxL + mw / 2, y + h - 36, 34, Draw.accent() & 0xFFFFFF, 0.22f);
        AbstractClientPlayerEntity p = entity();
        if (p != null) {
            InventoryScreen.drawEntity(ctx, mxL, y + 14, mxL + mw, y + h - 26, Math.min(62, (h - 40) / 2), 0.0625f, mx, my, p);
        } else {
            Identifier head = PlayerHeads.get(id);
            int hs = 48, hx = mxL + (mw - hs) / 2, hy = y + (h - hs) / 2 - 8;
            Draw.roundRect(ctx, hx - 2, hy - 2, hs + 4, hs + 4, 6, 0xFF0C0D12);
            if (head != null) ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, hx, hy, 0f, 0f, hs, hs, 32, 32, 32, 32, 0xFFFFFFFF);
        }
        ctx.fill(x + MODEL_W + 2, y + 12, x + MODEL_W + 3, y + h - 12, 0x18FFFFFF);

        // right: name and status
        int ix = infoX(), iw = x + w - PAD - ix;
        Draw.text(ctx, tr, Draw.bold(Draw.trim(tr, name, iw - 24)), ix, y + 12, 0xFFFFFFFF, false);
        boolean tempest = TempestNet.isTempestUser(id) || friend();
        String sub = friend() ? "Tempest friend" : tempest ? "Uses Tempest" : "Not on Tempest";
        int sw = Draw.textW(tr, sub) + 14;
        Draw.panel(ctx, ix, y + 25, sw, 13, 6, tempest ? 0x402A2350 : 0x30202430, tempest ? 0x808E7CFF : 0x30FFFFFF);
        Draw.text(ctx, tr, sub, ix + 7, y + 28, tempest ? 0xFFB7AAFF : 0xFF8B90A0, false);
        if (tempest) Icons.draw(ctx, "cloud", x + w - PAD - 14, y + 12, 14, 0xFF8E7CFF);

        // tiers
        int ty = y + 48;
        ty = tierSection(ctx, tr, "MCTIERS", TierLists.get(TierLists.Source.MCTIERS, id), true, ix, ty, iw, mx, my);
        tierSection(ctx, tr, "NOVATIERS", TierLists.get(TierLists.Source.NOVA, id), false, ix, ty + 4, iw, mx, my);

        // buttons
        String b = button();
        int by = y + h - 28;
        if (b != null) MenuKit.flatButton(ctx, tr, "card_main", ix, by, 96, 18, null, b, MenuKit.GREEN, Draw.hit(mx, my, ix, by, 96, 18), true, 1f);
        MenuKit.flatButton(ctx, tr, "card_close", x + w - PAD - 60, by, 60, 18, null, "Close", MenuKit.OUTLINE, Draw.hit(mx, my, x + w - PAD - 60, by, 60, 18), true, 1f);
        if (!note.isEmpty()) Draw.text(ctx, tr, Draw.trim(tr, note, iw), ix, by - 12, Draw.DIM, false);
        super.render(ctx, mx, my, delta);
        if (hoverTip != null) {
            int tw = Draw.textW(tr, hoverTip) + 10;
            int tx = Math.min(mx + 8, width - tw - 4), tyy = my - 18;
            Draw.panel(ctx, tx, tyy, tw, 14, 4, 0xF0181A22, 0x50FFFFFF);
            Draw.text(ctx, tr, hoverTip, tx + 5, tyy + 3, 0xFFFFFFFF, false);
        }
    }

    /** A label and a row of round mode icons with tier badges. Returns the y below it. */
    private int tierSection(DrawContext ctx, TextRenderer tr, String label, TierLists.Profile prof, boolean allModes,
                            int x, int y, int w, int mx, int my) {
        Draw.text(ctx, tr, label, x, y, 0xFF8B90A0, false);
        int top = y + 12;
        int boxH = CIRCLE + 22;
        Draw.panel(ctx, x - 4, top - 2, w + 8, boxH, 7, 0x60101218, 0x24FFFFFF);
        if (prof == null) {
            Draw.text(ctx, tr, "Loading…", x + 4, top + boxH / 2 - 6, 0xFF6B7080, false);
            return top + boxH;
        }
        List<String[]> modes = new ArrayList<>();
        if (allModes) {
            for (String[] m : MC_MODES) modes.add(m);
        } else {
            prof.ranks().entrySet().stream()
                    .sorted((a, b) -> Integer.compare(a.getValue().score(), b.getValue().score()))
                    .forEach(e -> modes.add(new String[]{e.getKey(), pretty(e.getKey())}));
            if (modes.isEmpty()) {
                Draw.text(ctx, tr, "Not ranked", x + 4, top + boxH / 2 - 6, 0xFF6B7080, false);
                return top + boxH;
            }
        }
        int fit = Math.max(1, (w + 4) / SLOT);
        for (int i = 0; i < Math.min(fit, modes.size()); i++) {
            String[] m = modes.get(i);
            TierLists.Rank r = prof.in(m[0]);
            int sx = x + i * SLOT, cx = sx + (SLOT - CIRCLE) / 2 - 2, cy = top + 1;
            boolean hv = Draw.hit(mx, my, sx - 2, top, SLOT, boxH - 2);
            if (r != null) {
                int col = 0xFF000000 | (r.retired() ? 0xA2D6FF : tierColor(r.level(), r.high()));
                Draw.roundRect(ctx, cx, cy, CIRCLE, CIRCLE, CIRCLE / 2, 0xFF1A1C24);
                Draw.ring(ctx, cx, cy, CIRCLE, CIRCLE, CIRCLE / 2, Draw.withAlpha(col, hv ? 0xFF : 0xB0));
                ctx.drawItem(new ItemStack(item(m[0])), cx + 3, cy + 3);
                String t = (r.retired() ? "R" : "") + r.label();
                int bw = Math.max(CIRCLE + 2, Draw.textW(tr, t) + 6), bx = cx + CIRCLE / 2 - bw / 2;
                Draw.roundRect(ctx, bx, cy + CIRCLE + 2, bw, 11, 4, Draw.withAlpha(col, 0xE0));
                Draw.centered(ctx, tr, t, cx + CIRCLE / 2, cy + CIRCLE + 4, r.level() <= 2 ? 0xFF101218 : 0xFFFFFFFF);
                if (hv) hoverTip = m[1] + " · " + t + (r.peakLevel() > 0 && r.peakScore() < r.score() ? "  (peak " + r.peakLabel() + ")" : "");
            } else {
                Draw.roundRect(ctx, cx, cy, CIRCLE, CIRCLE, CIRCLE / 2, 0x40101218);
                Draw.ring(ctx, cx, cy, CIRCLE, CIRCLE, CIRCLE / 2, hv ? 0x70FFFFFF : 0x30FFFFFF);
                if (hv) {
                    ctx.drawItem(new ItemStack(item(m[0])), cx + 3, cy + 3);
                    hoverTip = m[1] + " · not ranked";
                }
                Draw.roundRect(ctx, cx + 1, cy + CIRCLE + 2, CIRCLE - 2, 11, 4, 0x40202430);
                Draw.centered(ctx, tr, "-", cx + CIRCLE / 2, cy + CIRCLE + 4, 0xFF6B7080);
            }
        }
        return top + boxH;
    }

    /** The item shown in a mode's circle. */
    private static Item item(String mode) {
        String m = mode.toLowerCase(Locale.ROOT);
        if (m.contains("spear") && m.contains("mace")) return Items.MACE;
        if (m.contains("elytra")) return Items.ELYTRA;
        if (m.contains("spear")) return Items.DIAMOND_SPEAR;
        if (m.contains("mace")) return Items.MACE;
        if (m.contains("neth")) return Items.NETHERITE_CHESTPLATE;
        if (m.contains("pot")) return Items.SPLASH_POTION;
        if (m.contains("smp")) return Items.TOTEM_OF_UNDYING;
        if (m.contains("vanilla") || m.contains("crystal")) return Items.END_CRYSTAL;
        if (m.contains("uhc")) return Items.LAVA_BUCKET;
        if (m.contains("axe")) return Items.DIAMOND_AXE;
        if (m.contains("spleef")) return Items.DIAMOND_SHOVEL;
        if (m.contains("puffer")) return Items.PUFFERFISH;
        if (m.contains("cart")) return Items.TNT_MINECART;
        if (m.contains("op")) return Items.DIAMOND_CHESTPLATE;
        return Items.DIAMOND_SWORD;
    }

    /** Tier colours, same as Tier Tagger's name tags. */
    private static int tierColor(int level, boolean high) {
        return switch (level) {
            case 1 -> high ? 0xE8BA3A : 0xD5B355;
            case 2 -> high ? 0xC4D3E7 : 0xA0A7B2;
            case 3 -> high ? 0xF89F5A : 0xC67B42;
            case 4 -> high ? 0x81749A : 0x655B79;
            default -> high ? 0x8F82A8 : 0x655B79;
        };
    }

    private static String pretty(String mode) {
        String m = mode.toLowerCase(Locale.ROOT);
        if (m.equals("nethop") || m.equals("neth_pot")) return "NethPot";
        StringBuilder sb = new StringBuilder();
        for (String part : m.replace('_', ' ').replace('-', ' ').split(" ")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.length() <= 3 ? part.toUpperCase(Locale.ROOT) : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return sb.toString();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int x = x0(), y = y0(), w = cardW(), h = cardH(), by = y + h - 28, ix = infoX();
        if (!Draw.hit(mx, my, x, y, w, h) || Draw.hit(mx, my, x + w - PAD - 60, by, 60, 18)) { close(); return true; }
        String b = button();
        if (b != null && Draw.hit(mx, my, ix, by, 96, 18)) {
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
