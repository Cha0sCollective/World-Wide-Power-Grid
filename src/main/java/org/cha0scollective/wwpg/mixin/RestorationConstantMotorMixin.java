package org.cha0scollective.wwpg.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.kinetics.motor.ConstantSpeedMotorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ConstantSpeedMotorBlockEntity.class, remap = false)
public abstract class RestorationConstantMotorMixin {
    @Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true)
    private void wwpg$stopPower(CallbackInfoReturnable<Float> ci) {
        var motor = (GeneratingKineticBlockEntity) (Object) this;
        if (motor.getLevel() instanceof ServerLevel level && RestorationCoordinator.pausedAt(level, motor.getBlockPos())) ci.setReturnValue(0f);
    }
}
