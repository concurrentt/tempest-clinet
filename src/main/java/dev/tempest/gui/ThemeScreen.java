package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.module.ModuleManager;
import dev.tempest.modules.ClientSettingsModule;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Theme editor: pick an accent colour (presets, or any hue for the Custom theme) and the menu background scene.
 * Everything applies live, so the screen itself is the preview.
 */
public class ThemeScreen extends Screen {
    private static final String[] PRESETS = {"Tempest", "Midnight", "Crimson", "Emerald", "Violet", "Amber"};
    private static final int[] PRESET_COLORS = {0xFF9B8BCB, 0xFF6C8CFF, 0xFFFF5470, 0xFF38D9A0, 0xFFB085FF, 0xFFFFB347};
    private static final String[] BACKGROUNDS = {"Nebula", "Galaxy", "Aurora", "Black Hole", "Ringed Planet", "Blood Moon", "Animated", "Plain"};
    private static final String[] BG_FILES = {"menu_bg", "menu_bg_galaxy", "menu_bg_aurora", "menu_bg_blackhole", "menu_bg_planet", "menu_bg_bloodmoon", null, null};

    private static final int W = 440, H = 300, HEAD = 40;
    private static final int SW = 26, THUMB_W = 96, THUMB_H = 54, GAP = 8;

    private final Screen parent;
    private boolean draggingHue;

    public ThemeScreen(Screen parent) {
        super(Text.literal("Theme"));
        this.parent = parent;
    }

    private ClientSettingsModule cs() { return ModuleManager.get(ClientSettingsModule.class); }

    private int x0() { return (width - W) / 2; }
    private int y0() { return Math.max(8, (height - H) / 2); }
    private int swX(int i) { return x0() + 20 + i * (SW + 10); }
    private int swY() { return y0() + HEAD + 30; }
    private int hueX() { return x0() + 20; }
    private int hueY() { return swY() + SW + 14; }
    private int hueW() { return W - 40; }
    private int thumbX(int i) { return x0() + 20 + (i % 4) * (THUMB_W + GAP); }
    private int thumbY(int i) { return hueY() + 40 + (i / 4) * (THUMB_H + 20); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        ClientSettingsModule c = cs();
        int x = x0(), y = y0();
        Draw.window(ctx, tr, width, height, x, y, W, H, HEAD, "THEME", "Accent colour and menu background, applied live");

        // --- accent
        Draw.text(ctx, tr, "ACCENT", x + 20, y + HEAD + 14, Draw.DIM, false);
        String current = c == null ? "Tempest" : c.theme.get();
        for (int i = 0; i <= PRESETS.length; i++) {
            boolean custom = i == PRESETS.length;
            int col = custom ? (c == null ? 0xFF4FC3F7 : c.customAccent.get()) : PRESET_COLORS[i];
            boolean sel = custom ? current.equals("Custom") : current.equals(PRESETS[i]);
            boolean hv = Draw.hit(mx, my, swX(i), swY(), SW, SW);
            if (sel) Draw.roundRect(ctx, swX(i) - 3, swY() - 3, SW + 6, SW + 6, 8, 0xFFFFFFFF);
            Draw.roundRect(ctx, swX(i), swY(), SW, SW, 6, col | 0xFF000000);
            if (hv && !sel) Draw.ring(ctx, swX(i) - 2, swY() - 2, SW + 4, SW + 4, 7, 0x90FFFFFF);
            if (custom) Icons.draw(ctx, "palette", swX(i) + 7, swY() + 7, 12, 0xE0FFFFFF);
            if (hv) {
                String n = custom ? "Custom" : PRESETS[i];
                Draw.text(ctx, tr, n, swX(i) + SW / 2 - tr.getWidth(n) / 2, swY() + SW + 3, 0xFFFFFFFF, true);
            }
        }

        // --- hue strip for the custom colour
        int hx = hueX(), hy = hueY(), hw = hueW();
        for (int i = 0; i < hw; i++) ctx.fill(hx + i, hy, hx + i + 1, hy + 10, Draw.hsvToRgb(i / (float) hw, 0.72f, 1f) | 0xFF000000);
        Draw.ring(ctx, hx - 1, hy - 1, hw + 2, 12, 3, 0x60FFFFFF);
        if (c != null && current.equals("Custom")) {
            float hue = Draw.rgbToHsv(c.customAccent.get())[0];
            int kx = hx + Math.round(hue * hw);
            Draw.roundRect(ctx, kx - 2, hy - 3, 5, 16, 2, 0xFFFFFFFF);
        }
        Draw.text(ctx, tr, "Drag the strip for any colour (switches to Custom)", hx, hy + 15, Draw.DIM2, false);

        // --- backgrounds
        Draw.text(ctx, tr, "MENU BACKGROUND", x + 20, hueY() + 28, Draw.DIM, false);
        String bg = c == null ? "Nebula" : c.menuBackground.get();
        float time = MenuKit.time();
        for (int i = 0; i < BACKGROUNDS.length; i++) {
            int tx = thumbX(i), ty = thumbY(i);
            boolean sel = BACKGROUNDS[i].equals(bg), hv = Draw.hit(mx, my, tx, ty, THUMB_W, THUMB_H);
            ctx.enableScissor(tx, ty, tx + THUMB_W, ty + THUMB_H);
            if (BG_FILES[i] != null) {
                Identifier id = Identifier.of("tempest", "textures/gui/" + BG_FILES[i] + ".png");
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, tx, ty, 0f, 0f, THUMB_W, THUMB_H, 1280, 720, 1280, 720, 0xFFFFFFFF);
            } else if (BACKGROUNDS[i].equals("Animated")) {
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(tx, ty);
                Draw.ambient(ctx, THUMB_W, THUMB_H, time);
                ctx.getMatrices().popMatrix();
            } else {
                ctx.fillGradient(tx, ty, tx + THUMB_W, ty + THUMB_H, Draw.lerpColor(0xFF0B0D13, Draw.accent(), 0.10f), 0xFF07080C);
            }
            ctx.disableScissor();
            Draw.ring(ctx, tx - 1, ty - 1, THUMB_W + 2, THUMB_H + 2, 4, sel ? Draw.accent() : hv ? 0xA0FFFFFF : 0x30FFFFFF);
            if (sel) Draw.ring(ctx, tx - 2, ty - 2, THUMB_W + 4, THUMB_H + 4, 5, Draw.withAlpha(Draw.accent(), 0x70));
            Draw.text(ctx, tr, BACKGROUNDS[i], tx + 2, ty + THUMB_H + 4, sel ? 0xFFFFFFFF : Draw.DIM, false);
        }

