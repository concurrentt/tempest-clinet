package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.TempestConfig;
import dev.tempest.gui.WaypointScreen;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.NumberSetting;
import dev.tempest.util.Chat;
import dev.tempest.util.Waypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/** Waypoints drawn as on-screen markers (projected from the camera), with an optional beam line. */
public class WaypointsModule extends Module {
    private final KeyBinding addKey = bindKey("waypoint_add", "Add Waypoint Key", InputUtil.UNKNOWN_KEY.getCode());
    private final BoolSetting beam = add(new BoolSetting("beam", "Beam Line", true));
    private final BoolSetting distance = add(new BoolSetting("distance", "Show Distance", true));
    private final NumberSetting maxDist = add(new NumberSetting("max", "Max Distance", 5000, 100, 30000, 100));

    public WaypointsModule() {
        super("waypoints", "Waypoints", "Markers with distance. Deaths are added automatically.", Category.RENDER, true);
        add(new ActionSetting("manage", "Manage Waypoints", "Open", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            c.setScreen(new WaypointScreen(c.currentScreen));
        }));
    }

    @Override
    public void onTick(MinecraftClient c) {
        while (addKey.wasPressed()) addHere(c);
    }

    public static void addHere(MinecraftClient c) {
        if (c.player == null || c.world == null) return;
        BlockPos p = c.player.getBlockPos();
        int n = TempestConfig.get().waypoints.size();
        TempestConfig.get().waypoints.add(new Waypoint("Waypoint " + (n + 1), p.getX(), p.getY(), p.getZ(),
                c.world.getRegistryKey().getValue().toString(), ColorSetting.PALETTE[n % ColorSetting.PALETTE.length]));
        TempestConfig.save();
        Chat.info("Waypoint added.");
    }

    public void render(DrawContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null || c.currentScreen != null || StreamerModeModule.hideCoords()) return;
        if (TempestConfig.get().waypoints.isEmpty()) return;

        String dim = c.world.getRegistryKey().getValue().toString();
        Vec3d eye = c.player.getEyePos();
        double yaw = Math.toRadians(c.player.getYaw()), pitch = Math.toRadians(c.player.getPitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -Math.sin(yaw) * Math.sin(pitch), uy = Math.cos(pitch), uz = Math.cos(yaw) * Math.sin(pitch);

        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        double tv = Math.tan(Math.toRadians(c.options.getFov().getValue()) / 2.0);
        double th = tv * W / (double) H;
        TextRenderer tr = c.textRenderer;

        for (Waypoint wp : TempestConfig.get().waypoints) {
            if (!dim.equals(wp.dim)) continue;
            double dx = wp.x + 0.5 - eye.x, dy = wp.y + 0.5 - eye.y, dz = wp.z + 0.5 - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > maxDist.get()) continue;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth <= 0.1) continue;
            int sx = (int) (W / 2.0 + (dx * rx + dz * rz) / depth / th * (W / 2.0));
            int sy = (int) (H / 2.0 - (dx * ux + dy * uy + dz * uz) / depth / tv * (H / 2.0));
            if (sx < 4 || sx > W - 4 || sy < 4 || sy > H - 4) continue;

            int col = wp.color;
            if (beam.get()) {
                int beamA = 0x38;
                ctx.fillGradient(sx, 0, sx + 1, sy, Draw.withAlpha(col, 0x00), Draw.withAlpha(col, beamA));
                ctx.fillGradient(sx, sy, sx + 1, H, Draw.withAlpha(col, beamA), Draw.withAlpha(col, 0x00));
                ctx.fillGradient(sx - 1, sy - 18, sx, sy + 18, Draw.withAlpha(col, 0x00), Draw.withAlpha(col, 0x18));
                ctx.fillGradient(sx + 1, sy - 18, sx + 2, sy + 18, Draw.withAlpha(col, 0x00), Draw.withAlpha(col, 0x18));
            }

            // Marker dot with subtle glow
            Draw.circle(ctx, sx, sy, 5, 0xA0000000);
            Draw.circle(ctx, sx, sy, 4, Draw.withAlpha(col, 0xCC));
            Draw.circle(ctx, sx, sy, 2, 0xFFFFFFFF);
            Draw.circle(ctx, sx, sy, 7, Draw.withAlpha(col, 0x28));

            String ds = String.format(Locale.ROOT, "%.0fm", dist);
            String name = wp.name;
            int nameW = tr.getWidth(name);
            int distW = distance.get() ? tr.getWidth(ds) : 0;
            int padX = 7;
            int gap = distance.get() ? 6 : 0;
            int lw = nameW + gap + distW + padX * 2;
            int lh = 16;
            int lx = sx - lw / 2;
            int ly = sy - 26;

            Draw.shadow(ctx, lx, ly, lw, lh, 4, 3);
            Draw.panel(ctx, lx, ly, lw, lh, 5, 0xB8101422, Draw.withAlpha(col, 0x55));
            Draw.roundRect(ctx, lx + 3, ly + 4, 2, lh - 8, 1, col);
            ctx.drawText(tr, name, lx + padX + 2, ly + 4, 0xFFF0F4FF, false);
            if (distance.get()) {
                ctx.drawText(tr, ds, lx + lw - padX - distW, ly + 4, 0xFF9AA4C0, false);
            }
        }
    }
}
