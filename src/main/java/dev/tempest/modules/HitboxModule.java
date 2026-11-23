package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Combat Hitboxes: recolours the vanilla hitboxes (F3 + B). A hitbox turns red only while your crosshair is on that
 * entity and it is inside your reach (and no block is in the way), so you can see exactly when a hit would land. Done in mixin/HitboxColorMixin, inside vanilla's own hitbox
 * drawing, so it costs nothing extra.
 */
public class HitboxModule extends Module {
    private final ColorSetting reachColor = add(new ColorSetting("reach_color", "Targeted Color", 0xFFFF2A2A));
    private final ColorSetting normalColor = add(new ColorSetting("normal_color", "Normal Color", 0xFFFFFFFF));
    private final BoolSetting playersOnly = add(new BoolSetting("players_only", "Reach Color On Players Only", false));
    private final BoolSetting myReach = add(new BoolSetting("my_reach", "Use My Reach", true));
    private final NumberSetting customReach = add(new NumberSetting("custom_reach", "Custom Reach", 3.0, 1.0, 6.0, 0.1));

    public HitboxModule() {
        super("hitboxes", "Combat Hitboxes", "Vanilla hitboxes (F3 + B) turn red when your crosshair is on them and they're within your reach.", Category.PVP, false);
        keyHint = "Turn hitboxes on with F3 + B.";
    }

    /** Colour for an entity's hitbox box, given vanilla's colour (white, or green for the singleplayer server box). */
    public static int color(Entity e, int vanilla) {
        HitboxModule m = ModuleManager.get(HitboxModule.class);
        if (m == null || !m.isEnabled() || e == null) return vanilla;
        if (vanilla != -1) return vanilla;            // leave the green server-side box alone
        return m.inReach(e) ? m.reachColor.get() : m.normalColor.get();
    }

    private boolean inReach(Entity e) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || e == c.player || e.isSpectator()) return false;
        if (playersOnly.get() && !(e instanceof PlayerEntity)) return false;
        double reach = myReach.get() ? c.player.getEntityInteractionRange() : customReach.get();
        // the line from your eyes along your crosshair, as long as your reach
        Vec3d eye = c.player.getEyePos();
        Vec3d end = eye.add(c.player.getRotationVec(1f).multiply(reach));
        Box box = e.getBoundingBox().expand(e.getTargetingMargin());
        Vec3d hit = box.contains(eye) ? eye : box.raycast(eye, end).orElse(null);
        if (hit == null) return false;
        // a block between you and them blocks the hit
        if (c.crosshairTarget instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK
                && bh.getPos().squaredDistanceTo(eye) < hit.squaredDistanceTo(eye)) return false;
        return true;
    }
}
