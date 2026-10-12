package org.cha0scollective.wwpg.api;

/**
 * Accepted end-of-tick electrical history. The add-on serializes these values;
 * restoration supplies the corresponding model's initial value once.
 */
public sealed interface ElementState permits ElementState.Capacitor, ElementState.Inductor {
    /** Capacitor voltage in element orientation, in volts. */
    record Capacitor(double voltageVolts) implements ElementState {
        /**
         * Constructs ElementState.Capacitor with validated captured values.
         * @param voltageVolts accepted oriented capacitor voltage in volts; finite, with magnitude at most 1e15 (inclusive)
         * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
         */
        public Capacitor { ApiValidation.finite(voltageVolts, "capacitor voltageVolts"); }
    }
    /** Inductor current from positive node to negative node, in amperes. */
    record Inductor(double currentAmps) implements ElementState {
        /**
         * Constructs ElementState.Inductor with validated captured values.
         * @param currentAmps accepted oriented inductor current in amperes; finite, with magnitude at most 1e15 (inclusive)
         * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
         */
        public Inductor { ApiValidation.finite(currentAmps, "inductor currentAmps"); }
    }
}
