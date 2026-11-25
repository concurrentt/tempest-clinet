package dev.tempest.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Exp Tempest: pick how the Right Shift menu looks. A picture of each style; click one to switch to it. */
public class ExpStyleScreen extends Screen {
    private static final int PW = 480, PH = 270;            // preview image size
    private final Screen back;                              // the menu you came from
    private final Screen parent;                            // what that menu returns to
    private final long openedAt = System.nanoTime();

    public ExpStyleScreen(Screen back, Screen parent) {
        super(Text.literal("Menu style"));
        this.back = back;
        this.parent = parent;
    }

    private int cols() { return 3; }
    private int gap() { return 10; }
    private int tw() {
        int byW = (Math.min(width - 40, 560) - (cols() - 1) * gap()) / cols();
        int rows = (ExpMenus.STYLES.size() + cols() - 1) / cols();
        int byH = (int) (((height - 70 - (rows - 1) * (gap() + 12)) / rows - 12) * 16f / 9f);
        return Math.max(80, Math.min(byW, byH));
    }
    private int th() { return tw() * 9 / 16; }
    private int gx() { return width / 2 - (cols() * tw() + (cols() - 1) * gap()) / 2; }
    private int tx(int i) { return gx() + (i % cols()) * (tw() + gap()); }
    private int ty(int i) { return 40 + (i / cols()) * (th() + 12 + gap()); }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() { MenuBlur.restore(); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float t = MenuKit.time();
        float e = dev.tempest.modules.ClientSettingsModule.animationsOn() ? MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.35f) : 1f;
        ExpOrbitScreen.paintBackground(ctx, width, height, width / 2, height / 2, t, e);
        MenuKit.scaledCentered(ctx, tr, Draw.bold("Menu style"), width / 2f, 12, 1.6f, MenuKit.fa(0xFFFFFFFF, e), false);
        String sub = "How Right Shift looks. You can change it any time from the Style button.";
        MenuKit.scaledCentered(ctx, tr, Draw.uiText(sub), width / 2f, 27, 0.8f, MenuKit.fa(0xB0D6CFF2, e), false);

        String cur = ExpMenus.current();
        for (int i = 0; i < ExpMenus.STYLES.size(); i++) {
            ExpMenus.Style s = ExpMenus.STYLES.get(i);
            int x = tx(i), y = ty(i), w = tw(), h = th();
            boolean hv = Draw.hit(mx, my, x, y, w, h + 12);
            boolean sel = s.id().equals(cur);
            float hp = Draw.hoverProgress("exps_" + i, hv);
            if (sel || hp > 0.05f) TempestLoading.drawGlow(ctx, x + w / 2, y + h / 2, Math.round(w * 1.3f), Draw.accent(), (sel ? 0.35f : 0.2f * hp) * e);
            Draw.panel(ctx, x - 2, y - 2, w + 4, h + 4, 7, MenuKit.fa(0xFF120E22, e),
                    MenuKit.fa(sel ? (Draw.accent() | 0xFF000000) : Draw.lerpColor(0x30FFFFFF, 0xA0FFFFFF, hp), e));
            Identifier img = Identifier.of("tempest", "textures/gui/exp_style_" + s.id() + ".png");
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, img, x, y, 0f, 0f, w, h, PW, PH, PW, PH, MenuKit.fa(0xFFFFFFFF, e));
            String name = s.name() + (sel ? "   In use" : "");
            Draw.text(ctx, tr, Draw.ellipsize(tr, name, w), x, y + h + 4, MenuKit.fa(sel ? 0xFFFFFFFF : 0xFFD7D2EC, e), false);
            if (hv) {
                int lw = Draw.textW(tr, s.line()) + 12, lx = Math.max(4, Math.min(width - lw - 4, x + w / 2 - lw / 2));
                Draw.panel(ctx, lx, y + h - 16, lw, 13, 6, 0xF0181430, 0x40FFFFFF);
                Draw.text(ctx, tr, s.line(), lx + 6, y + h - 13, 0xFFFFFFFF, false);
            }
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (int i = 0; i < ExpMenus.STYLES.size(); i++) {
            if (Draw.hit(click.x(), click.y(), tx(i), ty(i), tw(), th() + 12)) {
                ExpMenus.use(ExpMenus.STYLES.get(i).id(), parent);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(back); }
}
