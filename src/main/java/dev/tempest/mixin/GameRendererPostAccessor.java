package dev.tempest.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Color Saturation: lets Tempest switch the game's full-screen post effect. */
@Mixin(GameRenderer.class)
public interface GameRendererPostAccessor {
    @Invoker("setPostProcessor")
    void tempest$setPostProcessor(Identifier id);
}
