package dev.tempest.modules;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tempest.gui.FriendsScreen;
import dev.tempest.gui.SocialScreen;
import dev.tempest.module.ActionSetting;
import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.ColorSetting;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import dev.tempest.util.Chat;
import dev.tempest.util.FriendManager;
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
    private final BoolSetting glow = add(new BoolSetting("glow", "Glow Friends", true));
    private final ColorSetting glowColor = add(new ColorSetting("glow_color", "Glow Color", 0xFF55FF55));

    @Override
    public boolean alwaysOn() { return true; }

    public FriendsModule() {
        super("friends", "Social", "Tempest friends: requests, who's online and which server they're on. Friends can glow too.", Category.UTILITY, true);
        add(new ActionSetting("open_social", "Social Menu", "Open", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            c.setScreen(new SocialScreen(c.currentScreen));
        }));
        add(new ActionSetting("manage", "Glow List (any player, only on your screen)", "Open", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            c.setScreen(new FriendsScreen(c.currentScreen));
        }));
        keyHint = "Command: /tempest friend add|remove <name>";
    }

    @Override
    public void onInit() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("tempest").then(ClientCommandManager.literal("friend")
                        .then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    String n = StringArgumentType.getString(ctx, "player");
                                    FriendManager.addFriend(n);
                                    Chat.info("Added " + n + " - they now glow.");
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    String n = StringArgumentType.getString(ctx, "player");
                                    FriendManager.removeFriend(n);
                                    Chat.info("Removed " + n + ".");
                                    return 1;
                                }))))));
    }

    @Override
    public void onTick(MinecraftClient c) {
        while (openKey.wasPressed()) if (c.currentScreen == null) c.setScreen(new SocialScreen(null));
    }

    public static boolean isFriend(String name) { return FriendManager.isFriend(name); }

    public static boolean glows(AbstractClientPlayerEntity p) {
        FriendsModule m = ModuleManager.get(FriendsModule.class);
        MinecraftClient c = MinecraftClient.getInstance();
        return m != null && m.isEnabled() && m.glow.get() && p != c.player
                && (FriendManager.isFriend(p.getName().getString()) || dev.tempest.util.Social.isFriend(p.getUuid()));
    }

    private static FriendsModule get() { return ModuleManager.get(FriendsModule.class); }

    public static boolean onlineToFriends() { FriendsModule m = get(); return m != null && m.isEnabled() && m.onlineToFriends.get(); }
    public static boolean shareServer() { FriendsModule m = get(); return m != null && m.shareServer.get(); }
    public static boolean requestAlerts() { FriendsModule m = get(); return m == null || m.alerts.get(); }
    public static String keyName() {
        FriendsModule m = get();
        return m == null ? "U" : m.openKey.getBoundKeyLocalizedText().getString();
    }

    public static int glowColor() {
        FriendsModule m = ModuleManager.get(FriendsModule.class);
        return m == null ? 0x55FF55 : m.glowColor.get() & 0xFFFFFF;
    }
}
