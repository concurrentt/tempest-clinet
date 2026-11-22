package dev.tempest.gui;

import dev.tempest.gui.TempestWorldLoading.Phase;
import dev.tempest.modules.ClientSettingsModule;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.util.Util;

/**
 * Decides when {@link TempestWorldLoading} is drawn over a vanilla screen.
 *
 * It does not replace any vanilla screen: Minecraft keeps running its own connect / load / save
 * screens (so cancelling, timeouts and errors behave exactly as before). Tempest just paints over
 * them after they have rendered, then draws their buttons (Cancel) back on top.
 *
 * Nothing here depends on the names of Minecraft's loading screens. The state is worked out from
 * events that Fabric API already exposes:
 *   - ConnectScreen                          -> connecting to a server
 *   - JOIN event until the screen closes     -> loading terrain after joining
 *   - integrated server up, no world yet     -> opening a singleplayer world
 *   - DISCONNECT event until a menu shows up -> saving / leaving
 */
public final class WorldLoadingHooks {
    private static boolean joinPending;
    private static boolean leaving;
    private static long leavingSinceMs;

    /** A leave that never reaches a menu screen is dropped after this long, so we never get stuck. */
    private static final long LEAVE_TIMEOUT_MS = 20_000L;

    private WorldLoadingHooks() { }

    // ------------------------------------------------------------------------ events
    public static void onScreenInit(MinecraftClient client, Screen screen, int w, int h) {
        // Registered for every screen; the check itself runs per frame and is a few boolean reads.
        ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            Phase p = phaseFor(c, s);
            if (p == null) return;
            TempestWorldLoading.start(p);
            TempestWorldLoading.render(ctx, mouseX, mouseY);
            // Vanilla's own buttons (e.g. Cancel) go back on top, below the load bar.
            int barBottom = TempestWorldLoading.lastBarBottom();
            int y = barBottom > 0 ? barBottom + 32 : -1;
            for (ClickableWidget b : Screens.getButtons(s)) {
                if (y >= 0) b.setY(y);
                b.render(ctx, mouseX, mouseY, delta);
            }
        });
    }

    public static void onJoin() {
        joinPending = true;
        leaving = false;
    }

    public static void onLeave() {
        joinPending = false;
        leaving = true;
        leavingSinceMs = Util.getMeasuringTimeMs();
    }

    public static void tick(MinecraftClient client) {
        Screen s = client.currentScreen;
        // The world is visible: whatever loading was going on is over.
        if (s == null) joinPending = false;
        if (leaving && (isMenu(s) || Util.getMeasuringTimeMs() - leavingSinceMs > LEAVE_TIMEOUT_MS)) leaving = false;
        if (phaseFor(client, s) == null) TempestWorldLoading.stop();
    }

    // ------------------------------------------------------------------------ state
    private static Phase phaseFor(MinecraftClient c, Screen s) {
        if (s == null || !ClientSettingsModule.worldLoadingOn()) return null;
        if (s instanceof ConnectScreen) return Phase.CONNECTING;
        if (isMenu(s)) return null;
        if (leaving) return Phase.LEAVING;
        if (joinPending && c.world != null) return Phase.JOINING;
        // Singleplayer: the integrated server is starting but the client has no world yet.
        if (c.isIntegratedServerRunning() && c.world == null) return Phase.JOINING;
        return null;
    }

    /** Screens that are real destinations, never loading screens. */
    private static boolean isMenu(Screen s) {
        return s instanceof TitleScreen || s instanceof MultiplayerScreen || s instanceof DisconnectedScreen
                || TempestScreens.isTempestMenu(s);
    }
}
