package dev.tempest.mixin;

import dev.tempest.modules.ClumpsModule;
import net.minecraft.entity.ExperienceOrbEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Clumps: nearby XP orbs merge into one. In 1.21.11 an orb's XP lives in synced data (getValue / setValue) instead
 * of the old "amount" field, which made this mixin fail to load.
 */
@Mixin(ExperienceOrbEntity.class)
public abstract class ExperienceOrbEntityMixin {
    @Shadow private int pickingCount;

    @Shadow public abstract int getValue();

    @Shadow
    private void setValue(int value) {
        throw new AssertionError();
    }

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

        int total = getValue();
        for (ExperienceOrbEntity other : nearby) {
            if (other.isRemoved()) continue;
            total += other.getValue();
            this.pickingCount += ((ExperienceOrbEntityMixin) (Object) other).pickingCount;
            other.discard();
        }
        setValue(total);
    }
}