        // --- done
        int bx = x + W - 90, by = y + 12;
        Draw.ghost(ctx, tr, bx, by, 72, 18, "Done", Draw.hit(mx, my, bx, by, 72, 18));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    private void setHue(double mx) {
        ClientSettingsModule c = cs();
        if (c == null) return;
        float h = (float) Math.max(0, Math.min(1, (mx - hueX()) / hueW()));
        c.customAccent.set(Draw.hsvToRgb(h, 0.72f, 1f) | 0xFF000000);
        c.theme.set("Custom");
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        ClientSettingsModule c = cs();
        if (c == null) return super.mouseClicked(click, doubled);
        if (Draw.hit(mx, my, x0() + W - 90, y0() + 12, 72, 18)) { close(); return true; }
        for (int i = 0; i <= PRESETS.length; i++) {
            if (!Draw.hit(mx, my, swX(i), swY(), SW, SW)) continue;
            c.theme.set(i == PRESETS.length ? "Custom" : PRESETS[i]);
            return true;
        }
        if (Draw.hit(mx, my, hueX(), hueY() - 3, hueW(), 16)) { draggingHue = true; setHue(mx); return true; }
        for (int i = 0; i < BACKGROUNDS.length; i++) {
            if (Draw.hit(mx, my, thumbX(i), thumbY(i), THUMB_W, THUMB_H)) { c.menuBackground.set(BACKGROUNDS[i]); return true; }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (draggingHue) { setHue(click.x()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingHue = false;
        return super.mouseReleased(click);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        TempestConfig.save();
        if (client != null) client.setScreen(parent);
    }
}
