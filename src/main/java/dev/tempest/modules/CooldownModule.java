package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public class CooldownModule extends HudModule {
    private final BoolSetting pearl = add(new BoolSetting("pearl", "Ender Pearl", true));
    private final BoolSetting shield = add(new BoolSetting("shield", "Shield", true));
    private final BoolSetting chorus = add(new BoolSetting("chorus", "Chorus Fruit", false));
    private final BoolSetting eating = add(new BoolSetting("eating", "Eating / Gap Timer", true));

    public CooldownModule() { super("cooldowns", "Cooldowns", "Pearl, shield and eating timers.", Category.PVP, false, 120, 250); }

    private record Row(ItemStack icon, String label, float progress) { }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        List<Row> rows = new ArrayList<>();
        if (preview || c.player == null) {
            rows.add(new Row(new ItemStack(Items.ENDER_PEARL), "Pearl", 0.6f));
            rows.add(new Row(new ItemStack(Items.GOLDEN_APPLE), "Eating", 0.3f));
        } else {
            ClientPlayerEntity p = c.player;
            if (pearl.get()) add(rows, p, new ItemStack(Items.ENDER_PEARL), "Pearl");
            if (shield.get()) add(rows, p, new ItemStack(Items.SHIELD), "Shield");
            if (chorus.get()) add(rows, p, new ItemStack(Items.CHORUS_FRUIT), "Chorus");
            if (eating.get() && p.isUsingItem() && p.getActiveItem().get(DataComponentTypes.FOOD) != null) {
                int used = p.getItemUseTime(), left = p.getItemUseTimeLeft();
                float f = used / (float) Math.max(1, used + left);
                rows.add(new Row(p.getActiveItem().copy(), "Eating", f));
            }
        }
        if (rows.isEmpty()) return;

        TextRenderer tr = c.textRenderer;
        w = 90;
        h = rows.size() * 18 + 4;
        box(ctx);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = y + 2 + i * 18;
            ctx.drawItem(r.icon(), x + 2, ry);
            ctx.drawText(tr, r.label(), x + 22, ry, color.get(), true);
            Draw.roundRect(ctx, x + 22, ry + 10, w - 26, 4, 2, 0xFF222838);
            int fw = (int) ((w - 26) * Math.max(0f, Math.min(1f, r.progress())));
            if (fw > 0) Draw.roundRect(ctx, x + 22, ry + 10, Math.max(3, fw), 4, 2, Draw.accent());
        }
    }

    private void add(List<Row> rows, ClientPlayerEntity p, ItemStack icon, String label) {
        float prog = p.getItemCooldownManager().getCooldownProgress(icon, 0f);
        if (prog > 0f) rows.add(new Row(icon, label, prog));
    }
}
