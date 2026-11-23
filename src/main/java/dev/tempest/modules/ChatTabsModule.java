package dev.tempest.modules;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tempest.TempestConfig;
import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.util.Chat;
import dev.tempest.util.ChatRefresher;
import dev.tempest.util.Keys;
import dev.tempest.util.Social;
import dev.tempest.util.TempestChat;
import dev.tempest.util.TempestNet;
import dev.tempest.gui.ChatGroupScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat Tabs: Tempest chat in the chat box. Tabs: Chat (everything), Private (pick a friend and message them), Party
 * (invite friends and talk together) and one tab per group you make with "+". Messages go through the Tempest service
 * (util/TempestChat), so they work on any server and the server never sees them. Typing in a Tempest tab sends there;
 * commands (/...) always go to the server. Hide filters (regular expressions): /tempest filter add|remove|list|clear.
 * Hooks: ChatHudMixin (which lines show), ChatScreenMixin (clicks, sending), TempestClient (drawing).
 */
public final class ChatTabsModule extends Module {
    private final BoolSetting showUnread = add(new BoolSetting("unread", "Unread Counts", true));
    private final BoolSetting filtersInAll = add(new BoolSetting("filters", "Apply Hide Filters", true));
    private final BoolSetting sound = add(new BoolSetting("sound", "Message Sound", true));

    /** Current tab: "all", "pm", "party" or "g:<group id>". */
    private static String tab = "all";
    /** The friend you're talking to in Private (null = all private messages, send to whoever messaged last). */
    private static UUID pmTarget, lastPartner;
    private static final Map<String, Integer> UNREAD = new HashMap<>();
    private static List<Pattern> compiled;
    private static List<String> compiledFrom;

    public ChatTabsModule() {
        super("chat_tabs", "Chat Tabs", "Private messages with friends, party chat and group chats, in tabs above the chat box.", Category.CHAT, true);
        keyHint = "Open chat and click a tab. + makes a group. Filters: /tempest filter add <regex>, /tempest filter list.";
    }

