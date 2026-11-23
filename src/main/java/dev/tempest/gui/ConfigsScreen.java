package dev.tempest.gui;

import dev.tempest.util.ConfigManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Configs: named saves of every module, setting and the HUD layout. Create one from your current setup, load,
 * overwrite, rename, copy to share, import from the clipboard, delete.
 */
public class ConfigsScreen extends Screen {
    private static final int HEAD = 40, ROW = 32, BAR = 34, FOOT = 26;

    private final Screen parent;
    private int pX, pY, pW, pH;
    private TextFieldWidget nameField, renameField;
    private List<ConfigManager.Entry> configs = List.of();
    private String renaming;           // config being renamed
    private String armedDelete;        // asked "Sure?" for this one
    private long armedAt;
    private double scroll, scrollTarget;
    private String message;
    private boolean messageBad;
    private long messageAt;

    public ConfigsScreen(Screen parent) {
        super(Text.literal("Configs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pW = Math.min(width - 24, 440);
        pH = Math.min(height - 24, 300);
        pX = (width - pW) / 2;
        pY = (height - pH) / 2;
        nameField = new TextFieldWidget(textRenderer, pX + 30, pY + HEAD + 13, pW - 30 - 14 - 140, 16, Text.literal("Name"));
        nameField.setMaxLength(ConfigManager.MAX_NAME);
        nameField.setDrawsBackground(false);
        nameField.setPlaceholder(Text.literal("New config name"));
        addDrawableChild(nameField);
        renameField = new TextFieldWidget(textRenderer, 0, 0, 120, 12, Text.literal("Rename"));
        renameField.setMaxLength(ConfigManager.MAX_NAME);
        renameField.setDrawsBackground(false);
        renameField.visible = false;
        addDrawableChild(renameField);
        reload();
    }

    private void reload() { configs = ConfigManager.list(); }

    private int listTop() { return pY + HEAD + BAR + 6; }
    private int listH() { return pY + pH - FOOT - listTop(); }
    private int rowX() { return pX + 14; }
    private int rowW() { return pW - 28; }

    private void say(String m) {
        message = m;
        messageBad = m != null && (m.startsWith("Couldn't") || m.contains("doesn't") || m.contains("already") || m.startsWith("Give") || m.contains("damaged"));
        messageAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        String active = ConfigManager.active();
        Draw.window(ctx, tr, width, height, pX, pY, pW, pH, HEAD, "CONFIGS",
                active == null || active.isEmpty() ? "Save your mods, settings and HUD as named setups" : "Active: " + active);

        // create bar: name box + Create + Import
        int by = pY + HEAD + 8;
        boolean f = nameField.isFocused();
        Draw.panel(ctx, pX + 14, by, pW - 28 - 140, 22, 7, Draw.card(), f ? Draw.withAlpha(Draw.accent(), 0xB0) : Draw.border());
        Icons.draw(ctx, "plus", pX + 20, by + 6, 10, f ? Draw.accent() : Draw.DIM2);
        int cx = pX + pW - 14 - 134;
        Draw.button(ctx, tr, cx, by, 66, 22, "Create", Draw.hit(mx, my, cx, by, 66, 22), Draw.accent() & 0xFFFFFF);
        Draw.ghost(ctx, tr, cx + 72, by, 62, 22, "Import", Draw.hit(mx, my, cx + 72, by, 62, 22));
        ctx.fill(pX + 14, listTop() - 5, pX + pW - 14, listTop() - 4, Draw.border());

        // list
        double max = Math.max(0, configs.size() * ROW - listH());
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        scroll += (scrollTarget - scroll) * 0.35;
        if (Math.abs(scrollTarget - scroll) < 0.1) scroll = scrollTarget;
        ctx.enableScissor(pX + 1, listTop(), pX + pW - 1, listTop() + listH());
        renameField.visible = false;
        for (int i = 0; i < configs.size(); i++) {
            int ry = listTop() + i * ROW - (int) Math.round(scroll);
            if (ry + ROW < listTop() || ry > listTop() + listH()) continue;
            drawRow(ctx, tr, configs.get(i), ry, mx, my, active);
        }
        ctx.disableScissor();
        if (max > 0) Draw.scrollbar(ctx, pX + pW - 8, listTop(), listH(), max, scroll);
        if (configs.isEmpty()) {
            int ccx = pX + pW / 2, ccy = listTop() + listH() / 2 - 26;
            Draw.circle(ctx, ccx, ccy + 10, 18, Draw.withAlpha(Draw.accent(), 0x1C));
            Icons.draw(ctx, "layers", ccx - 9, ccy + 1, 18, Draw.withAlpha(Draw.accent(), 0xD0));
            Draw.centered(ctx, tr, "No configs yet", ccx, ccy + 36, Draw.TEXT);
            Draw.centered(ctx, tr, "Name your current setup above and press Create", ccx, ccy + 49, Draw.DIM);
        }

        // footer: last result, or a hint
        long age = System.currentTimeMillis() - messageAt;
        if (message != null && age < 5000) {
            Draw.text(ctx, tr, Draw.trim(tr, message, pW - 28), pX + 14, pY + pH - FOOT + 9, messageBad ? 0xFFFF7A7A : 0xFF8EE3AE, false);
        } else {
            Draw.text(ctx, tr, "Load switches everything to that config. Save stores your current setup in it.", pX + 14,
                    pY + pH - FOOT + 9, Draw.DIM2, false);
        }
        super.render(ctx, mx, my, delta);
    }

    private void drawRow(DrawContext ctx, TextRenderer tr, ConfigManager.Entry e, int ry, int mx, int my, String active) {
        int x = rowX(), w = rowW(), h = ROW - 4;
        boolean isActive = e.name().equalsIgnoreCase(active);
        float hv = Draw.hoverProgress("cfg_row_" + e.name(), Draw.hit(mx, my, x, ry, w, h));
        Draw.panel(ctx, x, ry, w, h, 7, Draw.lerpColor(Draw.card(), Draw.cardHover(), hv),
                isActive ? Draw.withAlpha(Draw.accent(), 0xA0) : Draw.lerpColor(Draw.border(), Draw.withAlpha(Draw.accent(), 0x80), hv));
        Icons.draw(ctx, "layers", x + 8, ry + 9, 10, isActive ? Draw.accent() : Draw.DIM2);

        int textW = w - 30 - btnSpace();
        if (e.name().equals(renaming)) {
            Draw.panel(ctx, x + 24, ry + 4, Math.min(textW, 150), 14, 4, Draw.withAlpha(Draw.solid(), 0xE0), Draw.withAlpha(Draw.accent(), 0xB0));
            renameField.setX(x + 28);
            renameField.setY(ry + 7);
            renameField.setWidth(Math.min(textW, 150) - 8);
            renameField.visible = true;
        } else {
            String name = Draw.trim(tr, e.name(), textW - (isActive ? 44 : 0));
            Draw.text(ctx, tr, Draw.bold(name), x + 26, ry + 5, Draw.TEXT, false);
            if (isActive) {
                int cw = Draw.textW(tr, "Active") + 10, chx = x + 26 + Draw.textW(tr, Draw.bold(name)) + 6;
                Draw.roundRect(ctx, chx, ry + 4, cw, 11, 5, Draw.withAlpha(Draw.accent(), 0x40));
                Draw.text(ctx, tr, "Active", chx + 5, ry + 6, Draw.accent(), false);
            }
        }
        Draw.text(ctx, tr, "Saved " + ago(e.savedAt()), x + 26, ry + 16, Draw.DIM, false);

        // buttons: Load, Save, Copy, rename, delete
        int bx = x + w - 6 - btnSpace(), byy = ry + 5;
        Draw.button(ctx, tr, bx, byy, 44, 18, "Load", Draw.hit(mx, my, bx, byy, 44, 18), Draw.accent() & 0xFFFFFF);
        Draw.ghost(ctx, tr, bx + 48, byy, 40, 18, "Save", Draw.hit(mx, my, bx + 48, byy, 40, 18));
        Draw.ghost(ctx, tr, bx + 92, byy, 40, 18, "Copy", Draw.hit(mx, my, bx + 92, byy, 40, 18));
        iconButton(ctx, bx + 136, byy, "pencil", Draw.hit(mx, my, bx + 136, byy, 18, 18), false);
        boolean armed = e.name().equals(armedDelete) && System.currentTimeMillis() - armedAt < 3000;
        if (armed) Draw.button(ctx, tr, bx + 158 - 22, byy, 40, 18, "Sure?", Draw.hit(mx, my, bx + 136, byy, 40, 18), 0xE0475B);
        else iconButton(ctx, bx + 158, byy, "trash", Draw.hit(mx, my, bx + 158, byy, 18, 18), true);
    }

    private static int btnSpace() { return 176; }

    private void iconButton(DrawContext ctx, int x, int y, String icon, boolean hover, boolean danger) {
        float p = Draw.hoverProgress("cfg_icon_" + icon + x + y, hover);
        int edge = danger ? Draw.lerpColor(Draw.border(), 0xC0E0475B, p) : Draw.lerpColor(Draw.border(), Draw.withAlpha(Draw.accent(), 0x90), p);
        Draw.panel(ctx, x, y, 18, 18, 5, Draw.lerpColor(Draw.card(), Draw.cardHover(), p), edge);
        Icons.draw(ctx, icon, x + 4, y + 4, 10, danger ? Draw.lerpColor(Draw.DIM, 0xFFFF7A7A, p) : Draw.lerpColor(Draw.DIM, Draw.TEXT, p));
    }

    private static String ago(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        long d = s / 86400;
        return d == 1 ? "yesterday" : d + " days ago";
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() == 0) {
            int by = pY + HEAD + 8, cx = pX + pW - 14 - 134;
            if (Draw.hit(mx, my, cx, by, 66, 22)) { create(); return true; }
            if (Draw.hit(mx, my, cx + 72, by, 62, 22)) { say(ConfigManager.importClipboard()); reload(); return true; }
            if (my >= listTop() && my < listTop() + listH()) {
                for (int i = 0; i < configs.size(); i++) {
                    ConfigManager.Entry e = configs.get(i);
                    int ry = listTop() + i * ROW - (int) Math.round(scroll);
                    int bx = rowX() + rowW() - 6 - btnSpace(), byy = ry + 5;
                    if (my < ry || my >= ry + ROW - 4) continue;
                    if (Draw.hit(mx, my, bx, byy, 44, 18)) { finishRename(false); say(ConfigManager.load(e.name())); reload(); return true; }
                    if (Draw.hit(mx, my, bx + 48, byy, 40, 18)) { finishRename(false); say(ConfigManager.save(e.name())); reload(); return true; }
                    if (Draw.hit(mx, my, bx + 92, byy, 40, 18)) { say(ConfigManager.copy(e.name())); return true; }
                    if (Draw.hit(mx, my, bx + 136, byy, 18, 18) && !(e.name().equals(armedDelete) && System.currentTimeMillis() - armedAt < 3000)) {
                        startRename(e.name());
                        return true;
                    }
                    boolean armed = e.name().equals(armedDelete) && System.currentTimeMillis() - armedAt < 3000;
                    if (armed && Draw.hit(mx, my, bx + 136, byy, 40, 18)) { armedDelete = null; say(ConfigManager.delete(e.name())); reload(); return true; }
                    if (!armed && Draw.hit(mx, my, bx + 158, byy, 18, 18)) { armedDelete = e.name(); armedAt = System.currentTimeMillis(); return true; }
                }
            }
        }
        if (renaming != null && !renameField.isMouseOver(mx, my)) finishRename(true);
        return super.mouseClicked(click, doubled);
    }

    private void create() {
        String n = ConfigManager.clean(nameField.getText());
        if (n.isEmpty()) { say("Give the config a name"); setFocused(nameField); return; }
        if (ConfigManager.exists(n)) { say("\"" + n + "\" already exists - use its Save button to overwrite it"); return; }
        say(ConfigManager.save(n));
        nameField.setText("");
        reload();
    }

    private void startRename(String name) {
        renaming = name;
        renameField.setText(name);
        renameField.setCursorToEnd(false);
        renameField.visible = true;
        setFocused(renameField);
    }

    private void finishRename(boolean apply) {
        if (renaming == null) return;
        String from = renaming;
        renaming = null;
        renameField.visible = false;
        if (getFocused() == renameField) setFocused(null);
        if (apply && !ConfigManager.clean(renameField.getText()).equals(from)) {
            say(ConfigManager.rename(from, renameField.getText()));
            reload();
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (renaming != null) {
            if (enter) { finishRename(true); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE) { finishRename(false); return true; }
        }
        if (enter && nameField.isFocused()) { create(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = Math.max(0, Math.min(Math.max(0, configs.size() * ROW - listH()), scrollTarget - vertical * 36));
        return true;
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
