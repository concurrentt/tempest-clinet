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
 * left (blurred a little, by the Menu Blur setting). Category tabs run across the top of the drawer, mods are a scrolling list
 * with switches. Click a row to toggle, right-click for options; click the game side to close.
 */
public class ExpDrawerScreen extends Screen {
    private static Category savedCat = Category.PVP;
    private final Screen parent;
    private Category cat = savedCat;
    private double scroll, scrollTarget;
    private final long openedAt = System.nanoTime();
    private static final int ROW_H = 20, TAB = 20;
    private static final float OPTS_SECS = 0.25f;
    private ExpSettings opts;                 // a mod's settings: pushed in over the list like a second page

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
        if (client != null && client.world == null) Draw.menuBackground(ctx, width, height);
        MenuBlur.apply(ctx);
        ctx.fillGradient(0, 0, width, height, MenuKit.fa(0x20080614, e), MenuKit.fa(0x50080614, e));
        int x = dx(), w = dw();
        Draw.shadow(ctx, x, 0, w + 20, height, 0, 14);
        ctx.fill(x, 0, width, height, 0xE8120E22);
        ctx.fill(x, 0, x + 1, height, 0x40FFFFFF);

        float op = 0f;
        if (opts != null && opts.gone(OPTS_SECS)) opts = null;
        if (opts != null) op = MenuKit.ease(opts.progress(OPTS_SECS));
        int push = Math.round(op * w * 0.35f);                 // the list slides away to the left as the page comes in
        int lmx = op > 0.01f ? -9999 : mx;
        ctx.enableScissor(x, 0, width, height);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(-push, 0);
        renderList(ctx, tr, x, w, lmx, my, 1f - op * 0.6f);
        ctx.getMatrices().popMatrix();
        if (op > 0.01f) ctx.fill(x, 0, width, height, MenuKit.fa(0xA0120E22, op));
        if (opts != null) {
            int ox = x + 6 + Math.round((1f - op) * w);
            opts.render(ctx, tr, ox, 30, w - 12, height - 50, mx, my, Math.max(0.01f, op), op >= 1f && !opts.closing());
        }
        ctx.disableScissor();
        Draw.text(ctx, tr, opts != null ? "Esc or the arrow to go back" : "Right-click for options", x + 10, height - 13, 0x70FFFFFF, false);
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void renderList(DrawContext ctx, TextRenderer tr, int x, int w, int mx, int my, float a) {
        TempestLoading.drawLogo(ctx, x + 10, 9, 16);
        Draw.text(ctx, tr, "Tempest", x + 30, 13, 0xFFFFFFFF, false);
        for (int i = 0; i < ExpKit.CATS.length; i++) {
            boolean sel = ExpKit.CATS[i] == cat;
            float p = Math.max(Draw.hoverProgress("expd_t" + i, sel), Draw.hoverProgress("expd_th" + i, Draw.hit(mx, my, tabX(i), tabsY(), TAB, TAB)) * 0.35f);
            ExpKit.orb(ctx, tabX(i), tabsY(), TAB, Icons.forCategory(ExpKit.CATS[i]), p, a);
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
            ExpKit.modRow(ctx, tr, mods.get(i), x + 10, ry, w - 20, ROW_H, hv, a);
        }
        ctx.disableScissor();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        double mx = click.x(), my = click.y();
        if (opts != null) {
            if (!opts.closing() && opts.mouseClicked(mx, my, click.button()) && opts.closeRequested) opts.startClosing();
            else if (mx < dx()) opts.startClosing();
            return true;
        }
        if (mx < dx()) { close(); return true; }
        for (int i = 0; i < ExpKit.CATS.length; i++) {
            if (Draw.hit(mx, my, tabX(i), tabsY(), TAB, TAB)) { cat = savedCat = ExpKit.CATS[i]; scroll = scrollTarget = 0; return true; }
        }
        if (my >= listY() && my < height - 20) {
            List<Module> mods = ExpKit.mods(cat);
            int i = (int) ((my - listY() + scroll) / (ROW_H + 2));
            if (i >= 0 && i < mods.size()) {
                if (click.button() == 1) opts = new ExpSettings(mods.get(i), true);
                else ExpKit.toggle(mods.get(i));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (opts != null) { opts.mouseScrolled(mouseX, mouseY, vertical); return true; }
        scrollTarget -= vertical * 30;
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (opts != null && opts.mouseDragged(click.x(), click.y())) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (opts != null) opts.mouseReleased();
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (opts != null && opts.keyPressed(input.key())) return true;
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        MenuBlur.restore();
        if (opts != null) opts.close();
    }

    @Override
    public void close() {
        if (opts != null && !opts.closing()) { opts.startClosing(); return; }
        if (client != null) client.setScreen(parent);
    }
}
