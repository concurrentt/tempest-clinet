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
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat Tabs: All / Party / Guild / Private tabs above the chat box (click them while chat is open), with unread
 * counts, plus hide filters (regular expressions) managed with /tempest filter add|remove|list|clear.
 * Lines are only hidden from view; switching back to All or removing a filter brings them back.
 * Hooks: ChatHudMixin (classify + hide lines), ChatScreenMixin (clicks), TempestClient (drawing).
 */
public final class ChatTabsModule extends Module {
    public static final String[] TABS = {"All", "Party", "Guild", "Private"};
    private static final Pattern[] MATCH = {
            null,
            Pattern.compile("(?i)^\\s*(\\[?party\\]?\\s*[>:\\]]|\\(party\\)|p\\s*>|\\[p\\]\\s)"),
            Pattern.compile("(?i)^\\s*(\\[?guild\\]?\\s*[>:\\]]|\\(guild\\)|g\\s*>|\\[g\\]\\s|officer\\s*>)"),
            Pattern.compile("(?i)^\\s*(from |to |\\[[^\\]]*\\s->\\s[^\\]]*\\]|you whisper to |\\S+ whispers to you|\\[msg\\]|\\[pm\\])"),
    };
    private final BoolSetting showUnread = add(new BoolSetting("unread", "Unread Counts", true));
    private final BoolSetting filtersInAll = add(new BoolSetting("filters", "Apply Hide Filters", true));

    private static int tab = 0;
    private static final int[] UNREAD = new int[TABS.length];
    private static List<Pattern> compiled;
    private static List<String> compiledFrom;

    public ChatTabsModule() {
        super("chat_tabs", "Chat Tabs", "All / Party / Guild / Private chat tabs and hide filters (/tempest filter).", Category.CHAT, false);
        keyHint = "Open chat and click a tab. Filters: /tempest filter add <regex>, /tempest filter list.";
    }

    private static ChatTabsModule on() {
        ChatTabsModule m = ModuleManager.get(ChatTabsModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    @Override public void onDisable() { tab = 0; refresh(); }
    @Override public void onEnable() { refresh(); }

    public static int classify(String raw) {
        for (int i = 1; i < MATCH.length; i++) if (MATCH[i].matcher(raw).find()) return i;
        return 0;
    }

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

    /** A new chat line arrived (counts unread for the other tabs). */
    public static void onMessage(String raw) {
        ChatTabsModule m = on();
        if (m == null) return;
        int k = classify(raw);
        if (k != 0 && k != tab) UNREAD[k] = Math.min(99, UNREAD[k] + 1);
    }

    /** Whether a chat line is shown with the current tab and filters. */
    public static boolean visible(Text content) {
        ChatTabsModule m = on();
        if (m == null || content == null) return true;
        String raw = content.getString();
        if (m.filtersInAll.get()) for (Pattern p : filters()) if (p.matcher(raw).find()) return false;
        return tab == 0 || classify(raw) == tab;
    }

    private static void refresh() {
        MinecraftClient c = MinecraftClient.getInstance();
        try {
            if (c.inGameHud != null && c.inGameHud.getChatHud() instanceof ChatRefresher r) r.tempest$refreshChat();
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ tab strip (drawn over the chat screen)
    private static final int TAB_H = 13, TAB_GAP = 3;

    private static int tabY(int height) { return height - 31; }

    private static int tabW(TextRenderer tr, int i) {
        int w = tr.getWidth(TABS[i]) + 12;
        if (UNREAD[i] > 0) w += tr.getWidth(Integer.toString(UNREAD[i])) + 7;
        return w;
    }

    public static void renderTabs(DrawContext ctx, int width, int height, int mx, int my) {
        ChatTabsModule m = on();
        if (m == null) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = 2, y = tabY(height);
        for (int i = 0; i < TABS.length; i++) {
            int w = tabW(tr, i);
            boolean sel = i == tab, hv = Draw.hit(mx, my, x, y, w, TAB_H);
            int fill = sel ? Draw.withAlpha(Draw.accent(), 0xC8) : hv ? 0xC0242838 : 0xA0101118;
            Draw.panel(ctx, x, y, w, TAB_H, 4, fill, sel ? Draw.withAlpha(0xFFFFFF, 0x50) : 0x30FFFFFF);
            ctx.drawText(tr, TABS[i], x + 6, y + 3, sel ? 0xFFFFFFFF : hv ? 0xFFE6E8F0 : 0xFFA9AEC0, false);
            if (m.showUnread.get() && UNREAD[i] > 0) {
                String n = Integer.toString(UNREAD[i]);
                int bx = x + 6 + tr.getWidth(TABS[i]) + 4;
                Draw.roundRect(ctx, bx, y + 2, tr.getWidth(n) + 5, 9, 4, 0xFFE04A5F);
                ctx.drawText(tr, n, bx + 3, y + 3, 0xFFFFFFFF, false);
            }
            x += w + TAB_GAP;
        }
    }

    /** Click on the tab strip (from ChatScreenMixin). True = handled. */
    public static boolean click(double mx, double my, int height) {
        if (on() == null) return false;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = 2, y = tabY(height);
        for (int i = 0; i < TABS.length; i++) {
            int w = tabW(tr, i);
            if (Draw.hit(mx, my, x, y, w, TAB_H)) {
                if (tab != i) { tab = i; refresh(); }
                UNREAD[i] = 0;
                return true;
            }
            x += w + TAB_GAP;
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
