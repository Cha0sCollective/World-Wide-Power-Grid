package org.cha0scollective.wwpg.api;

/**
 * Valid oriented electrical measurements over the accepted substeps of one tick.
 * Means are arithmetic substep means; RMS is sqrt(mean(square(sample))).
 * meanPowerWatts is mean(V*I), not mean(V)*mean(I). energyJoules is the signed
 * sum of V*I*substepSeconds; positive values denote absorbed energy.
 */
public record ElementResult(double lastVoltageVolts, double meanVoltageVolts, double rmsVoltageVolts,
                            double lastCurrentAmps, double meanCurrentAmps, double rmsCurrentAmps,
                            double meanPowerWatts, double energyJoules) {
    /**
     * Constructs ElementResult with validated captured values.
     * @param lastVoltageVolts signed last voltage in volts; finite, with magnitude at most 1e15 (inclusive)
     * @param meanVoltageVolts signed mean voltage in volts; finite, with magnitude at most 1e15 (inclusive)
     * @param rmsVoltageVolts RMS voltage in volts in [0, 1e15] inclusive
     * @param lastCurrentAmps signed last current in amperes; finite, with magnitude at most 1e15 (inclusive)
     * @param meanCurrentAmps signed mean current in amperes; finite, with magnitude at most 1e15 (inclusive)
     * @param rmsCurrentAmps RMS current in amperes in [0, 1e15] inclusive
     * @param meanPowerWatts mean signed V*I in watts; finite, with magnitude at most 1e15 (inclusive)
     * @param energyJoules signed absorbed energy in joules; finite, with magnitude at most 1e15 (inclusive)
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     */
    public ElementResult {
        ApiValidation.finite(lastVoltageVolts, "lastVoltageVolts");
        ApiValidation.finite(meanVoltageVolts, "meanVoltageVolts");
        ApiValidation.nonnegative(rmsVoltageVolts, "rmsVoltageVolts");
        ApiValidation.finite(lastCurrentAmps, "lastCurrentAmps");
        ApiValidation.finite(meanCurrentAmps, "meanCurrentAmps");
        ApiValidation.nonnegative(rmsCurrentAmps, "rmsCurrentAmps");
        ApiValidation.finite(meanPowerWatts, "meanPowerWatts");
        ApiValidation.finite(energyJoules, "energyJoules");
    }
}
