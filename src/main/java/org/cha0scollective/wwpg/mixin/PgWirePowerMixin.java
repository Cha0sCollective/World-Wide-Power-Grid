package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.PropertyCurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = AbstractElectricWire.class, remap = false)
public abstract class PgWirePowerMixin {
    @Shadow protected ElectricalNetwork network;
    @Shadow public abstract double conductance();
    @Shadow public abstract double current();
    @Shadow public abstract double potentialDifference();

    /**
     * @author WWPG
     * @reason Share the one-differential calculation without separate hot overrides.
     */
    @Overwrite
    public double internalPower() {
        Class<?> type = getClass();
        if (network != null && (type == ElectricWire.class || type == TransmissionLine.class
                || type == PropertyCurrentSourceWire.class)) {
            double conductance = conductance();
            if (Double.isFinite(conductance) && conductance > 0) {
                double voltage = potentialDifference();
                return (voltage * conductance) * voltage;
            }
        }
        // Preserve virtual current/voltage and their order for every other wire.
        return current() * potentialDifference();
    }
}
