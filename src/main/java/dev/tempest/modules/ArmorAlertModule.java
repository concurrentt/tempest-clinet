package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;

/**
 * Armor Break Alert: a warning in the middle of the screen (and a sound) when a piece of armor you're wearing drops
 * below a durability you choose - once per piece, again only after it's been repaired or swapped.
 */
public class ArmorAlertModule extends Module {
    private final NumberSetting threshold = add(new NumberSetting("threshold", "Warn Below %", 20, 5, 60, 5));
    private final BoolSetting sound = add(new BoolSetting("sound", "Sound", true));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] NAMES = {"Helmet", "Chestplate", "Leggings", "Boots"};
    private final boolean[] warned = new boolean[4];
    private static String message;
    private static long shownAt;

    public ArmorAlertModule() {
        super("armor_alert", "Armor Break Alert", "Warns you when a piece of your armor is about to break.", Category.PVP, false);
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (c.player == null) return;
        for (int i = 0; i < 4; i++) {
            ItemStack s = c.player.getEquippedStack(SLOTS[i]);
            if (s.isEmpty() || !s.isDamageable()) { warned[i] = false; continue; }
            int pct = Math.round(100f * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage());
            if (pct <= threshold.get()) {
                if (!warned[i]) {
                    warned[i] = true;
                    message = NAMES[i] + " low! " + pct + "%";
                    shownAt = System.currentTimeMillis();
                    if (sound.get()) dev.tempest.util.Keys.sound(SoundEvents.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
                }
            } else if (pct > threshold.get() + 5) {
                warned[i] = false;                                   // repaired / swapped: can warn again
            }
        }
    }

    /** Drawn over the HUD (TempestClient HUD callback): big red text above the crosshair for 2.5 s. */
    public static void render(DrawContext ctx) {
        if (message == null || !ModuleManager.enabled(ArmorAlertModule.class)) return;
        long age = System.currentTimeMillis() - shownAt;
        if (age > 2500) { message = null; return; }
        MinecraftClient c = MinecraftClient.getInstance();
        int a = age > 2000 ? (int) (255 * (2500 - age) / 500f) : 255;
        int col = (Math.max(8, a) << 24) | 0xFF4D4D;
        float scale = 1.6f;
        int w = c.textRenderer.getWidth(message);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(ctx.getScaledWindowWidth() / 2f, ctx.getScaledWindowHeight() / 2f - 40);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawTextWithShadow(c.textRenderer, message, -w / 2, 0, col);
        ctx.getMatrices().popMatrix();
    }
}
