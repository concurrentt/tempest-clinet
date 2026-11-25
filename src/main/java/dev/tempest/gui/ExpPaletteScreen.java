package dev.tempest.gui;

import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.Setting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * EXPERIMENTAL Right Shift menu: a command palette. One search bar in the middle of the screen; type and the best
 * matches show underneath (mods, their settings, and categories). Up/Down picks, Enter toggles a mod (or opens a
 * category or setting), Tab opens the picked mod's options. With nothing typed it lists the mods that are on.
 */
public class ExpPaletteScreen extends Screen {
    private record Hit(Module mod, Category cat, String setting, int score) { }

    private static final int W = 320, BAR_H = 30, ROW_H = 26, MAX_ROWS = 7;
    private final Screen parent;
    private TextFieldWidget field;
    private int sel;
    private final long openedAt = System.nanoTime();

    public ExpPaletteScreen(Screen parent) {
        super(Text.literal("Search"));
        this.parent = parent;
    }

    private int px() { return (width - Math.min(W, width - 20)) / 2; }
    private int pw() { return Math.min(W, width - 20); }
    private int py() { return Math.max(16, height / 5); }

    @Override
    protected void init() {
        String old = field != null ? field.getText() : "";
        field = new TextFieldWidget(textRenderer, px() + 30, py() + 11, pw() - 70, 12, Text.literal("Search"));
        field.setDrawsBackground(false);
        field.setPlaceholder(Text.literal("Search mods, settings and categories"));
        field.setMaxLength(64);
        field.setText(old);
        field.setChangedListener(s -> sel = 0);
        addDrawableChild(field);
        setInitialFocus(field);
    }

    private List<Hit> results() {
        String q = field.getText().trim().toLowerCase(Locale.ROOT);
        List<Hit> out = new ArrayList<>();
        if (q.isEmpty()) {
            for (Module m : ModuleManager.all()) if (m.isEnabled() && !m.alwaysOn()) out.add(new Hit(m, null, null, 0));
            return out;
        }
        for (Category c : ExpKit.CATS) {
            int s = score(ExpKit.title(c), q);
            if (s > 0) out.add(new Hit(null, c, null, s + 5));
        }
        for (Module m : ModuleManager.all()) {
            int s = Math.max(score(m.getName(), q), score(m.getDescription(), q) / 3);
            if (s > 0) out.add(new Hit(m, null, null, s + 10));
            for (Setting st : m.getSettings()) {
                int ss = score(st.name, q);
                if (ss > 0) out.add(new Hit(m, null, st.name, ss));
            }
        }
        out.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return out;
    }

