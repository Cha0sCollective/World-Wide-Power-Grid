package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(value = AbstractElectricWire.class, remap = false)
public abstract class PgRestorationWireMixin {
    @ModifyReturnValue(method = {"current", "potentialDifference", "power", "internalPower"}, at = @At("RETURN"))
    private double wwpg$clearOutput(double original) {
        if (!RestorationCoordinator.anyPaused()) return original;
        var wire = (AbstractElectricWire) (Object) this;
        if (RestorationCoordinator.paused(wire.getNetwork())
                || wire.getNode1() != null && RestorationCoordinator.paused(wire.getNode1().getNetwork())
                || wire.getNode2() != null && RestorationCoordinator.paused(wire.getNode2().getNetwork())) return 0;
        return original;
    }
}
