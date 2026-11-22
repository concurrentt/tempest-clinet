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
    private static final Identifier SPARK = tex("aura_spark"), ORB = tex("aura_orb"), RING = tex("aura_ring"), FLAME = tex("aura_flame"),
            HEART = tex("aura_heart"), SNOW = tex("aura_snow"), NOTE = tex("aura_note");
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
            if (!CosmeticsModule.auraName(m).equals("None")) aura(matrices, queue, m, age);
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

    // ------------------------------------------------------------------ auras (own sprites, drawn with the player)
    // Model space here: y points down, feet at y = 1.5, neck at 0, top of the head at about -0.5.
    private static final float GOLDEN = 2.3999632f;

    private static float frac(float v) { return v - (float) Math.floor(v); }

    private static int argb(int rgb, float a) { return (Math.round(Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF); }

    private static void aura(MatrixStack matrices, OrderedRenderCommandQueue queue, CosmeticsModule m, float age) {
        float t = age * m.auraSpeed.getFloat();
        int accent = dev.tempest.gui.Draw.accent() & 0xFFFFFF;
        int light = dev.tempest.gui.Draw.lerpColor(0xFF000000 | accent, 0xFFFFFFFF, 0.45f) & 0xFFFFFF;
        switch (CosmeticsModule.auraName(m)) {
            case "Sparkles" -> queue.submitCustom(matrices, RenderLayers.entityTranslucent(SPARK), (e, vc) -> {
                for (int i = 0; i < 12; i++) {
                    float ang = t * 0.045f + i * GOLDEN;
                    float rad = 0.55f + 0.15f * (float) Math.sin(i * 1.7f);
                    float k = frac(t * 0.012f + i * 0.137f);                    // 0..1 rising
                    float y = 1.45f - k * 2.1f;
                    float size = 0.085f * (0.55f + 0.45f * (float) Math.sin(t * 0.35f + i * 2.1f));
                    float a = (float) Math.sin(Math.PI * k);
                    sprite(vc, e, (float) Math.cos(ang) * rad, y, (float) Math.sin(ang) * rad, size, argb(i % 3 == 0 ? 0xFFFFFF : light, a));
                }
            });
            case "Hearts" -> queue.submitCustom(matrices, RenderLayers.entityTranslucent(HEART), (e, vc) -> {
                for (int i = 0; i < 5; i++) {
                    float k = frac(t * 0.010f + i * 0.2f);
                    float ang = i * GOLDEN;
                    float y = 0.2f - k * 1.3f;
                    float sway = 0.08f * (float) Math.sin(t * 0.08f + i);
                    float rad = 0.42f + 0.1f * k;
                    float a = k < 0.15f ? k / 0.15f : 1f - (k - 0.15f) / 0.85f;
                    sprite(vc, e, (float) Math.cos(ang) * rad + sway, y, (float) Math.sin(ang) * rad, 0.1f + 0.03f * k, argb(0xFFFFFF, a));
                }
            });
            case "Flames" -> queue.submitCustom(matrices, RenderLayers.entityTranslucent(FLAME), (e, vc) -> {
                for (int i = 0; i < 9; i++) {
                    float ang = i * (float) (Math.PI * 2 / 9) + t * 0.015f;
                    float flick = 0.8f + 0.2f * (float) Math.sin(t * 0.6f + i * 1.9f) + 0.1f * (float) Math.sin(t * 1.3f + i);
                    float hgt = 0.42f * flick, w = 0.13f;
                    float x = (float) Math.cos(ang) * 0.48f, z = (float) Math.sin(ang) * 0.48f;
                    standing(vc, e, x, 1.5f, z, w, hgt, argb(0xFFFFFF, 0.9f));
                }
            });
            case "Snow" -> queue.submitCustom(matrices, RenderLayers.entityTranslucent(SNOW), (e, vc) -> {
                for (int i = 0; i < 14; i++) {
                    float k = frac(t * 0.006f + i * 0.0714f);               // 0..1 falling
                    float ang = i * GOLDEN + (float) Math.sin(t * 0.02f + i) * 0.3f;
                    float rad = 0.35f + 0.45f * frac(i * 0.618f);
                    float y = -1.1f + k * 2.6f;
                    float a = Math.min(1f, Math.min(k * 5f, (1f - k) * 5f));
                    sprite(vc, e, (float) Math.cos(ang) * rad, y, (float) Math.sin(ang) * rad, 0.06f, argb(0xFFFFFF, a * 0.9f));
                }
            });
            case "Notes" -> queue.submitCustom(matrices, RenderLayers.entityTranslucent(NOTE), (e, vc) -> {
                for (int i = 0; i < 4; i++) {
                    float k = frac(t * 0.009f + i * 0.25f);
                    float ang = i * (float) (Math.PI / 2) + t * 0.01f;
                    float y = -0.3f - k * 1.0f;
                    float rad = 0.45f + 0.15f * (float) Math.sin(t * 0.05f + i);
                    float a = Math.min(1f, Math.min(k * 6f, (1f - k) * 3f));
                    int rgb = dev.tempest.gui.Draw.hsvToRgb(frac(t * 0.004f + i * 0.25f), 0.55f, 1f) & 0xFFFFFF;
                    sprite(vc, e, (float) Math.cos(ang) * rad, y, (float) Math.sin(ang) * rad, 0.11f, argb(rgb, a));
                }
            });
            case "Rings" -> queue.submitCustom(matrices, RenderLayers.entityTranslucent(RING), (e, vc) -> {
                float pulse = frac(t * 0.02f);
                flat(vc, e, 0f, 0.7f, 0f, 0.7f, (float) Math.toRadians(14 * Math.sin(t * 0.03f)), t * 0.03f, argb(light, 0.85f));
                flat(vc, e, 0f, 0.2f, 0f, 0.55f, (float) Math.toRadians(-18 * Math.sin(t * 0.025f + 1)), -t * 0.04f, argb(accent, 0.7f));
                flat(vc, e, 0f, 1.48f, 0f, 0.4f + pulse * 0.6f, 0f, 0f, argb(accent, 0.8f * (1f - pulse)));
            });
            default -> {        // Theme Orbit: three glowing orbs circling the waist, each with a fading trail
                queue.submitCustom(matrices, RenderLayers.entityTranslucent(ORB), (e, vc) -> {
                    for (int o = 0; o < 3; o++) {
                        for (int s = 0; s < 7; s++) {
                            float ang = t * 0.09f + o * (float) (Math.PI * 2 / 3) - s * 0.12f;
                            float y = 0.65f + 0.25f * (float) Math.sin(ang * 1.5f + o);
                            float size = 0.11f * (1f - s * 0.12f);
                            float a = 1f - s / 7f;
                            sprite(vc, e, (float) Math.cos(ang) * 0.72f, y, (float) Math.sin(ang) * 0.72f, size, argb(s == 0 ? light : accent, a));
                        }
                    }
                });
            }
        }
    }

    /** A glowing sprite as three crossed quads (both sides), so it looks round from any angle. */
    private static void sprite(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, float s, int col) {
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI / 3;
            float dx = (float) Math.cos(a) * s, dz = (float) Math.sin(a) * s;
            vert(vc, e, x - dx, y - s, z - dz, 0, 0, col, FULL_BRIGHT);
            vert(vc, e, x + dx, y - s, z + dz, 1, 0, col, FULL_BRIGHT);
            vert(vc, e, x + dx, y + s, z + dz, 1, 1, col, FULL_BRIGHT);
            vert(vc, e, x - dx, y + s, z - dz, 0, 1, col, FULL_BRIGHT);
            vert(vc, e, x - dx, y + s, z - dz, 0, 1, col, FULL_BRIGHT);
            vert(vc, e, x + dx, y + s, z + dz, 1, 1, col, FULL_BRIGHT);
            vert(vc, e, x + dx, y - s, z + dz, 1, 0, col, FULL_BRIGHT);
            vert(vc, e, x - dx, y - s, z - dz, 0, 0, col, FULL_BRIGHT);
        }
    }

    /** A sprite standing on (x, baseY, z) and rising h, crossed twice (flames). */
    private static void standing(VertexConsumer vc, MatrixStack.Entry e, float x, float baseY, float z, float w, float h, int col) {
        for (int k = 0; k < 2; k++) {
            double a = k * Math.PI / 2;
            float dx = (float) Math.cos(a) * w, dz = (float) Math.sin(a) * w;
            vert(vc, e, x - dx, baseY - h, z - dz, 0, 0, col, FULL_BRIGHT);
            vert(vc, e, x + dx, baseY - h, z + dz, 1, 0, col, FULL_BRIGHT);
            vert(vc, e, x + dx, baseY, z + dz, 1, 1, col, FULL_BRIGHT);
            vert(vc, e, x - dx, baseY, z - dz, 0, 1, col, FULL_BRIGHT);
            vert(vc, e, x - dx, baseY, z - dz, 0, 1, col, FULL_BRIGHT);
            vert(vc, e, x + dx, baseY, z + dz, 1, 1, col, FULL_BRIGHT);
            vert(vc, e, x + dx, baseY - h, z + dz, 1, 0, col, FULL_BRIGHT);
            vert(vc, e, x - dx, baseY - h, z - dz, 0, 0, col, FULL_BRIGHT);
        }
    }

    /** A flat ring sprite around (x, y, z), tilted about X and spun about Y (both sides). */
    private static void flat(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, float r, float tilt, float spin, int col) {
        float[][] c = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt), cs = (float) Math.cos(spin), ss = (float) Math.sin(spin);
        float[][] p = new float[4][3];
        for (int i = 0; i < 4; i++) {
            float px = c[i][0], pz = c[i][1];
            float rx = px * cs - pz * ss, rz = px * ss + pz * cs;   // spin
            p[i][0] = x + rx;
            p[i][1] = y + rz * st;                                     // tilt
            p[i][2] = z + rz * ct;
        }
        for (int i = 0; i < 4; i++) vert(vc, e, p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1], col, FULL_BRIGHT);
        for (int i = 3; i >= 0; i--) vert(vc, e, p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1], col, FULL_BRIGHT);
    }

    private static void vert(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int argb, int light) {
        vc.vertex(entry, x, y, z).color(argb).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, -1, 0);
    }
}
