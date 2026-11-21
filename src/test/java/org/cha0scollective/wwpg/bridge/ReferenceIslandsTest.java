package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.*;
import org.patryk3211.powergrid.electricity.sim.node.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceIslandsTest {
    @Test void physicalEarthOnPrimaryDoesNotSuppressSecondaryZero() {
        var network = new ElectricalNetwork(false);
        var p = new FloatingNode(); var n = new FloatingNode();
        var s = new FloatingNode(); var t = new FloatingNode();
        for (var node : List.of(p,n,s,t)) network.addNode(node);
        var stamp = new TransformerStamp(network,p,n,s,t,4.6);
        var source = new VoltageSourceCoupling(p,n,0); network.addNode(source);
        var earth = new ElectricWire(1,n,null); network.addWire(earth);
        var reference = new CurrentSourceWire(s,null,1000); network.addWire(reference);
        var windings = Map.of(stamp.coupling(),List.<IElectricNode>of(p,n));
        var separate = Map.of(stamp.coupling(),List.of(windings.get(stamp.coupling()),List.<IElectricNode>of(s,t)));
        var regions = ReferenceIslands.partition(network,List.of(earth,reference),Set.of(reference),separate);
        assertSame(regions.get(p),regions.get(n)); assertSame(regions.get(s),regions.get(t));
        assertNotSame(regions.get(p),regions.get(s));
        assertTrue(regions.get(p).grounded); assertFalse(regions.get(s).grounded);
        var bond = new ElectricWire(1,n,t); network.addWire(bond);
        regions = ReferenceIslands.partition(network,List.of(earth,reference,bond),Set.of(reference),separate);
        assertSame(regions.get(p),regions.get(s)); assertTrue(regions.get(s).grounded);
    }

    @Test void sharedTerminalAndNativeVoltageSourceJoinReferences() {
        var network = new ElectricalNetwork(false);
        var p = new FloatingNode(); var common = new FloatingNode(); var s = new FloatingNode(); var extra = new FloatingNode();
        for (var node : List.of(p,common,s,extra)) network.addNode(node);
        var stamp = new TransformerStamp(network,p,common,s,common,.5);
        network.addNode(new VoltageSourceCoupling(s,extra,0));
        var regions = ReferenceIslands.partition(network,List.of(),Set.of(),
                Map.of(stamp.coupling(),List.of(List.<IElectricNode>of(p,common),List.<IElectricNode>of(s,common))));
        assertSame(regions.get(p),regions.get(s)); assertSame(regions.get(p),regions.get(extra));
        assertFalse(regions.get(p).grounded);
    }
}
