package org.cha0scollective.wwpg.api.cee;

/**
 * Accepted electrical substep. Voltage is first-node minus second-node voltage;
 * current flows from the first node to the second. Invalid or failed solves do
 * not construct this result and must not invoke an adapter's afterSubstep callback.
 */
public record CeeSubstepResult(double voltageVolts, double currentAmps, int substep,
                              int totalSubsteps, double substepSeconds) {
    public CeeSubstepResult {
        CeeElectricalParameters.requireFinite(voltageVolts, "voltageVolts");
        CeeElectricalParameters.requireFinite(currentAmps, "currentAmps");
        CeeSubstepContext.validateSchedule(substep, totalSubsteps, substepSeconds);
    }
}
