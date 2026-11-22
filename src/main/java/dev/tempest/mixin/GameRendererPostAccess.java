package dev.tempest.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Motion Blur run its post effect through the same slot vanilla uses for the creeper/spider view. */
@Mixin(GameRenderer.class)
public interface GameRendererPostAccess {
    @Invoker("setPostProcessor")
    void tempest$setPostProcessor(Identifier id);

    @Accessor("postProcessorId")
    Identifier tempest$getPostProcessorId();
}
