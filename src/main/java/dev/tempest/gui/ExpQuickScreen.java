package dev.tempest.gui;

import dev.tempest.TempestConfig;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * EXPERIMENTAL Right Shift menu: quick toggles, like a phone's control centre. Big square tiles for your starred
 * mods (star them in the mod list; a starter set shows until you do) that light up when they're on. Click a tile to
 * toggle, right-click for options. The last tile, More, opens the full mod list.
 */
public class ExpQuickScreen extends Screen {
    private static final String[] STARTER = {"zoom", "fullbright", "hitboxes", "keystrokes", "fps", "freelook", "toggle_sprint", "totem_pops", "coordinates", "motion_blur", "waypoints"};
    private final Screen parent;
    private final long openedAt = System.nanoTime();

    public ExpQuickScreen(Screen parent) {
        super(Text.literal("Quick toggles"));
        this.parent = parent;
    }

    private List<Module> tiles() {
        List<String> ids = TempestConfig.get().favoriteMods;
        boolean starter = ids == null || ids.isEmpty();
        List<Module> out = new ArrayList<>();
        for (String id : starter ? List.of(STARTER) : ids) {
            for (Module m : ModuleManager.all()) if (m.getId().equals(id)) { out.add(m); break; }
        }
        return out.size() > 15 ? out.subList(0, 15) : out;
    }

    private int cols() { return 4; }
    private int ts() {
        int rows = (tiles().size() + 1 + cols() - 1) / cols();
        return Math.max(36, Math.min(64, (height - 76 - (rows - 1) * gap()) / rows));
    }
    private int gap() { return 8; }
    private int gx() { return width / 2 - (cols() * ts() + (cols() - 1) * gap()) / 2; }
    private int gy() { return 44; }
    private int tileX(int i) { return gx() + (i % cols()) * (ts() + gap()); }
    private int tileY(int i) { return gy() + (i / cols()) * (ts() + gap()); }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() { MenuBlur.restore(); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float t = MenuKit.time();
        float e = dev.tempest.modules.ClientSettingsModule.animationsOn() ? MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.3f) : 1f;
        ExpKit.overGame(ctx, width, height, 0.55f, t, e);
        MenuKit.scaledCentered(ctx, tr, Draw.bold("Quick toggles"), width / 2f, 18, 1.5f, MenuKit.fa(0xFFFFFFFF, e), false);

        List<Module> ms = tiles();
        int n = ms.size() + 1;
        for (int i = 0; i < n; i++) {
            int x = tileX(i), y = tileY(i), s = ts();
            float te = dev.tempest.modules.ClientSettingsModule.animationsOn() ? MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.3f - i * 0.03f) : 1f;
            y += Math.round((1f - te) * 10);
            boolean hv = Draw.hit(mx, my, x, y, s, s);
            float hp = Draw.hoverProgress("expq_h" + i, hv);
            boolean more = i == ms.size();
            Module m = more ? null : ms.get(i);
            float p = m == null ? 0f : Draw.hoverProgress("expq_on" + m.getId(), m.isEnabled());
            if (p > 0.05f) TempestLoading.drawGlow(ctx, x + s / 2, y + s / 2, s * 2, Draw.accent(), 0.28f * p * te);
            int fill = Draw.lerpColor(Draw.lerpColor(0xC81E1A32, 0xD82A2446, hp), Draw.lerpColor(Draw.accent() | 0xF0000000, 0xF0B3A6FF, hp * 0.3f), p);
            Draw.panel(ctx, x, y, s, s, 12, MenuKit.fa(fill, te), MenuKit.fa(Draw.lerpColor(0x30FFFFFF, 0x90FFFFFF, Math.max(p, hp * 0.5f)), te));
            int is = Math.round(s * 0.34f);
            Icons.draw(ctx, more ? "grid" : Icons.forModule(m.getId()), x + s / 2 - is / 2, y + Math.round(s * 0.2f), is, MenuKit.fa(0xFFFFFFFF, te));
            String label = Draw.ellipsize(tr, more ? "More" : m.getName(), s - 6);
            Draw.text(ctx, tr, label, x + s / 2 - Draw.textW(tr, label) / 2, y + s - 15, MenuKit.fa(p > 0.5f ? 0xFFFFFFFF : 0xFFD7D2EC, te), false);
        }
        boolean starter = TempestConfig.get().favoriteMods == null || TempestConfig.get().favoriteMods.isEmpty();
        String hint = starter ? "Star mods in the mod list to put them here   Right-click a tile for options"
                : "Your starred mods   Right-click a tile for options";
        Draw.text(ctx, tr, hint, width / 2 - Draw.textW(tr, hint) / 2, height - 16, MenuKit.fa(0x90FFFFFF, e), false);
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        List<Module> ms = tiles();
        for (int i = 0; i <= ms.size(); i++) {
            if (!Draw.hit(click.x(), click.y(), tileX(i), tileY(i), ts(), ts())) continue;
            if (i == ms.size()) { if (client != null) client.setScreen(new ExpModsScreen(null, this, "")); }
            else if (click.button() == 1) ExpKit.openOptions(this, ms.get(i));
            else ExpKit.toggle(ms.get(i));
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
