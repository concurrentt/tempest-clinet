package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

/**
 * Block Outline: recolours and thickens Minecraft's own outline (mixin/WorldRendererOutlineMixin) and can fill the
 * targeted block with a translucent colour. Everything is drawn in the world pass, so it costs next to nothing.
 */
public final class BlockOutlineModule extends Module {
    private final ColorSetting outlineColor = add(new ColorSetting("outline_color", "Outline Color", 0xFF000000));
    private final NumberSetting outlineAlpha = add(new NumberSetting("outline_alpha", "Outline Opacity", 0.6, 0.1, 1.0, 0.05));
    private final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", 1.5, 0.5, 4.0, 0.25));
    private final BoolSetting fill = add(new BoolSetting("overlay", "Fill", false));
    private final ColorSetting fillColor = add(new ColorSetting("overlay_color", "Fill Color", 0xFFFFFFFF));
    private final NumberSetting fillAlpha = add(new NumberSetting("fill_alpha", "Fill Opacity", 0.2, 0.05, 0.8, 0.05));

    private static boolean fillFailed;

    public BlockOutlineModule() {
        super("block_outline", "Block Outline", "Change the outline colour and thickness, and fill the block you're looking at.", Category.VISUALS, false);
    }

    private static BlockOutlineModule on() {
        BlockOutlineModule m = ModuleManager.get(BlockOutlineModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public static int color(int vanilla) {
        BlockOutlineModule m = on();
        if (m == null) return vanilla;
        int a = (int) Math.round(m.outlineAlpha.get() * 255);
        return (a << 24) | (m.outlineColor.get() & 0xFFFFFF);
    }

    public static float width(float vanilla) {
        BlockOutlineModule m = on();
        return m == null ? vanilla : vanilla * m.thickness.getFloat();
    }

    /**
     * Fills the targeted block. {@code camX/Y/Z} are the camera position that vanilla's outline was drawn
     * relative to; the matrix is the outline's own.
     */
    public static void drawFill(Matrix4f matrix, double camX, double camY, double camZ) {
        BlockOutlineModule m = on();
        if (m == null || !m.fill.get() || fillFailed) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.player == null || !(c.crosshairTarget instanceof BlockHitResult bhr)
                || c.crosshairTarget.getType() != HitResult.Type.BLOCK) return;
        try {
            BlockPos pos = bhr.getBlockPos();
            VoxelShape shape = c.world.getBlockState(pos).getOutlineShape(c.world, pos, ShapeContext.of(c.player));
            if (shape.isEmpty()) return;
            int a = (int) Math.round(m.fillAlpha.get() * 255);
            int argb = (a << 24) | (m.fillColor.get() & 0xFFFFFF);
            RenderLayer layer = RenderLayers.debugQuads();
            BufferBuilder b = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
            for (Box box : shape.getBoundingBoxes()) {
                Box w = box.offset(pos).expand(0.002);
                face(b, matrix, w, camX, camY, camZ, argb);
            }
            BuiltBuffer built = b.endNullable();
            if (built != null) layer.draw(built);
        } catch (Throwable t) {
            fillFailed = true;   // never keep failing every frame
            System.err.println("[Tempest] Block Outline fill turned off: " + t);
        }
    }

    private static void face(BufferBuilder b, Matrix4f m, Box w, double cx, double cy, double cz, int c) {
        float x0 = (float) (w.minX - cx), y0 = (float) (w.minY - cy), z0 = (float) (w.minZ - cz);
        float x1 = (float) (w.maxX - cx), y1 = (float) (w.maxY - cy), z1 = (float) (w.maxZ - cz);
        float[][] q = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},   // bottom
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},   // top
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},   // north
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},   // south
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},   // west
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};  // east
        for (float[] f : q) {
            for (int i = 0; i < 4; i++) b.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(c);
        }
    }
}
