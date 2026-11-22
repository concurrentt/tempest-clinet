package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Chat {
    private Chat() { }

    public static void info(String msg) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.inGameHud == null) return;
        c.inGameHud.getChatHud().addMessage(Text.literal("[Tempest] ").formatted(Formatting.GOLD)
                .append(Text.literal(msg).formatted(Formatting.WHITE)));
    }
}
