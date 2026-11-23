package dev.tempest.util;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Gives the game window the Tempest icon (taskbar, title bar, Alt-Tab) instead of the grass block. Set once on the
 * first client tick. Windows and Linux only: on macOS the dock icon isn't set through the window.
 */
public final class WindowIcon {
    private static boolean done;

    private WindowIcon() { }

    public static void tick(MinecraftClient c) {
        if (done) return;
        done = true;
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) return;
        List<ByteBuffer> pixels = new ArrayList<>();
        try (InputStream in = WindowIcon.class.getResourceAsStream("/assets/tempest/icon.png")) {
            if (in == null) return;
            BufferedImage src = ImageIO.read(in);
            if (src == null) return;
            int[] sizes = {16, 32, 48, 64, 128, 256};
            try (GLFWImage.Buffer icons = GLFWImage.malloc(sizes.length)) {
                for (int i = 0; i < sizes.length; i++) {
                    int s = sizes[i];
                    BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
                    var g = img.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(src, 0, 0, s, s, null);
                    g.dispose();
                    ByteBuffer buf = MemoryUtil.memAlloc(s * s * 4);
                    pixels.add(buf);
                    for (int y = 0; y < s; y++) {
                        for (int x = 0; x < s; x++) {
                            int argb = img.getRGB(x, y);
                            buf.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                        }
                    }
                    buf.flip();
                    icons.position(i).width(s).height(s).pixels(buf);
                }
                icons.position(0);
                GLFW.glfwSetWindowIcon(c.getWindow().getHandle(), icons);
            }
        } catch (Throwable t) {
            System.err.println("[Tempest] Couldn't set the window icon: " + t);
        } finally {
            for (ByteBuffer b : pixels) MemoryUtil.memFree(b);
        }
    }
}
