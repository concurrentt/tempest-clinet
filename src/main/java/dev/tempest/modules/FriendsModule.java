package dev.tempest.modules;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tempest.gui.FriendsScreen;
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
    private final KeyBinding openKey = bindKey("friends", "Friends Menu Key", InputUtil.UNKNOWN_KEY.getCode());
    private final BoolSetting glow = add(new BoolSetting("glow", "Glow Friends", true));
    private final ColorSetting glowColor = add(new ColorSetting("glow_color", "Glow Color", 0xFF55FF55));

    @Override
    public boolean alwaysOn() { return true; }

    public FriendsModule() {
        super("friends", "Friends", "Add players as friends and they glow in the color you pick (only on your screen).", Category.UTILITY, true);
        add(new ActionSetting("manage", "Manage Friends", "Open", () -> {
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
        while (openKey.wasPressed()) if (c.currentScreen == null) c.setScreen(new FriendsScreen(null));
    }

    public static boolean isFriend(String name) { return FriendManager.isFriend(name); }

    public static boolean glows(AbstractClientPlayerEntity p) {
        FriendsModule m = ModuleManager.get(FriendsModule.class);
        MinecraftClient c = MinecraftClient.getInstance();
        return m != null && m.isEnabled() && m.glow.get() && p != c.player && FriendManager.isFriend(p.getName().getString());
    }

    public static int glowColor() {
        FriendsModule m = ModuleManager.get(FriendsModule.class);
        return m == null ? 0x55FF55 : m.glowColor.get() & 0xFFFFFF;
    }
}
