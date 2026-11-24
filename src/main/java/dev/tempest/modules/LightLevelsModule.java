package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;

/**
 * Light Levels: marks every block around you where monsters can spawn.
 * <ul>
 *   <li><b>Red</b> - no block light and little sky light: monsters can spawn here any time (caves, dark rooms).</li>
 *   <li><b>Yellow</b> - no block light but open to the sky: monsters spawn here at night.</li>
 * </ul>
 * Since 1.18 monsters only spawn where the block light is 0, so placing any light source clears the mark.
 * Toggle with its key (F7 by default) or in the mods menu. Only drawn on your screen.
 */
public class LightLevelsModule extends Module {
    private final KeyBinding toggleKey = bindKey("light_levels", "Toggle Key", org.lwjgl.glfw.GLFW.GLFW_KEY_F7);
    private final NumberSetting range = add(new NumberSetting("range", "Range", 16, 8, 24, 4));

    private static final int RED = 0x70FF3B3B, YELLOW = 0x70FFD23B;
    private static final int MAX_MARKS = 6000;

    /** Marked spots: x, y, z of the air block above the floor, and the colour. Rebuilt a few times a second. */
    private static volatile List<int[]> marks = List.of();
    private int scanTimer;

    public LightLevelsModule() {
        super("light_levels", "Light Levels", "Shows where monsters can spawn: red any time, yellow at night.", Category.UTILITY, false);
        keyHint = "Press F7 (change it in Controls) to turn it on and off. Light up red and yellow spots to stop spawns.";
    }

    @Override
    public void onInit() {
        // the key works while the module is off too (that's how you turn it on)
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (toggleKey.wasPressed()) {
                if (c.currentScreen != null) continue;
                toggle();
                dev.tempest.util.Chat.info("Light Levels " + (isEnabled() ? "on" : "off"));
            }
        });
    }

    @Override
    public void onDisable() { marks = List.of(); }

    @Override
    public void onTick(MinecraftClient c) {
        if (c.world == null || c.player == null) { marks = List.of(); return; }
        if (scanTimer-- > 0) return;
        scanTimer = 5;                                   // 4 scans a second
        marks = scan(c.world, c.player.getBlockPos(), range.getInt());
    }

    private static List<int[]> scan(ClientWorld w, BlockPos center, int r) {
        List<int[]> out = new ArrayList<>();
        BlockPos.Mutable pos = new BlockPos.Mutable(), below = new BlockPos.Mutable();
        int vr = Math.max(6, r / 2);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int dy = -vr; dy <= vr; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!w.isInBuildLimit(pos)) continue;
                    BlockState here = w.getBlockState(pos);
                    if (!here.getCollisionShape(w, pos).isEmpty() || !here.getFluidState().isEmpty()) continue;
                    below.set(pos.getX(), pos.getY() - 1, pos.getZ());
                    BlockState floor = w.getBlockState(below);
                    if (!floor.isSideSolidFullSquare(w, below, Direction.UP)) continue;
                    if (!floor.allowsSpawning(w, below, EntityType.ZOMBIE)) continue;     // e.g. glass, slabs top-half aside
                    if (w.getLightLevel(LightType.BLOCK, pos) > 0) continue;
                    int sky = w.getLightLevel(LightType.SKY, pos);
                    out.add(new int[]{pos.getX(), pos.getY(), pos.getZ(), sky > 7 ? YELLOW : RED});
                    if (out.size() >= MAX_MARKS) return out;
                }
            }
        }
        return out;
    }

    /** Draws the marks as flat squares on the floor (from WorldRendererEffectsMixin; the camera is at the origin). */
    public static void render(MatrixStack matrices, OrderedRenderCommandQueue queue) {
        LightLevelsModule m = ModuleManager.get(LightLevelsModule.class);
        List<int[]> list = marks;
        if (m == null || !m.isEnabled() || list.isEmpty()) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.gameRenderer == null) return;
        Vec3d cam = c.gameRenderer.getCamera().getCameraPos();
        try {
            queue.submitCustom(matrices, RenderLayers.debugQuads(), (entry, vc) -> {
                for (int[] k : list) {
                    float x0 = (float) (k[0] + 0.1 - cam.x), x1 = (float) (k[0] + 0.9 - cam.x);
                    float z0 = (float) (k[2] + 0.1 - cam.z), z1 = (float) (k[2] + 0.9 - cam.z);
                    float y = (float) (k[1] + 0.02 - cam.y);
                    int col = k[3];
                    vc.vertex(entry, x0, y, z0).color(col);
                    vc.vertex(entry, x0, y, z1).color(col);
                    vc.vertex(entry, x1, y, z1).color(col);
                    vc.vertex(entry, x1, y, z0).color(col);
                }
            });
        } catch (Throwable ignored) { }
    }
}
