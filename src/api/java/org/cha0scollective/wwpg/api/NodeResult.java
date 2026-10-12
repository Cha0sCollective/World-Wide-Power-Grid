package org.cha0scollective.wwpg.api;

/** Valid node voltages relative to the solver's circuit reference, over this tick. */
public record NodeResult(double lastVoltageVolts, double meanVoltageVolts, double rmsVoltageVolts) {
    /**
     * Constructs NodeResult with validated captured values.
     * @param lastVoltageVolts signed last voltage in volts; finite, with magnitude at most 1e15 (inclusive)
     * @param meanVoltageVolts signed arithmetic mean voltage in volts; finite, with magnitude at most 1e15 (inclusive)
     * @param rmsVoltageVolts RMS voltage in volts, finite in [0, 1e15] inclusive
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     */
    public NodeResult {
        ApiValidation.finite(lastVoltageVolts, "lastVoltageVolts");
        ApiValidation.finite(meanVoltageVolts, "meanVoltageVolts");
        ApiValidation.nonnegative(rmsVoltageVolts, "rmsVoltageVolts");
    }
}
