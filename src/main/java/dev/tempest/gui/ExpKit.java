package dev.tempest.gui;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

/** Shared bits for the Right Shift menu styles (ExpMenus). */
final class ExpKit {
    private ExpKit() { }

    static final Category[] CATS = Category.values();

    static String title(Category c) {
        if (c == null) return "All mods";
        return switch (c) {
            case HUD -> "HUD";
            case PVP -> "Combat";
            case RENDER -> "Render";
            case VISUALS -> "Visuals";
            case QOL -> "Quality of life";
            case UTILITY -> "Utility";
            case CHAT -> "Chat";
            case CLIENT -> "Client";
        };
    }

    static List<Module> mods(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.all()) if (c == null || m.getCategory() == c) out.add(m);
        return out;
    }

    /** {total, on} */
    static int[] counts(Category c) {
        int total = 0, on = 0;
        for (Module m : ModuleManager.all()) {
            if (c != null && m.getCategory() != c) continue;
            total++;
            if (m.isEnabled()) on++;
        }
        return new int[]{total, on};
    }

    static String countLine(Category c) {
        int[] k = counts(c);
        return k[1] + " of " + k[0] + " on";
    }

    /** A mod's options open in the normal settings page; Esc returns to {@code parent}. */
    static void openOptions(Screen parent, Module m) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClickGuiScreen gui = ClickGuiScreen.openTo(m.getCategory(), parent);
        mc.setScreen(gui);
        try {
            java.lang.reflect.Field f = ClickGuiScreen.class.getDeclaredField("open");
            f.setAccessible(true);
            f.set(gui, m);
        } catch (ReflectiveOperationException ignored) { }
    }

    /** Toggles a mod (always-on mods ignore it). */
    static void toggle(Module m) { if (!m.alwaysOn()) m.toggle(); }

    /** Round glass button with an icon. {@code p} = how lit it is (0 dark .. 1 accent). */
    static void orb(DrawContext ctx, int x, int y, int size, String icon, float p, float a) {
        int r = size / 2;
        if (p > 0.6f) TempestLoading.drawGlow(ctx, x + r, y + r, size * 3, Draw.accent(), 0.30f * p * a);
        int fill = Draw.lerpColor(0xE0221C38, Draw.accent() | 0xFF000000, p);
        Draw.panel(ctx, x, y, size, size, r, MenuKit.fa(fill, a), MenuKit.fa(Draw.lerpColor(0x38FFFFFF, 0xA0FFFFFF, p), a));
        int is = Math.max(6, Math.round(size * 0.5f));
        Icons.draw(ctx, icon, x + r - is / 2, y + r - is / 2, is, MenuKit.fa(Draw.lerpColor(0xC8E4E0F5, 0xFFFFFFFF, p), a));
    }

    /** Dark glass panel. */
    static void glass(DrawContext ctx, int x, int y, int w, int h, int r, float a) {
        Draw.shadow(ctx, x, y, w, h, r, 8);
        Draw.panel(ctx, x, y, w, h, r, MenuKit.fa(0xE6161228, a), MenuKit.fa(0x40FFFFFF, a));
    }

    /** The game stays visible: blur it (Menu Blur setting) and darken it a little. Title screen: the Exp night. */
    static void overGame(DrawContext ctx, int w, int h, float dim, float t, float e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) { ExpOrbitScreen.paintBackground(ctx, w, h, w / 2, h / 2, t, e); return; }
        MenuBlur.apply(ctx);
        ctx.fill(0, 0, w, h, MenuKit.fa(Draw.withAlpha(0x0A0818, Math.round(255 * dim)), e));
    }

    /** One mod row: icon, name, switch. Returns nothing; hit-testing is done by the caller with the same rect. */
    static void modRow(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, Module m, int x, int y, int w, int h, boolean hv, float a) {
        float p = Draw.hoverProgress("expk_on_" + m.getId(), m.isEnabled());
        if (hv) Draw.roundRect(ctx, x - 4, y, w + 8, h, 6, MenuKit.fa(0x18FFFFFF, a));
        int os = h - 4;
        Draw.panel(ctx, x, y + 2, os, os, os / 2, MenuKit.fa(Draw.lerpColor(0xFF2A2342, Draw.accent() | 0xFF000000, p), a), MenuKit.fa(0x30FFFFFF, a));
        int is = Math.round(os * 0.56f);
        Icons.draw(ctx, Icons.forModule(m.getId()), x + os / 2 - is / 2, y + 2 + os / 2 - is / 2, is, MenuKit.fa(0xFFFFFFFF, a));
        Draw.text(ctx, tr, Draw.ellipsize(tr, m.getName(), w - os - 44), x + os + 6, y + (h - 8) / 2, MenuKit.fa(Draw.lerpColor(0xFFCFCBE4, 0xFFFFFFFF, p), a), false);
        if (m.alwaysOn()) Draw.text(ctx, tr, "On", x + w - 16, y + (h - 8) / 2, MenuKit.fa(0x90FFFFFF, a), false);
        else if (a > 0.6f) Draw.toggle(ctx, x + w - 28, y + (h - 14) / 2, p);
    }

    // ---------------------------------------------------------------- the Style button (top-right of every Exp menu)
    private static final int SB_W = 54, SB_H = 16;

    static void styleButton(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int screenW, int mx, int my) {
        int x = screenW - SB_W - 6, y = 6;
        boolean hv = Draw.hit(mx, my, x, y, SB_W, SB_H);
        float p = Draw.hoverProgress("exp_style_btn", hv);
        Draw.panel(ctx, x, y, SB_W, SB_H, SB_H / 2, Draw.lerpColor(0xB0181430, 0xE02A2250, p), Draw.lerpColor(0x40FFFFFF, Draw.withAlpha(Draw.accent(), 0xD0), p));
        Icons.draw(ctx, "palette", x + 7, y + 4, 8, 0xE0FFFFFF);
        Draw.text(ctx, tr, "Style", x + 19, y + 4, 0xFFFFFFFF, false);
    }

    /** True if the click hit the Style button (and the style picker was opened). */
    static boolean clickStyleButton(Screen screen, Screen parent, double mx, double my) {
        if (!Draw.hit(mx, my, screen.width - SB_W - 6, 6, SB_W, SB_H)) return false;
        MinecraftClient.getInstance().setScreen(new ExpStyleScreen(screen, parent));
        return true;
    }
}
