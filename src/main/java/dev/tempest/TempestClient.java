package dev.tempest;

import dev.tempest.gui.HudEditorScreen;
import dev.tempest.gui.TempestMenuScreen;
import dev.tempest.gui.WorldLoadingHooks;
import dev.tempest.module.HudModule;
import dev.tempest.module.ModuleManager;
import dev.tempest.modules.AppleSkinModule;
import dev.tempest.modules.TotemPopModule;
import dev.tempest.modules.SmoothHotbarModule;
import dev.tempest.modules.QuickServersModule;
import dev.tempest.modules.ReconnectModule;
import dev.tempest.modules.TooltipInfoModule;
import dev.tempest.modules.WaypointsModule;
import dev.tempest.modules.MotionBlurModule;
import dev.tempest.util.CombatTracker;
import dev.tempest.util.PlaytimeTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import dev.tempest.render.CosmeticsFeature;
import net.minecraft.entity.EntityType;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import dev.tempest.gui.VisualsRenderer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class TempestClient implements ClientModInitializer {
    public static final String MOD_ID = "tempest";
    public static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    public static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        TempestConfig.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tempest.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

        ModuleManager.init();

        dev.tempest.modules.LowFireModule.registerModels();
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) dev.tempest.modules.AutoGgModule.onMessage(message);
        });
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) ->
                dev.tempest.modules.AutoGgModule.onMessage(message));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MotionBlurModule.update(client);
            dev.tempest.gui.TempestScreens.tick(client);
            dev.tempest.modules.KillEffectsModule.tick(client);
            dev.tempest.util.PingTracker.tick(client);
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new TempestMenuScreen(null));
            }
            CombatTracker.tick(client);
            PlaytimeTracker.tick(client);
            ModuleManager.tick(client);
            WorldLoadingHooks.tick(client);
            dev.tempest.util.TempestNet.tick(client);
        });

        // Cosmetics (wings, halo, horns...) on the player model, like the cape.
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityType == EntityType.PLAYER) helper.register(new CosmeticsFeature(entityRenderer));
        });

        // Visuals tab: drawn before (under) every vanilla HUD element.
        HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, Identifier.of(MOD_ID, "visuals"),
                (ctx, tickCounter) -> VisualsRenderer.render(ctx, tickCounter.getTickProgress(false)));

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player == null || c.world == null || c.options.hudHidden) return;
            if (c.currentScreen instanceof HudEditorScreen) return;
            SmoothHotbarModule.render(ctx, tickCounter.getTickProgress(false));
            TotemPopModule.render(ctx, tickCounter.getTickProgress(false));
            for (HudModule m : ModuleManager.huds()) {
                if (m.isEnabled()) m.render(ctx, false);
            }
            WaypointsModule wp = ModuleManager.get(WaypointsModule.class);
            if (wp != null && wp.isEnabled()) wp.render(ctx);
        });

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            AppleSkinModule.addTooltip(stack, lines);
            TooltipInfoModule.addTooltip(stack, tooltipType, lines);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ReconnectModule.remember(client.getCurrentServerEntry());
            PlaytimeTracker.onJoin(client);
            WorldLoadingHooks.onJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PlaytimeTracker.onLeave();
            WorldLoadingHooks.onLeave();
            dev.tempest.util.TempestNet.onLeave();
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ReconnectModule.onScreenInit(client, screen, w, h);
            QuickServersModule.onScreenInit(client, screen, w, h);
            WorldLoadingHooks.onScreenInit(client, screen, w, h);
            if (screen instanceof net.minecraft.client.gui.screen.ChatScreen) {
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) ->
                        dev.tempest.modules.ChatTabsModule.renderTabs(ctx, s.width, s.height, mx, my));
            }
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            dev.tempest.util.Updater.resumePending();
            if (dev.tempest.modules.ClientSettingsModule.checkUpdatesOn()) dev.tempest.util.Updater.checkAsync();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TempestConfig.save());
    }
}
