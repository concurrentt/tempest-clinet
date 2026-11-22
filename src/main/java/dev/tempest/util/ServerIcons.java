package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Turns server icons (PNG bytes, any size; scaled to 64x64) into textures, once per server, and reuses them while the icon is unchanged. */
public final class ServerIcons {
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static final Map<String, Integer> HASHES = new HashMap<>();

    private ServerIcons() { }

    /** Icons that are not 64x64 (some proxies send 128x128 or odd sizes) are scaled, instead of dropped like vanilla does. */
    private static byte[] to64(byte[] png) throws java.io.IOException {
        java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        if (src == null) throw new java.io.IOException("not an image");
        if (src.getWidth() == 64 && src.getHeight() == 64 && png.length > 8 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G') return png;
        java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int side = Math.min(src.getWidth(), src.getHeight());
        int ox = (src.getWidth() - side) / 2, oy = (src.getHeight() - side) / 2;
        g.drawImage(src, 0, 0, 64, 64, ox, oy, ox + side, oy + side, null);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(dst, "png", out);
        return out.toByteArray();
    }

    /** Texture for this server's icon, or null if there is none (or it could not be read). Call on the render thread. */
    public static Identifier get(String address, byte[] png) {
        if (address == null || png == null || png.length == 0) return null;
        int hash = Arrays.hashCode(png);
        Integer known = HASHES.get(address);
        if (known != null && known == hash) return TEXTURES.get(address);
        HASHES.put(address, hash);
        TEXTURES.remove(address);
        try {
            NativeImage img = NativeImage.read(to64(png));
            Identifier id = Identifier.of("tempest", "server_icon/" + address.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_"));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "Tempest server icon " + address, img));
            TEXTURES.put(address, id);
            return id;
        } catch (Exception e) {
            return null;
        }
    }
}
