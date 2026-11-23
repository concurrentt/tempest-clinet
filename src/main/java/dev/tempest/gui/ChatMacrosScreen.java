package dev.tempest.gui;

import dev.tempest.TempestConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Edit Chat Macros: each row is a key and the message (or /command) it sends. */
public class ChatMacrosScreen extends Screen {
    private static final int MAX = 8, ROW_H = 26, KEY_W = 74;
    private final Screen parent;
    private final List<TextFieldWidget> fields = new ArrayList<>();
    /** Row waiting for a key press, or -1. */
    private int capturing = -1;
    private int removeArmed = -1;
    private long removeArmedAt;

    public ChatMacrosScreen(Screen parent) {
        super(Text.literal("Chat Macros"));
        this.parent = parent;
    }

    private static List<TempestConfig.ChatMacro> macros() { return TempestConfig.get().chatMacros; }

    private int pw() { return Math.min(width - 24, 380); }
    private int ph() { return Math.min(height - 24, 76 + MAX * ROW_H + 34); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int rowY(int i) { return py() + 44 + i * ROW_H; }
    private int visibleRows() { return Math.max(1, Math.min(MAX, (ph() - 44 - 64) / ROW_H)); }

    @Override
    protected void init() {
        fields.clear();
        List<TempestConfig.ChatMacro> ms = macros();
        int x = px(), w = pw();
        for (int i = 0; i < Math.min(ms.size(), visibleRows()); i++) {
            TempestConfig.ChatMacro m = ms.get(i);
            TextFieldWidget f = new TextFieldWidget(textRenderer, x + 14 + KEY_W + 12, rowY(i) + 6, w - KEY_W - 70, 12, Text.literal("Message"));
            f.setDrawsBackground(false);
            f.setMaxLength(256);
            f.setPlaceholder(Text.literal("Message, or /command"));
            f.setText(m.text == null ? "" : m.text);
            f.setChangedListener(s -> m.text = s);
            addDrawableChild(f);
            fields.add(f);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean shouldPause() { return false; }

    private static String keyName(int code) {
        if (code < 0) return "Set key";
        try { return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString(); }
        catch (Throwable t) { return "Key " + code; }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer tr = textRenderer;
        ctx.fillGradient(0, 0, width, height, 0x90000000, 0x70000000);
        int x = px(), y = py(), w = pw(), h = ph();
        Draw.shadow(ctx, x, y, w, h, 10, 10);
        Draw.glass(ctx, x, y, w, h, 10, 0xE8101218, 0x40FFFFFF);
        Draw.roundRectV(ctx, x + 1, y + 1, w - 2, 32, 9, Draw.withAlpha(Draw.accent(), 0x26), Draw.withAlpha(Draw.accent(), 0x00));
        Draw.text(ctx, tr, Draw.bold("Chat Macros"), x + 14, y + 11, 0xFFFFFFFF, false);
        Draw.text(ctx, tr, "Press the key in game to send the message", x + 14, y + 23, 0xFF8B90A0, false);
        Draw.hairline(ctx, x + 10, y + 35, w - 20, Draw.accent() & 0xFFFFFF, 0x50);

        List<TempestConfig.ChatMacro> ms = macros();
        if (ms.isEmpty()) Draw.centered(ctx, tr, "No macros yet - press + Add macro", x + w / 2, rowY(0) + 8, 0xFF8B90A0);
        for (int i = 0; i < fields.size(); i++) {
            TempestConfig.ChatMacro m = ms.get(i);
            int ry = rowY(i);
            // key button
            boolean cap = capturing == i, kh = Draw.hit(mx, my, x + 14, ry + 2, KEY_W, 20);
            Draw.panel(ctx, x + 14, ry + 2, KEY_W, 20, 5, cap ? Draw.withAlpha(Draw.accent(), 0x50) : kh ? 0xEC2A2C33 : 0xD81A1B20,
                    cap ? Draw.withAlpha(Draw.accent(), 0xE0) : 0x40FFFFFF);
            String kl = cap ? "Press a key…" : keyName(m.key);
            Draw.centered(ctx, tr, Draw.ellipsize(tr, kl, KEY_W - 6), x + 14 + KEY_W / 2, ry + 8, m.key < 0 && !cap ? 0xFFFFD27A : 0xFFFFFFFF);
            // message box
            TextFieldWidget f = fields.get(i);
            int fx = x + 14 + KEY_W + 6, fw = w - KEY_W - 58;
            Draw.panel(ctx, fx, ry + 2, fw, 20, 5, f.isFocused() ? 0xE0202330 : 0xC0181A22,
                    f.isFocused() ? Draw.withAlpha(Draw.accent(), 0xA0) : 0x30FFFFFF);
            // remove
            int bx = x + w - 34;
            boolean armed = removeArmed == i && System.currentTimeMillis() - removeArmedAt < 3000;
            boolean rh = Draw.hit(mx, my, bx, ry + 2, 20, 20);
            Draw.panel(ctx, bx, ry + 2, 20, 20, 5, rh || armed ? 0xC03A1C22 : 0x80181A22, rh || armed ? 0xA0FF6B6B : 0x30FFFFFF);
            Draw.centered(ctx, tr, armed ? "?" : "✕", bx + 10, ry + 8, armed ? 0xFFFF8A8A : 0xFFC9CCD6);
        }

        int by = y + h - 30;
        Draw.text(ctx, tr, Draw.ellipsize(tr, "Use keys you don't need for anything else. Click ✕ twice to delete.", w - 28), x + 14, by - 16, 0xFF5E6375, false);
        boolean canAdd = ms.size() < visibleRows();
        MenuKit.flatButton(ctx, tr, "cm_add", x + 12, by, 100, 20, null, "+ Add macro", MenuKit.GREEN, Draw.hit(mx, my, x + 12, by, 100, 20), canAdd, 1f);
        MenuKit.flatButton(ctx, tr, "cm_done", x + w - 72, by, 60, 20, null, "Done", MenuKit.OUTLINE, Draw.hit(mx, my, x + w - 72, by, 60, 20), true, 1f);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int x = px(), y = py(), w = pw(), h = ph(), by = y + h - 30;
        if (capturing >= 0) { capturing = -1; return true; }   // clicking anywhere cancels "press a key"
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        if (Draw.hit(mx, my, x + w - 72, by, 60, 20)) { close(); return true; }
        if (Draw.hit(mx, my, x + 12, by, 100, 20) && macros().size() < visibleRows()) {
            macros().add(new TempestConfig.ChatMacro());
            clearAndInit();
            capturing = macros().size() - 1;
            return true;
        }
        for (int i = 0; i < fields.size(); i++) {
            int ry = rowY(i);
            if (Draw.hit(mx, my, x + 14, ry + 2, KEY_W, 20)) {
                setFocused(null);
                capturing = i;
                return true;
            }
            if (Draw.hit(mx, my, x + w - 34, ry + 2, 20, 20)) {
                if (removeArmed == i && System.currentTimeMillis() - removeArmedAt < 3000) {
                    macros().remove(i);
                    removeArmed = -1;
                    clearAndInit();
                } else {
                    removeArmed = i;
                    removeArmedAt = System.currentTimeMillis();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (capturing >= 0 && capturing < macros().size()) {
            int k = input.key();
            if (k == GLFW.GLFW_KEY_ESCAPE) capturing = -1;
            else {
                macros().get(capturing).key = k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE ? -1 : k;
                capturing = -1;
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        // drop rows that were never filled in
        macros().removeIf(m -> m.key < 0 && (m.text == null || m.text.isBlank()));
        TempestConfig.save();
        client.setScreen(parent);
    }
}