    private static ChatTabsModule on() {
        ChatTabsModule m = ModuleManager.get(ChatTabsModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Keeps the live link to the Tempest service up for chat. */
    public static boolean tempestChatOn() { return on() != null; }

    @Override public void onDisable() { tab = "all"; refresh(); }
    @Override public void onEnable() { refresh(); }

    private static List<Pattern> filters() {
        List<String> src = TempestConfig.get().chatFilters;
        if (compiled == null || !src.equals(compiledFrom)) {
            compiled = new ArrayList<>();
            for (String f : src) {
                try { compiled.add(Pattern.compile(f, Pattern.CASE_INSENSITIVE)); } catch (PatternSyntaxException ignored) { }
            }
            compiledFrom = new ArrayList<>(src);
        }
        return compiled;
    }

    /** A server chat line arrived (nothing to count: server chat lives in the Chat tab). */
    public static void onMessage(String raw) { }

    /** Which tab a conversation belongs to. */
    private static String tabFor(String key) {
        if (key.startsWith("pm:")) return "pm";
        TempestChat.Group g = TempestChat.group(key.substring(2));
        return g != null && g.isParty() ? "party" : key;
    }

    /** A Tempest message arrived (from TempestChat, game thread). */
    public static void onTempestMessage(String key, UUID from) {
        ChatTabsModule m = on();
        if (m == null) return;
        if (key.startsWith("pm:")) lastPartner = from;
        String t = tabFor(key);
        if (!t.equals(tab)) UNREAD.merge(t, 1, (a, b) -> Math.min(99, a + b));
        if (m.sound.get()) Keys.sound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.7f);
    }

    /** Whether a chat line is shown with the current tab and filters. */
    public static boolean visible(Text content) {
        ChatTabsModule m = on();
        if (m == null || content == null) return true;
        String key = TempestChat.keyOf(content);
        switch (tab) {
            case "all" -> {
                if (key != null) return true;
                if (m.filtersInAll.get()) {
                    String raw = content.getString();
                    for (Pattern p : filters()) if (p.matcher(raw).find()) return false;
                }
                return true;
            }
            case "pm" -> { return key != null && key.startsWith("pm:") && (pmTarget == null || key.equals("pm:" + pmTarget)); }
            case "party" -> {
                TempestChat.Group p = TempestChat.party();
                return key != null && p != null && key.equals(p.key());
            }
            default -> { return key != null && key.equals(tab); }
        }
    }

    /** Typing Enter in a Tempest tab: send it there instead of to the server. True = handled. */
    public static boolean handleSend(String text) {
        if (on() == null || text == null || text.isBlank() || text.startsWith("/") || tab.equals("all")) return false;
        switch (tab) {
            case "pm" -> {
                UUID to = pmTarget != null ? pmTarget : lastPartner;
                if (to == null) { Chat.info("Pick a friend in the Private tab first (click their name)."); return true; }
                TempestChat.send("pm:" + to, text);
            }
            case "party" -> {
                TempestChat.Group p = TempestChat.party();
                if (p == null) { Chat.info("You're not in a party - open chat, go to Party and click Start a party."); return true; }
                TempestChat.send(p.key(), text);
            }
            default -> {
                if (TempestChat.group(tab.substring(2)) == null) { Chat.info("That group is gone."); tab = "all"; refresh(); return true; }
                TempestChat.send(tab, text);
            }
        }
        return true;
    }

    private static void refresh() {
        MinecraftClient c = MinecraftClient.getInstance();
        try {
            if (c.inGameHud != null && c.inGameHud.getChatHud() instanceof ChatRefresher r) r.tempest$refreshChat();
        } catch (Throwable ignored) { }
    }

    private static void select(String t) {
        if (!t.equals(tab)) { tab = t; refresh(); }
        UNREAD.remove(t);
        if (!t.equals("all")) TempestChat.refreshGroups();
        if (t.equals("pm")) Social.refreshIfStale();
    }

    // ------------------------------------------------------------------ tab strip + the tab's own row (drawn over the chat screen)
    private record Item(String id, String label, int color, boolean on) { }
    private static final int TAB_H = 13, TAB_GAP = 3;

    private static int tabY(int height) { return height - 31; }

    private static List<Item> tabs() {
        List<Item> out = new ArrayList<>();
        out.add(new Item("all", "Chat", 0, false));
        out.add(new Item("pm", "Private", 0xD58CFF, false));
        out.add(new Item("party", "Party", 0x5AB4FF, false));
        for (TempestChat.Group g : TempestChat.groups()) {
            if (g.isParty()) continue;
            String n = g.name().length() > 12 ? g.name().substring(0, 11) + "\u2026" : g.name();
            out.add(new Item(g.key(), n, 0x6AF09A, false));
        }
        out.add(new Item("+", "+", 0, false));
        // remember: a group tab that vanished falls back to Chat
        if (tab.startsWith("g:") && TempestChat.group(tab.substring(2)) == null) { tab = "all"; refresh(); }
        return out;
    }

    /** Chips after the tabs: friends to pick (Private), party members + Invite / Leave, group members + Add / Leave. */
    private static List<Item> extras() {
        List<Item> out = new ArrayList<>();
        UUID me = MinecraftClient.getInstance().getSession() == null ? null : MinecraftClient.getInstance().getSession().getUuidOrNull();
        switch (tab) {
            case "pm" -> {
                List<Social.Friend> fs = Social.friends();
                if (fs.isEmpty()) out.add(new Item("", "No friends yet - add some in Social", 0, false));
                for (Social.Friend f : fs) out.add(new Item("pm:" + f.id(), Social.nameOf(f.id()), f.online() ? 0xFF6AF09A : 0xFF6B7080, f.id().equals(pmTarget)));
            }
            case "party" -> {
                TempestChat.Group p = TempestChat.party();
                if (p == null) out.add(new Item("invite", "+ Start a party", 0, false));
                else {
                    for (UUID u : p.members()) if (!u.equals(me)) out.add(new Item("", Social.nameOf(u), u.equals(p.owner()) ? 0xFFFFD27A : 0xFF9DA2B3, false));
                    if (p.owner().equals(me)) out.add(new Item("invite", "+ Invite", 0, false));
                    out.add(new Item("leave:" + p.id(), p.owner().equals(me) ? "End party" : "Leave", 0, false));
                }
            }
            default -> {
                TempestChat.Group g = tab.startsWith("g:") ? TempestChat.group(tab.substring(2)) : null;
                if (g == null) break;
                for (UUID u : g.members()) if (!u.equals(me)) out.add(new Item("", Social.nameOf(u), u.equals(g.owner()) ? 0xFFFFD27A : 0xFF9DA2B3, false));
                if (g.owner().equals(me)) out.add(new Item("add:" + g.id(), "+ Add", 0, false));
                out.add(new Item("leave:" + g.id(), g.owner().equals(me) ? "Delete group" : "Leave", 0, false));
            }
        }
        return out;
    }

    private static int tabW(TextRenderer tr, Item t, ChatTabsModule m) {
        int w = Draw.textW(tr, t.label) + 12;
        int n = UNREAD.getOrDefault(t.id, 0);
        if (m.showUnread.get() && n > 0) w += Draw.textW(tr, Integer.toString(n)) + 7;
        return w;
    }

    public static void renderTabs(DrawContext ctx, int width, int height, int mx, int my) {
        ChatTabsModule m = on();
        if (m == null) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = 2, y = tabY(height);
        for (Item t : tabs()) {
            int w = tabW(tr, t, m);
            boolean sel = t.id.equals(tab), hv = Draw.hit(mx, my, x, y, w, TAB_H);
            int fill = sel ? Draw.withAlpha(Draw.accent(), 0xC8) : hv ? 0xC0242838 : 0xA0101118;
            Draw.panel(ctx, x, y, w, TAB_H, 4, fill, sel ? Draw.withAlpha(0xFFFFFF, 0x50) : 0x30FFFFFF);
            if (t.color != 0 && !sel) ctx.fill(x + 3, y + TAB_H - 2, x + w - 3, y + TAB_H - 1, 0xC0000000 | t.color);
            Draw.text(ctx, tr, t.label, x + 6, y + 3, sel ? 0xFFFFFFFF : hv ? 0xFFE6E8F0 : 0xFFA9AEC0, false);
            int n = UNREAD.getOrDefault(t.id, 0);
            if (m.showUnread.get() && n > 0) {
                String s = Integer.toString(n);
                int bx = x + 6 + Draw.textW(tr, t.label) + 4;
                Draw.roundRect(ctx, bx, y + 2, Draw.textW(tr, s) + 5, 9, 4, 0xFFE04A5F);
                Draw.text(ctx, tr, s, bx + 3, y + 3, 0xFFFFFFFF, false);
            }
            x += w + TAB_GAP;
        }
        // the tab's own row, after a small gap
        x += 6;
        for (Item e : extras()) {
            int w = Draw.textW(tr, e.label) + 10;
            if (x + w > width - 4) { Draw.text(ctx, tr, "\u2026", x, y + 3, 0xFF9DA2B3, true); break; }
            boolean clickable = !e.id.isEmpty(), hv = clickable && Draw.hit(mx, my, x, y, w, TAB_H);
            int fill = e.on ? Draw.withAlpha(Draw.accent(), 0xB0) : hv ? 0xC0242838 : clickable ? 0x90101118 : 0x60101118;
            Draw.panel(ctx, x, y, w, TAB_H, 6, fill, e.on ? 0x60FFFFFF : clickable ? 0x28FFFFFF : 0x10FFFFFF);
            int col = e.color != 0 && !e.on ? e.color : 0xFFFFFFFF;
            if (e.id.startsWith("pm:")) Draw.circle(ctx, x + 4, y + TAB_H / 2, 1, e.color);
            Draw.text(ctx, tr, e.label, x + 5 + (e.id.startsWith("pm:") ? 2 : 0), y + 3, e.id.startsWith("pm:") ? 0xFFFFFFFF : col, false);
            x += w + 2;
        }
    }

    /** Click on the tab strip (from ChatScreenMixin). True = handled. */
    public static boolean click(double mx, double my, int width, int height) {
        ChatTabsModule m = on();
        if (m == null) return false;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = 2, y = tabY(height);
        if (my < y || my >= y + TAB_H) return false;
        MinecraftClient c = MinecraftClient.getInstance();
        for (Item t : tabs()) {
            int w = tabW(tr, t, m);
            if (Draw.hit(mx, my, x, y, w, TAB_H)) {
                if (t.id.equals("+")) {
                    if (!TempestNet.signedIn()) { Chat.info("Not connected to Tempest yet - try again in a moment."); return true; }
                    c.setScreen(new ChatGroupScreen(ChatGroupScreen.Mode.CREATE, null));
                } else select(t.id);
                return true;
            }
            x += w + TAB_GAP;
        }
        x += 6;
        for (Item e : extras()) {
            int w = Draw.textW(tr, e.label) + 10;
            if (x + w > width - 4) break;
            if (!e.id.isEmpty() && Draw.hit(mx, my, x, y, w, TAB_H)) {
                if (e.id.startsWith("pm:")) {
                    UUID u = UUID.fromString(e.id.substring(3));
                    pmTarget = u.equals(pmTarget) ? null : u;
                    refresh();
                } else if (e.id.equals("invite")) {
                    c.setScreen(new ChatGroupScreen(ChatGroupScreen.Mode.PARTY, null));
                } else if (e.id.startsWith("add:")) {
                    c.setScreen(new ChatGroupScreen(ChatGroupScreen.Mode.ADD, e.id.substring(4)));
                } else if (e.id.startsWith("leave:")) {
                    TempestChat.leave(e.id.substring(6), msg -> {
                        Chat.info(msg != null ? msg : "Done.");
                        select("all");
                    });
                }
                return true;
            }
            x += w + 2;
        }
        return false;
    }

    // ------------------------------------------------------------------ /tempest filter
    @Override
    public void onInit() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("tempest").then(ClientCommandManager.literal("filter")
                        .then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("regex", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String re = StringArgumentType.getString(ctx, "regex");
                                    try { Pattern.compile(re); } catch (PatternSyntaxException e) { Chat.info("Not a valid pattern: " + e.getDescription()); return 0; }
                                    TempestConfig.get().chatFilters.add(re);
                                    TempestConfig.save();
                                    refresh();
                                    Chat.info("Hiding chat lines matching: " + re);
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("number", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int i = IntegerArgumentType.getInteger(ctx, "number") - 1;
                                    List<String> l = TempestConfig.get().chatFilters;
                                    if (i < 0 || i >= l.size()) { Chat.info("No filter #" + (i + 1) + "."); return 0; }
                                    String re = l.remove(i);
                                    TempestConfig.save();
                                    refresh();
                                    Chat.info("Removed filter: " + re);
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("list").executes(ctx -> {
                            List<String> l = TempestConfig.get().chatFilters;
                            if (l.isEmpty()) Chat.info("No chat filters. Add one with /tempest filter add <regex>");
                            for (int i = 0; i < l.size(); i++) Chat.info("#" + (i + 1) + "  " + l.get(i));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("clear").executes(ctx -> {
                            TempestConfig.get().chatFilters.clear();
                            TempestConfig.save();
                            refresh();
                            Chat.info("Chat filters cleared.");
                            return 1;
                        })))));
    }
}
