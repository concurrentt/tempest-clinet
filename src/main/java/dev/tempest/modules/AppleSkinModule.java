package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

/** AppleSkin-like overlays drawn directly over the vanilla hunger bar. */
public class AppleSkinModule extends HudModule {
    private final BoolSetting tooltips = add(new BoolSetting("tooltips", "Food Tooltips", true));
    private final BoolSetting preview = add(new BoolSetting("preview", "Food Preview", true));
    private final BoolSetting saturationBar = add(new BoolSetting("saturation", "Saturation Overlay", true));

    public AppleSkinModule() {
        super("appleskin", "AppleSkin", "Food values, saturation and held-food previews on the vanilla HUD.", Category.HUD, true, 6, 210);
    }

    /** This module no longer draws a separate panel; its visuals belong on vanilla's food bar. */
    @Override
    public void renderHud(DrawContext ctx, boolean editor) { }

    private static final int GOLD = 0xFFFFC21A, GOLD_PREVIEW = 0xFFFFE680;

    public static void renderFoodOverlay(DrawContext ctx, PlayerEntity player, int top, int right) {
        AppleSkinModule module = ModuleManager.get(AppleSkinModule.class);
        if (module == null || !module.isEnabled() || player == null) return;

        HungerManager hunger = player.getHungerManager();
        int food = hunger.getFoodLevel();
        float saturation = Math.max(0f, Math.min(20f, hunger.getSaturationLevel()));
        ItemStack heldStack = getHeldFood(player);
        FoodComponent heldFood = heldStack.get(DataComponentTypes.FOOD);
        float pulse = 0.55f + 0.45f * (float) ((Math.sin((System.currentTimeMillis() % 100000L) / 180.0) + 1.0) * 0.5);

        boolean previewing = module.preview.get() && heldFood != null && !heldStack.isEmpty();
        int predictedFood = food;
        float predictedSat = saturation;
        if (previewing) {
            predictedFood = Math.min(20, food + heldFood.nutrition());
            predictedSat = Math.min(predictedFood, saturation + heldFood.nutrition() * heldFood.saturation() * 2.0f);
        }

        // Restored hunger: flashing shanks where the held food would fill the bar.
        if (previewing) {
            int alpha = (int) (0xCC * pulse);
            for (int i = food / 2; i < (predictedFood + 1) / 2 && i < 10; i++) {
                int iconX = right - i * 8 - 9;
                int levelInIcon = predictedFood - i * 2;
                Identifier tex = Identifier.ofVanilla(levelInIcon >= 2 ? "hud/food_full" : "hud/food_half");
                ctx.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, iconX, top, 9, 9, (alpha << 24) | 0xFFFFFF);
            }
        }

