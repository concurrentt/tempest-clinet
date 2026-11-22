package dev.tempest.mixin;

import dev.tempest.modules.AutoGgModule;
import dev.tempest.modules.ShieldFixesModule;
import dev.tempest.modules.TotemPopModule;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    // HEAD, main thread only. Minecraft first calls this on the network thread (it then hands the packet to the
    // main thread), so the isOnThread check makes it run exactly once - and at HEAD it can never be skipped by an
    // early return inside the vanilla handler (the old TAIL hook missed totem pops, deaths and shield disables).
    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void tempest$status(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (!MinecraftClient.getInstance().isOnThread()) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        Entity e = packet.getEntity(c.world);
        TotemPopModule.onStatus(e, packet.getStatus());
        ShieldFixesModule.onStatus(e, packet.getStatus());
        dev.tempest.modules.ShieldStatusModule.onStatus(e, packet.getStatus());
        if (packet.getStatus() == 3) AutoGgModule.onDeath(e);
    }

    /** Ping answers for util/PingTracker (runs on the network thread first; PingTracker only counts it once). */
    @Inject(method = "onPingResult", at = @At("HEAD"), require = 0)
    private void tempest$pingResult(net.minecraft.network.packet.s2c.query.PingResultS2CPacket packet, CallbackInfo ci) {
        dev.tempest.util.PingTracker.onResult();
    }
}
