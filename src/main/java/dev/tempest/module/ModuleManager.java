package dev.tempest.module;

import dev.tempest.modules.*;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<Class<?>, Module> BY_CLASS = new HashMap<>();

    public static void init() {
        // HUD - on-screen info
        register(new FpsModule());
        register(new CpsModule());
        register(new PingModule());
        register(new KeystrokesModule());
        register(new CoordinatesModule());
        register(new PotionModule());
        register(new ArmorHudModule());
        register(new InventoryHudModule());
        register(new AppleSkinModule());
        register(new InfoBarModule());
        register(new SessionModule());
        register(new ServerAddressModule());
        register(new ScoreboardModule());
        register(new PackDisplayModule());
        register(new SpotifyModule());
        // PvP - combat
        register(new ReachModule());
        register(new ComboModule());
        register(new TargetHealthModule());
        register(new CooldownModule());
        register(new ShieldStatusModule());
        register(new HitboxModule());
        register(new OldAnimationsModule());
        register(new ShieldFixesModule());
        register(new LowShieldsModule());
        register(new TotemPopModule());
        register(new TotemTweaksModule());
        register(new ClientSideCrystalsModule());
        register(new AlertsModule());
        register(new TierTaggerModule());
        register(new CombatTimerModule());
        register(new ArmorAlertModule());
        // Render - camera and world
        register(new ZoomModule());
        register(new FreelookModule());
        register(new CinematicCameraModule());
        register(new CustomFovModule());
        register(new NametagsModule());
        register(new WhoAmIModule());
        register(new WaypointsModule());
        register(new XrayModule());
        register(new FakeChunksModule());
        register(new FpsSaverModule());
        // Visuals - how the game looks
        register(new CustomSkyModule());
        register(new CinematicModule());
        register(new FullbrightModule());
        register(new SaturationModule());
        register(new TimeChangerModule());
        register(new BlockOutlineModule());
        register(new ItemPhysicsModule());
        register(new MotionBlurModule());
        register(new KillEffectsModule());
        register(new ParticleFilterModule());
        register(new LowFireModule());
        // QOL - smoother play
        register(new ToggleSprintModule());
        register(new MouseTweaksModule());
        register(new ArmorSwapModule());
        register(new SmoothHotbarModule());
        register(new ShulkerPreviewModule());
        register(new TooltipInfoModule());
        register(new FastScreensModule());
        register(new ClumpsModule());
        // Utility - tools
        register(new QuickServersModule());
        register(new ReconnectModule());
        register(new ScreenshotToolsModule());
        register(new ReplayModModule());
        register(new StreamerModeModule());
        register(new AntiLeakModule());
        register(new FlySpeedModule());
        register(new LightLevelsModule());
        register(new ServerSetupsModule());
        // Chat
        register(new ChatTabsModule());
        register(new ChatUpgradesModule());
        register(new ChatMentionsModule());
        register(new MessageStackingModule());
        register(new ChatAnimationModule());
        register(new ChatMacrosModule());
        register(new AutoGgModule());
        // Client - Tempest itself
        register(new ClientSettingsModule());
        register(new TexturePainterModule());
        register(new CosmeticsModule());
        register(new CapesModule());
        register(new EmotesModule());
        register(new FriendsModule());
        register(new MiddleClickFriendModule());
        register(new DiscordModule());

        MODULES.forEach(Module::onInit);
    }

    private static void register(Module m) {
        hudCache = null;
        MODULES.add(m);
        BY_CLASS.put(m.getClass(), m);
    }

    public static List<Module> all() { return Collections.unmodifiableList(MODULES); }

    private static List<HudModule> hudCache;

    /** HUD modules (built once: the list never changes after start-up, and this is read every frame). */
    public static List<HudModule> huds() {
        List<HudModule> c = hudCache;
        if (c == null || c.isEmpty()) {
            List<HudModule> out = new ArrayList<>();
            for (Module m : MODULES) if (m instanceof HudModule h) out.add(h);
            hudCache = c = Collections.unmodifiableList(out);
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> type) { return (T) BY_CLASS.get(type); }

    public static <T extends Module> boolean enabled(Class<T> type) {
        Module m = BY_CLASS.get(type);
        return m != null && m.isEnabled();
    }

    public static void tick(MinecraftClient client) {
        for (Module m : MODULES) if (m.isEnabled()) m.onTick(client);
    }
}
