package dev.tempest.gui;

import dev.tempest.module.Category;
import dev.tempest.util.ConfigManager;
import dev.tempest.util.PlayerHeads;
import dev.tempest.util.Social;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * EXPERIMENTAL Right Shift menu: a split hub, like a game's character screen. Your player stands on the left on a
 * soft spotlight (turning to follow the mouse, wearing your cosmetics), with your name, config and friends online.
 * The right side has a tile per category with how many of its mods are on, and shortcuts underneath.
 */
public class ExpHubScreen extends Screen {
    private static final String[][] SHORTCUTS = {{"layout", "Edit HUD"}, {"layers", "Configs"}, {"people", "Social"}, {"shirt", "Cosmetics"}};
    private final Screen parent;
    private final long openedAt = System.nanoTime();

    public ExpHubScreen(Screen parent) {
        super(Text.literal("Tempest"));
        this.parent = parent;
    }

    private int leftW() { return Math.max(140, Math.round(width * 0.36f)); }
    private int rx() { return leftW() + 6; }
    private int rw() { return Math.min(width - rx() - 20, 300); }
    private int tileW() { return (rw() - 8) / 2; }
    private int tileH() { return Math.max(26, Math.min(36, (height - 150) / 4)); }
    private int tilesY() { return 62; }
    private int tileX(int i) { return rx() + (i % 2) * (tileW() + 8); }
    private int tileY(int i) { return tilesY() + (i / 2) * (tileH() + 8); }
    private int shortY() { return tileY(ExpKit.CATS.length - 1) + tileH() + 14; }
    private int shortW() { return (rw() - 3 * 6) / 4; }
    private int shortX(int i) { return rx() + i * (shortW() + 6); }

