package dev.tempest.mixin;

import dev.tempest.modules.ChatTabsModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat Tabs: clicks on the tab strip, and messages typed in a Tempest tab (Private / Party / a group). */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin(Text title) { super(title); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$tabClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == 0 && ChatTabsModule.click(click.x(), click.y(), this.width, this.height)) cir.setReturnValue(true);
    }

    /** In a Tempest tab, Enter sends to that conversation instead of the server (commands still go to the server). */
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$tempestChat(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (!ChatTabsModule.handleSend(chatText)) return;
        if (addToHistory && this.client != null) this.client.inGameHud.getChatHud().addToMessageHistory(chatText);
        ci.cancel();
    }
}
