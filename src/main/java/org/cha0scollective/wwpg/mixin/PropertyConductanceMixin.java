package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.PropertyCurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** PG 0.6.2 drops deltas below G_MIN*0.1 while its wire still stores the new G.
 * Repeated tiny CEE diode updates therefore drift away from the matrix. */
@Mixin(value = ElectricalNetwork.class, remap = false)
public abstract class PropertyConductanceMixin {
    @ModifyConstant(method = "updateConductance", constant = @Constant(doubleValue = 1e-9))
    private double wwpg$keepPropertyConductance(double threshold, AbstractElectricWire wire, double change) {
        // Keep the native no-op path for exactly unchanged parameters.
        return wire instanceof PropertyCurrentSourceWire ? Double.MIN_VALUE : threshold;
    }
}
