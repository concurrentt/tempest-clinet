package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shows how many totems a player has popped on their nametag ("Steve -3"). Resets when they die. */
public class TotemPopModule extends Module {
    private static final Map<UUID, Integer> POPS = new HashMap<>();
    private static ClientWorld lastWorld;

    private final BoolSetting nametag = add(new BoolSetting("nametag", "Show On Nametag", true));
    private final BoolSetting showHotbarCount = add(new BoolSetting("hotbar_count", "Totem Count", true));

    public TotemPopModule() { super("totem_pops", "Totem Pop Counter", "Shows how many totems each player has popped on their nametag (e.g. Steve -3). Resets on death.", Category.PVP, true); }

    /** Called from ClientPlayNetworkHandlerMixin for every entity status packet. */
    public static void onStatus(Entity e, byte status) {
        if (e == null) return;
        // always track, even while disabled, so the counts are right if you switch it on mid-fight
        if (status == 35) POPS.merge(e.getUuid(), 1, Integer::sum);
        else if (status == 3) POPS.remove(e.getUuid());
    }

    public static int getPops(UUID id) {
        Integer n = POPS.get(id);
        return n == null ? 0 : n;
    }

    public static void render(DrawContext ctx, float td) {
        TotemPopModule m = ModuleManager.get(TotemPopModule.class);
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world != lastWorld) { POPS.clear(); lastWorld = c.world; }
        if (m == null || !m.isEnabled() || c.player == null || c.world == null || c.currentScreen != null) return;
        // your own counter resets the moment you die (death status isn't always sent for the local player)
        if (c.player.isDead() || c.player.getHealth() <= 0f) POPS.remove(c.player.getUuid());

        if (m.showHotbarCount.get()) m.renderHotbarCount(ctx, c);
    }

    /** Whether the pop count goes on nametags (read by NametagsModule). */
    public static boolean onNametag() {
        TotemPopModule m = ModuleManager.get(TotemPopModule.class);
        return m != null && m.isEnabled() && m.nametag.get();
    }

    private void renderHotbarCount(DrawContext ctx, MinecraftClient c) {
        int count = countTotems(c);
        if (count <= 0) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int x = sw / 2 - 8;
        int y = sh - 59; // between hearts and hunger, above hotbar

        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        ctx.drawItem(totem, x, y);
        String text = String.valueOf(count);
        int tw = c.textRenderer.getWidth(text);
        // vanilla stack-count position: bottom-right of the 16x16 item, no gap
        ctx.drawText(c.textRenderer, text, x + 19 - 2 - tw, y + 6 + 3, 0xFFFFFFFF, true);
    }

    private static int countTotems(MinecraftClient c) {
        if (c.player == null) return 0;
        int count = 0;
        for (int i = 0; i < c.player.getInventory().size(); i++) {
            ItemStack s = c.player.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) count += s.getCount();
        }
        return count;
    }
}
