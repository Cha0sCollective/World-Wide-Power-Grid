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
    /**
     * Constructs CeeElectricalParameters with validated captured values.
     * @param conductanceSiemens conductance in siemens, finite in [0, 1e15] inclusive
     * @param currentSourceAmps CEE-native signed source current in amperes; finite, with magnitude at most 1e15 (inclusive)
     * @param voltageSourceVolts CEE-native signed source voltage in volts; finite, with magnitude at most 1e15 (inclusive)
     * @param idealVoltageSource must be true for nonzero voltageSourceVolts; retain true for ideal-source zero crossings
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     */
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
