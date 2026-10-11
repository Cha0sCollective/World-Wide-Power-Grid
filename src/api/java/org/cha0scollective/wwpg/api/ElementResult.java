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
