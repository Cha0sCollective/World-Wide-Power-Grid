package org.cha0scollective.wwpg.api.cee;

/**
 * Previous accepted branch voltage within the current game tick and authoritative
 * substep schedule. previousVoltageVolts is first-node voltage minus second-node
 * voltage. Substep zero always receives zero, matching CEE's native initial
 * voltage arrays. This observation never substitutes for persisted history;
 * initialize every tick's storage model from the source's committed native state.
 */
public record CeeSubstepContext(double previousVoltageVolts, int substep, int totalSubsteps,
                               double substepSeconds) {
    public CeeSubstepContext {
        CeeElectricalParameters.requireFinite(previousVoltageVolts, "previousVoltageVolts");
        validateSchedule(substep, totalSubsteps, substepSeconds);
    }

    static void validateSchedule(int substep, int totalSubsteps, double substepSeconds) {
        if (totalSubsteps < 1 || totalSubsteps > 4096 || substep < 0 || substep >= totalSubsteps)
            throw new IllegalArgumentException("substep must be 0..totalSubsteps-1 and totalSubsteps must be 1..4096");
        CeeElectricalParameters.requireFinite(substepSeconds, "substepSeconds");
        if (substepSeconds <= 0 || substepSeconds > 1)
            throw new IllegalArgumentException("substepSeconds must be positive and at most 1 simulated second");
    }
}
