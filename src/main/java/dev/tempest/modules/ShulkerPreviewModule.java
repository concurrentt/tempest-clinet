package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;

public class ShulkerPreviewModule extends Module {
    private final BoolSetting requireShift = add(new BoolSetting("shift", "Require Shift", true));
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Grid", "Grid", "Simple"));

    public ShulkerPreviewModule() {
        super("shulker_preview", "Shulker Preview", "Preview shulker contents in inventory. Grid or simple compact style.", Category.UTILITY, true);
    }

    /** @return true if a preview was drawn (the vanilla tooltip should be cancelled). */
    public static boolean render(DrawContext ctx, Slot slot, int mx, int my, ScreenHandler handler) {
        ShulkerPreviewModule m = ModuleManager.get(ShulkerPreviewModule.class);
        if (m == null || !m.isEnabled()) return false;
        if (slot == null || !slot.hasStack() || !handler.getCursorStack().isEmpty()) return false;
        if (m.requireShift.get() && !dev.tempest.util.Keys.shift()) return false;

        ItemStack st = slot.getStack();
        if (!(st.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof ShulkerBoxBlock)) return false;
        ContainerComponent comp = st.get(DataComponentTypes.CONTAINER);
        if (comp == null) return false;

        DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
        comp.copyTo(items);
        boolean any = false;
        for (ItemStack s : items) if (!s.isEmpty()) { any = true; break; }
        if (!any) return false;

        return m.style.is("Simple") ? renderSimple(ctx, st, items, mx, my) : renderGrid(ctx, st, items, mx, my);
    }

    private static boolean renderGrid(DrawContext ctx, ItemStack st, DefaultedList<ItemStack> items, int mx, int my) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int pw = 9 * 18 + 14, ph = 3 * 18 + 14 + 12;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int px = mx + 12, py = my + 12;
        if (px + pw > sw) px = mx - pw - 8;
        if (py + ph > sh) py = sh - ph - 2;
        px = Math.max(2, px);
        py = Math.max(2, py);

        Draw.shadow(ctx, px, py, pw, ph, 6, 6);
        Draw.panel(ctx, px, py, pw, ph, 6, 0xF2121018, Draw.withAlpha(Draw.accent(), 0xAA));
        ctx.drawText(tr, st.getName(), px + 7, py + 4, 0xFFFFFFFF, false);

        for (int i = 0; i < 27; i++) {
            int sx = px + 7 + (i % 9) * 18;
            int sy = py + 16 + (i / 9) * 18;
            Draw.roundRect(ctx, sx, sy, 18, 18, 3, 0xFF23222C);
            ItemStack s = items.get(i);
            if (!s.isEmpty()) {
                ctx.drawItem(s, sx + 1, sy + 1);
                ctx.drawStackOverlay(tr, s, sx + 1, sy + 1);
            }
        }
        return true;
    }

    private static boolean renderSimple(DrawContext ctx, ItemStack st, DefaultedList<ItemStack> items, int mx, int my) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cols = 9, rows = 3;
        int cell = 18;
        int pw = cols * cell + 10;
        int ph = rows * cell + 22;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int px = mx + 12, py = my + 12;
        if (px + pw > sw) px = mx - pw - 8;
        if (py + ph > sh) py = sh - ph - 2;
        px = Math.max(2, px);
        py = Math.max(2, py);

        Draw.shadow(ctx, px, py, pw, ph, 5, 4);
        Draw.panel(ctx, px, py, pw, ph, 5, 0xEE10141C, Draw.withAlpha(Draw.accent(), 0x66));
        ctx.drawText(tr, st.getName(), px + 6, py + 4, 0xFFEAEFFF, false);

        for (int i = 0; i < 27; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            int sx = px + 5 + (i % cols) * cell;
            int sy = py + 16 + (i / cols) * cell;
            ctx.drawItem(s, sx, sy);
            ctx.drawStackOverlay(tr, s, sx, sy);
        }
        return true;
    }
}
