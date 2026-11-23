package dev.tempest.render;

import dev.tempest.modules.CosmeticsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Draws Tempest cosmetics on the local player: wings on the back, and a halo / horns / crown / cat ears on the head.
 *
 * <p>Runs as a player feature (like the cape). Positions are in model space, where 1 unit = 1 block and +Y is
 * DOWN; after {@code body.applyTransform} the origin is the neck with the back toward +Z, after
 * {@code head.applyTransform} the origin is the neck with the head going up to y = -0.5. Everything is
 * submitted as plain textured quads through {@code submitCustom}.</p>
 */
public class CosmeticsFeature extends FeatureRenderer<PlayerEntityRenderState, BipedEntityModel<PlayerEntityRenderState>> {
    private static final Identifier HALO = tex("halo"), HORN = tex("horn"), CROWN = tex("crown"), CAT_EAR = tex("cat_ear");
    private static final Identifier BUNNY = tex("bunny_ear"), ANTLER = tex("antler"), FLOWERS = tex("flower_crown"),
            WITCH = tex("witch_hat"), TOP_HAT = tex("top_hat");
    private static final int FULL_BRIGHT = 0xF000F0;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public CosmeticsFeature(FeatureRendererContext context) {
        super(context);
    }

    private static Identifier tex(String n) { return Identifier.of("tempest", "textures/cosmetics/" + n + ".png"); }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        CosmeticsModule m = CosmeticsModule.forRender();
        if (m == null || state.invisible) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || state.id != mc.player.getId()) return;   // only on yourself
        try {
            float age = state.age;
            BipedEntityModel<PlayerEntityRenderState> model = getContextModel();
            if (!CosmeticsModule.wingsName(m).equals("None")) {
                matrices.push();
                model.body.applyTransform(matrices);
                wings(matrices, queue, light, m, age, state.isGliding);
                matrices.pop();
            }
            if (!CosmeticsModule.headName(m).equals("None")) {
                matrices.push();
                model.head.applyTransform(matrices);
                head(matrices, queue, light, m, age);
                matrices.pop();
            }
        } catch (RuntimeException e) {
            // never break player rendering over a cosmetic
        }
    }

    // ------------------------------------------------------------------ wings
    private static void wings(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CosmeticsModule m, float age, boolean gliding) {
        Identifier texture = tex("wings_" + CosmeticsModule.wingsName(m).toLowerCase(java.util.Locale.ROOT));
        float size = m.wingSize.getFloat();
        float flap = (float) Math.sin(age * 0.18f * m.flapSpeed.getFloat());
        // swept back ~28 degrees, flapping +/-16 (wider when gliding)
        double angle = Math.toRadians((gliding ? 12 : 28) + flap * (gliding ? 6 : 16));
        final float rootX = 0.06f, rootY = 0.20f, rootZ = 0.16f;
        final float w = 0.95f * size, h = 0.95f * size;
        final float top = -0.32f * h, bottom = 0.68f * h;          // the root sits a third of the way down the texture
        final float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        RenderLayer layer = RenderLayers.entityTranslucent(texture);
        for (int side = -1; side <= 1; side += 2) {
            final int sd = side;
            queue.submitCustom(matrices, layer, (entry, vc) -> {
                // corners in wing space (x outward along the wing, y down), then swung back around the root
                float[][] c = {{0, top}, {w, top}, {w, bottom}, {0, bottom}};
                float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                for (int k = 0; k < 4; k++) {
                    float x = c[k][0];
                    float px = sd * (rootX + x * cos);
                    float py = rootY + c[k][1];
                    float pz = rootZ + x * sin;
                    vert(vc, entry, px, py, pz, uv[k][0], uv[k][1], 0xFFFFFFFF, light);
                }
            });
        }
    }

    // ------------------------------------------------------------------ head pieces
    private static void head(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CosmeticsModule m, float age) {
        switch (CosmeticsModule.headName(m)) {
            case "Halo" -> {
                float y = -0.68f + 0.025f * (float) Math.sin(age * 0.1f);
                float r = 0.27f;
                int col = 0xFF000000 | m.haloRgb();
                queue.submitCustom(matrices, RenderLayers.entityTranslucent(HALO), (entry, vc) -> {
                    vert(vc, entry, -r, y, -r, 0, 0, col, FULL_BRIGHT);
                    vert(vc, entry, r, y, -r, 1, 0, col, FULL_BRIGHT);
                    vert(vc, entry, r, y, r, 1, 1, col, FULL_BRIGHT);
                    vert(vc, entry, -r, y, r, 0, 1, col, FULL_BRIGHT);
                });
            }
            case "Devil Horns" -> {
                for (int side = -1; side <= 1; side += 2) crossed(matrices, queue, HORN, side * 0.15f, -0.48f, -0.08f, 0.13f, 0.30f, side * 0.07f, light);
            }
            case "Cat Ears" -> {
                for (int side = -1; side <= 1; side += 2) {
                    final int sd = side;
                    queue.submitCustom(matrices, RenderLayers.entityTranslucent(CAT_EAR), (entry, vc) -> {
                        float bx = sd * 0.17f, by = -0.48f, z = -0.04f, hw = 0.10f, hh = 0.20f, lean = sd * 0.04f;
                        vert(vc, entry, bx - hw, by, z, 0, 1, 0xFFFFFFFF, light);
                        vert(vc, entry, bx + hw, by, z, 1, 1, 0xFFFFFFFF, light);
                        vert(vc, entry, bx + hw + lean, by - hh, z, 1, 0, 0xFFFFFFFF, light);
                        vert(vc, entry, bx - hw + lean, by - hh, z, 0, 0, 0xFFFFFFFF, light);
                    });
                }
            }
            case "Crown" -> {
                queue.submitCustom(matrices, RenderLayers.entityTranslucent(CROWN), (entry, vc) -> {
                    final int segs = 8;
                    float r = 0.29f, y0 = -0.47f, y1 = -0.66f;
                    for (int i = 0; i < segs; i++) {
                        double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                        float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
                        float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
                        float u0 = (i % 4) / 4f, u1 = (i % 4 + 1) / 4f;   // the texture holds four crown points
                        vert(vc, entry, x0, y0, z0, u0, 1, 0xFFFFFFFF, FULL_BRIGHT);
                        vert(vc, entry, x1, y0, z1, u1, 1, 0xFFFFFFFF, FULL_BRIGHT);
                        vert(vc, entry, x1, y1, z1, u1, 0, 0xFFFFFFFF, FULL_BRIGHT);
                        vert(vc, entry, x0, y1, z0, u0, 0, 0xFFFFFFFF, FULL_BRIGHT);
                    }
                });
            }
            case "Bunny Ears" -> {
                for (int side = -1; side <= 1; side += 2) {
                    final int sd = side;
                    queue.submitCustom(matrices, RenderLayers.entityTranslucent(BUNNY), (entry, vc) -> {
                        float bx = sd * 0.12f, by = -0.47f, z = 0.02f, hw = 0.075f, hh = 0.40f, lean = sd * 0.06f;
                        vert(vc, entry, bx - hw, by, z, 0, 1, 0xFFFFFFFF, light);
                        vert(vc, entry, bx + hw, by, z, 1, 1, 0xFFFFFFFF, light);
                        vert(vc, entry, bx + hw + lean, by - hh, z + 0.06f, 1, 0, 0xFFFFFFFF, light);
                        vert(vc, entry, bx - hw + lean, by - hh, z + 0.06f, 0, 0, 0xFFFFFFFF, light);
                    });
                }
            }
            case "Antlers" -> {
                for (int side = -1; side <= 1; side += 2) crossed(matrices, queue, ANTLER, side * 0.17f, -0.46f, 0.0f, 0.21f, 0.42f, side * 0.10f, light);
            }
            case "Flower Crown" -> ring(matrices, queue, FLOWERS, 0.295f, -0.40f, -0.55f, 10, 5, light);
            case "Witch Hat" -> {
                brim(matrices, queue, WITCH, 0.50f, -0.48f, light);
                cone(matrices, queue, WITCH, 0.30f, -0.48f, -1.18f, 0.16f, light);
            }
            case "Top Hat" -> {
                brim(matrices, queue, TOP_HAT, 0.42f, -0.48f, light);
                ring(matrices, queue, TOP_HAT, 0.27f, -0.48f, -0.98f, 16, 1, light);
                cap(matrices, queue, TOP_HAT, 0.27f, -0.98f, light);
            }
            default -> { }
        }
    }

    /** An open cylinder around the head (both sides drawn), the texture repeated `repeats` times around it. */
    private static void ring(MatrixStack matrices, OrderedRenderCommandQueue queue, Identifier texture, float r, float y0, float y1,
                             int segs, int repeats, int light) {
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, vc) -> {
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
                float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
                float u0 = (float) i * repeats / segs, u1 = (float) (i + 1) * repeats / segs;
                u0 -= (float) Math.floor(u0); u1 = u0 + (float) repeats / segs;
                vert(vc, entry, x0, y0, z0, u0, 1, 0xFFFFFFFF, light);
                vert(vc, entry, x1, y0, z1, u1, 1, 0xFFFFFFFF, light);
                vert(vc, entry, x1, y1, z1, u1, 0, 0xFFFFFFFF, light);
                vert(vc, entry, x0, y1, z0, u0, 0, 0xFFFFFFFF, light);
                vert(vc, entry, x0, y1, z0, u0, 0, 0xFFFFFFFF, light);
                vert(vc, entry, x1, y1, z1, u1, 0, 0xFFFFFFFF, light);
                vert(vc, entry, x1, y0, z1, u1, 1, 0xFFFFFFFF, light);
                vert(vc, entry, x0, y0, z0, u0, 1, 0xFFFFFFFF, light);
            }
        });
    }

    /** A flat disc (hat brim), both faces, using the texture's lower (band-free) colour. */
    private static void brim(MatrixStack matrices, OrderedRenderCommandQueue queue, Identifier texture, float r, float y, int light) {
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, vc) -> {
            final int segs = 16;
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
                float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
                vert(vc, entry, 0, y, 0, 0.5f, 0.97f, 0xFFFFFFFF, light);
                vert(vc, entry, x0, y, z0, 0.2f, 0.98f, 0xFFFFFFFF, light);
                vert(vc, entry, x1, y, z1, 0.8f, 0.98f, 0xFFFFFFFF, light);
                vert(vc, entry, 0, y, 0, 0.5f, 0.97f, 0xFFFFFFFF, light);
                vert(vc, entry, 0, y, 0, 0.5f, 0.97f, 0xFFFFFFFF, light);
                vert(vc, entry, x1, y, z1, 0.8f, 0.98f, 0xFFFFFFFF, light);
                vert(vc, entry, x0, y, z0, 0.2f, 0.98f, 0xFFFFFFFF, light);
                vert(vc, entry, 0, y, 0, 0.5f, 0.97f, 0xFFFFFFFF, light);
            }
        });
    }

    /** A closed top for a cylinder. */
    private static void cap(MatrixStack matrices, OrderedRenderCommandQueue queue, Identifier texture, float r, float y, int light) {
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, vc) -> {
            final int segs = 16;
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
                float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
                vert(vc, entry, 0, y, 0, 0.5f, 0.05f, 0xFFFFFFFF, light);
                vert(vc, entry, x1, y, z1, 0.6f, 0.08f, 0xFFFFFFFF, light);
                vert(vc, entry, x0, y, z0, 0.4f, 0.08f, 0xFFFFFFFF, light);
                vert(vc, entry, 0, y, 0, 0.5f, 0.05f, 0xFFFFFFFF, light);
            }
        });
    }

    /** A cone from a base ring up to a tip that bends backwards (witch hat). */
    private static void cone(MatrixStack matrices, OrderedRenderCommandQueue queue, Identifier texture, float r, float y0, float tipY,
                             float tipBack, int light) {
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, vc) -> {
            final int segs = 14;
            final int rows = 4;                       // a few rows so the cone can curve backwards toward the tip
            for (int j = 0; j < rows; j++) {
                float t0 = (float) j / rows, t1 = (float) (j + 1) / rows;
                float ra = r * (1 - t0), rb = r * (1 - t1);
                float ya = y0 + (tipY - y0) * t0, yb = y0 + (tipY - y0) * t1;
                float za = tipBack * t0 * t0, zb = tipBack * t1 * t1;
                for (int i = 0; i < segs; i++) {
                    double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                    float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                    float u0 = (float) i / segs, u1 = (float) (i + 1) / segs;
                    float va = 1 - t0 * 0.95f, vb = 1 - t1 * 0.95f;
                    vert(vc, entry, c0 * ra, ya, s0 * ra + za, u0, va, 0xFFFFFFFF, light);
                    vert(vc, entry, c1 * ra, ya, s1 * ra + za, u1, va, 0xFFFFFFFF, light);
                    vert(vc, entry, c1 * rb, yb, s1 * rb + zb, u1, vb, 0xFFFFFFFF, light);
                    vert(vc, entry, c0 * rb, yb, s0 * rb + zb, u0, vb, 0xFFFFFFFF, light);
                    vert(vc, entry, c0 * rb, yb, s0 * rb + zb, u0, vb, 0xFFFFFFFF, light);
                    vert(vc, entry, c1 * rb, yb, s1 * rb + zb, u1, vb, 0xFFFFFFFF, light);
                    vert(vc, entry, c1 * ra, ya, s1 * ra + za, u1, va, 0xFFFFFFFF, light);
                    vert(vc, entry, c0 * ra, ya, s0 * ra + za, u0, va, 0xFFFFFFFF, light);
                }
            }
        });
    }

    /** Two quads crossed at right angles, standing up from (x, y, z), so the piece looks solid from any side. */
    private static void crossed(MatrixStack matrices, OrderedRenderCommandQueue queue, Identifier texture,
                                float x, float y, float z, float halfW, float height, float lean, int light) {
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, vc) -> {
            vert(vc, entry, x - halfW, y, z, 0, 1, 0xFFFFFFFF, light);
            vert(vc, entry, x + halfW, y, z, 1, 1, 0xFFFFFFFF, light);
            vert(vc, entry, x + halfW + lean, y - height, z, 1, 0, 0xFFFFFFFF, light);
            vert(vc, entry, x - halfW + lean, y - height, z, 0, 0, 0xFFFFFFFF, light);

            vert(vc, entry, x, y, z - halfW, 0, 1, 0xFFFFFFFF, light);
            vert(vc, entry, x, y, z + halfW, 1, 1, 0xFFFFFFFF, light);
            vert(vc, entry, x + lean, y - height, z + halfW, 1, 0, 0xFFFFFFFF, light);
            vert(vc, entry, x + lean, y - height, z - halfW, 0, 0, 0xFFFFFFFF, light);
        });
    }

    private static void vert(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int argb, int light) {
        vc.vertex(entry, x, y, z).color(argb).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, -1, 0);
    }
}
