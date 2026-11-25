package dev.tempest.mixin;

import dev.tempest.gui.Draw;
import dev.tempest.modules.ChatTabsModule;
import dev.tempest.util.Emojis;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Chat Tabs: clicks on the tab strip, and messages typed in a Tempest tab (Private / Party / a group).
 * Emoji autocomplete: type ":so" and a Discord-style list pops up above the chat box (Tab / Enter picks,
 * Up / Down moves, Esc hides it, or click one).
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow protected TextFieldWidget chatField;

    @Unique private static final int TEMPEST_ROW_H = 12, TEMPEST_MAX = 8, TEMPEST_POP_W = 170;
    @Unique private List<String> tempest$emojis = List.of();
    @Unique private int tempest$colon = -1;      // index of the ':' that starts the code being typed
    @Unique private int tempest$sel;
    @Unique private String tempest$lastQuery = "";
    @Unique private String tempest$dismissed;    // Esc hides the list until the code changes

    protected ChatScreenMixin(Text title) { super(title); }

    // ---------------------------------------------------------------- emoji autocomplete
    /** Finds the ":code" right before the cursor and refreshes the list. */
    @Unique
    private void tempest$update() {
        tempest$emojis = List.of();
        tempest$colon = -1;
        if (chatField == null) return;
        String s = chatField.getText();
        int cur = Math.min(chatField.getCursor(), s.length());
        int i = cur;
        while (i > 0) {
            char c = s.charAt(i - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+') i--;
            else break;
        }
        if (i == 0 || s.charAt(i - 1) != ':' || cur - i < 2) return;
        int colon = i - 1;
        if (colon > 0 && Character.isLetterOrDigit(s.charAt(colon - 1))) return;   // "12:30", "http:" etc.
        String q = s.substring(i, cur);
        if (q.equals(tempest$dismissed)) return;
        tempest$dismissed = null;
        List<String> found = Emojis.suggest(q, TEMPEST_MAX);
        if (found.isEmpty()) return;
        if (!q.equals(tempest$lastQuery)) tempest$sel = 0;
        tempest$lastQuery = q;
        tempest$sel = Math.max(0, Math.min(tempest$sel, found.size() - 1));
        tempest$emojis = found;
        tempest$colon = colon;
    }

    /** Replaces the ":code" with ":name: ". */
    @Unique
    private void tempest$complete(int idx) {
        if (tempest$colon < 0 || idx < 0 || idx >= tempest$emojis.size()) return;
        String s = chatField.getText();
        int cur = Math.min(chatField.getCursor(), s.length());
        String after = s.substring(cur);
        String ins = ":" + tempest$emojis.get(idx) + ":" + (after.startsWith(" ") ? "" : " ");
        chatField.setText(s.substring(0, tempest$colon) + ins + after);
        chatField.setCursor(tempest$colon + ins.length(), false);
        tempest$emojis = List.of();
        tempest$colon = -1;
    }

    @Unique private int tempest$popX() { return 2; }
    @Unique private int tempest$popH() { return 14 + tempest$emojis.size() * TEMPEST_ROW_H + 4; }
    @Unique private int tempest$popY() { return this.height - 16 - tempest$popH(); }
    @Unique private int tempest$rowY(int k) { return tempest$popY() + 16 + k * TEMPEST_ROW_H; }

    @Unique
    private int tempest$rowAt(double mx, double my) {
        if (tempest$emojis.isEmpty() || mx < tempest$popX() || mx >= tempest$popX() + TEMPEST_POP_W) return -1;
        for (int k = 0; k < tempest$emojis.size(); k++) if (my >= tempest$rowY(k) && my < tempest$rowY(k) + TEMPEST_ROW_H) return k;
        return -1;
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void tempest$drawEmojis(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        tempest$update();
        if (tempest$emojis.isEmpty()) return;
        int hov = tempest$rowAt(mx, my);
        if (hov >= 0) tempest$sel = hov;
        int x = tempest$popX(), y = tempest$popY(), w = TEMPEST_POP_W, h = tempest$popH();
        Draw.panel(ctx, x, y, w, h, 6, 0xF0141220, 0x40FFFFFF);
        Draw.text(ctx, this.textRenderer, "EMOJI MATCHING :" + tempest$lastQuery, x + 7, y + 5, 0xFF8E89A8, false);
        for (int k = 0; k < tempest$emojis.size(); k++) {
            int ry = tempest$rowY(k);
            String name = tempest$emojis.get(k);
            if (k == tempest$sel) Draw.roundRect(ctx, x + 3, ry, w - 6, TEMPEST_ROW_H, 4, Draw.withAlpha(Draw.accent(), 0x60));
            ctx.drawText(this.textRenderer, Emojis.icon(name), x + 7, ry + 2, 0xFFFFFFFF, false);
            Draw.text(ctx, this.textRenderer, Draw.ellipsize(this.textRenderer, ":" + name + ":", w - 30), x + 21, ry + 2,
                    k == tempest$sel ? 0xFFFFFFFF : 0xFFC9C5DD, false);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$emojiKeys(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        tempest$update();
        if (tempest$emojis.isEmpty()) return;
        int key = input.key();
        switch (key) {
            case 258, 257, 335 -> tempest$complete(tempest$sel);                         // Tab, Enter, keypad Enter
            case 265 -> tempest$sel = (tempest$sel + tempest$emojis.size() - 1) % tempest$emojis.size();   // Up
            case 264 -> tempest$sel = (tempest$sel + 1) % tempest$emojis.size();          // Down
            case 256 -> { tempest$dismissed = tempest$lastQuery; tempest$emojis = List.of(); } // Esc
            default -> { return; }
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$tabClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == 0) {
            int k = tempest$rowAt(click.x(), click.y());
            if (k >= 0) { tempest$complete(k); cir.setReturnValue(true); return; }
        }
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
