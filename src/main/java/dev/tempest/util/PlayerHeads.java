package dev.tempest.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Face textures (skin face + hat layer, 32x32) for the menus. Looks the skin up through Mojang's public profile
 * endpoint once per player, off the render thread; returns null until it has arrived (or for offline accounts).
 */
public final class PlayerHeads {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<UUID, Identifier> READY = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> STARTED = new ConcurrentHashMap<>();

    private PlayerHeads() { }

    public static Identifier get(UUID id) {
        if (id == null || id.version() != 4) return null;     // offline-mode UUIDs have no Mojang profile
        Identifier ready = READY.get(id);
        if (ready != null) return ready;
        if (STARTED.putIfAbsent(id, Boolean.TRUE) == null) fetch(id);
        return null;
    }

    private static void fetch(UUID id) {
        String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + id.toString().replace("-", "");
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).build(), HttpResponse.BodyHandlers.ofString())
                .thenCompose(res -> {
                    if (res.statusCode() != 200) throw new IllegalStateException("profile " + res.statusCode());
                    JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                    String value = o.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
                    JsonObject tex = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject();
                    String skin = tex.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                    if (skin.startsWith("http://")) skin = "https://" + skin.substring(7);
                    return HTTP.sendAsync(HttpRequest.newBuilder(URI.create(skin)).timeout(Duration.ofSeconds(8)).build(), HttpResponse.BodyHandlers.ofByteArray());
                })
                .thenAccept(res -> {
                    try {
                        byte[] png = face(res.body());
                        MinecraftClient.getInstance().execute(() -> register(id, png));
                    } catch (Exception ignored) { }
                })
                .exceptionally(e -> null);
    }

    /** Crops face (8,8) and hat (40,8) from a skin and scales the result to 32x32 with hard pixels. */
    private static byte[] face(byte[] skinPng) throws Exception {
        BufferedImage skin = ImageIO.read(new ByteArrayInputStream(skinPng));
        int unit = Math.max(1, skin.getWidth() / 64);
        BufferedImage out = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int sx = (8 + x / 4) * unit, sy = (8 + y / 4) * unit;
                int base = skin.getRGB(sx, sy) | 0xFF000000;
                int hat = skin.getRGB(sx + 32 * unit, sy);
                int a = hat >>> 24;
                int c = base;
                if (a > 0) {
                    int r = (((hat >> 16) & 255) * a + ((base >> 16) & 255) * (255 - a)) / 255;
                    int g = (((hat >> 8) & 255) * a + ((base >> 8) & 255) * (255 - a)) / 255;
                    int b = ((hat & 255) * a + (base & 255) * (255 - a)) / 255;
                    c = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                out.setRGB(x, y, c);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bytes);
        return bytes.toByteArray();
    }

    private static void register(UUID id, byte[] png) {
        try {
            NativeImage img = NativeImage.read(png);
            Identifier tex = Identifier.of("tempest", "head/" + id.toString().replace("-", ""));
            MinecraftClient.getInstance().getTextureManager().registerTexture(tex, new NativeImageBackedTexture(() -> "Tempest head " + id, img));
            READY.put(id, tex);
        } catch (Exception ignored) { }
    }
}
