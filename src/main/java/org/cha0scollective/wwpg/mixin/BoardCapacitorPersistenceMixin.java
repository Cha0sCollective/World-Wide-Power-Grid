package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.circuits.components.CapacitorComponent;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CapacitorComponent.class, remap = false)
public abstract class BoardCapacitorPersistenceMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/electricity/sim/special/CRSeriesWire;capacitorVoltage()D"))
    private double wwpg$saveCommittedCharge(CRSeriesWire wire) {
        // Terminal voltages can still be zero just after a circuit is baked or
        // attached. Persist the solver's capacitor history, not that cold reading.
        return ((CapacitorHistoryAccessor) wire).wwpg$storedVoltage();
    }
}
