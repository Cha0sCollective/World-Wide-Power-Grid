package org.cha0scollective.wwpg.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(targets = {"org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlockEntity",
        "com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlockEntity"}, remap = false)
public abstract class RestorationMotorMixin {
    @Unique private boolean wwpg$wasPaused;
    @Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true)
    private void wwpg$stopPower(CallbackInfoReturnable<Float> ci) {
        var motor = (GeneratingKineticBlockEntity) (Object) this;
        if (motor.getLevel() instanceof ServerLevel level && RestorationCoordinator.pausedAt(level, motor.getBlockPos())) ci.setReturnValue(0f);
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void wwpg$refreshRotation(CallbackInfo ci) {
        var motor = (GeneratingKineticBlockEntity) (Object) this;
        if (!(motor.getLevel() instanceof ServerLevel level)) return;
        boolean paused = RestorationCoordinator.pausedAt(level, motor.getBlockPos());
        if (paused != wwpg$wasPaused) { wwpg$wasPaused = paused; motor.updateGeneratedRotation(); }
    }
}
