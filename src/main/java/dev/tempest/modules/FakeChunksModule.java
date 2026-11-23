package dev.tempest.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ModeSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.module.NumberSetting;
import dev.tempest.util.Chat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake Chunks (singleplayer): like the Chunky mod, generates the land around where you spawned in the background,
 * so it's already there when you walk or fly out. Square of "radius" chunks around the chunk you first joined in,
 * nearest chunks first.
 *
 * <p>Light: one chunk at a time on the world's own thread, then a pause several times as long as that chunk took,
 * so generation only ever uses a small share of the game's time (Light = about 15%, Normal 30%, Fast 50%). Progress
 * is saved per world (config/tempest-fakechunks.json), so leaving and rejoining continues where it stopped.
 * Only works in singleplayer: a server only sends you chunks within its own view distance.</p>
 */
public final class FakeChunksModule extends Module {
    private final NumberSetting radius = add(new NumberSetting("radius", "Radius (chunks)", 32, 8, 64, 1));
    private final ModeSetting speed = add(new ModeSetting("speed", "Speed", "Light", "Light", "Normal", "Fast"));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Worker worker;

    public FakeChunksModule() {
        super("fake_chunks", "Fake Chunks", "Singleplayer: generates the land around your spawn in the background, like Chunky.",
                Category.UTILITY, true);
        add(new ActionSetting("progress", "Progress", "Show", () -> Chat.info(status())));
        add(new ActionSetting("restart", "Start Over Here", "Restart", FakeChunksModule::restartHere));
        keyHint = "Starts when you load a singleplayer world. Pauses while the game is busy.";
    }

    @Override
    public void onTick(MinecraftClient c) {
        IntegratedServer server = c.getServer();
        Worker w = worker;
        if (server == null || c.world == null || c.player == null) {
            if (w != null) { w.stop(); worker = null; }
            return;
        }
        String key = key(server, c);
        if (w != null && !w.key.equals(key)) { w.stop(); worker = w = null; }   // another world or dimension
        if (w != null && (w.alive() || w.finished || w.crashed)) return;         // running, or done this session
        startFor(server, c, key, false);
    }

    @Override
    public void onDisable() {
        Worker w = worker;
        if (w != null) { w.stop(); worker = null; }
    }

    private static String key(IntegratedServer server, MinecraftClient c) {
        String save = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().toString();
        return save + "|" + c.world.getRegistryKey().getValue();
    }

    private void startFor(IntegratedServer server, MinecraftClient c, String key, boolean fresh) {
        JsonObject all = load();
        JsonObject p = !fresh && all.has(key) ? all.getAsJsonObject(key) : null;
        int r = radius.getInt();
        int cx, cz, index = 0;
        if (p != null && p.get("radius").getAsInt() == r) {
            cx = p.get("cx").getAsInt();
            cz = p.get("cz").getAsInt();
            index = p.get("index").getAsInt();
        } else {
            cx = c.player.getChunkPos().x;   // where you spawned / first joined
            cz = c.player.getChunkPos().z;
        }
        Worker w = new Worker(server, c.world.getRegistryKey(), key, cx, cz, r, index, duty());
        worker = w;
        if (index < w.total) {
            if (index == 0) Chat.info("Fake Chunks: generating " + w.total + " chunks around you in the background.");
            w.start();
        } else {
            w.finished = true;
        }
    }

    private float duty() {
        return switch (speed.get()) { case "Fast" -> 0.5f; case "Normal" -> 0.3f; default -> 0.15f; };
    }

    private static void restartHere() {
        FakeChunksModule m = ModuleManager.get(FakeChunksModule.class);
        MinecraftClient c = MinecraftClient.getInstance();
        if (m == null || c.getServer() == null || c.world == null || c.player == null) { Chat.info("Fake Chunks only works in singleplayer."); return; }
        Worker w = worker;
        if (w != null) w.stop();
        worker = null;
        if (!m.isEnabled()) m.setEnabled(true);
        m.startFor(c.getServer(), c, key(c.getServer(), c), true);
        Chat.info("Fake Chunks: starting over around you.");
    }

