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
    private final BoolSetting slotIcon = add(new BoolSetting("slot_icon", "Item Icon On Shulker", true));
    private final ModeSetting displayItem = add(new ModeSetting("display_item", "Icon Shows", "Most Items", "Most Items", "First Slot", "Last Slot"));
    private final BoolSetting nameIcon = add(new BoolSetting("name_icon", "Use Name As Icon (e.g. \"diamond\")", true));
    private final ModeSetting iconSize = add(new ModeSetting("icon_size", "Icon Size", "Medium", "Small", "Medium", "Large"));
    private final BoolSetting capacityBar = add(new BoolSetting("capacity_bar", "Fullness Bar", true));

    public ShulkerPreviewModule() {
        super("shulker_preview", "Shulker Preview", "See what's in a shulker without opening it: an icon of its contents on the item, a fullness bar, and a preview on hover.", Category.QOL, true);
    }

    private static boolean drawingIcon;   // the icon itself goes through the same item drawing - never recurse

    private static boolean isShulker(ItemStack st) {
        return !st.isEmpty() && st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    /**
     * Called for every item drawn in a slot (inventories, hotbar, chests): on a shulker box, draws an icon of what's
     * inside over it and a bar showing how full it is.
     */
    public static void drawSlotIcon(DrawContext ctx, ItemStack st, int x, int y) {
        if (drawingIcon || !isShulker(st)) return;
        ShulkerPreviewModule m = ModuleManager.get(ShulkerPreviewModule.class);
        if (m == null || !m.isEnabled() || (!m.slotIcon.get() && !m.capacityBar.get())) return;
        ContainerComponent comp = st.get(DataComponentTypes.CONTAINER);
        DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
        if (comp != null) comp.copyTo(items);

        float fill = 0f;
        for (ItemStack s : items) if (!s.isEmpty()) fill += s.getCount() / (float) Math.max(1, s.getMaxCount());
        fill /= 27f;

        if (m.slotIcon.get()) {
            ItemStack icon = m.pickIcon(st, items);
            if (!icon.isEmpty()) {
                float scale = switch (m.iconSize.get()) { case "Small" -> 0.5f; case "Large" -> 0.75f; default -> 0.625f; };
                float size = 16 * scale;
                drawingIcon = true;
                try {
                    ctx.getMatrices().pushMatrix();
                    ctx.getMatrices().translate(x + 16 - size + 0.5f, y + 16 - size - (m.capacityBar.get() && fill > 0 ? 1.5f : 0f));
                    ctx.getMatrices().scale(scale, scale);
                    ctx.drawItem(icon, 0, 0);
                    ctx.getMatrices().popMatrix();
                } finally {
                    drawingIcon = false;
                }
            }
        }
        if (m.capacityBar.get() && fill > 0f) {
            int w = Math.max(1, Math.round(13 * fill));
            int col = fill >= 0.999f ? 0xFFFF5A5A : fill >= 0.75f ? 0xFFFFC857 : 0xFF5BE38A;
            ctx.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
            ctx.fill(x + 2, y + 13, x + 2 + w, y + 14, col);
        }
    }

    /** Which item to show: the shulker's name if it names an item, else by the "Icon Shows" setting. */
    private ItemStack pickIcon(ItemStack shulker, DefaultedList<ItemStack> items) {
        if (nameIcon.get()) {
            net.minecraft.text.Text custom = shulker.get(DataComponentTypes.CUSTOM_NAME);
            if (custom != null) {
                String n = custom.getString().trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(n.contains(":") ? n : "minecraft:" + n);
                if (id != null && net.minecraft.registry.Registries.ITEM.containsId(id)) {
                    net.minecraft.item.Item it = net.minecraft.registry.Registries.ITEM.get(id);
                    if (it != net.minecraft.item.Items.AIR) return new ItemStack(it);
                }
            }
        }
        switch (displayItem.get()) {
            case "First Slot" -> { for (ItemStack s : items) if (!s.isEmpty()) return s; }
            case "Last Slot" -> { for (int i = items.size() - 1; i >= 0; i--) if (!items.get(i).isEmpty()) return items.get(i); }
            default -> {
                java.util.Map<net.minecraft.item.Item, Integer> totals = new java.util.HashMap<>();
                ItemStack best = ItemStack.EMPTY;
                int bestCount = 0;
                for (ItemStack s : items) {
                    if (s.isEmpty()) continue;
                    int t = totals.merge(s.getItem(), s.getCount(), Integer::sum);
                    if (t > bestCount) { bestCount = t; best = s; }
                }
                return best;
            }
        }
        return ItemStack.EMPTY;
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