        // Saturation: a gold outline around each shank, like AppleSkin. Each shank holds 2 saturation and its
        // outline fills in quarters, right to left in the same order as the hunger bar.
        if (module.saturationBar.get()) {
            for (int i = 0; i < 10; i++) {
                int iconX = right - i * 8 - 9;
                float have = saturation - i * 2;
                float gain = predictedSat - i * 2;
                if (have > 0) outline(ctx, iconX, top, Math.min(2f, have) / 2f, GOLD);
                if (previewing && gain > Math.max(0, have)) {
                    int a = (int) (0xFF * pulse);
                    outline(ctx, iconX, top, Math.min(2f, gain) / 2f, (a << 24) | (GOLD_PREVIEW & 0xFFFFFF));
                }
            }
        }
    }

    // ------------------------------------------------------------------ shank outline
    /** Outline pixels of the 9x9 hunger icon, in drawing order (starting at the bone, going round). */
    private static int[][] OUTLINE;

    /** Draws `fraction` (0..1, rounded to quarters) of the shank outline at the icon position. */
    private static void outline(DrawContext ctx, int x, int y, float fraction, int argb) {
        int[][] px = outlinePixels();
        float q = (float) Math.floor(fraction * 4f + 1e-4f) / 4f;
        if (q <= 0) q = 0.25f;                       // any saturation at all shows the first quarter
        int n = (int) Math.ceil(px.length * q);
        for (int k = 0; k < n; k++) ctx.fill(x + px[k][0], y + px[k][1], x + px[k][0] + 1, y + px[k][1] + 1, argb);
    }

    private static int[][] outlinePixels() {
        if (OUTLINE != null) return OUTLINE;
        boolean[][] mask = readIconMask();
        if (mask == null) mask = builtInMask();
        List<int[]> edge = new java.util.ArrayList<>();
        for (int yy = 0; yy < 9; yy++) {
            for (int xx = 0; xx < 9; xx++) {
                if (!mask[yy][xx]) continue;
                boolean border = xx == 0 || yy == 0 || xx == 8 || yy == 8
                        || !mask[yy][xx - 1] || !mask[yy][xx + 1] || !mask[yy - 1][xx] || !mask[yy + 1][xx];
                if (border) edge.add(new int[]{xx, yy});
            }
        }
        // order the pixels around the icon, starting at the bottom-left (the bone) and going clockwise
        double start = Math.atan2(4, -4);
        edge.sort((p1, p2) -> Double.compare(angleFrom(p1, start), angleFrom(p2, start)));
        OUTLINE = edge.toArray(new int[0][]);
        return OUTLINE;
    }

    private static double angleFrom(int[] p, double start) {
        double a = Math.atan2(p[1] - 4, p[0] - 4) - start;
        while (a < 0) a += Math.PI * 2;
        return a;
    }

    /** Reads the hunger icon from the active resource packs so the outline always matches the shank you see. */
    private static boolean[][] readIconMask() {
        try {
            var mc = MinecraftClient.getInstance();
            var res = mc.getResourceManager().getResource(Identifier.ofVanilla("textures/gui/sprites/hud/food_full.png"));
            if (res.isEmpty()) return null;
            try (java.io.InputStream in = res.get().getInputStream()) {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
                if (img == null) return null;
                int w = img.getWidth(), h = Math.min(img.getHeight(), w);   // animated strips: first frame only
                boolean[][] mask = new boolean[9][9];
                int count = 0;
                for (int gy = 0; gy < 9; gy++) {
                    for (int gx = 0; gx < 9; gx++) {
                        int sx = Math.min(w - 1, (gx * w + w / 2) / 9), sy = Math.min(h - 1, (gy * h + h / 2) / 9);
                        mask[gy][gx] = ((img.getRGB(sx, sy) >>> 24) & 255) > 64;
                        if (mask[gy][gx]) count++;
                    }
                }
                return count >= 8 ? mask : null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** Fallback drumstick shape: meat top-right, bone bottom-left. */
    private static boolean[][] builtInMask() {
        String[] rows = {
                ".....###.",
                "....#####",
                "...######",
                "...######",
                "..######.",
                ".#####...",
                "####.....",
                "###......",
                ".#......."};
        boolean[][] m = new boolean[9][9];
        for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) m[y][x] = rows[y].charAt(x) == '#';
        return m;
    }

    /** Resource reloads (F3+T, new pack) should re-read the icon. */
    public static void resetOutline() { OUTLINE = null; }

    private static ItemStack getHeldFood(PlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main.get(DataComponentTypes.FOOD) != null) return main;
        ItemStack off = player.getOffHandStack();
        if (off.get(DataComponentTypes.FOOD) != null) return off;
        return ItemStack.EMPTY;
    }

    public static void addTooltip(ItemStack stack, List<Text> lines) {
        AppleSkinModule module = ModuleManager.get(AppleSkinModule.class);
        if (module == null || !module.isEnabled() || !module.tooltips.get()) return;
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food == null) return;

        float saturation = food.nutrition() * food.saturation() * 2.0f;
        lines.add(Text.literal("When eaten:").formatted(Formatting.GRAY));
        lines.add(Text.literal("  +" + food.nutrition() + " Food Points").formatted(Formatting.GOLD));
        lines.add(Text.literal(String.format(Locale.ROOT, "  +%.1f Saturation", saturation)).formatted(Formatting.YELLOW));
    }
}
