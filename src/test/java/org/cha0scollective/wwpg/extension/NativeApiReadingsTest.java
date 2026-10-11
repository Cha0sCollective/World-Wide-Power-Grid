package org.cha0scollective.wwpg.extension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeApiReadingsTest {
    @Test void differentialRmsUsesSimultaneousWaveformsRatherThanSubtractingRmsValues() {
        double[] first = {10, -10, 10, -10};
        double[] opposite = {-10, 10, -10, 10};
        assertEquals(10, NativeApiReadings.summarize(first).rmsVoltageVolts(), 1e-12);
        assertEquals(10, NativeApiReadings.summarize(opposite).rmsVoltageVolts(), 1e-12);
        var measurement = NativeApiReadings.difference(first, opposite);
        assertEquals(-20, measurement.lastVoltageVolts(), 1e-12);
        assertEquals(0, measurement.meanVoltageVolts(), 1e-12);
        assertEquals(20, measurement.rmsVoltageVolts(), 1e-12);
        assertEquals(0, NativeApiReadings.difference(first, first).rmsVoltageVolts(), 1e-12);
    }

    @Test void differentialDcPolarityIsAminusBWithUnsignedRms() {
        double[] positive = {12, 12, 12}, negative = {3, 3, 3};
        var forward = NativeApiReadings.difference(positive, negative);
        var reverse = NativeApiReadings.difference(negative, positive);
        assertEquals(9, forward.lastVoltageVolts(), 1e-12);
        assertEquals(9, forward.meanVoltageVolts(), 1e-12);
        assertEquals(-9, reverse.meanVoltageVolts(), 1e-12);
        assertEquals(9, reverse.rmsVoltageVolts(), 1e-12);
    }

    @Test void incompleteNonfiniteOrExtremeWaveformsCannotBecomeValidNumbers() {
        assertThrows(IllegalArgumentException.class, () -> NativeApiReadings.summarize(new double[0]));
        assertThrows(IllegalArgumentException.class,
                () -> NativeApiReadings.difference(new double[]{1}, new double[]{1, 2}));
        assertThrows(IllegalArgumentException.class, () -> NativeApiReadings.summarize(new double[]{Double.NaN}));
        assertThrows(IllegalArgumentException.class, () -> NativeApiReadings.summarize(new double[]{Double.POSITIVE_INFINITY}));
        assertThrows(IllegalArgumentException.class, () -> NativeApiReadings.summarize(new double[]{2e15}));
        assertEquals(1e15, NativeApiReadings.summarize(new double[]{1e15, 1e15}).rmsVoltageVolts(), 1);
    }
}
