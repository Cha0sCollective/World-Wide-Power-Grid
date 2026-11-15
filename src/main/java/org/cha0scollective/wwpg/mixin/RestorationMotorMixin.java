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
    // Detach while Create still knows the old shaft speed. Its base tick's
    // validateKinetics otherwise silently zeros a source whose generated speed
    // is paused, leaving saved consumers turning in the same network. Their
    // first attachment then trips Create's inconsistent-cycle destruction.
    @Inject(method = "tick", at = @At("HEAD"))
    private void wwpg$refreshRotation(CallbackInfo ci) {
        var motor = (GeneratingKineticBlockEntity) (Object) this;
        if (!(motor.getLevel() instanceof ServerLevel level)) return;
        boolean paused = RestorationCoordinator.pausedAt(level, motor.getBlockPos());
        // Native electrical motors can also defer their ordinary loss-of-power
        // update past this validation tick. Do not leave their consumers on a
        // saved nonzero speed when Create is about to zero the source directly.
        // A motor back-driven by another source must retain that shaft input.
        boolean stoppedSource = !motor.hasSource() && motor.getTheoreticalSpeed() != 0
                && motor.getGeneratedSpeed() == 0;
        if (paused != wwpg$wasPaused || stoppedSource) {
            wwpg$wasPaused = paused;
            motor.updateGeneratedRotation();
        }
    }
}
