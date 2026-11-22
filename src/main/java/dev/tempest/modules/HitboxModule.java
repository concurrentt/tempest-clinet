package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import dev.tempest.util.CombatTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Combat Hitboxes: recolours the vanilla hitboxes (F3 + B). A hitbox turns red the moment that entity is inside
 * your reach, so you can see when a hit would land. Done in mixin/HitboxColorMixin, inside vanilla's own hitbox
 * drawing, so it costs nothing extra.
 */
public class HitboxModule extends Module {
    private final ColorSetting reachColor = add(new ColorSetting("reach_color", "In Reach Color", 0xFFFF2A2A));
    private final ColorSetting normalColor = add(new ColorSetting("normal_color", "Normal Color", 0xFFFFFFFF));
    private final BoolSetting playersOnly = add(new BoolSetting("players_only", "Reach Color On Players Only", false));
    private final BoolSetting myReach = add(new BoolSetting("my_reach", "Use My Reach", true));
    private final NumberSetting customReach = add(new NumberSetting("custom_reach", "Custom Reach", 3.0, 1.0, 6.0, 0.1));

    public HitboxModule() {
        super("hitboxes", "Combat Hitboxes", "Vanilla hitboxes (F3 + B) turn red when the entity is within your reach.", Category.PVP, false);
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
        var eye = c.player.getEyePos();
        return eye.squaredDistanceTo(CombatTracker.closestPoint(e.getBoundingBox(), eye)) <= reach * reach;
    }
}
