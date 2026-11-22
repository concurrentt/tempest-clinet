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
        if (raw.startsWith("<" + name + ">") || raw.startsWith("[Tempest]")) return t;
        if (!raw.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) return t;
        if (m.sound.get()) dev.tempest.util.Keys.sound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        int rgb = m.highlight.get() & 0xFFFFFF;
        return t.copy().styled(s -> s.withColor(rgb));
    }
}
