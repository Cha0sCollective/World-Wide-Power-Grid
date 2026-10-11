package org.cha0scollective.wwpg.api;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VoltageMeasurementTest {
    private static final TickContext TICK = new TickContext(1, .05, 16);

    @Test void acceptedZeroHasNumbersWhileEveryInvalidStatusIsEmpty() {
        var zero = new VoltageMeasurement(TICK, MeasurementStatus.VALID, Optional.of(new NodeResult(0, 0, 0)), "");
        assertEquals(0, zero.voltage().orElseThrow().lastVoltageVolts());
        for (var status : MeasurementStatus.values()) {
            if (status == MeasurementStatus.VALID) continue;
            assertTrue(VoltageMeasurement.unavailable(TICK, status, "No accepted frame").voltage().isEmpty());
            assertThrows(IllegalArgumentException.class,
                    () -> new VoltageMeasurement(TICK, status, zero.voltage(), ""));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new VoltageMeasurement(TICK, MeasurementStatus.VALID, Optional.empty(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> VoltageMeasurement.unavailable(TICK, MeasurementStatus.VALID, ""));
    }
}
