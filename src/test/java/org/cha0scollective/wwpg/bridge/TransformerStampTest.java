package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import static org.junit.jupiter.api.Assertions.*;

class TransformerStampTest {
    @Test void ratioPolarityCurrentAndParameterChanges() {
        var network = new ElectricalNetwork(false);
        network.warmUp(-1);
        var p1 = new FloatingNode(); var p2 = new FloatingNode();
        var s1 = new FloatingNode(); var s2 = new FloatingNode();
        for (var n : new FloatingNode[] {p1, p2, s1, s2}) network.addNode(n);
        network.addWire(new ElectricWire(0.001, p2, null));
        network.addWire(new ElectricWire(0.001, s2, null));
        var source = new VoltageSourceCoupling(p1, p2, 0);
        source.setVoltage(20); network.addNode(source);
        network.addWire(new ElectricWire(10, s1, s2));
        var transformer = new TransformerStamp(network, p1, p2, s1, s2, 2);
        network.calculate(1);
        assertEquals(10, s1.getVoltage() - s2.getVoltage(), 1e-4);
        assertEquals(0.5, transformer.primaryCurrent(), 1e-4);
        assertEquals(-1, transformer.secondaryCurrent(), 1e-4);
        transformer.update(0.5);
        network.calculate(1);
        assertEquals(40, s1.getVoltage() - s2.getVoltage(), 1e-4);
        assertEquals(8, transformer.primaryCurrent(), 1e-4);
        transformer.update(-2);
        network.calculate(1);
        assertEquals(-10, s1.getVoltage() - s2.getVoltage(), 1e-4);
        assertEquals(0.5, transformer.primaryCurrent(), 1e-4);
        assertEquals(1, transformer.secondaryCurrent(), 1e-4);
    }

    @Test void invalidRatiosCannotReachPg() {
        for (double ratio : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE, Double.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> TransformerStamp.ratio(ratio));
    }

    @Test void neutralRegulatorAndSignedStepsKeepTheSameCoupling() {
        var network = new ElectricalNetwork(false);
        network.warmUp(-1);
        var input = new FloatingNode(); var output = new FloatingNode(); var dividedInput = new FloatingNode();
        var ground = new FloatingNode();
        for (var node : new FloatingNode[]{input, output, dividedInput, ground}) network.addNode(node);
        network.addWire(new ElectricWire(.001, ground, null));
        var supply = new VoltageSourceCoupling(input, ground, 0);
        supply.setVoltage(100);
        network.addNode(supply);
        network.addWire(new ElectricWire(.01, input, dividedInput));
        network.addWire(new ElectricWire(1000, output, ground));
        var transformer = new TransformerStamp(network, input, output, ground, dividedInput, 0);
        for (double ratio : new double[]{0, .1, 0, -.1, 0}) {
            transformer.update(ratio);
            network.calculate(1);
            assertSame(network, transformer.network());
            assertEquals(100 * (1 + ratio), output.getVoltage(), .002);
            assertEquals(output.getVoltage() / 1000, transformer.primaryCurrent(), 1e-6);
            assertEquals(-ratio * transformer.primaryCurrent(), transformer.secondaryCurrent(), 1e-6);
        }
    }
}
