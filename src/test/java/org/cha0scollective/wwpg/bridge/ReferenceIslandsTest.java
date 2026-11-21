package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.*;
import org.patryk3211.powergrid.electricity.sim.node.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceIslandsTest {
    @Test void physicalEarthOnPrimaryDoesNotSuppressSecondaryZero() {
        var network = new ElectricalNetwork(false);
        var primary = new FloatingNode();
        var primaryReturn = new FloatingNode();
        var secondary = new FloatingNode();
        var secondaryReturn = new FloatingNode();
        for (var node : List.of(primary, primaryReturn, secondary, secondaryReturn))
            network.addNode(node);
        var stamp = new TransformerStamp(network, primary, primaryReturn, secondary, secondaryReturn, 4.6);
        network.addNode(new VoltageSourceCoupling(primary, primaryReturn, 0));
        var earth = new ElectricWire(1, primaryReturn, null);
        var reference = new CurrentSourceWire(secondary, null, 1000);
        network.addWire(earth);
        network.addWire(reference);
        var windings = Map.of(stamp.coupling(),
                List.of(List.<IElectricNode>of(primary, primaryReturn), List.<IElectricNode>of(secondary, secondaryReturn)));
        var regions = ReferenceIslands.partition(network, List.of(earth, reference), Set.of(reference), windings);
        assertSame(regions.get(primary), regions.get(primaryReturn));
        assertSame(regions.get(secondary), regions.get(secondaryReturn));
        assertNotSame(regions.get(primary), regions.get(secondary));
        assertTrue(regions.get(primary).grounded);
        assertFalse(regions.get(secondary).grounded);

        var bond = new ElectricWire(1, primaryReturn, secondaryReturn);
        network.addWire(bond);
        regions = ReferenceIslands.partition(network, List.of(earth, reference, bond), Set.of(reference), windings);
        assertSame(regions.get(primary), regions.get(secondary));
        assertTrue(regions.get(secondary).grounded);
    }

    @Test void sharedTerminalAndNativeVoltageSourceJoinReferences() {
        var network = new ElectricalNetwork(false);
        var primary = new FloatingNode();
        var common = new FloatingNode();
        var secondary = new FloatingNode();
        var extra = new FloatingNode();
        for (var node : List.of(primary, common, secondary, extra)) network.addNode(node);
        var stamp = new TransformerStamp(network, primary, common, secondary, common, .5);
        network.addNode(new VoltageSourceCoupling(secondary, extra, 0));
        var windings = Map.of(stamp.coupling(),
                List.of(List.<IElectricNode>of(primary, common), List.<IElectricNode>of(secondary, common)));
        var regions = ReferenceIslands.partition(network, List.of(), Set.of(), windings);
        assertSame(regions.get(primary), regions.get(secondary));
        assertSame(regions.get(primary), regions.get(extra));
        assertFalse(regions.get(primary).grounded);

        network.addNode(new VoltageSourceCoupling(extra, null, 0));
        regions = ReferenceIslands.partition(network, List.of(), Set.of(), windings);
        assertTrue(regions.get(primary).grounded);
    }
}
