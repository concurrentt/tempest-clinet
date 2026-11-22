package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

public class InventoryHudModule extends HudModule {
    private final BoolSetting showWhileMoving = add(new BoolSetting("moving_only", "Show While Moving", false));
    private final BoolSetting durabilityBars = add(new BoolSetting("durability", "Durability Bars", true));

    public InventoryHudModule() {
        super("inventory_hud", "Inventory HUD", "Shows your main inventory on screen.", Category.HUD, false, 200, 6);
    }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        w = 9 * 18 + 4;
        h = 3 * 18 + 4;

        if (!preview && showWhileMoving.get() && c.player != null) {
            double dx = c.player.getX() - c.player.lastX;
            double dz = c.player.getZ() - c.player.lastZ;
            boolean moving = dx * dx + dz * dz > 1.0E-6
                    || c.player.isSprinting() || c.player.handSwinging || c.player.isUsingItem();
            if (!moving) {
                // Keep layout size stable for editor, but don't draw when idle.
                return;
            }
        }

        box(ctx);
        if (preview || c.player == null) return;

        for (int i = 0; i < 27; i++) {
            ItemStack s = c.player.getInventory().getStack(9 + i);
            if (s.isEmpty()) continue;
            int ix = x + 2 + (i % 9) * 18;
            int iy = y + 2 + (i / 9) * 18;
            ctx.drawItem(s, ix + 1, iy + 1);
            if (durabilityBars.get()) {
                ctx.drawStackOverlay(c.textRenderer, s, ix + 1, iy + 1);
            } else if (s.getCount() > 1) {
                String count = String.valueOf(s.getCount());
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(0, 0); // keep same layer
                ctx.drawText(c.textRenderer, count, ix + 19 - 2 - c.textRenderer.getWidth(count), iy + 6 + 3, 0xFFFFFFFF, true);
                ctx.getMatrices().popMatrix();
            }
        }
    }
}
