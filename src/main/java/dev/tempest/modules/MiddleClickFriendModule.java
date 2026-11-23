package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.util.Chat;
import dev.tempest.util.Social;
import dev.tempest.util.TempestNet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/** Middle click a player to send them a Tempest friend request (they glow once they accept). */
public final class MiddleClickFriendModule extends Module {
    public MiddleClickFriendModule() {
        super("middle_click_friend", "Middle Click Friend", "Middle click a player to send them a Tempest friend request.", Category.CLIENT, false);
    }

    public static boolean onMiddleClick(MinecraftClient c) {
        if (!ModuleManager.enabled(MiddleClickFriendModule.class)) return false;
        if (c == null || c.player == null || c.crosshairTarget == null) return false;
        if (c.crosshairTarget.getType() != HitResult.Type.ENTITY) return false;
        if (!(c.crosshairTarget instanceof EntityHitResult ehr)) return false;
        if (!(ehr.getEntity() instanceof PlayerEntity player)) return false;
        if (player == c.player) return false;

        String name = player.getName().getString();
        if (Social.isFriend(player.getUuid())) {
            Chat.info(name + " is already your friend.");
        } else if (player.getUuid().version() != 4) {
            Chat.info(name + " isn't a Microsoft account, so they can't be added.");
        } else if (!TempestNet.signedIn()) {
            Chat.info("Not connected to Tempest yet - try again in a moment.");
        } else {
            Social.request(player.getUuid(), msg -> { if (msg != null) Chat.info(msg); });
        }
        return true;
    }
}
