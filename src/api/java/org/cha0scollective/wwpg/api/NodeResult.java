package org.cha0scollective.wwpg.api;

/** Valid node voltages relative to the solver's circuit reference, over this tick. */
public record NodeResult(double lastVoltageVolts, double meanVoltageVolts, double rmsVoltageVolts) {
    public NodeResult {
        ApiValidation.finite(lastVoltageVolts, "lastVoltageVolts");
        ApiValidation.finite(meanVoltageVolts, "meanVoltageVolts");
        ApiValidation.nonnegative(rmsVoltageVolts, "rmsVoltageVolts");
    }
}
