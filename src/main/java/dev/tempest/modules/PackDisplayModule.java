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
    private final dev.tempest.module.BoolSetting icons = add(new dev.tempest.module.BoolSetting("icons", "Show Pack Icons", true));

    private long cachedAt;
    private List<String> cached = List.of();
    private List<String> cachedIds = List.of();
    /** pack id -> its pack.png bytes (empty array = the pack has none); read once per pack. */
    private static final java.util.Map<String, byte[]> ICON_BYTES = new java.util.HashMap<>();

    public PackDisplayModule() { super("pack_display", "Pack Display", "Shows which resource pack you're using.", Category.HUD, false, 6, 74); }

    private List<String> packs() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 1000) return cached;          // the pack list only changes on a reload
        cachedAt = now;
        List<String> out = new ArrayList<>(), ids = new ArrayList<>();
        try {
            List<ResourcePackProfile> enabled = new ArrayList<>(MinecraftClient.getInstance().getResourcePackManager().getEnabledProfiles());
            for (int i = enabled.size() - 1; i >= 0; i--) {             // last in the list = top of the pack screen
                ResourcePackProfile p = enabled.get(i);
                String id = p.getId();
                if (!(id.startsWith("file/") || id.startsWith("server") || id.startsWith("world") || id.startsWith("download"))) continue;
                String name = p.getDisplayName().getString().replaceAll("§.", "").trim();
                if (name.toLowerCase().endsWith(".zip")) name = name.substring(0, name.length() - 4);
                if (!name.isEmpty()) { out.add(name); ids.add(id); }
                if (!ICON_BYTES.containsKey(id)) ICON_BYTES.put(id, readIcon(p));
            }
        } catch (Throwable ignored) { }
        cached = out;
        cachedIds = ids;
        return out;
    }

    /** The pack's pack.png, or an empty array. */
    private static byte[] readIcon(ResourcePackProfile p) {
        try (net.minecraft.resource.ResourcePack pack = p.createResourcePack()) {
            var supplier = pack.openRoot("pack.png");
            if (supplier == null) return new byte[0];
            try (java.io.InputStream in = supplier.get()) { return in.readAllBytes(); }
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    @Override
    public void renderHud(DrawContext ctx, boolean preview) {
        List<String> names = preview ? List.of("Tempest 16x", "Low Fire") : packs();
        List<String> lines = new ArrayList<>();
        if (names.isEmpty()) lines.add("Pack: Default");
        int n = Math.min(names.size(), max.getInt());
        List<net.minecraft.util.Identifier> tex = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lines.add((n == 1 ? "Pack" : "#" + (i + 1)) + ": " + names.get(i));
            tex.add(!preview && icons.get() && i < cachedIds.size() ? icon(cachedIds.get(i)) : null);
        }
        if (names.size() > n) { lines.add("+" + (names.size() - n) + " more"); tex.add(null); }
        drawLines(ctx, lines, null, tex);
    }

    private static net.minecraft.util.Identifier icon(String packId) {
        byte[] png = ICON_BYTES.get(packId);
        return png == null || png.length == 0 ? null : dev.tempest.util.ServerIcons.get("pack_" + packId, png);
    }
}
