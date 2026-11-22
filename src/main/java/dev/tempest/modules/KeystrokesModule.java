package dev.tempest.modules;

import dev.tempest.gui.Draw;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.NumberSetting;
import dev.tempest.util.CombatTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;

public class KeystrokesModule extends HudModule {
    private final BoolSetting mouse = add(new BoolSetting("mouse", "Mouse Buttons", true));
    private final BoolSetting space = add(new BoolSetting("space", "Space Bar", true));
    private final BoolSetting cps = add(new BoolSetting("cps", "Show CPS", true));
    private final NumberSetting animSpeed = add(new NumberSetting("anim_speed", "Animation Speed", 12.0, 4.0, 30.0, 1.0));
    private final ModeSetting easing = add(new ModeSetting("easing", "Easing", "Smooth", "Smooth", "Linear", "Snappy"));
    private final float[] fade = new float[7];
    private long lastNanos;

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "WASD, mouse buttons and jump.", Category.HUD, false, 6, 44);
    }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        GameOptions o = MinecraftClient.getInstance().options;
        boolean[] down = new boolean[7];
        if (!preview) {
            down[0] = o.forwardKey.isPressed();
            down[1] = o.leftKey.isPressed();
            down[2] = o.backKey.isPressed();
            down[3] = o.rightKey.isPressed();
            down[4] = o.attackKey.isPressed();
            down[5] = o.useKey.isPressed();
            down[6] = o.jumpKey.isPressed();
        }

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        for (int i = 0; i < fade.length; i++) {
            fade[i] = ease(fade[i], down[i] ? 1f : 0f, dt);
        }

        int k = 22, g = 3;
        w = 3 * k + 2 * g;
        h = 2 * k + g;

        key(ctx, x + k + g, y, k, k, "W", fade[0]);
        key(ctx, x, y + k + g, k, k, "A", fade[1]);
        key(ctx, x + k + g, y + k + g, k, k, "S", fade[2]);
        key(ctx, x + 2 * (k + g), y + k + g, k, k, "D", fade[3]);

        int row = y + 2 * (k + g);
        if (mouse.get()) {
            int half = (w - g) / 2;
            String l = cps.get() ? "LMB " + (preview ? 8 : CombatTracker.leftCps()) : "LMB";
            String r = cps.get() ? "RMB " + (preview ? 3 : CombatTracker.rightCps()) : "RMB";
            key(ctx, x, row, half, k, l, fade[4]);
            key(ctx, x + half + g, row, half, k, r, fade[5]);
            row += k + g;
            h += k + g;
        }
        if (space.get()) {
            key(ctx, x, row, w, 11, "", fade[6]);
            int bar = 18;
            Draw.roundRect(ctx, x + (w - bar) / 2, row + 5, bar, 1, 0,
                    Draw.lerpColor(color.get(), 0xFFFFFFFF, fade[6]));
            h += 11 + g;
        }
    }

    private float ease(float cur, float target, float dt) {
        float speed = (float) animSpeed.get();
        String mode = easing.get();
        float next;
        if ("Linear".equals(mode)) {
            float step = speed * dt * 0.08f;
            if (Math.abs(target - cur) <= step) return target;
            next = cur + Math.copySign(step, target - cur);
        } else if ("Snappy".equals(mode)) {
            next = cur + (target - cur) * (1f - (float) Math.exp(-dt * speed * 1.6f));
        } else {
            next = cur + (target - cur) * (1f - (float) Math.exp(-dt * speed));
        }
        if (Math.abs(target - next) < 0.001f) next = target;
        return next;
    }

    private void key(DrawContext ctx, int kx, int ky, int kw, int kh, String label, float f) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int a = Math.max(0x40, alpha());
        int base = background.get() ? Draw.withAlpha(0x0A1233, a) : Draw.withAlpha(0x0A1233, 0x30);
        Draw.roundRect(ctx, kx, ky, kw, kh, 4, Draw.lerpColor(base, Draw.accent(), f));
        if (label.isEmpty()) return;
        int tw = tr.getWidth(label);
        ctx.drawText(tr, label, kx + (kw - tw) / 2, ky + (kh - 8) / 2, Draw.lerpColor(color.get(), 0xFFFFFFFF, f), false);
    }
}
