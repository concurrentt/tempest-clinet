package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;

public class MessageStackingModule extends Module {
    public static String lastRaw = null;
    public static int count = 1;

    public MessageStackingModule() { super("message_stacking", "Message Stacking", "Merges repeated chat messages into one (x2, x3...).", Category.CHAT, true); }
}
