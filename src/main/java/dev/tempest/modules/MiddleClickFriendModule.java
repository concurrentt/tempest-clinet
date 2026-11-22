package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.util.Chat;
import dev.tempest.util.FriendManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/** Middle click a player to add/remove them as a friend. */
public final class MiddleClickFriendModule extends Module {
    public MiddleClickFriendModule() {
        super("middle_click_friend", "Middle Click Friend", "Middle click a player to add or remove them as a friend.", Category.UTILITY, false);
    }

    public static boolean onMiddleClick(MinecraftClient c) {
        if (!ModuleManager.enabled(MiddleClickFriendModule.class)) return false;
        if (c == null || c.player == null || c.crosshairTarget == null) return false;
        if (c.crosshairTarget.getType() != HitResult.Type.ENTITY) return false;
        if (!(c.crosshairTarget instanceof EntityHitResult ehr)) return false;
        if (!(ehr.getEntity() instanceof PlayerEntity player)) return false;
        if (player == c.player) return false;

        String name = player.getName().getString();
        if (FriendManager.isFriend(name)) {
            FriendManager.removeFriend(name);
            Chat.info("Removed " + name + " from friends.");
        } else {
            FriendManager.addFriend(name);
            Chat.info("Added " + name + " as a friend.");
        }
        return true;
    }
}
