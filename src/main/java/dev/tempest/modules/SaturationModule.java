package dev.tempest.modules;

import dev.tempest.mixin.GameRendererPostAccessor;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Color Saturation: makes the whole game more colourful (over 100%) or more washed-out / black and white (under 100%).
 * A full-screen shader (assets/tempest/post_effect/saturation_N.json), only on your screen. It steps aside when the
 * game uses its own screen effect (e.g. spectating a creeper or spider) and doesn't touch Tempest's menus.
 */
public class SaturationModule extends Module {
    private final NumberSetting amount = add(new NumberSetting("amount", "Saturation %", 140, 0, 200, 10));
    private Identifier applied;

    public SaturationModule() {
        super("saturation", "Color Saturation", "Make colours more vivid, or wash them out to black and white.", Category.VISUALS, false);
    }

    private static boolean ours(Identifier id) {
        return id != null && id.getNamespace().equals("tempest") && id.getPath().startsWith("saturation_");
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (c.gameRenderer == null || c.world == null) return;
        Identifier current = c.gameRenderer.getPostProcessorId();
        if (current != null && !ours(current)) { applied = null; return; }      // the game's own effect wins
        int pct = (int) (Math.round(amount.get() / 10.0) * 10);
        Identifier want = pct == 100 ? null : Identifier.of("tempest", "saturation_" + Math.max(0, Math.min(200, pct)));
        if (want == null) {
            if (ours(current)) c.gameRenderer.clearPostProcessor();
            applied = null;
        } else if (!want.equals(current)) {
            ((GameRendererPostAccessor) c.gameRenderer).tempest$setPostProcessor(want);
            applied = want;
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.gameRenderer != null && ours(c.gameRenderer.getPostProcessorId())) c.gameRenderer.clearPostProcessor();
        applied = null;
    }
}
