package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ElectricWire.class, remap = false)
public abstract class PgPlainWirePowerMixin extends AbstractElectricWire {
    protected PgPlainWirePowerMixin(IElectricNode first, IElectricNode second) {
        super(first, second);
    }

    @Override
    public double internalPower() {
        if (network == null) return super.internalPower();
        Class<?> type = getClass();
        // Other wire families retain their virtual current and voltage implementations.
        if (type != ElectricWire.class && type != TransmissionLine.class) return super.internalPower();
        double conductance = conductance();
        // The base return hook masks restoring circuits, including zero times infinity.
        if (!Double.isFinite(conductance) || conductance <= 0) return super.internalPower();
        double voltage = potentialDifference();
        return (voltage * conductance) * voltage;
    }
}
