package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Locale;

public class ChatMentionsModule extends Module {
    private final BoolSetting sound = add(new BoolSetting("sound", "Play Sound", true));
    private final ColorSetting highlight = add(new ColorSetting("color", "Highlight Color", 0xFFFFAA00));

    public ChatMentionsModule() { super("chat_mentions", "Chat Mentions", "Highlights messages that mention your name.", Category.CHAT, true); }

    public static Text process(Text t, String raw) {
        ChatMentionsModule m = ModuleManager.get(ChatMentionsModule.class);
        if (m == null || !m.isEnabled()) return t;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return t;
        String name = c.player.getName().getString();
        if (raw.startsWith("[Tempest]")) return t;
        String low = raw.toLowerCase(Locale.ROOT), lname = name.toLowerCase(Locale.ROOT);
        int first = low.indexOf(lname);
        if (first < 0) return t;
        // your own message ("<you> hi", "IRON you: hi", "you » hi"): no sound, nothing to highlight
        String after = low.substring(first + lname.length()).stripLeading();
        if (first < 40 && (after.startsWith(":") || after.startsWith(">") || after.startsWith("»") || after.startsWith("]"))) return t;
        if (m.sound.get()) dev.tempest.util.Keys.sound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        // colour just your name (bold), keeping the rest of the message's own colours
        int rgb = m.highlight.get() & 0xFFFFFF;
        net.minecraft.text.MutableText out = Text.empty();
        t.visit((style, str) -> {
            String sl = str.toLowerCase(Locale.ROOT);
            int from = 0, k;
            while ((k = sl.indexOf(lname, from)) >= 0) {
                if (k > from) out.append(Text.literal(str.substring(from, k)).setStyle(style));
                out.append(Text.literal(str.substring(k, k + name.length())).setStyle(style.withColor(rgb).withBold(true)));
                from = k + name.length();
            }
            if (from < str.length()) out.append(Text.literal(str.substring(from)).setStyle(style));
            return java.util.Optional.empty();
        }, net.minecraft.text.Style.EMPTY);
        return out;
    }
}
