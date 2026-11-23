package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public class ArmorHudModule extends HudModule {
    private final ModeSetting layout = add(new ModeSetting("layout", "Layout", "Vertical", "Vertical", "Hotbar"));
    private final ModeSetting display = add(new ModeSetting("display", "Durability", "Percent", "Percent", "Number", "Off"));
    private final BoolSetting hand = add(new BoolSetting("hand", "Show Held Item", true));

    public ArmorHudModule() { super("armor_hud", "Armor HUD", "Armor pieces and held item with durability.", Category.HUD, false, 6, 250); }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        TextRenderer tr = c.textRenderer;
        List<ItemStack> items = new ArrayList<>();
        if (preview || c.player == null) {
            ItemStack helm = new ItemStack(Items.DIAMOND_HELMET); helm.setDamage(60);
            ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE); chest.setDamage(200);
            ItemStack legs = new ItemStack(Items.DIAMOND_LEGGINGS); legs.setDamage(10);
            ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
            items.add(helm); items.add(chest); items.add(legs); items.add(boots);
            if (hand.get()) items.add(new ItemStack(Items.DIAMOND_SWORD));
        } else {
            items.add(c.player.getEquippedStack(EquipmentSlot.HEAD));
            items.add(c.player.getEquippedStack(EquipmentSlot.CHEST));
            items.add(c.player.getEquippedStack(EquipmentSlot.LEGS));
            items.add(c.player.getEquippedStack(EquipmentSlot.FEET));
            if (hand.get()) items.add(c.player.getMainHandStack());
        }

        if (layout.is("Hotbar")) renderHotbar(ctx, tr, items);
        else renderVertical(ctx, tr, items);
    }

    private void renderVertical(DrawContext ctx, TextRenderer tr, List<ItemStack> items) {
        boolean bars = display.is("Number");
        int row = bars ? 21 : 18;
        w = 62;
        h = items.size() * row + 4;
        box(ctx);
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            int iy = y + 2 + i * row;
            if (s.isEmpty()) continue;
            ctx.drawItem(s, x + 2, iy);
            drawDurability(ctx, tr, s, x + 22, iy + 4);
            if (bars) durabilityBar(ctx, s, x + 2, iy + 17, w - 6);
        }
    }

    /** Number mode: a bar under the piece showing how much durability is left (full = full bar). */
    private static void durabilityBar(DrawContext ctx, ItemStack s, int bx, int by, int bw) {
        if (!s.isDamageable()) return;
        int max = Math.max(1, s.getMaxDamage());
        float f = Math.max(0f, Math.min(1f, (max - s.getDamage()) / (float) max));
        int col = f > 0.6f ? 0xFF55FF55 : f > 0.3f ? 0xFFFFD83A : 0xFFFF5555;
        Draw.roundRect(ctx, bx, by, bw, 3, 1, 0xB0000000);
        int fw = Math.round((bw - 2) * f);
        if (fw > 0) Draw.roundRect(ctx, bx + 1, by + 1, fw, 1, 0, col);
    }

    private void renderHotbar(DrawContext ctx, TextRenderer tr, List<ItemStack> items) {
        int slot = 20;
        int pad = 2;
        boolean bars = display.is("Number");
        w = items.size() * slot + pad * 2;
        h = bars ? 32 : 28;
        box(ctx);
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            int sx = x + pad + i * slot;
            int sy = y + 2;
            Draw.roundRect(ctx, sx, sy, 18, 18, 3, 0x55202028);
            if (s.isEmpty()) continue;
            ctx.drawItem(s, sx + 1, sy + 1);
            if (bars) durabilityBar(ctx, s, sx, sy + 19, 18);
            if (s.isDamageable() && !display.is("Off")) {
                int max = s.getMaxDamage();
                int left = max - s.getDamage();
                String t = display.is("Percent") ? (left * 100 / Math.max(1, max)) + "%" : Integer.toString(left);
                float f = left / (float) Math.max(1, max);
                int col = f > 0.6f ? 0xFF55FF55 : f > 0.3f ? 0xFFFFD83A : 0xFFFF5555;
                int tw = Draw.textW(tr, t);
                float scale = 0.7f;
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().scale(scale, scale);
                Draw.text(ctx, tr, t, (int) ((sx + 9 - tw * scale / 2f) / scale), (int) ((sy + (bars ? 23 : 20)) / scale), col, true);
                ctx.getMatrices().popMatrix();
            }
        }
    }

    private void drawDurability(DrawContext ctx, TextRenderer tr, ItemStack s, int tx, int ty) {
        if (!s.isDamageable() || display.is("Off")) return;
        int max = s.getMaxDamage();
        int left = max - s.getDamage();
        String t = display.is("Percent") ? (left * 100 / Math.max(1, max)) + "%" : Integer.toString(left);
        float f = left / (float) Math.max(1, max);
        int col = f > 0.6f ? 0xFF55FF55 : f > 0.3f ? 0xFFFFD83A : 0xFFFF5555;
        Draw.text(ctx, tr, t, tx, ty, col, true);
    }
}
