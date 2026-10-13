package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(targets = {
    "org.patryk3211.powergrid.electricity.sim.SwitchedWire",
    "org.patryk3211.powergrid.electricity.sim.special.CapacitorWire",
    "org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire",
    "org.patryk3211.powergrid.electricity.sim.special.InductorWire",
    "org.patryk3211.powergrid.electricity.sim.special.LRSeriesWire",
    "org.patryk3211.powergrid.electricity.sim.special.ElectronTubeWire",
    "org.patryk3211.powergrid.electricity.sim.special.NeonBulbWire",
    "org.patryk3211.powergrid.electricity.sim.special.PentodeWire",
    "org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire",
    "org.patryk3211.powergrid.electricity.sim.special.PNJunctionWireSolar",
    "org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart"
}, remap = false)
public abstract class PgRestorationDerivedCurrentMixin {
    @ModifyReturnValue(method = "current", at = @At("RETURN"))
    private double wwpg$clearCurrent(double original) {
        if (!RestorationCoordinator.anyPaused()) return original;
        var wire = (AbstractElectricWire) (Object) this;
        if (RestorationCoordinator.paused(wire.getNetwork())
                || wire.getNode1() != null && RestorationCoordinator.paused(wire.getNode1().getNetwork())
                || wire.getNode2() != null && RestorationCoordinator.paused(wire.getNode2().getNetwork())) return 0;
        return original;
    }
}
