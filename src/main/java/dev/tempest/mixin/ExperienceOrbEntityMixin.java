package dev.tempest.mixin;

import dev.tempest.modules.ClumpsModule;
import net.minecraft.entity.ExperienceOrbEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ExperienceOrbEntity.class)
public abstract class ExperienceOrbEntityMixin {
    @Shadow private int amount;
    @Shadow private int pickingCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void tempest$clump(CallbackInfo ci) {
        if (!ClumpsModule.active()) return;
        ExperienceOrbEntity self = (ExperienceOrbEntity) (Object) this;
        if (self.isRemoved() || self.getEntityWorld().isClient()) return;
        if (self.age % 5 != 0) return;

        List<ExperienceOrbEntity> nearby = self.getEntityWorld().getEntitiesByClass(
                ExperienceOrbEntity.class,
                self.getBoundingBox().expand(1.5),
                other -> other != self && !other.isRemoved()
        );
        if (nearby.isEmpty()) return;

        for (ExperienceOrbEntity other : nearby) {
            if (other.isRemoved()) continue;
            // Merge smaller/younger orbs into this one
            ExperienceOrbEntityMixin mix = (ExperienceOrbEntityMixin) (Object) other;
            this.amount += mix.amount;
            this.pickingCount += mix.pickingCount;
            other.discard();
        }
    }
}