    private float intro(float d) {
        if (!dev.tempest.modules.ClientSettingsModule.animationsOn()) return 1f;
        return MenuKit.ease(((System.nanoTime() - openedAt) / 1e9f - d) / 0.4f);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() { MenuBlur.restore(); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float t = MenuKit.time(), e = intro(0f);
        ExpOrbitScreen.paintBackground(ctx, width, height, leftW() / 2, height / 2, t, e);

        // left: the player
        int lw = leftW(), pcx = lw / 2;
        TempestLoading.drawGlow(ctx, pcx, height / 2 + 10, Math.round(height * 0.9f), Draw.accent(), 0.35f * e);
        Draw.roundRect(ctx, pcx - 44, height - 46, 88, 8, 4, MenuKit.fa(0x50000000, e));
        if (client != null && client.player != null) {
            int size = Math.max(40, Math.min(80, (height - 110) / 2));
            InventoryScreen.drawEntity(ctx, pcx - 60, 40, pcx + 60, height - 40, size, 0.0625f, mx, my, client.player);
        } else {
            UUID id = client != null && client.getSession() != null ? client.getSession().getUuidOrNull() : null;
            Identifier head = id != null ? PlayerHeads.get(id) : null;
            int hs = 64;
            if (head != null) {
                Draw.roundRect(ctx, pcx - hs / 2 - 3, height / 2 - hs / 2 - 3, hs + 6, hs + 6, 8, MenuKit.fa(0xFF120E22, e));
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, head, pcx - hs / 2, height / 2 - hs / 2, 0f, 0f, hs, hs, 32, 32, 32, 32, MenuKit.fa(0xFFFFFFFF, e));
            }
        }
        String name = client != null && client.getSession() != null ? client.getSession().getUsername() : "Player";
        int nw = Draw.textW(tr, name);
        Draw.text(ctx, tr, name, pcx - nw / 2 - 4, 22, MenuKit.fa(0xFFFFFFFF, e), false);
        Draw.circle(ctx, pcx + nw / 2 + 3, 25, 2, MenuKit.fa(dev.tempest.util.TempestNet.signedIn() ? 0xFF43D17A : 0xFF6B6F7E, e));
        int online = 0;
        for (Social.Friend f : Social.friends()) if (f.online()) online++;
        String active = ConfigManager.active();
        String info = (active == null || active.isBlank() ? "" : "Config: " + active + "   ") + online + (online == 1 ? " friend online" : " friends online");
        MenuKit.scaledCentered(ctx, tr, Draw.uiText(info), pcx, height - 32, 0.8f, MenuKit.fa(0xB0D6CFF2, e), false);

        // right: title, category tiles, shortcuts
        float er = intro(0.08f);
        MenuKit.scaledLeft(ctx, tr, Draw.bold("Tempest"), rx(), 22, 1.8f, MenuKit.fa(0xFFFFFFFF, er), false);
        Draw.text(ctx, tr, "Client v" + dev.tempest.util.Updater.currentVersion(), rx() + 1, 42, MenuKit.fa(0x90D6CFF2, er), false);
        for (int i = 0; i < ExpKit.CATS.length; i++) {
            Category c = ExpKit.CATS[i];
            float te = intro(0.12f + i * 0.03f);
            int x = tileX(i), y = tileY(i) + Math.round((1f - te) * 8), w = tileW(), h = tileH();
            boolean hv = Draw.hit(mx, my, x, y, w, h);
            float hp = Draw.hoverProgress("exph_" + i, hv);
            int fill = Draw.lerpColor(0xC0181430, Draw.accent() | 0xE0000000, hp);
            Draw.panel(ctx, x, y, w, h, 10, MenuKit.fa(fill, te), MenuKit.fa(Draw.lerpColor(0x30FFFFFF, 0xA0FFFFFF, hp), te));
            int os = h - 10;
            Draw.panel(ctx, x + 5, y + 5, os, os, os / 2, MenuKit.fa(Draw.lerpColor(0xFF2C2646, 0x40FFFFFF, hp), te), MenuKit.fa(0x30FFFFFF, te));
            int is = Math.round(os * 0.55f);
            Icons.draw(ctx, Icons.forCategory(c), x + 5 + os / 2 - is / 2, y + 5 + os / 2 - is / 2, is, MenuKit.fa(0xFFFFFFFF, te));
            Draw.text(ctx, tr, Draw.ellipsize(tr, ExpKit.title(c), w - os - 18), x + os + 12, y + h / 2 - 9, MenuKit.fa(0xFFFFFFFF, te), false);
            MenuKit.scaledLeft(ctx, tr, Draw.uiText(ExpKit.countLine(c)), x + os + 12, y + h / 2 + 2, 0.75f, MenuKit.fa(0xC0E2DCFA, te), false);
        }
        float es = intro(0.4f);
        for (int i = 0; i < SHORTCUTS.length; i++) {
            int x = shortX(i), y = shortY(), w = shortW(), h = 20;
            boolean hv = Draw.hit(mx, my, x, y, w, h);
            float hp = Draw.hoverProgress("exphs_" + i, hv);
            Draw.panel(ctx, x, y, w, h, 10, MenuKit.fa(Draw.lerpColor(0x90181430, 0xC02A2250, hp), es), MenuKit.fa(Draw.lerpColor(0x28FFFFFF, 0x80FFFFFF, hp), es));
            String l = Draw.ellipsize(tr, SHORTCUTS[i][1], w - 22);
            int tw = 10 + 4 + Draw.textW(tr, l), sx = x + w / 2 - tw / 2;
            Icons.draw(ctx, SHORTCUTS[i][0], sx, y + 5, 10, MenuKit.fa(0xD0FFFFFF, es));
            Draw.text(ctx, tr, l, sx + 14, y + 6, MenuKit.fa(0xFFFFFFFF, es), false);
        }
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        double mx = click.x(), my = click.y();
        if (client == null) return false;
        for (int i = 0; i < ExpKit.CATS.length; i++) {
            if (Draw.hit(mx, my, tileX(i), tileY(i), tileW(), tileH())) { client.setScreen(new ExpModsScreen(ExpKit.CATS[i], this, "")); return true; }
        }
        for (int i = 0; i < SHORTCUTS.length; i++) {
            if (!Draw.hit(mx, my, shortX(i), shortY(), shortW(), 20)) continue;
            switch (i) {
                case 0 -> client.setScreen(new HudEditorScreen(this));
                case 1 -> client.setScreen(new ConfigsScreen(this));
                case 2 -> client.setScreen(new SocialScreen(this));
                default -> client.setScreen(new CosmeticsScreen(this, CosmeticsScreen.CAPES));
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
