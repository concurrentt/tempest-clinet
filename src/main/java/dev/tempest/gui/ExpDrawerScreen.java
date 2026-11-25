package dev.tempest.gui;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * EXPERIMENTAL Right Shift menu: a drawer that slides in from the right edge while the game stays visible on the
 * left (it isn't blurred or darkened). Category tabs run across the top of the drawer, mods are a scrolling list
 * with switches. Click a row to toggle, right-click for options; click the game side to close.
 */
public class ExpDrawerScreen extends Screen {
    private static Category savedCat = Category.PVP;
    private final Screen parent;
    private Category cat = savedCat;
    private double scroll, scrollTarget;
    private final long openedAt = System.nanoTime();
    private static final int ROW_H = 20, TAB = 20;

    public ExpDrawerScreen(Screen parent) {
        super(Text.literal("Tempest"));
        this.parent = parent;
    }

    private int dw() { return Math.min(210, Math.max(170, width / 3)); }
    private float slide() {
        if (!dev.tempest.modules.ClientSettingsModule.animationsOn()) return 1f;
        return MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.28f);
    }
    private int dx() { return width - Math.round(dw() * slide()); }
    private int tabsY() { return 34; }
    private int listY() { return 86; }
    private int tabX(int i) { return dx() + 10 + i * (TAB + 4); }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float e = slide();
        if (client != null && client.world == null) ExpOrbitScreen.paintBackground(ctx, width, height, width / 3, height / 2, MenuKit.time(), 1f);
        int x = dx(), w = dw();
        Draw.shadow(ctx, x, 0, w + 20, height, 0, 14);
        ctx.fill(x, 0, width, height, 0xE8120E22);
        ctx.fill(x, 0, x + 1, height, 0x40FFFFFF);

        TempestLoading.drawLogo(ctx, x + 10, 9, 16);
        Draw.text(ctx, tr, "Tempest", x + 30, 13, 0xFFFFFFFF, false);
        for (int i = 0; i < ExpKit.CATS.length; i++) {
            boolean sel = ExpKit.CATS[i] == cat;
            float p = Math.max(Draw.hoverProgress("expd_t" + i, sel), Draw.hoverProgress("expd_th" + i, Draw.hit(mx, my, tabX(i), tabsY(), TAB, TAB)) * 0.35f);
            ExpKit.orb(ctx, tabX(i), tabsY(), TAB, Icons.forCategory(ExpKit.CATS[i]), p, 1f);
        }
        MenuKit.scaledLeft(ctx, tr, Draw.bold(ExpKit.title(cat)), x + 10, 60, 1.4f, 0xFFFFFFFF, false);
        Draw.text(ctx, tr, ExpKit.countLine(cat), x + 10, 74, 0xA0D6CFF2, false);

        List<Module> mods = ExpKit.mods(cat);
        double max = Math.max(0, mods.size() * (ROW_H + 2) - (height - 20 - listY()));
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        scroll += (scrollTarget - scroll) * 0.35;
        ctx.enableScissor(x, listY(), width, height - 20);
        for (int i = 0; i < mods.size(); i++) {
            int ry = listY() + i * (ROW_H + 2) - (int) Math.round(scroll);
            if (ry + ROW_H < listY() || ry > height - 20) continue;
            boolean hv = my >= listY() && my < height - 20 && Draw.hit(mx, my, x + 10, ry, w - 20, ROW_H);
            ExpKit.modRow(ctx, tr, mods.get(i), x + 10, ry, w - 20, ROW_H, hv, 1f);
        }
        ctx.disableScissor();
        Draw.text(ctx, tr, "Right-click for options", x + 10, height - 13, 0x70FFFFFF, false);
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        double mx = click.x(), my = click.y();
        if (mx < dx()) { close(); return true; }
        for (int i = 0; i < ExpKit.CATS.length; i++) {
            if (Draw.hit(mx, my, tabX(i), tabsY(), TAB, TAB)) { cat = savedCat = ExpKit.CATS[i]; scroll = scrollTarget = 0; return true; }
        }
        if (my >= listY() && my < height - 20) {
            List<Module> mods = ExpKit.mods(cat);
            int i = (int) ((my - listY() + scroll) / (ROW_H + 2));
            if (i >= 0 && i < mods.size()) {
                if (click.button() == 1) ExpKit.openOptions(this, mods.get(i));
                else ExpKit.toggle(mods.get(i));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget -= vertical * 30;
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
