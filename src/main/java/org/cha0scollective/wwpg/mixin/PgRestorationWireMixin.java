package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(value = AbstractElectricWire.class, remap = false)
public abstract class PgRestorationWireMixin {
    @ModifyReturnValue(method = {"current", "potentialDifference", "power", "internalPower"}, at = @At("RETURN"))
    private double wwpg$clearOutput(double original) {
        if (!RestorationCoordinator.anyPaused()) return original;
        return wwpg$restoring() ? 0 : original;
    }

    @ModifyReturnValue(method = "isConverged", at = @At("RETURN"))
    private boolean wwpg$requireAvailableReading(boolean original) {
        // The backend retains its last accepted state while restoration holds
        // solving. Its old convergence flag must not validate a masked zero:
        // contactors and other coil controls would treat it as a real power loss.
        return original && (!RestorationCoordinator.anyPaused() || !wwpg$restoring());
    }

    @Unique
    private boolean wwpg$restoring() {
        var wire = (AbstractElectricWire) (Object) this;
        return RestorationCoordinator.paused(wire.getNetwork())
                || wire.getNode1() != null && RestorationCoordinator.paused(wire.getNode1().getNetwork())
                || wire.getNode2() != null && RestorationCoordinator.paused(wire.getNode2().getNetwork());
    }
}
