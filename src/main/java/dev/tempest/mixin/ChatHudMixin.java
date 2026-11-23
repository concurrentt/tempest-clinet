package dev.tempest.mixin;

import dev.tempest.module.ModuleManager;
import dev.tempest.modules.ChatMentionsModule;
import dev.tempest.modules.ChatUpgradesModule;
import dev.tempest.modules.MessageStackingModule;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements dev.tempest.util.ChatRefresher {
    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow private void refresh() { }   // private in 1.21.11; body is replaced by the real method

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true)
    private Text tempest$process(Text message) {
        if (dev.tempest.util.TempestChat.adding) return message;   // our own Tempest chat line: leave it exactly as is
        Text out = message;
        String raw = message.getString();
        dev.tempest.modules.ChatTabsModule.onMessage(raw);

        if (ModuleManager.enabled(MessageStackingModule.class)) {
            if (raw.equals(MessageStackingModule.lastRaw) && !messages.isEmpty()) {
                MessageStackingModule.count++;
                messages.remove(0);
                refresh();
                out = message.copy().append(Text.literal(" (x" + MessageStackingModule.count + ")").formatted(Formatting.GRAY));
            } else {
                MessageStackingModule.lastRaw = raw;
                MessageStackingModule.count = 1;
            }
        }
        return ChatUpgradesModule.decorate(ChatMentionsModule.process(out, raw), raw);
    }

    @Override
    public void tempest$refreshChat() { refresh(); }

    // ---- Chat Animation: count the lines each new message adds, then draw the chat sliding into place ----
    @Shadow @Final private List<?> visibleMessages;
    @Shadow private int scrolledLines;
    @org.spongepowered.asm.mixin.Unique private boolean tempest$refreshing;
    @org.spongepowered.asm.mixin.Unique private int tempest$before;
    @org.spongepowered.asm.mixin.Unique private boolean tempest$slid;

    @org.spongepowered.asm.mixin.injection.Inject(method = "refresh", at = @At("HEAD"), require = 0)
    private void tempest$refreshStart(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { tempest$refreshing = true; }

    @org.spongepowered.asm.mixin.injection.Inject(method = "refresh", at = @At("RETURN"), require = 0)
    private void tempest$refreshEnd(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { tempest$refreshing = false; }

    @org.spongepowered.asm.mixin.injection.Inject(method = "addVisibleMessage", at = @At("HEAD"), require = 0)
    private void tempest$countBefore(ChatHudLine message, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        tempest$before = visibleMessages.size();
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "addVisibleMessage", at = @At("RETURN"), require = 0)
    private void tempest$countAfter(ChatHudLine message, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!tempest$refreshing && scrolledLines == 0) dev.tempest.modules.ChatAnimationModule.onLinesAdded(visibleMessages.size() - tempest$before);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At("HEAD"), require = 0)
    private void tempest$slideStart(net.minecraft.client.gui.DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int tick, int mx, int my,
                                    boolean interactable, boolean b, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        float off = scrolledLines == 0 ? dev.tempest.modules.ChatAnimationModule.offset() : 0f;
        tempest$slid = off > 0.01f;
        if (!tempest$slid) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        ctx.enableScissor(0, 0, w, h - 40 + 1);          // the chat's bottom edge
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0f, off);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At("RETURN"), require = 0)
    private void tempest$slideEnd(net.minecraft.client.gui.DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int tick, int mx, int my,
                                  boolean interactable, boolean b, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!tempest$slid) return;
        tempest$slid = false;
        ctx.getMatrices().popMatrix();
        ctx.disableScissor();
    }

    /** Chat Tabs / hide filters: lines that don't belong to the current tab are not shown (they stay in history). */
    @org.spongepowered.asm.mixin.injection.Inject(method = "addVisibleMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$tabFilter(ChatHudLine message, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!dev.tempest.modules.ChatTabsModule.visible(message.content())) ci.cancel();
    }

    /** Vanilla keeps 100 lines; this raises the limit (stored lines and wrapped visible lines). */
    @ModifyConstant(method = {"addMessage", "addVisibleMessage"}, constant = @Constant(intValue = 100))
    private int tempest$history(int original) {
        return ChatUpgradesModule.historyLimit();
    }
}
