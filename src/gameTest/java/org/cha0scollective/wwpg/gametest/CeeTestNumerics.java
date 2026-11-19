package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import org.cha0scollective.wwpg.compat.cee.CeeCompatibility;

/** Versioned upstream inputs for independent analytical circuit references. */
final class CeeTestNumerics {
    // These fixtures connect their source from ground to supply. CEE 1.2's
    // Norton convention requires a negative source parameter for positive supply.
    static double sourceVoltage(double voltage) { return CeeCompatibility.modern() ? -voltage : voltage; }
    static ElectricalProperties supply(double resistance,double voltage) {
        return ElectricalProperties.fromThevenin(resistance,sourceVoltage(voltage));
    }
    static double capacitorAlpha() {
        if(!CeeCompatibility.modern())return 1; // Backward Euler in 1.1.
        try {
            var simulation=CEEConfigs.server().simulationConfig;
            var value=simulation.getClass().getField("capacitySolverDamp").get(simulation);
            return ((Number)value.getClass().getMethod("get").invoke(value)).doubleValue();
        } catch(ReflectiveOperationException error){throw new IllegalStateException("Missing CEE capacitor integration setting",error);}
    }
}
