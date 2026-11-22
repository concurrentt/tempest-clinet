package dev.tempest.mixin;

import dev.tempest.modules.AppleSkinModule;
import dev.tempest.modules.ScoreboardModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;
    @Unique private boolean tempest$scoreboardMoved;

    @Inject(method = "renderFood(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;II)V", at = @At("RETURN"))
    private void tempest$appleSkinFoodOverlay(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
        AppleSkinModule.renderFoodOverlay(context, player, top, right);
    }

    /** No crosshair poking through the Tempest menus. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$hideCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (client.currentScreen instanceof dev.tempest.gui.TempestMenuScreen || client.currentScreen instanceof dev.tempest.gui.ClickGuiScreen
                || client.currentScreen instanceof dev.tempest.gui.HudEditorScreen) ci.cancel();
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("RETURN"))
    private void tempest$potionTimers(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (client.player == null) return;
        Collection<StatusEffectInstance> effects = client.player.getStatusEffects();
        if (effects.isEmpty()) return;

        int screenW = context.getScaledWindowWidth();
        int x = screenW - 25;
        int y = 1;
        int beneficialCount = 0;
        for (StatusEffectInstance effect : effects) {
            if (!effect.shouldShowIcon()) continue;
            boolean beneficial = effect.getEffectType().value().isBeneficial();
            // vanilla draws beneficial and non-beneficial in separate rows
            int drawY = beneficial ? y : y + 26;
            String dur = effect.isInfinite() ? "∞" : formatSeconds(effect.getDuration() / 20);
            context.getMatrices().pushMatrix();
            float scale = 0.7f;
            context.getMatrices().scale(scale, scale);
            int tw = client.textRenderer.getWidth(dur);
            int ox = (int) ((x + 22 - tw * scale) / scale);
            int oy = (int) ((drawY + 22 - 7 * scale) / scale);
            context.drawText(client.textRenderer, dur, ox, oy, 0xFFFFFFFF, true);
            context.getMatrices().popMatrix();

            x -= 25;
            if (beneficialCount++ > 100) break; // safety
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempest$scoreboardHide(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        tempest$scoreboardMoved = false;
        if (ScoreboardModule.shouldHide()) {
            ci.cancel();
            return;
        }
        int x = ScoreboardModule.offsetX();
        int y = ScoreboardModule.offsetY();
        if (x != 0 || y != 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            tempest$scoreboardMoved = true;
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("RETURN"), require = 0)
    private void tempest$scoreboardPop(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (tempest$scoreboardMoved) {
            context.getMatrices().popMatrix();
            tempest$scoreboardMoved = false;
        }
    }

    private static String formatSeconds(int seconds) {
        if (seconds >= 3600) return (seconds / 3600) + ":" + String.format("%02d:%02d", (seconds % 3600) / 60, seconds % 60);
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }
}
