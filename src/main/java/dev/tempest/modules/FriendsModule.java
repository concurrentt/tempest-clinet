package dev.tempest.modules;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tempest.gui.SocialScreen;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.util.Chat;
import dev.tempest.util.Social;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Friends who also run Tempest. Confirmed friends get a colored glow (default green) that you can change. */
public class FriendsModule extends Module {
    private final KeyBinding openKey = bindKey("social", "Social Menu Key", org.lwjgl.glfw.GLFW.GLFW_KEY_U);
    private final BoolSetting onlineToFriends = add(new BoolSetting("online_to_friends", "Show Friends I'm Online", true));
    private final BoolSetting shareServer = add(new BoolSetting("share_server", "Show Friends My Server", true));
    private final BoolSetting alerts = add(new BoolSetting("request_alerts", "Friend Request Alerts", true));
    private final BoolSetting onlineAlerts = add(new BoolSetting("online_alerts", "Pop-up When Friends Come Online", true));
    private final BoolSetting offlineAlerts = add(new BoolSetting("offline_alerts", "Pop-up When Friends Go Offline", false));
    private final BoolSetting joinAlerts = add(new BoolSetting("join_alerts", "Pop-up When Friends Join Your Server", true));
    private final BoolSetting glow = add(new BoolSetting("glow_friends", "Glow Friends", false));
    private final ColorSetting glowColor = add(new ColorSetting("glow_color", "Glow Color", 0xFF55FF55));
    private final BoolSetting lightGlow = add(new BoolSetting("light_glow", "Light Glow (more FPS)", true));

    @Override
    public boolean alwaysOn() { return true; }

    public FriendsModule() {
        super("friends", "Social", "Tempest friends: requests, who's online and which server they're on. Friends can glow too.", Category.CLIENT, true);
        add(new ActionSetting("open_social", "Social Menu", "Open", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            c.setScreen(new SocialScreen(c.currentScreen));
        }));
        keyHint = "Commands: /tempest friend add <name> (sends a request), /tempest friend remove <name>";
    }

    @Override
    public void onInit() {
        // "/tempest join": the [Join] button on a friend's invite in chat
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("tempest").then(ClientCommandManager.literal("join").executes(ctx -> {
                    MinecraftClient.getInstance().execute(() -> {
                        if (!dev.tempest.util.Invites.joinLatest()) Chat.info("No invite to join (they expire after 10 minutes).");
                    });
                    return 1;
                }))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("tempest").then(ClientCommandManager.literal("friend")
                        .then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    String n = StringArgumentType.getString(ctx, "player");
                                    if (!dev.tempest.util.TempestNet.signedIn()) { Chat.info("Not connected to Tempest yet - try again in a moment."); return 1; }
                                    Social.requestByName(n, msg -> { if (msg != null) Chat.info(msg); });
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    String n = StringArgumentType.getString(ctx, "player");
                                    UUID id = null;
                                    for (Social.Friend f : Social.friends()) if (Social.nameOf(f.id()).equalsIgnoreCase(n)) id = f.id();
                                    if (id == null) { Chat.info(n + " isn't on your friend list."); return 1; }
                                    UUID target = id;
                                    Social.remove(target, msg -> Chat.info(msg != null ? msg : "Removed " + n + " from friends."));
                                    return 1;
                                }))))));
    }

    @Override
    public void onTick(MinecraftClient c) {
        while (openKey.wasPressed()) if (c.currentScreen == null) c.setScreen(new SocialScreen(null));
        Social.poll();
    }

    /** Accepted Tempest friend (by account, not name). */
    public static boolean isFriend(UUID id) { return Social.isFriend(id); }

    public static boolean glows(AbstractClientPlayerEntity p) {
        FriendsModule m = ModuleManager.get(FriendsModule.class);
        MinecraftClient c = MinecraftClient.getInstance();
        return m != null && m.isEnabled() && m.glow.get() && p != c.player
                && Social.isFriend(p.getUuid());   // only friends who accepted your request
    }

    private static FriendsModule get() { return ModuleManager.get(FriendsModule.class); }

    public static boolean onlineToFriends() { FriendsModule m = get(); return m != null && m.isEnabled() && m.onlineToFriends.get(); }
    public static boolean shareServer() { FriendsModule m = get(); return m != null && m.shareServer.get(); }
    public static boolean onlineAlerts() { FriendsModule m = get(); return m != null && m.isEnabled() && m.onlineAlerts.get(); }
    public static boolean offlineAlerts() { FriendsModule m = get(); return m != null && m.isEnabled() && m.offlineAlerts.get(); }
    public static boolean joinAlerts() { FriendsModule m = get(); return m != null && m.isEnabled() && m.joinAlerts.get(); }
    public static boolean requestAlerts() { FriendsModule m = get(); return m == null || m.alerts.get(); }
    public static String keyName() {
        FriendsModule m = get();
        return m == null ? "U" : m.openKey.getBoundKeyLocalizedText().getString();
    }

    public static boolean glowOn() { FriendsModule m = get(); return m != null && m.glow.get(); }

    public static void setGlow(boolean on) {
        FriendsModule m = get();
        if (m == null) return;
        m.glow.set(on);
        dev.tempest.TempestConfig.save();
    }

    /**
     * Light Glow: a sharp 1 px outline drawn in 2 screen passes instead of vanilla's soft one (4 passes with a blur),
     * so glowing costs about half as much. Applies to every glowing outline while it's on.
     */
    public static boolean lightGlow() { FriendsModule m = get(); return m != null && m.lightGlow.get(); }

    private static final net.minecraft.util.Identifier VANILLA_OUTLINE = net.minecraft.util.Identifier.ofVanilla("entity_outline");
    private static final net.minecraft.util.Identifier LIGHT_OUTLINE = net.minecraft.util.Identifier.of("tempest", "entity_outline_light");

    /** The glow-outline post effect to use (WorldRendererGlowMixin). */
    public static net.minecraft.util.Identifier outlineEffect() { return lightGlow() ? LIGHT_OUTLINE : VANILLA_OUTLINE; }

    public static int glowColor() {
        FriendsModule m = ModuleManager.get(FriendsModule.class);
        return m == null ? 0x55FF55 : m.glowColor.get() & 0xFFFFFF;
    }
}
