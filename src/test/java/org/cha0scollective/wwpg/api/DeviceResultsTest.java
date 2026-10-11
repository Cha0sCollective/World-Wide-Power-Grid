package org.cha0scollective.wwpg.api;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class DeviceResultsTest {
    private static final TickContext TICK = new TickContext(123, .05, 16);

    private static ElementResult reading(double current) {
        return new ElementResult(12, 12, 12, current, current, Math.abs(current), 12 * current, .05 * 12 * current);
    }

    @Test void legitimateZeroAndUnavailableReadingsAreDistinct() {
        var valid = new DeviceResults(TICK, MeasurementStatus.VALID, Map.of("ground", new NodeResult(0, 0, 0)),
                Map.of("open_switch", reading(0)), Map.of(), "");
        assertEquals(0, valid.node("ground").orElseThrow().lastVoltageVolts());
        assertEquals(0, valid.element("open_switch").orElseThrow().meanCurrentAmps());
        for (var status : MeasurementStatus.values()) {
            if (status == MeasurementStatus.VALID) continue;
            var unavailable = DeviceResults.unavailable(TICK, status, "waiting for endpoint");
            assertTrue(unavailable.node("ground").isEmpty());
            assertTrue(unavailable.element("open_switch").isEmpty());
            assertTrue(unavailable.electricalState().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> new DeviceResults(TICK, status,
                    valid.nodes(), valid.elements(), Map.of(), "must not expose stale values"));
        }
        assertThrows(IllegalArgumentException.class, () -> DeviceResults.unavailable(TICK, MeasurementStatus.VALID, ""));
    }

    @Test void acceptedStorageSnapshotsAndMeasurementsAreImmutableAndOriented() {
        var nodes = new HashMap<>(Map.of("positive", new NodeResult(12, 12, 12)));
        var elements = new HashMap<>(Map.of("battery", reading(-2)));
        var states = new HashMap<String, ElementState>(Map.of("battery", new ElementState.Capacitor(12)));
        var snapshot = new DeviceResults(TICK, MeasurementStatus.VALID, nodes, elements, states, "");
        nodes.clear(); elements.clear(); states.clear();
        assertEquals(-24, snapshot.element("battery").orElseThrow().meanPowerWatts());
        assertEquals(-1.2, snapshot.element("battery").orElseThrow().energyJoules(), 1e-12);
        assertEquals(12, ((ElementState.Capacitor) snapshot.state("battery").orElseThrow()).voltageVolts());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.electricalState().clear());
    }

    @Test void rejectsUnmeasuredHistoryAndInvalidNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceResults(TICK, MeasurementStatus.VALID,
                Map.of(), Map.of(), Map.of("forgotten", new ElementState.Inductor(1)), ""));
        assertThrows(IllegalArgumentException.class, () -> new NodeResult(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new NodeResult(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new ElementState.Capacitor(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new ElementResult(0, 0, 0, 0, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> DeviceResults.unavailable(TICK, MeasurementStatus.ISOLATED, "x".repeat(1025)));
    }

    @Test void terminalQueryCannotHideAnUnavailableReadingAsZero() {
        assertEquals(0, new TerminalMeasurement(MeasurementStatus.VALID, OptionalDouble.of(0), "").voltageVolts().orElseThrow());
        assertTrue(new TerminalMeasurement(MeasurementStatus.NOT_READY, OptionalDouble.empty(), "unloaded").voltageVolts().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new TerminalMeasurement(MeasurementStatus.NOT_READY, OptionalDouble.of(0), ""));
        assertThrows(IllegalArgumentException.class, () -> new TerminalMeasurement(MeasurementStatus.VALID, OptionalDouble.empty(), ""));
    }

    @Test void timingIsSimulatedAndExplicitRatherThanWallClock() {
        assertEquals(.003125, TICK.substepSeconds(), 1e-15);
        assertEquals(6.15, TICK.startTimeSeconds(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new TickContext(0, .05, 0));
        assertThrows(IllegalArgumentException.class, () -> new TickContext(0, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new TickContext(-1, .05, 16));
    }

    @Test void modelsRejectInvalidNumericalInputsBeforeReachingASolver() {
        assertThrows(IllegalArgumentException.class, () -> new ElectricalModel.Resistor(0));
        assertThrows(IllegalArgumentException.class, () -> new ElectricalModel.Switch(true, -1));
        assertThrows(IllegalArgumentException.class, () -> new ElectricalModel.VoltageSource(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ElectricalModel.Capacitor(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ElectricalModel.Inductor(1, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new ElectricalModel.AcVoltageSource(12, -60, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ElectricalModel.Diode(1e-9, .1, -273.15, 1));
        assertEquals(1e-14, new ElectricalModel.Diode(1e-14, .1, 20, 1).saturationCurrentAmps());
        assertThrows(IllegalArgumentException.class, () -> new ElectricalModel.Transformer("s1", "s2", 0));
        assertThrows(IllegalArgumentException.class, () -> new ElectricalModel.Transformer("s1", "s1", 1));
        assertEquals(-2, new ElectricalModel.Transformer("s1", "s2", -2).ratio());
    }
}
