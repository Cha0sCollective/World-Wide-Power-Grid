package org.cha0scollective.wwpg.api.cee;

/**
 * Accepted electrical substep. Voltage is first-node minus second-node voltage;
 * current flows from the first node to the second. Invalid or failed solves do
 * not construct this result and must not invoke an adapter's afterSubstep callback.
 */
public record CeeSubstepResult(double voltageVolts, double currentAmps, int substep,
                              int totalSubsteps, double substepSeconds) {
    /**
     * Constructs CeeSubstepResult with validated captured values.
     * @param voltageVolts first-node minus second-node voltage in volts; finite, with magnitude at most 1e15 (inclusive)
     * @param currentAmps first-node to second-node current in amperes; finite, with magnitude at most 1e15 (inclusive)
     * @param substep zero-based index in [0, totalSubsteps-1]
     * @param totalSubsteps step count in [1, 4096] inclusive
     * @param substepSeconds finite simulated seconds in (0, 1]
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     */
    public CeeSubstepResult {
        CeeElectricalParameters.requireFinite(voltageVolts, "voltageVolts");
        CeeElectricalParameters.requireFinite(currentAmps, "currentAmps");
        CeeSubstepContext.validateSchedule(substep, totalSubsteps, substepSeconds);
    }
}
