package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;

import static org.junit.jupiter.api.Assertions.*;

class PropertyCurrentSourcePowerTest {
    private static final class CountingNode extends FloatingNode {
        double value;
        int reads;
        CountingNode(double value) { this.value = value; }
        @Override public double getVoltage() { reads++; return value; }
    }

    // Unit JVMs do not apply mixins. The applied fast path is covered by
    // PlainWirePowerGameTests on each backend; these retain the stamp's numeric oracle.
    @Test void untransformedPropertyStampRetainsNativePower() {
        var network = new ElectricalNetwork(false);
        try {
            for (double voltage : new double[]{10, -10, 0.0, -0.0, Double.MIN_VALUE, 1e-308, 1e150}) {
                for (double conductance : new double[]{.25, 0.0, -0.0, Double.MIN_VALUE, 1e150}) {
                    var referenceNode = new CountingNode(voltage);
                    var actualNode = new CountingNode(voltage);
                    var reference = new CurrentSourceWire(referenceNode, null, conductance);
                    var actual = new PropertyCurrentSourceWire(actualNode, null, conductance);
                    reference.setNetwork(network); actual.setNetwork(network);
                    for (double sourceCurrent : new double[]{100, 0, -100}) {
                        reference.setCurrent(sourceCurrent); actual.setCurrent(sourceCurrent);
                        referenceNode.reads = actualNode.reads = 0;
                        assertSameBits(reference.internalPower(), actual.internalPower());
                        assertEquals(2, referenceNode.reads, "Native reference should repeat its differential read");
                        assertEquals(2, actualNode.reads, "Untransformed marker should retain inherited callbacks");
                    }
                }
            }
        } finally { network.cleanup(); }
    }

    @Test void sixteenPowerSamplesKeepNativeAggregationAndParameterChanges() {
        var network = new ElectricalNetwork(false);
        try {
            var referenceFirst = new CountingNode(0); var referenceSecond = new CountingNode(0);
            var actualFirst = new CountingNode(0); var actualSecond = new CountingNode(0);
            var reference = new CurrentSourceWire(referenceFirst, referenceSecond, .25);
            var actual = new PropertyCurrentSourceWire(actualFirst, actualSecond, .25);
            reference.setNetwork(network); actual.setNetwork(network);
            reference.prepare(16); actual.prepare(16);
            double expected = 0;
            for (int step = 0; step < 16; step++) {
                double first = step % 2 == 0 ? step : -step;
                double second = step % 3;
                double conductance = step % 4 == 0 ? .5 : .25;
                referenceFirst.value = actualFirst.value = first;
                referenceSecond.value = actualSecond.value = second;
                reference.setConductance(conductance); actual.setConductance(conductance);
                reference.setCurrent(step - 8); actual.setCurrent(step - 8);
                reference.postMicroTick(); actual.postMicroTick();
                expected += ((first - second) * conductance) * (first - second) / 16;
                assertSameBits(reference.power(), actual.power());
            }
            assertSameBits(expected, actual.power());
            assertEquals(32, referenceFirst.reads); assertEquals(32, referenceSecond.reads);
            assertEquals(32, actualFirst.reads); assertEquals(32, actualSecond.reads);
            reference.prepare(16); actual.prepare(16);
            assertSameBits(0.0, actual.power());
            assertSameBits(reference.power(), actual.power());
        } finally { network.cleanup(); }
    }

    @Test void detachedAndNonfiniteCasesKeepInheritedNativeBehavior() {
        var network = new ElectricalNetwork(false);
        try {
            for (boolean attached : new boolean[]{false, true}) {
                for (double conductance : new double[]{.25, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                    for (double voltage : new double[]{-10, 0.0, -0.0, Double.NaN, Double.POSITIVE_INFINITY}) {
                        var referenceNode = new CountingNode(voltage);
                        var actualNode = new CountingNode(voltage);
                        var reference = new CurrentSourceWire(referenceNode, null, conductance);
                        var actual = new PropertyCurrentSourceWire(actualNode, null, conductance);
                        if (attached) { reference.setNetwork(network); actual.setNetwork(network); }
                        assertSameBits(reference.internalPower(), actual.internalPower());
                        if (!attached || !Double.isFinite(conductance))
                            assertEquals(referenceNode.reads, actualNode.reads, "Exceptional path should retain native fallback");
                    }
                }
            }
        } finally { network.cleanup(); }
    }

    private static void assertSameBits(double expected, double actual) {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual),
                "Power multiplication order, signed zero or nonfinite handling changed");
    }
}
