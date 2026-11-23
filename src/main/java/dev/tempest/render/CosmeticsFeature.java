package dev.tempest.render;

import dev.tempest.modules.CosmeticsModule;
import dev.tempest.util.RemoteCosmetics;
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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import dev.tempest.modules.CapesModule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Draws Tempest cosmetics: wings on the back, and a halo / horns / crown / ears / hat on the head - on you, and on
 * other Tempest players who share theirs (util/RemoteCosmetics). Wings are three hinged panels per side so they
 * fold and ripple as they flap.
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
        if (state.invisible) return;
        try {
            RemoteCosmetics.Look look = lookFor(state.id);
            if (look == null) return;
            float age = state.age + (state.id % 97) * 1.7f;      // players don't flap in sync
            BipedEntityModel<PlayerEntityRenderState> model = getContextModel();
            Fit fit = fit(state.id, look);
            if (look.hasWings() && !fit.elytra) {                 // an elytra already sits on the back: never both
                matrices.push();
                model.body.applyTransform(matrices);
                wings(matrices, queue, light, look, age, state.isGliding, fit.backZ);
                matrices.pop();
            }
            if (look.hasHead()) {
                matrices.push();
                model.head.applyTransform(matrices);
                head(matrices, queue, light, look, age, fit.top, fit.half);
                matrices.pop();
            }
        } catch (RuntimeException e) {
            // never break player rendering over a cosmetic
        }
    }

    /**
     * Where cosmetics have to sit so armor never hides them: the top and half-width of the head *including* a helmet
     * (or a worn block / skull, or the skin's hat layer), how far behind the body the wings start (clear of a
     * chestplate and a cape), and whether an elytra is worn.
     */
    private record Fit(float top, float half, float backZ, boolean elytra) { }

    private static Fit fit(int entityId, RemoteCosmetics.Look look) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player != null && entityId == mc.player.getId() ? mc.player
                : mc.world != null && mc.world.getEntityById(entityId) instanceof PlayerEntity pe ? pe : null;
        ItemStack headStack = p == null ? ItemStack.EMPTY : p.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack chest = p == null ? ItemStack.EMPTY : p.getEquippedStack(EquipmentSlot.CHEST);
        float top, half;
        if (headStack.isEmpty()) { top = -0.536f; half = 0.2813f; }                         // head + hat layer (0.5 px)
        else if (headStack.getItem() instanceof BlockItem) { top = -0.625f; half = 0.33f; }  // pumpkin, skull, block hat
        else { top = -0.570f; half = 0.3125f; }                                              // helmet (1 px out)
        boolean elytra = chest.isOf(Items.ELYTRA) || chest.contains(DataComponentTypes.GLIDER);
        boolean chestplate = !chest.isEmpty() && !elytra;
        boolean cape = mc.player != null && entityId == mc.player.getId() ? CapesModule.selected() != null
                : look.cape() != null && !look.cape().isEmpty() && !look.cape().equalsIgnoreCase("None");
        // body back is at z 0.125; jacket layer +0.03, chestplate +0.0625, cape another ~0.07 behind that
        float backZ = 0.125f + (chestplate ? 0.075f : 0.04f) + (cape ? 0.07f : 0f);
        return new Fit(top, half, backZ, elytra);
    }

    /** Your own look (with any try-on preview), or another Tempest player's shared look. */
    private static RemoteCosmetics.Look lookFor(int entityId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && entityId == mc.player.getId()) {
            CosmeticsModule m = CosmeticsModule.forRender();
            if (m == null) return null;
            return new RemoteCosmetics.Look(null, CosmeticsModule.wingsName(m), CosmeticsModule.headName(m), m.haloColor.get(),
                    m.wingSize.getFloat(), m.flapSpeed.getFloat());
        }
        if (mc.world == null) return null;
        Entity e = mc.world.getEntityById(entityId);
        return e instanceof PlayerEntity p ? RemoteCosmetics.of(p.getUuid()) : null;
    }

    // ------------------------------------------------------------------ wings
    /**
     * How each wing moves: [base sweep (deg), flap amplitude (deg), bend at joint 1, bend at joint 2 (deg), lift].
     * Feathered and bat wings fold at two joints; insect wings are stiff and just beat.
     */
    private static float[] wingStyle(String name) {
        return switch (name) {
            case "Demon", "Dragon" -> new float[]{30, 15, 16, 22, 0.05f};
            case "Butterfly", "Fairy" -> new float[]{22, 20, 3, 3, 0.02f};
            case "Crystal" -> new float[]{28, 12, 8, 10, 0.03f};
            default -> new float[]{28, 16, 12, 18, 0.05f};       // Angel, Phoenix: feathered
        };
    }

    private static final float[] SPLIT = {0f, 0.34f, 0.68f, 1f};

    private static void wings(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, RemoteCosmetics.Look look, float age,
                              boolean gliding, float rootZ) {
        Identifier texture = tex("wings_" + look.wings().toLowerCase(java.util.Locale.ROOT));
        float[] st = wingStyle(look.wings());
        float size = look.wingSize();
        float ph = age * 0.18f * look.flapSpeed();
        float glideOpen = gliding ? 0.45f : 1f;           // spread wide while gliding
        double base = Math.toRadians(st[0] * glideOpen), amp = Math.toRadians(st[1] * (gliding ? 0.4f : 1f));
        final float rootX = 0.06f, rootY = 0.20f;
        final float w = 0.95f * size, h = 0.95f * size;
        final float top = -0.32f * h, bottom = 0.68f * h;          // the root sits a third of the way down the texture
        // joint positions (x outward, z back) and a little lift toward the tip, wave travelling outward
        float[] jx = new float[4], jz = new float[4], jy = new float[4];
        double dir = base + amp * Math.sin(ph);
        jx[0] = rootX; jz[0] = rootZ; jy[0] = 0;
        for (int k = 1; k < 4; k++) {
            float len = (SPLIT[k] - SPLIT[k - 1]) * w;
            jx[k] = jx[k - 1] + len * (float) Math.cos(dir);
            jz[k] = jz[k - 1] + len * (float) Math.sin(dir);
            jy[k] = jy[k - 1] - st[4] * size * (float) Math.sin(ph - k * 0.5f);
            if (k < 3) dir += Math.toRadians(st[k + 1]) * (0.6 + 0.4 * Math.sin(ph - k * 0.6));
        }
        RenderLayer layer = RenderLayers.entityTranslucent(texture);
        for (int side = -1; side <= 1; side += 2) {
            final int sd = side;
            queue.submitCustom(matrices, layer, (entry, vc) -> {
                for (int k = 0; k < 3; k++) {
                    float u0 = SPLIT[k], u1 = SPLIT[k + 1];
                    vert(vc, entry, sd * jx[k], rootY + top + jy[k], jz[k], u0, 0, 0xFFFFFFFF, light);
                    vert(vc, entry, sd * jx[k + 1], rootY + top + jy[k + 1], jz[k + 1], u1, 0, 0xFFFFFFFF, light);
                    vert(vc, entry, sd * jx[k + 1], rootY + bottom + jy[k + 1], jz[k + 1], u1, 1, 0xFFFFFFFF, light);
                    vert(vc, entry, sd * jx[k], rootY + bottom + jy[k], jz[k], u0, 1, 0xFFFFFFFF, light);
                }
            });
        }
    }

    // ------------------------------------------------------------------ head pieces
    /**
     * Head pieces, placed on the fitted head: T is the top of the head / helmet (model y, up is negative), H its
     * half-width. Hats and halos sit on top of it, crowns are square bands just outside it, and horns / ears start
     * right at its top edge - so a helmet never swallows them. k scales pieces to the head they sit on.
     */
    private static void head(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, RemoteCosmetics.Look look, float age,
                             float T, float H) {
        float k = H / 0.2813f;
        switch (look.head()) {
            case "Halo" -> {
                float y = T - 0.17f + 0.025f * (float) Math.sin(age * 0.1f);
                float r = 0.27f * k, g = 0.40f * k;
                int col = 0xFF000000 | look.haloRgb();
                int glow = 0x50000000 | look.haloRgb();
                queue.submitCustom(matrices, RenderLayers.entityTranslucent(HALO), (entry, vc) -> {
                    // soft outer glow, then the ring itself
                    vert(vc, entry, -g, y + 0.002f, -g, 0, 0, glow, FULL_BRIGHT);
                    vert(vc, entry, g, y + 0.002f, -g, 1, 0, glow, FULL_BRIGHT);
                    vert(vc, entry, g, y + 0.002f, g, 1, 1, glow, FULL_BRIGHT);
                    vert(vc, entry, -g, y + 0.002f, g, 0, 1, glow, FULL_BRIGHT);
                    vert(vc, entry, -r, y, -r, 0, 0, col, FULL_BRIGHT);
                    vert(vc, entry, r, y, -r, 1, 0, col, FULL_BRIGHT);
                    vert(vc, entry, r, y, r, 1, 1, col, FULL_BRIGHT);
                    vert(vc, entry, -r, y, r, 0, 1, col, FULL_BRIGHT);
                });
            }
            case "Devil Horns" -> {
                for (int side = -1; side <= 1; side += 2)
                    crossed(matrices, queue, HORN, side * H * 0.55f, T + 0.03f, -0.08f * k, 0.13f * k, 0.30f * k, side * 0.07f * k, light);
            }
            case "Cat Ears" -> {
                for (int side = -1; side <= 1; side += 2) {
                    final int sd = side;
                    queue.submitCustom(matrices, RenderLayers.entityTranslucent(CAT_EAR), (entry, vc) -> {
                        float bx = sd * H * 0.62f, by = T + 0.03f, z = -0.04f * k, hw = 0.10f * k, hh = 0.20f * k, lean = sd * 0.04f * k;
                        vert(vc, entry, bx - hw, by, z, 0, 1, 0xFFFFFFFF, light);
                        vert(vc, entry, bx + hw, by, z, 1, 1, 0xFFFFFFFF, light);
                        vert(vc, entry, bx + hw + lean, by - hh, z, 1, 0, 0xFFFFFFFF, light);
                        vert(vc, entry, bx - hw + lean, by - hh, z, 0, 0, 0xFFFFFFFF, light);
                    });
                }
            }
            case "Crown" -> band(matrices, queue, CROWN, H + 0.012f, T + 0.07f, T - 0.15f, 1, FULL_BRIGHT);
            case "Bunny Ears" -> {
                for (int side = -1; side <= 1; side += 2) {
                    final int sd = side;
                    queue.submitCustom(matrices, RenderLayers.entityTranslucent(BUNNY), (entry, vc) -> {
                        float bx = sd * H * 0.43f, by = T + 0.03f, z = 0.02f * k, hw = 0.075f * k, hh = 0.40f * k, lean = sd * 0.06f * k;
                        vert(vc, entry, bx - hw, by, z, 0, 1, 0xFFFFFFFF, light);
                        vert(vc, entry, bx + hw, by, z, 1, 1, 0xFFFFFFFF, light);
                        vert(vc, entry, bx + hw + lean, by - hh, z + 0.06f * k, 1, 0, 0xFFFFFFFF, light);
                        vert(vc, entry, bx - hw + lean, by - hh, z + 0.06f * k, 0, 0, 0xFFFFFFFF, light);
                    });
                }
            }
            case "Antlers" -> {
                for (int side = -1; side <= 1; side += 2)
                    crossed(matrices, queue, ANTLER, side * H * 0.6f, T + 0.03f, 0.0f, 0.21f * k, 0.42f * k, side * 0.10f * k, light);
            }
            case "Flower Crown" -> band(matrices, queue, FLOWERS, H + 0.016f, T + 0.08f, T - 0.06f, 2, light);
            case "Witch Hat" -> {
                brim(matrices, queue, WITCH, 0.50f * k, T - 0.003f, light);
                cone(matrices, queue, WITCH, 0.30f * k, T - 0.003f, T - 0.70f * k, 0.16f * k, light);
            }
            case "Top Hat" -> {
                brim(matrices, queue, TOP_HAT, 0.42f * k, T - 0.003f, light);
                ring(matrices, queue, TOP_HAT, 0.27f * k, T - 0.003f, T - 0.50f * k, 16, 1, light);
                cap(matrices, queue, TOP_HAT, 0.27f * k, T - 0.50f * k, light);
            }
            default -> { }
        }
    }

    /**
     * A square band hugging the head just outside it (crowns): four double-sided walls at +-h, the texture repeated
     * `perSide` times along each wall. Being outside the head/helmet box, nothing can poke through it.
     */
    private static void band(MatrixStack matrices, OrderedRenderCommandQueue queue, Identifier texture, float h, float y0, float y1,
                             int perSide, int light) {
        float[][] c = {{-h, -h}, {h, -h}, {h, h}, {-h, h}};
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, vc) -> {
            for (int i = 0; i < 4; i++) {
                float[] a = c[i], b = c[(i + 1) % 4];
                for (int j = 0; j < perSide; j++) {
                    float t0 = (float) j / perSide, t1 = (float) (j + 1) / perSide;
                    float xa = a[0] + (b[0] - a[0]) * t0, za = a[1] + (b[1] - a[1]) * t0;
                    float xb = a[0] + (b[0] - a[0]) * t1, zb = a[1] + (b[1] - a[1]) * t1;
                    vert(vc, entry, xa, y0, za, 0, 1, 0xFFFFFFFF, light);
                    vert(vc, entry, xb, y0, zb, 1, 1, 0xFFFFFFFF, light);
                    vert(vc, entry, xb, y1, zb, 1, 0, 0xFFFFFFFF, light);
                    vert(vc, entry, xa, y1, za, 0, 0, 0xFFFFFFFF, light);
                    vert(vc, entry, xa, y1, za, 0, 0, 0xFFFFFFFF, light);
                    vert(vc, entry, xb, y1, zb, 1, 0, 0xFFFFFFFF, light);
                    vert(vc, entry, xb, y0, zb, 1, 1, 0xFFFFFFFF, light);
                    vert(vc, entry, xa, y0, za, 0, 1, 0xFFFFFFFF, light);
                }
            }
        });
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
