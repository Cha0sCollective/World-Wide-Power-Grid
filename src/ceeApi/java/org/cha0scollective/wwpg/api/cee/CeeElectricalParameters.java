package org.cha0scollective.wwpg.api.cee;

/**
 * Immutable CEE-native two-terminal parameters. Conductance is nonnegative
 * siemens. currentSourceAmps uses CEE's current-source convention; voltageSourceVolts
 * uses CEE's native source polarity. These values correspond directly to CEE
 * ElectricalProperties getters; WWPG handles the polarity change for PG.
 * idealVoltageSource keeps the ideal constraint present at a zero crossing.
 */
public record CeeElectricalParameters(double conductanceSiemens, double currentSourceAmps,
                                     double voltageSourceVolts, boolean idealVoltageSource) {
    public CeeElectricalParameters {
        requireFinite(conductanceSiemens, "conductanceSiemens");
        requireFinite(currentSourceAmps, "currentSourceAmps");
        requireFinite(voltageSourceVolts, "voltageSourceVolts");
        if (conductanceSiemens < 0) throw new IllegalArgumentException("conductanceSiemens must be nonnegative");
        if (voltageSourceVolts != 0 && !idealVoltageSource)
            throw new IllegalArgumentException("nonzero CEE voltageSourceVolts requires idealVoltageSource=true");
    }

    /** Standard polarity-reversed snapshot, preserving conductance and source family. */
    public CeeElectricalParameters inverted() {
        return new CeeElectricalParameters(conductanceSiemens, -currentSourceAmps, -voltageSourceVolts, idealVoltageSource);
    }

    static void requireFinite(double value, String field) {
        if (!Double.isFinite(value) || Math.abs(value) > 1e15)
            throw new IllegalArgumentException(field + " must be finite and have magnitude at most 1e15: " + value);
    }
}