    /** Simple fuzzy score: starts-with beats contains beats letters-in-order. 0 = no match. */
    private static int score(String text, String q) {
        if (text == null) return 0;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.startsWith(q)) return 100 - Math.min(40, t.length() - q.length());
        if (t.contains(" " + q)) return 70;
        if (t.contains(q)) return 50;
        int i = 0;
        for (char ch : t.toCharArray()) if (i < q.length() && ch == q.charAt(i)) i++;
        return i == q.length() ? 20 : 0;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void removed() { MenuBlur.restore(); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        float t = MenuKit.time();
        float e = dev.tempest.modules.ClientSettingsModule.animationsOn() ? MenuKit.ease((System.nanoTime() - openedAt) / 1e9f / 0.2f) : 1f;
        ExpKit.overGame(ctx, width, height, 0.5f, t, e);
        List<Hit> hits = results();
        sel = Math.max(0, Math.min(sel, Math.min(hits.size(), MAX_ROWS) - 1));
        int rows = Math.min(hits.size(), MAX_ROWS);
        int x = px(), y = py() - Math.round((1f - e) * 8), w = pw();
        int h = BAR_H + 18 + Math.max(1, rows) * (ROW_H + 2) + 8;
        ExpKit.glass(ctx, x, y, w, h, 14, e);
        Draw.roundRect(ctx, x + 1, y + 1, w - 2, BAR_H, 13, MenuKit.fa(0xFF1E1838, e));
        Icons.draw(ctx, "search", x + 12, y + 9, 12, MenuKit.fa(0xC0FFFFFF, e));
        Draw.text(ctx, tr, "Esc", x + w - 12 - Draw.textW(tr, "Esc"), y + 11, MenuKit.fa(0x70FFFFFF, e), false);
        field.setY(y + 11);

        boolean empty = field.getText().isBlank();
        Draw.text(ctx, tr, empty ? "Turned on" : hits.isEmpty() ? "Nothing found" : "Best matches", x + 12, y + BAR_H + 6, MenuKit.fa(0x90D6CFF2, e), false);
        int ry = y + BAR_H + 18;
        for (int i = 0; i < rows; i++) {
            Hit hit = hits.get(i);
            boolean hv = Draw.hit(mx, my, x + 6, ry, w - 12, ROW_H);
            if (hv) sel = i;
            if (i == sel) Draw.panel(ctx, x + 6, ry, w - 12, ROW_H, 8, MenuKit.fa(Draw.withAlpha(Draw.accent(), 0x46), e), MenuKit.fa(Draw.withAlpha(Draw.accent(), 0xA0), e));
            String icon, name, sub;
            boolean lit;
            if (hit.cat() != null) {
                icon = Icons.forCategory(hit.cat()); name = ExpKit.title(hit.cat()); sub = "Category   " + ExpKit.countLine(hit.cat()); lit = false;
            } else if (hit.setting() != null) {
                icon = Icons.forModule(hit.mod().getId()); name = hit.setting(); sub = "Setting in " + hit.mod().getName(); lit = false;
            } else {
                icon = Icons.forModule(hit.mod().getId()); name = hit.mod().getName();
                sub = (i == sel ? "Enter to toggle   Tab for options" : ExpKit.title(hit.mod().getCategory())); lit = hit.mod().isEnabled();
            }
            int os = ROW_H - 6;
            Draw.panel(ctx, x + 12, ry + 3, os, os, os / 2, MenuKit.fa(lit ? (Draw.accent() | 0xFF000000) : 0xFF2A2342, e), MenuKit.fa(0x30FFFFFF, e));
            Icons.draw(ctx, icon, x + 12 + os / 2 - 6, ry + 3 + os / 2 - 6, 12, MenuKit.fa(0xFFFFFFFF, e));
            Draw.text(ctx, tr, Draw.ellipsize(tr, name, w - 110), x + 12 + os + 8, ry + 4, MenuKit.fa(0xFFFFFFFF, e), false);
            MenuKit.scaledLeft(ctx, tr, Draw.uiText(sub), x + 12 + os + 8, ry + 15, 0.75f, MenuKit.fa(0xB0D6CFF2, e), false);
            if (hit.mod() != null && hit.setting() == null && !hit.mod().alwaysOn() && e > 0.6f)
                Draw.toggle(ctx, x + w - 44, ry + (ROW_H - 14) / 2, Draw.hoverProgress("expp_" + hit.mod().getId(), hit.mod().isEnabled()));
            ry += ROW_H + 2;
        }
        String hint = "Up/Down to pick   Enter toggles   Tab opens options";
        Draw.text(ctx, tr, hint, width / 2 - Draw.textW(tr, hint) / 2, height - 16, MenuKit.fa(0x90FFFFFF, e), false);
        ExpKit.styleButton(ctx, textRenderer, width, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void activate(Hit h, boolean options) {
        if (client == null || h == null) return;
        if (h.cat() != null) client.setScreen(new ExpModsScreen(h.cat(), this, ""));
        else if (options || h.setting() != null) ExpKit.openOptions(this, h.mod());
        else ExpKit.toggle(h.mod());
    }

    private Hit current() {
        List<Hit> hits = results();
        return sel < hits.size() ? hits.get(sel) : null;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_DOWN) { sel++; return true; }
        if (k == GLFW.GLFW_KEY_UP) { sel = Math.max(0, sel - 1); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { activate(current(), false); return true; }
        if (k == GLFW.GLFW_KEY_TAB) { Hit h = current(); if (h != null && h.mod() != null) ExpKit.openOptions(this, h.mod()); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ExpKit.clickStyleButton(this, parent, click.x(), click.y())) return true;
        int rows = Math.min(results().size(), MAX_ROWS), ry = py() + BAR_H + 18;
        for (int i = 0; i < rows; i++) {
            if (Draw.hit(click.x(), click.y(), px() + 6, ry + i * (ROW_H + 2), pw() - 12, ROW_H)) {
                sel = i;
                activate(current(), click.button() == 1);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
