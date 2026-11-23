package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows how many totems a player has popped on their nametag ("Steve -3", resets when they die), and how many
 * totems you have left in a small pill you can move in the HUD editor (it starts just above the hotbar).
 */
public class TotemPopModule extends HudModule {
    private static final Map<UUID, Integer> POPS = new HashMap<>();
    private static ClientWorld lastWorld;

    private final BoolSetting nametag = add(new BoolSetting("nametag", "Show On Nametag", true));
    private final BoolSetting showHotbarCount = add(new BoolSetting("hotbar_count", "Totem Count", true));

    public TotemPopModule() {
        // -1, -1: not placed yet - it starts centred above the hotbar (see onTick) until you move it
        super("totem_pops", "Totem Pop Counter", "Shows how many totems each player has popped next to their name (e.g. Steve -3), and how many you have left.",
                Category.PVP, true, -1, -1);
    }

    @Override
    public void onTick(MinecraftClient c) {
        if ((x < 0 || y < 0) && c.getWindow() != null) {
            int sw = c.getWindow().getScaledWidth(), sh = c.getWindow().getScaledHeight();
            setPos(Math.max(0, sw / 2 - 22), Math.max(0, sh - 60));
        }
    }

    @Override
    public void resetPos() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.getWindow() != null) setPos(Math.max(0, c.getWindow().getScaledWidth() / 2 - 22), Math.max(0, c.getWindow().getScaledHeight() - 60));
        else super.resetPos();
    }

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

    public static void track(float td) {
        TotemPopModule m = ModuleManager.get(TotemPopModule.class);
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world != lastWorld) { POPS.clear(); lastWorld = c.world; }
        if (m == null || !m.isEnabled() || c.player == null || c.world == null) return;
        // your own counter resets the moment you die (death status isn't always sent for the local player)
        if (c.player.isDead() || c.player.getHealth() <= 0f) POPS.remove(c.player.getUuid());
        // the totem count itself is drawn as a HUD element (renderHud), so it can be moved
    }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        int count = preview ? 3 : showHotbarCount.get() && c.player != null ? countTotems(c) : 0;
        if (count <= 0 || x < 0 || y < 0) { w = 0; h = 0; return; }
        String text = Integer.toString(count);
        Text label = Text.literal(text).styled(s -> s.withBold(true));
        int tw = Draw.textW(c.textRenderer, label);
        w = 4 + 16 + 3 + tw + 6;
        h = 18;
        // small glass pill: a warm edge that turns red when you're on your last totem
        if (background.get()) {
            int edge = count == 1 ? 0xC0FF5A5A : count <= 3 ? 0xA0FFC857 : 0x50FFFFFF;
            Draw.panel(ctx, x, y, w, h, 6, Draw.withAlpha(0x101118, Math.max(0x30, alpha())), edge);
        }
        ctx.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), x + 3, y + 1);
        int col = count == 1 ? 0xFFFF6B6B : count <= 3 ? 0xFFFFD36B : 0xFFFFFFFF;
        Draw.text(ctx, c.textRenderer, label, x + 4 + 16 + 3, y + 5, col, true);
    }

    /** Whether the pop count goes on nametags (read by NametagsModule). */
    public static boolean onNametag() {
        TotemPopModule m = ModuleManager.get(TotemPopModule.class);
        return m != null && m.isEnabled() && m.nametag.get();
    }

    private static int totemCount, totemCountAge = -1;
    private static Object totemCountPlayer;

    /** Counted once per game tick (the inventory can't change between frames of the same tick). */
    private static int countTotems(MinecraftClient c) {
        if (c.player == null) return 0;
        if (c.player == totemCountPlayer && c.player.age == totemCountAge) return totemCount;
        totemCountPlayer = c.player;
        totemCountAge = c.player.age;
        totemCount = countTotemsNow(c);
        return totemCount;
    }

    private static int countTotemsNow(MinecraftClient c) {
        int count = 0;
        for (int i = 0; i < c.player.getInventory().size(); i++) {
            ItemStack s = c.player.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) count += s.getCount();
        }
        return count;
    }
}
