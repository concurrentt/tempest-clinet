package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * "Tempest Custom": a normal resource pack in your resourcepacks folder that the Texture Painter saves into.
 * Each painted texture is written to the same path it has in the game (assets/&lt;ns&gt;/textures/...), so it
 * replaces that texture while the pack is on. You can share the folder like any other pack.
 */
public final class CustomPack {
    public static final String NAME = "Tempest Custom";
    public static final String PACK_ID = "file/" + NAME;

    private CustomPack() { }

    public static Path dir() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("resourcepacks").resolve(NAME);
    }

    public static Path fileFor(Identifier id) {
        return dir().resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
    }

    public static boolean edited(Identifier id) { return Files.isRegularFile(fileFor(id)); }

    /** Every texture currently in the pack. */
    public static Set<Identifier> editedTextures() {
        Set<Identifier> out = new HashSet<>();
        Path assets = dir().resolve("assets");
        if (!Files.isDirectory(assets)) return out;
        try (Stream<Path> s = Files.walk(assets)) {
            s.filter(p -> p.toString().endsWith(".png")).forEach(p -> {
                Path rel = assets.relativize(p);
                if (rel.getNameCount() < 3) return;
                String ns = rel.getName(0).toString();
                String path = rel.subpath(1, rel.getNameCount()).toString().replace('\\', '/');
                Identifier id = Identifier.tryParse(ns + ":" + path);
                if (id != null) out.add(id);
            });
        } catch (IOException ignored) { }
        return out;
    }

    public static void ensureMeta() throws IOException {
        Path d = dir();
        Files.createDirectories(d);
        Path meta = d.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            String json = """
                    {
                      "pack": {
                        "pack_format": 34,
                        "supported_formats": [34, 999],
                        "min_format": [34, 0],
                        "max_format": [999, 0],
                        "description": "Textures you painted in Tempest"
                      }
                    }
                    """;
            Files.writeString(meta, json, StandardCharsets.UTF_8);
        }
        Path icon = d.resolve("pack.png");
        if (!Files.exists(icon)) {
            try (InputStream in = CustomPack.class.getResourceAsStream("/assets/tempest/icon.png")) {
                if (in != null) Files.copy(in, icon);
            }
        }
    }

    /** Writes the texture (ARGB pixels) into the pack, plus its animation file if the original has one. */
    public static void save(Identifier id, int[] argb, int w, int h) throws IOException {
        ensureMeta();
        Path out = fileFor(id);
        Files.createDirectories(out.getParent());
        try (NativeImage img = new NativeImage(w, h, true)) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setColorArgb(x, y, argb[y * w + x]);
            img.writeTo(out);
        }
        Identifier metaId = Identifier.of(id.getNamespace(), id.getPath() + ".mcmeta");
        Path metaOut = fileFor(metaId);
        if (!Files.exists(metaOut)) {
            Optional<Resource> meta = MinecraftClient.getInstance().getResourceManager().getResource(metaId);
            if (meta.isPresent()) {
                try (InputStream in = meta.get().getInputStream()) { Files.copy(in, metaOut); }
            }
        }
    }

    /** Takes a texture back out of the pack (the game's or your other packs' version shows again). */
    public static void remove(Identifier id) throws IOException {
        Files.deleteIfExists(fileFor(id));
        Files.deleteIfExists(fileFor(Identifier.of(id.getNamespace(), id.getPath() + ".mcmeta")));
    }

    /** The texture as the game currently shows it (null if missing). */
    public static NativeImage read(Identifier id, boolean skipOurs) {
        var rm = MinecraftClient.getInstance().getResourceManager();
        try {
            List<Resource> all = rm.getAllResources(id);
            for (int i = all.size() - 1; i >= 0; i--) {
                Resource r = all.get(i);
                if (skipOurs && PACK_ID.equals(r.getPackId())) continue;
                try (InputStream in = r.getInputStream()) { return NativeImage.read(in); }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Turns the pack on (on top of your other packs) if needed, and reloads resources so your edits show. */
    public static void apply() {
        MinecraftClient c = MinecraftClient.getInstance();
        ResourcePackManager mgr = c.getResourcePackManager();
        mgr.scanPacks();
        if (!mgr.getEnabledIds().contains(PACK_ID) && mgr.getProfile(PACK_ID) != null) {
            mgr.enable(PACK_ID);
            c.options.refreshResourcePacks(mgr);
        }
        c.reloadResources();
    }

    public static boolean enabled() {
        return MinecraftClient.getInstance().getResourcePackManager().getEnabledIds().contains(PACK_ID);
    }
}
