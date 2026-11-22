package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Timestamps, click-to-copy and a longer chat history. */
public class ChatUpgradesModule extends Module {
    private final BoolSetting timestamps = add(new BoolSetting("timestamps", "Timestamps", true));
    private final ModeSetting format = add(new ModeSetting("format", "Time Format", "24h", "24h", "24h + seconds", "12h"));
    private final ColorSetting stampColor = add(new ColorSetting("stamp_color", "Timestamp Color", 0xFFFFFFFF));
    private final BoolSetting copy = add(new BoolSetting("copy", "Click Message To Copy", true));
    private final NumberSetting history = add(new NumberSetting("history", "History Length", 500, 100, 2000, 100));

    public ChatUpgradesModule() { super("chat_upgrades", "Chat Upgrades", "Timestamps, click a message to copy it, longer chat history.", Category.CHAT, true); }

    public static int historyLimit() {
        ChatUpgradesModule m = ModuleManager.get(ChatUpgradesModule.class);
        return m != null && m.isEnabled() ? m.history.getInt() : 100;
    }

    /** @param raw the plain message text (used for copying). */
    public static Text decorate(Text t, String raw) {
        ChatUpgradesModule m = ModuleManager.get(ChatUpgradesModule.class);
        if (m == null || !m.isEnabled()) return t;
        MutableText out = Text.empty();
        if (m.copy.get()) {
            out.setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent.CopyToClipboard(raw))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to copy"))));
        }
        if (m.timestamps.get()) {
            DateTimeFormatter f = DateTimeFormatter.ofPattern(switch (m.format.get()) {
                case "24h + seconds" -> "HH:mm:ss";
                case "12h" -> "hh:mm a";
                default -> "HH:mm";
            });
            out.append(Text.literal("[" + LocalTime.now().format(f) + "] ").setStyle(Style.EMPTY.withColor(m.stampColor.get() & 0xFFFFFF)));
        }
        out.append(t);
        return out;
    }
}
