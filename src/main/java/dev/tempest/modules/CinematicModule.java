package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/** Screen colour grading: soft vignette, a colour mood and optional film letterbox bars. */
public class CinematicModule extends Module {
    private final ModeSetting mood = add(new ModeSetting("mood", "Color Mood", "Cool Night", "None", "Cool Night", "Golden Hour", "Dreamy", "Noir", "Theme"));
    private final NumberSetting strength = add(new NumberSetting("strength", "Mood Strength", 0.5, 0.05, 1.0, 0.05));
    private final NumberSetting vignette = add(new NumberSetting("vignette", "Vignette", 0.55, 0, 1, 0.05));
    private final BoolSetting letterbox = add(new BoolSetting("letterbox", "Letterbox Bars", false));

    public CinematicModule() {
        super("cinematic_grade", "Cinematic Grade", "Vignette, color mood and film bars for screenshots and videos.", Category.VISUALS, false);
    }

    public static void render(DrawContext ctx) {
        CinematicModule m = ModuleManager.get(CinematicModule.class);
        if (m != null && m.isEnabled()) m.draw(ctx);
    }

    private void draw(DrawContext ctx) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float s = strength.getFloat();

        int tint = switch (mood.get()) {
            case "Cool Night" -> 0x1A3C8C;
            case "Golden Hour" -> 0xFF9A3C;
            case "Dreamy" -> 0xD46CFF;
            case "Noir" -> 0x101014;
            case "Theme" -> Draw.accent() & 0xFFFFFF;
            default -> -1;
        };
        if (tint >= 0) {
            int a = Math.round((mood.is("Noir") ? 0x60 : 0x34) * s);
            ctx.fillGradient(0, 0, w, h, (a << 24) | tint, ((a * 2 / 3) << 24) | tint);
        }

        float v = vignette.getFloat();
        if (v > 0.01f) {
            int edge = Math.max(24, Math.min(w, h) / 3);
            int a = Math.round(0xC0 * v);
            ctx.fillGradient(0, 0, w, edge, a << 24, 0);
            ctx.fillGradient(0, h - edge, w, h, 0, a << 24);
            // horizontal fades are built from thin strips (fillGradient is vertical only)
            final int strips = 24;
            for (int i = 0; i < strips; i++) {
                float k = 1f - i / (float) strips;
                int sa = Math.round(a * k * k);
                int x0 = edge * i / strips, x1 = edge * (i + 1) / strips;
                ctx.fill(x0, 0, x1, h, sa << 24);
                ctx.fill(w - x1, 0, w - x0, h, sa << 24);
            }
        }

        if (letterbox.get()) {
            int bar = Math.round(h * 0.11f);
            ctx.fill(0, 0, w, bar, 0xFF000000);
            ctx.fill(0, h - bar, w, h, 0xFF000000);
        }
    }
}
