package dev.tempest.modules;

import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.NumberSetting;
import dev.tempest.util.CombatTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Distance of your hits. Advanced mode adds average / longest / shortest, hit count and the last few hits. */
public class ReachModule extends HudModule {
    private final ModeSetting mode = add(new ModeSetting("mode", "Mode", "Advanced", "Simple", "Advanced"));
    private final NumberSetting decimals = add(new NumberSetting("decimals", "Decimals", 2, 1, 3, 1));
    private final BoolSetting colorByDistance = add(new BoolSetting("by_distance", "Color By Distance", true));
    private final BoolSetting recent = add(new BoolSetting("recent", "Show Last 5 Hits", false));
    private final NumberSetting hold = add(new NumberSetting("hold", "Hold Last Hit (s)", 5, 1, 30, 1));

    public ReachModule() {
        super("reach", "Reach Display", "Distance of your hits, with average / longest / shortest.", Category.HUD, false, 6, 112);
        add(new ActionSetting("reset", "Reset Stats", "Reset", CombatTracker::resetReachStats));
    }

    private String fmt(double v) { return String.format(Locale.ROOT, "%." + decimals.getInt() + "f", v); }

    private int tint(double reach) {
        if (!colorByDistance.get()) return color.get();
        double max = 3.0;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null) max = c.player.getEntityInteractionRange();
        if (reach <= max - 0.4) return 0xFF55FF55;
        if (reach <= max + 0.05) return 0xFFFFD83A;
        return 0xFFFF5555;
    }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        boolean live = !preview && System.currentTimeMillis() - CombatTracker.reachTime < hold.get() * 1000;
        double last = preview ? 3.0 : CombatTracker.reach;
        List<String> lines = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();

        lines.add("Reach: " + (preview || live ? fmt(last) : "-"));
        cols.add(preview || live ? tint(last) : color.get());

        if (mode.is("Advanced")) {
            int hits = preview ? 12 : CombatTracker.reachHits;
            if (hits > 0) {
                double avg = preview ? 2.84 : CombatTracker.reachAvg();
                double max = preview ? 3.2 : CombatTracker.reachMax;
                double min = preview ? 1.9 : CombatTracker.reachMin;
                lines.add("Avg: " + fmt(avg) + "  Hits: " + hits);
                cols.add(color.get());
                lines.add("Max: " + fmt(max) + "  Min: " + fmt(min));
                cols.add(color.get());
            }
            if (recent.get()) {
                List<Double> r = preview ? List.of(2.7, 3.0, 2.4) : CombatTracker.recentReaches();
                if (!r.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = r.size() - 1; i >= 0; i--) sb.append(fmt(r.get(i))).append(i > 0 ? "  " : "");
                    lines.add(sb.toString());
                    cols.add(0xFF8B96C0);
                }
            }
        }
        drawLines(ctx, lines, cols);
    }
}
