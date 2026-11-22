package dev.tempest.mixin;

import dev.tempest.modules.MouseTweaksModule;
import dev.tempest.modules.ShulkerPreviewModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected Slot focusedSlot;
    @Shadow protected boolean cursorDragging;
    @Shadow @org.spongepowered.asm.mixin.Final protected Set<Slot> cursorDragSlots;
    @Shadow protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);
    @Shadow protected abstract Slot getSlotAt(double x, double y);

    @Unique private final Set<Slot> tempest$visited = new HashSet<>();
    @Unique private boolean tempest$collected;

    // ---- Shulker preview ----
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void tempest$shulker(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        HandledScreen<?> self = (HandledScreen<?>) (Object) this;
        if (ShulkerPreviewModule.render(context, focusedSlot, mouseX, mouseY, self.getScreenHandler())) ci.cancel();
    }

    // ---- Mouse tweaks ----
    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void tempest$click(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        tempest$visited.clear();
        tempest$collected = false;
        Slot s = getSlotAt(click.x(), click.y());
        if (s != null) tempest$visited.add(s);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void tempest$drag(Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != 0) return;
        Slot slot = getSlotAt(click.x(), click.y());
        if (slot == null || tempest$visited.contains(slot)) return;

        HandledScreen<?> self = (HandledScreen<?>) (Object) this;
        ItemStack cursor = self.getScreenHandler().getCursorStack();
        boolean shift = dev.tempest.util.Keys.shift();

        if (shift && cursor.isEmpty() && MouseTweaksModule.shiftDrag()) {
            tempest$visited.add(slot);
            if (slot.hasStack()) onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
            cir.setReturnValue(true);
        } else if (!shift && !cursor.isEmpty() && MouseTweaksModule.collect() && slot.hasStack()
                && ItemStack.areItemsAndComponentsEqual(cursor, slot.getStack())
                && cursor.getCount() + slot.getStack().getCount() <= cursor.getMaxCount()) {
            tempest$visited.add(slot);
            tempest$collected = true;
            onMouseClick(slot, slot.id, 0, SlotActionType.PICKUP);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void tempest$release(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (tempest$collected) {
            tempest$collected = false;
            tempest$visited.clear();
            cursorDragging = false;
            cursorDragSlots.clear();
            cir.setReturnValue(true);
        }
    }
}
