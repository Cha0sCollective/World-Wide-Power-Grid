package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;

import static org.junit.jupiter.api.Assertions.*;

class LinearBranchTest {
    @Test void nortonSourceHasCeePolarityAndRespondsToParameterChanges() {
        var network = new ElectricalNetwork(false);
        network.warmUp(-1);
        var a = new FloatingNode(); var b = new FloatingNode();
        network.addNode(a); network.addNode(b);
        network.addWire(new ElectricWire(0.001, a, null));
        var load = new ElectricWire(9, a, b);
        network.addWire(load);
        var bridge = new LinearBranch(network, a, b, ElectricalProperties.fromThevenin(1, 10), false);
        network.calculate(1);
        assertEquals(9, b.getVoltage() - a.getVoltage(), 1e-5);
        assertEquals(1, bridge.current(), 1e-5);

        bridge.update(ElectricalProperties.fromThevenin(1, 20));
        network.calculate(1);
        assertEquals(18, b.getVoltage() - a.getVoltage(), 1e-5);
        load.setResistance(19);
        network.calculate(1);
        assertEquals(19, b.getVoltage() - a.getVoltage(), 1e-5);
        assertSame(network, bridge.network());
        assertEquals(1, bridge.current(), 1e-5);
    }

    @Test void idealSourcePreservesVoltageAndSignedCurrent() {
        var network = new ElectricalNetwork(false);
        network.warmUp(-1);
        var a = new FloatingNode(); var b = new FloatingNode();
        network.addNode(a); network.addNode(b);
        network.addWire(new ElectricWire(0.001, a, null));
        network.addWire(new ElectricWire(5, a, b));
        var bridge = new LinearBranch(network, a, b, new ElectricalProperties(1e11, 10, 0, true), true);
        network.calculate(1);
        assertEquals(-10, a.getVoltage() - b.getVoltage(), 1e-5);
        assertEquals(2, bridge.current(), 1e-5);
        bridge.update(new ElectricalProperties(1e11, -10, 0, true));
        network.calculate(1);
        assertEquals(10, a.getVoltage() - b.getVoltage(), 1e-5);
        assertEquals(-2, bridge.current(), 1e-5);
    }
}
