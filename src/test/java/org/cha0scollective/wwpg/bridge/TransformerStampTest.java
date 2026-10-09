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
        for (double ratio : new double[] {0, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE})
            assertThrows(IllegalArgumentException.class, () -> TransformerStamp.ratio(ratio));
    }
}