    private static String status() {
        Worker w = worker;
        if (MinecraftClient.getInstance().getServer() == null) return "Fake Chunks only works in singleplayer.";
        if (w == null) return "Fake Chunks isn't running (turn it on and load a world).";
        int done = Math.min(w.index, w.total);
        return String.format(java.util.Locale.ROOT, "Fake Chunks: %d / %d chunks (%.0f%%)%s", done, w.total, 100.0 * done / w.total,
                w.finished ? " - done" : "");
    }

    // ------------------------------------------------------------------ progress file
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("tempest-fakechunks.json"); }

    private static synchronized JsonObject load() {
        try {
            if (Files.exists(file())) return JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) { }
        return new JsonObject();
    }

    private static synchronized void save(String key, int cx, int cz, int r, int index) {
        JsonObject all = load();
        JsonObject p = new JsonObject();
        p.addProperty("cx", cx);
        p.addProperty("cz", cz);
        p.addProperty("radius", r);
        p.addProperty("index", index);
        all.add(key, p);
        try { Files.writeString(file(), GSON.toJson(all), StandardCharsets.UTF_8); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ the background worker
    private static final class Worker implements Runnable {
        final IntegratedServer server;
        final net.minecraft.registry.RegistryKey<net.minecraft.world.World> dim;
        final String key;
        final int cx, cz, r, total;
        final float duty;
        final int[] order;               // packed (dx, dz) offsets, nearest first
        volatile int index;
        volatile boolean stopped, finished, crashed;
        private Thread thread;

        Worker(IntegratedServer server, net.minecraft.registry.RegistryKey<net.minecraft.world.World> dim, String key,
               int cx, int cz, int r, int index, float duty) {
            this.server = server;
            this.dim = dim;
            this.key = key;
            this.cx = cx;
            this.cz = cz;
            this.r = r;
            this.duty = duty;
            List<int[]> offs = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) offs.add(new int[]{dx, dz});
            offs.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
            order = new int[offs.size()];
            for (int i = 0; i < order.length; i++) order[i] = (offs.get(i)[0] & 0xFFFF) << 16 | (offs.get(i)[1] & 0xFFFF);
            total = order.length;
            this.index = Math.max(0, Math.min(index, total));
        }

        void start() {
            thread = new Thread(this, "Tempest Fake Chunks");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }

        boolean alive() { return thread != null && thread.isAlive(); }

        void stop() {
            stopped = true;
            save(key, cx, cz, r, index);
        }

        @Override
        public void run() {
            long lastSave = System.currentTimeMillis();
            try {
                while (!stopped && index < total && server.isRunning()) {
                    FakeChunksModule m = ModuleManager.get(FakeChunksModule.class);
                    if (m == null || !m.isEnabled()) break;
                    MinecraftClient c = MinecraftClient.getInstance();
                    if (c.isPaused()) { Thread.sleep(500); continue; }          // the world is paused: so are we
                    int packed = order[index];
                    int x = cx + (short) (packed >> 16), z = cz + (short) packed;
                    long took;
                    try {
                        took = server.submit(() -> {
                            long t0 = System.nanoTime();
                            ServerWorld world = server.getWorld(dim);
                            if (world != null) world.getChunk(x, z);        // generates it (or loads it if it exists)
                            return System.nanoTime() - t0;
                        }).get(60, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (java.util.concurrent.ExecutionException e) {
                        took = 5_000_000L;                                  // skip a chunk that failed
                    } catch (java.util.concurrent.TimeoutException e) {
                        if (!server.isRunning()) break;                     // world closed while waiting
                        continue;                                           // try the same chunk again
                    }
                    index++;
                    // pause long enough that generation uses only `duty` of the time (at least 2 ticks)
                    long pauseMs = Math.max(100L, (long) (took / 1e6 * (1f - duty) / duty));
                    Thread.sleep(Math.min(pauseMs, 2_000L));
                    if (System.currentTimeMillis() - lastSave > 10_000) { save(key, cx, cz, r, index); lastSave = System.currentTimeMillis(); }
                }
            } catch (InterruptedException ignored) {
                return;
            } catch (Throwable t) {
                crashed = true;
                System.err.println("[Tempest] Fake Chunks stopped: " + t);
            }
            save(key, cx, cz, r, index);
            if (index >= total && !stopped) {
                finished = true;
                MinecraftClient.getInstance().execute(() -> Chat.info("Fake Chunks: done - " + total + " chunks around spawn are generated."));
            }
        }
    }
}
