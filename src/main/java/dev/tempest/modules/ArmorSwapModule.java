package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.util.Chat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class ArmorSwapModule extends Module {
    private final KeyBinding key = bindKey("armor_swap", "Swap Key", InputUtil.UNKNOWN_KEY.getCode());

    public ArmorSwapModule() { super("armor_swap", "Armor Swap", "One key to swap chestplate <-> elytra.", Category.QOL, true); }

    @Override
    public void onTick(MinecraftClient c) {
        while (key.wasPressed()) swap(c);
    }

    private static boolean isChestplate(ItemStack s) {
        EquippableComponent e = s.get(DataComponentTypes.EQUIPPABLE);
        return e != null && e.slot() == EquipmentSlot.CHEST && !s.isOf(Items.ELYTRA);
    }

    private void swap(MinecraftClient c) {
        if (c.player == null || c.interactionManager == null || c.currentScreen != null) return;
        PlayerInventory inv = c.player.getInventory();
        boolean wearingElytra = c.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);

        int found = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (wearingElytra ? isChestplate(s) : s.isOf(Items.ELYTRA)) { found = i; break; }
        }
        if (found < 0) { Chat.info(wearingElytra ? "No chestplate in your inventory." : "No elytra in your inventory."); return; }

        int slotId = found < 9 ? 36 + found : found;   // player screen handler slot ids
        int sync = c.player.playerScreenHandler.syncId;
        c.interactionManager.clickSlot(sync, slotId, 0, SlotActionType.PICKUP, c.player);
        c.interactionManager.clickSlot(sync, 6, 0, SlotActionType.PICKUP, c.player);   // 6 = chest armor slot
        c.interactionManager.clickSlot(sync, slotId, 0, SlotActionType.PICKUP, c.player);
    }
}
