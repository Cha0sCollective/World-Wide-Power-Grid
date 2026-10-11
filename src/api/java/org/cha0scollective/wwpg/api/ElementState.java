package org.cha0scollective.wwpg.api;

/**
 * Accepted end-of-tick electrical history. The add-on serializes these values;
 * restoration supplies the corresponding model's initial value once.
 */
public sealed interface ElementState permits ElementState.Capacitor, ElementState.Inductor {
    /** Capacitor voltage in element orientation, in volts. */
    record Capacitor(double voltageVolts) implements ElementState {
        public Capacitor { ApiValidation.finite(voltageVolts, "capacitor voltageVolts"); }
    }
    /** Inductor current from positive node to negative node, in amperes. */
    record Inductor(double currentAmps) implements ElementState {
        public Inductor { ApiValidation.finite(currentAmps, "inductor currentAmps"); }
    }
}
