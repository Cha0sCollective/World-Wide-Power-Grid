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
    static double highBiasDiodeLoadVoltage() {
        if(!CeeCompatibility.modern()) {
            double g=(1e-9/.05)*Math.exp(.8/.05), current=1e-9*(Math.exp(.8/.05)-1);
            return 1000*(70-.8+current/g)/(1000+1/g);
        }
        // CEE 1.2's DiodeDevice uses vt=.02585 and Is=1e-12. Solve the
        // independent Shockley/load-line intersection, not its Newton stamps.
        double low=0,high=70;
        for(int iteration=0;iteration<100;iteration++) {
            double voltage=(low+high)/2;
            double diodeCurrent=1e-12*Math.expm1((70-voltage)/.02585);
            if(diodeCurrent>voltage/1000)low=voltage;else high=voltage;
        }
        return (low+high)/2;
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
