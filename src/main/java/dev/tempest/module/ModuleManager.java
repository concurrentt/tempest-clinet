package dev.tempest.module;

import dev.tempest.modules.*;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<Class<?>, Module> BY_CLASS = new HashMap<>();

    public static void init() {
        // HUD
        register(new FpsModule());
        register(new CpsModule());
        register(new KeystrokesModule());
        register(new ReachModule());
        register(new ComboModule());
        register(new TargetHealthModule());
        register(new PotionModule());
        register(new AppleSkinModule());
        register(new ArmorHudModule());
        register(new CooldownModule());
        register(new ShieldStatusModule());
        register(new CoordinatesModule());
        register(new PingModule());
        register(new InventoryHudModule());
        register(new SessionModule());
        register(new InfoBarModule());
        register(new ScoreboardModule());
        register(new ServerAddressModule());
        register(new PackDisplayModule());
        // PvP
        register(new TotemPopModule());
        register(new AutoGgModule());
        register(new ShieldFixesModule());
        register(new ClientSideCrystalsModule());
        register(new AlertsModule());
        register(new HitboxModule());
        register(new TotemTweaksModule());
        register(new FriendsModule());
        register(new MiddleClickFriendModule());
        register(new OldAnimationsModule());
        register(new KillEffectsModule());
        // Render
        register(new ZoomModule());
        register(new FullbrightModule());
        register(new FreelookModule());
        register(new WhoAmIModule());
        register(new CapesModule());
        register(new CosmeticsModule());
        register(new NametagsModule());
        register(new CinematicCameraModule());
        register(new WaypointsModule());
        register(new ItemPhysicsModule());
        register(new TimeChangerModule());
        register(new BlockOutlineModule());
        register(new MotionBlurModule());
        register(new XrayModule());

        register(new CustomSkyModule());
        register(new FirefliesModule());
        register(new CinematicModule());
        // Utility
        register(new AntiLeakModule());
        register(new MouseTweaksModule());
        register(new ShulkerPreviewModule());
        register(new ToggleSprintModule());
        register(new ReconnectModule());
        register(new ArmorSwapModule());
        register(new TooltipInfoModule());
        register(new ScreenshotToolsModule());
        register(new StreamerModeModule());
        register(new QuickServersModule());
        // QOL
        register(new LowFireModule());
        register(new SmoothHotbarModule());
        register(new ParticleFilterModule());
        register(new CustomFovModule());
        register(new FastScreensModule());
        register(new ClumpsModule());
        // Chat
        register(new ChatMentionsModule());
        register(new MessageStackingModule());
        register(new ChatUpgradesModule());
        register(new ChatTabsModule());
        register(new ChatAnimationModule());
        // Client
        register(new ClientSettingsModule());

        MODULES.forEach(Module::onInit);
    }

    private static void register(Module m) {
        MODULES.add(m);
        BY_CLASS.put(m.getClass(), m);
    }

    public static List<Module> all() { return Collections.unmodifiableList(MODULES); }

    public static List<HudModule> huds() {
        List<HudModule> out = new ArrayList<>();
        for (Module m : MODULES) if (m instanceof HudModule h) out.add(h);
        return out;
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
