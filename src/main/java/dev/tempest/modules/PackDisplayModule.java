package dev.tempest.modules;

import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import dev.tempest.module.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.List;

/** Shows the resource packs you have on (top pack first). Built-in packs (vanilla, mods) are left out. */
public class PackDisplayModule extends HudModule {
    private final NumberSetting max = add(new NumberSetting("max", "Max Packs Shown", 3, 1, 8, 1));

    private long cachedAt;
    private List<String> cached = List.of();

    public PackDisplayModule() { super("pack_display", "Pack Display", "Shows which resource pack you're using.", Category.HUD, false, 6, 74); }

    private List<String> packs() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 1000) return cached;          // the pack list only changes on a reload
        cachedAt = now;
        List<String> out = new ArrayList<>();
        try {
            List<ResourcePackProfile> enabled = new ArrayList<>(MinecraftClient.getInstance().getResourcePackManager().getEnabledProfiles());
            for (int i = enabled.size() - 1; i >= 0; i--) {             // last in the list = top of the pack screen
                ResourcePackProfile p = enabled.get(i);
                String id = p.getId();
                if (!(id.startsWith("file/") || id.startsWith("server") || id.startsWith("world") || id.startsWith("download"))) continue;
                String name = p.getDisplayName().getString().replaceAll("§.", "").trim();
                if (name.toLowerCase().endsWith(".zip")) name = name.substring(0, name.length() - 4);
                if (!name.isEmpty()) out.add(name);
            }
        } catch (Throwable ignored) { }
        cached = out;
        return out;
    }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        List<String> names = preview ? List.of("Tempest 16x", "Low Fire") : packs();
        List<String> lines = new ArrayList<>();
        if (names.isEmpty()) lines.add("Pack: Default");
        int n = Math.min(names.size(), max.getInt());
        for (int i = 0; i < n; i++) lines.add((n == 1 ? "Pack" : "#" + (i + 1)) + ": " + names.get(i));
        if (names.size() > n) lines.add("+" + (names.size() - n) + " more");
        drawLines(ctx, lines);
    }
}
