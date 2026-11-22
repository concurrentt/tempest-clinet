package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class TooltipInfoModule extends Module {
    public TooltipInfoModule() { super("tooltip_info", "Tooltip Info", "Adds durability to item tooltips without F3+H.", Category.UTILITY, true); }

    public static void addTooltip(ItemStack stack, TooltipType type, List<Text> lines) {
        TooltipInfoModule m = ModuleManager.get(TooltipInfoModule.class);
        if (m == null || !m.isEnabled() || type.isAdvanced() || !stack.isDamageable()) return;
        int max = stack.getMaxDamage();
        int left = max - stack.getDamage();
        float f = left / (float) Math.max(1, max);
        Formatting col = f > 0.6f ? Formatting.GREEN : f > 0.3f ? Formatting.YELLOW : Formatting.RED;
        lines.add(Text.literal("Durability: " + left + " / " + max + " (" + (int) (f * 100) + "%)").formatted(col));
    }
}
